package example.rules;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import example.rules.dao.RuleStore;
import example.rules.model.RuleConfig;
import example.rules.model.RuleConfig.Condition;
import example.rules.model.RuleConfig.Assignment;
import example.rules.result.ExecuteResult;
import example.rules.result.RuleState;
import example.rules.service.RuleService;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@AutoConfigureMockMvc
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:rules;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.sql.init.mode=always"
})
class RuleEngineIntegrationTest {
    @Autowired ObjectMapper json;
    @Autowired MockMvc http;
    @Autowired JdbcTemplate jdbc;
    @Autowired RuleService service;
    @Autowired RuleStore store;

    private RuleConfig sample() throws Exception {
        RuleConfig config = json.readValue(Files.readAllBytes(Paths.get("examples/rule-config.json")), RuleConfig.class);
        config.businessCode = "business_" + UUID.randomUUID().toString().replace("-", "");
        return config;
    }

    private ObjectNode executeBody(String businessCode, String params) throws Exception {
        ObjectNode body = json.createObjectNode();
        body.put("business_code", businessCode);
        body.set("params", json.readTree(params));
        return body;
    }

    @Test
    void createQueryAndExecuteCurrentConfigWithNestedJsonFields() throws Exception {
        RuleConfig config = sample();
        http.perform(post("/api/rule-sets").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(config)))
                .andExpect(status().isCreated());
        http.perform(get("/api/rule-sets/" + config.businessCode))
                .andExpect(status().isOk()).andExpect(jsonPath("$.config.conditions.length()").value(8))
                .andExpect(jsonPath("$.config.conditions[0].field").value("pricing.metrics.grossProfitRate"));
        http.perform(get("/api/rule-sets/" + config.businessCode + "/conditions"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(8));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_rule_condition WHERE field='pricing.metrics.grossProfitRate' "
                + "AND rule_set_id=(SELECT id FROM audit_rule_set WHERE business_code=?)", Integer.class,
                config.businessCode)).isEqualTo(6);
        ObjectNode request = executeBody(config.businessCode,
                "{\"categoryId1\":\"CAT_1\",\"pricing\":{\"metrics\":{\"grossProfitRate\":55},\"costIncreaseRate\":50}}");
        http.perform(post("/api/rules/execute").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results.decision.sales").value("REVIEW_LEVEL_1"))
                .andExpect(jsonPath("$.results.decision.risk").value("CATEGORY_RISK_HIGH"))
                .andExpect(jsonPath("$.results.decision.cost").value("COST_INCREASE_50"))
                .andExpect(jsonPath("$.results.pricing.metrics.grossProfitRate").value(55))
                .andExpect(jsonPath("$.matches.length()").value(4));
        RuleState current = service.read(config.businessCode);
        Condition risk = current.config.conditions.stream()
                .filter(c -> "risk_by_category".equals(c.ruleCode)).findFirst().get();
        risk.thresholdValue = new BigDecimal("60");
        service.update(config.businessCode, current.config);
        http.perform(post("/api/rules/execute").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.matches.length()").value(3))
                .andExpect(jsonPath("$.results.decision.risk").doesNotExist());
    }

    @Test
    void insertAndUpdateConditionRegeneratesScript() throws Exception {
        RuleConfig config = sample();
        service.create(config);
        Condition added = json.readValue("{\"ruleCode\":\"cost\",\"ruleName\":\"成本上调幅度审核规则\","
                + "\"ruleOrder\":2,\"conditionCode\":\"increase80\","
                + "\"conditionType\":\"COST_INCREASE\",\"field\":\"pricing.costIncreaseRate\","
                + "\"predicateType\":\"THRESHOLD\",\"comparison\":\"GE\",\"thresholdValue\":80,"
                + "\"resultAssignments\":[{\"field\":\"decision.cost\",\"value\":\"COST_INCREASE_80\"}],\"conditionOrder\":2}", Condition.class);
        http.perform(post("/api/rule-sets/" + config.businessCode + "/conditions")
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(added)))
                .andExpect(status().isCreated());
        added.thresholdValue = new BigDecimal("60");
        added.resultAssignments.get(0).value = json.getNodeFactory().textNode("COST_INCREASE_60");
        http.perform(put("/api/rule-sets/" + config.businessCode
                + "/conditions/cost/increase80")
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(added)))
                .andExpect(status().isOk());
        ObjectNode request = executeBody(config.businessCode,
                "{\"pricing\":{\"metrics\":{\"grossProfitRate\":0},\"costIncreaseRate\":65}}");
        http.perform(post("/api/rules/execute").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results.decision.cost").value("COST_INCREASE_60"));
    }

    @Test
    void invalidPathAndConditionShapeAreRejectedBeforeDatabaseWrite() throws Exception {
        RuleConfig config = sample();
        config.conditions.get(0).field = "pricing..grossProfitRate";
        assertThatThrownBy(() -> service.create(config)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("field");
        config.conditions.get(0).field = "pricing.metrics.grossProfitRate";
        config.conditions.get(0).comparison = "GE";
        assertThatThrownBy(() -> service.create(config)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("区间条件");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_rule_set WHERE business_code=?",
                Integer.class, config.businessCode)).isZero();
    }

    @Test
    void omittedConditionIsSoftDeletedAndSameCodeCanBeRestored() throws Exception {
        RuleConfig config = sample();
        RuleState created = service.create(config);
        Condition removed = created.config.conditions.stream()
                .filter(c -> "risk_by_category".equals(c.ruleCode)).findFirst().get();
        long originalId = removed.id;
        RuleConfig update = created.config;
        update.conditions = new ArrayList<>(update.conditions);
        update.conditions.removeIf(c -> "risk_by_category".equals(c.ruleCode));
        service.update(config.businessCode, update);
        assertThat(jdbc.queryForObject("SELECT del_flag FROM audit_rule_condition WHERE id=?", Integer.class,
                originalId)).isEqualTo(1);
        RuleConfig restore = service.read(config.businessCode).config;
        restore.conditions.add(removed);
        service.update(config.businessCode, restore);
        Condition restored = service.read(config.businessCode).config.conditions.stream()
                .filter(c -> "risk_by_category".equals(c.ruleCode)).findFirst().get();
        assertThat(restored.id).isEqualTo(originalId);
    }

    @Test
    void unchangedSaveAndWrongNumericTypeReturnsBadRequest() throws Exception {
        RuleConfig config = sample();
        service.create(config);
        RuleState current = service.read(config.businessCode);
        service.update(config.businessCode, current.config);
        ObjectNode request = executeBody(config.businessCode,
                "{\"pricing\":{\"metrics\":{\"grossProfitRate\":\"bad\"},\"costIncreaseRate\":5}}");
        http.perform(post("/api/rules/execute").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void costIntervalAndSalesCostIncreaseOverrideUseConfiguredField() throws Exception {
        RuleConfig config = sample();
        Condition cost = config.conditions.stream()
                .filter(c -> "increase5".equals(c.conditionCode)).findFirst().get();
        cost.predicateType = "INTERVAL";
        cost.comparison = null;
        cost.thresholdValue = null;
        cost.lowerBound = new BigDecimal("5");
        cost.lowerInclusive = true;
        Condition band3 = config.conditions.stream()
                .filter(c -> "band3".equals(c.conditionCode)).findFirst().get();
        Assignment conditional = new Assignment();
        conditional.field = "decision.costIncreaseReview";
        conditional.value = json.readTree("true");
        conditional.when = "COST_INCREASE";
        band3.resultAssignments.add(conditional);
        service.create(config);
        ObjectNode request = executeBody(config.businessCode,
                "{\"pricing\":{\"metrics\":{\"grossProfitRate\":7},\"costIncreaseRate\":6}}");
        http.perform(post("/api/rules/execute").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results.decision.sales").value("COST_INCREASE_REVIEW"))
                .andExpect(jsonPath("$.results.decision.costIncreaseReview").value(true))
                .andExpect(jsonPath("$.results.decision.cost").value("COST_INCREASE_5"));
    }

    @Test
    void nestedResultsPreserveJsonTypesAndRejectScalarIntermediateNode() throws Exception {
        RuleConfig config = sample();
        Condition sales = config.conditions.stream()
                .filter(c -> "band4".equals(c.conditionCode)).findFirst().get();
        sales.resultAssignments.get(0).field = "decision.review.level";
        sales.resultAssignments.get(0).value = json.readTree("3");
        Assignment message = new Assignment();
        message.field = "decision.review.message";
        message.value = json.readTree("\"check margin\"");
        sales.resultAssignments.add(message);
        Condition risk = config.conditions.stream()
                .filter(c -> "risk_by_category".equals(c.ruleCode)).findFirst().get();
        risk.resultAssignments.get(0).field = "decision.review.risky";
        risk.resultAssignments.get(0).value = json.readTree("true");
        Condition highCost = config.conditions.stream()
                .filter(c -> "increase50".equals(c.conditionCode)).findFirst().get();
        highCost.resultAssignments.get(0).value = json.readTree("null");
        service.create(config);

        ObjectNode request = executeBody(config.businessCode,
                "{\"categoryId1\":\"CAT_1\",\"pricing\":{\"metrics\":{\"grossProfitRate\":55},\"costIncreaseRate\":0},\"decision\":{\"review\":{\"note\":\"keep\"}}}");
        http.perform(post("/api/rules/execute").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results.decision.review.level").value(3))
                .andExpect(jsonPath("$.results.decision.review.message").value("check margin"))
                .andExpect(jsonPath("$.results.decision.review.risky").value(true))
                .andExpect(jsonPath("$.results.decision.review.note").value("keep"));
        assertThat(request.at("/params/decision/review/level").isMissingNode()).isTrue();

        ObjectNode nullResult = executeBody(config.businessCode,
                "{\"pricing\":{\"metrics\":{\"grossProfitRate\":55},\"costIncreaseRate\":50}}");
        http.perform(post("/api/rules/execute").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(nullResult)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results.decision.cost").value(org.hamcrest.Matchers.nullValue()));

        ObjectNode collision = executeBody(config.businessCode,
                "{\"categoryId1\":\"CAT_1\",\"pricing\":{\"metrics\":{\"grossProfitRate\":55},\"costIncreaseRate\":0},\"decision\":\"scalar\"}");
        http.perform(post("/api/rules/execute").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(collision)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("中间节点不是JSON对象")));
    }

    @Test
    void batchExecutionUsesThreeCategoryIdsAndKeepsItemsIndependent() throws Exception {
        RuleConfig config = sample();
        Condition risk = config.conditions.stream()
                .filter(c -> "risk_by_category".equals(c.ruleCode)).findFirst().get();
        risk.categoryId2 = "CAT_2";
        risk.categoryId3 = "CAT_3";
        service.create(config);
        assertThat(service.read(config.businessCode).config.conditions.stream()
                .filter(c -> "risk_by_category".equals(c.ruleCode)).findFirst().get().categoryId3)
                .isEqualTo("CAT_3");

        ObjectNode batch = executeBody(config.businessCode, "["
                + "{\"categoryId1\":\"CAT_1\",\"categoryId2\":\"CAT_2\",\"categoryId3\":\"CAT_3\",\"pricing\":{\"metrics\":{\"grossProfitRate\":55},\"costIncreaseRate\":0}},"
                + "{\"categoryId1\":\"CAT_1\",\"categoryId2\":\"CAT_2\",\"categoryId3\":\"OTHER\",\"pricing\":{\"metrics\":{\"grossProfitRate\":55},\"costIncreaseRate\":0}}]");
        http.perform(post("/api/rules/execute").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(batch)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results.length()").value(2))
                .andExpect(jsonPath("$.results[0].decision.risk").value("CATEGORY_RISK_HIGH"))
                .andExpect(jsonPath("$.results[1].decision.risk").doesNotExist())
                .andExpect(jsonPath("$.matches[0].length()").value(2))
                .andExpect(jsonPath("$.matches[1].length()").value(1));
        http.perform(post("/api/rules/execute").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(executeBody(config.businessCode, "[{\"pricing\":{}},null]"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("params[1]")));
    }
}
