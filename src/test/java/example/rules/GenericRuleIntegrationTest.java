package example.rules;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.Arguments;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.jdbc.core.JdbcTemplate;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Stream;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:rules;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.sql.init.mode=always"
})
@AutoConfigureMockMvc
class GenericRuleIntegrationTest {
    @Autowired ObjectMapper json;
    @Autowired MockMvc http;
    @Autowired RuleService service;
    @Autowired RuleStore store;
    @Autowired DrlGenerator generator;
    @Autowired JdbcTemplate jdbc;

    private String file(String path) throws Exception {
        return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
    }
    private ObjectNode order() throws Exception { return (ObjectNode) json.readTree(file("examples/order-config.json")); }
    private ObjectNode request(String data) throws Exception {
        ObjectNode r = json.createObjectNode().put("rule_name", "order_approval");
        r.putObject("document").put("id", "ORDER-TEST").set("data", json.readTree(data));
        return r;
    }
    private JsonNode execute(JsonNode request) throws Exception {
        return json.readTree(http.perform(post("/api/rules/execute").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(request))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    @Test
    void orderHttpPublishesExecutesLogsAndExports() throws Exception {
        try (ExecutionLogCapture logs = new ExecutionLogCapture()) {
            MvcResult publication = http.perform(post("/api/rules/order_approval/publish").contentType(MediaType.APPLICATION_JSON)
                    .content(file("examples/order-config.json"))).andExpect(status().isOk()).andReturn();
            JsonNode version = json.readTree(publication.getResponse().getContentAsString(StandardCharsets.UTF_8));
            JsonNode response = execute(json.readTree(file("examples/execute-order.json")));
            assertThat(response.path("version").asLong()).isEqualTo(version.path("version").asLong());
            assertThat(response.path("policyId").isNull()).isTrue();
            assertThat(response.path("plan").path("decision").asText()).isEqualTo("MANUAL_APPROVAL");
            assertThat(response.path("plan").path("approvalSteps").size()).isEqualTo(2);
            assertThat(response.path("plan").path("approvalSteps").get(1).path("mode").asText()).isEqualTo("PARALLEL");
            assertThat(response.path("plan").path("ccAfterApproval").get(0).asText()).isEqualTo("ROLE:BUSINESS_LEADER");
            assertThat(response.path("results").path("discountRate").decimalValue()).isEqualByComparingTo("0.95");
            JsonNode record = logs.execution(response.path("executionId").asText());
            assertThat(record.path("input")).isEqualTo(json.valueToTree(json.readValue(file("examples/execute-order.json"), RuleService.ExecutionRequest.class)));
            assertThat(record.path("result")).isEqualTo(response);
            assertThat(record.path("rule_name").asText()).isEqualTo("order_approval");
            assertThat(record.path("version").asLong()).isEqualTo(version.path("version").asLong());
            assertThat(record.path("documentId").asText()).isEqualTo(record.path("input").path("document").path("id").asText());
            String saved = jdbc.queryForObject("SELECT config_json FROM rule_version WHERE rule_name=? AND version=?", String.class,
                    "order_approval", version.path("version").asLong());
            assertThat(json.readTree(saved).path("fields").size()).isEqualTo(5);
            Files.write(Paths.get("examples/order.drl"), version.path("drl").asText().getBytes(StandardCharsets.UTF_8));
            Files.write(Paths.get("examples/result-order.json"), (json.writerWithDefaultPrettyPrinter().writeValueAsString(response) + "\n")
                    .getBytes(StandardCharsets.UTF_8));
        }
    }

    @Test
    void smallOrderAndOverdueRejectionUseSameConfiguration() throws Exception {
        service.publish("order_approval", order());
        JsonNode small = execute(request("{\"orderAmount\":10000,\"customerLevel\":\"NORMAL\",\"overdue\":false}"));
        assertThat(small.path("plan").path("decision").asText()).isEqualTo("AUTO_APPROVE");
        JsonNode overdue = execute(request("{\"orderAmount\":15000,\"customerLevel\":\"VIP\",\"overdue\":true}"));
        assertThat(overdue.path("plan").path("decision").asText()).isEqualTo("REJECTED");
        assertThat(overdue.path("reasons").get(0).asText()).isEqualTo("客户存在逾期账款");
        assertThat(overdue.path("results").has("discountRate")).isFalse();
    }

    @Test
    void orderAmountPreservesDecimalPrecisionAcrossHttpAndStoredConfig() throws Exception {
        ObjectNode config = order();
        ObjectNode when = (ObjectNode) config.path("rules").get(1).path("when");
        when.set("value", json.readTree("10000.0000000000000000001"));
        ((ObjectNode) config.path("rules").get(0).path("when")).set("value", when.path("value"));
        service.publish("order_approval", config);
        JsonNode equal = execute(request("{\"orderAmount\":10000.0000000000000000001,\"customerLevel\":\"VIP\",\"overdue\":false}"));
        assertThat(equal.path("plan").path("decision").asText()).isEqualTo("AUTO_APPROVE");
        JsonNode above = execute(request("{\"orderAmount\":10000.0000000000000000002,\"customerLevel\":\"VIP\",\"overdue\":false}"));
        assertThat(above.path("plan").path("decision").asText()).isEqualTo("MANUAL_APPROVAL");
    }

    @Test
    void missingOrWrongTypesAndConflictingInputsReturn400() throws Exception {
        service.publish("order_approval", order());
        for (String data : Arrays.asList("{\"customerLevel\":\"VIP\",\"overdue\":false}",
                "{\"orderAmount\":\"15000\",\"customerLevel\":\"VIP\",\"overdue\":false}",
                "{\"orderAmount\":15000,\"customerLevel\":\"VIP\",\"overdue\":\"false\"}",
                "{\"orderAmount\":15000,\"customerLevel\":\"VIP\",\"overdue\":false,\"tags\":[123]}",
                "{\"orderAmount\":15000,\"customerLevel\":\"VIP\",\"overdue\":false,\"documentId\":\"OTHER\"}")) {
            http.perform(post("/api/rules/execute").contentType(MediaType.APPLICATION_JSON).content(request(data).toString()))
                    .andExpect(status().isBadRequest());
        }
        for (String path : Arrays.asList("/api/prices/validate", "/api/prices/validate-batch")) {
            http.perform(post(path).contentType(MediaType.APPLICATION_JSON).content("{\"rule_name\":\"order_approval\",\"documentId\":\"O\",\"product\":null,\"products\":[null]}"))
                    .andExpect(status().isNotFound());
        }
    }

    @Test
    void invalidConfigurationsAreRejectedBeforePublication() throws Exception {
        ObjectNode undeclared = order();
        ((ObjectNode) undeclared.path("rules").get(0).path("when")).put("field", "unknown");
        assertThatThrownBy(() -> service.publish("order_approval", undeclared)).hasMessageContaining("未声明");
        ObjectNode mismatch = order();
        ((ObjectNode) mismatch.path("rules").get(0).path("when")).put("value", "10000");
        assertThatThrownBy(() -> service.publish("order_approval", mismatch)).hasMessageContaining("NUMBER");
        ObjectNode typo = order().put("rulse", 1);
        assertThatThrownBy(() -> service.publish("order_approval", typo)).hasMessageContaining("格式错误");
        ObjectNode mixed = order(); mixed.putArray("policies");
        assertThatThrownBy(() -> service.publish("order_approval", mixed)).hasMessageContaining("混用");
        ObjectNode deep = order(); ObjectNode condition = json.createObjectNode().put("field", "orderAmount").put("operator", "EXISTS");
        for (int i = 0; i < 10; i++) {
            ObjectNode group = json.createObjectNode().put("logic", "NOT"); group.putArray("conditions").add(condition); condition = group;
        }
        ((ObjectNode) deep.path("rules").get(0)).set("when", condition);
        assertThatThrownBy(() -> service.publish("order_approval", deep)).hasMessageContaining("10层");
    }

    @Test
    void prioritiesResolveBaseConflictsAndOutputKeysDeterministically() throws Exception {
        ObjectNode c = order(); c.remove("outputs"); ArrayNode rules = c.putArray("rules");
        JsonNode exists = json.readTree("{\"field\":\"orderAmount\",\"operator\":\"EXISTS\"}");
        addRule(rules, "loser", "BASE", 0, exists, "[{\"type\":\"REJECT\",\"reason\":\"不应采用\"}]");
        addRule(rules, "chosen", "BASE", 50, exists, "[{\"type\":\"AUTO_APPROVE\"},{\"type\":\"SET_RESULT\",\"key\":\"route\",\"value\":\"base\"}]");
        addRule(rules, "tie_loser", "BASE", 50, exists, "[{\"type\":\"REJECT\",\"reason\":\"同优先级后置\"}]");
        addRule(rules, "lower", "SUPPLEMENT", 40, exists, "[{\"type\":\"SET_RESULT\",\"key\":\"route\",\"value\":\"lower\"}]");
        addRule(rules, "higher", "SUPPLEMENT", 100, exists, "[{\"type\":\"SET_RESULT\",\"key\":\"route\",\"value\":\"higher\"}]");
        service.publish("order_approval", c);
        JsonNode response = execute(request("{\"orderAmount\":15000,\"customerLevel\":\"VIP\",\"overdue\":false}"));
        assertThat(response.path("plan").path("decision").asText()).isEqualTo("AUTO_APPROVE");
        assertThat(response.path("results").path("route").asText()).isEqualTo("higher");
        assertThat(response.path("plan").path("matchedRules").toString())
                .isEqualTo("[\"order_approval:chosen\",\"order_approval:higher\",\"order_approval:lower\"]");
    }
    private void addRule(ArrayNode rules, String id, String kind, int priority, JsonNode when, String actions) throws Exception {
        ObjectNode r = rules.addObject().put("id", id).put("kind", kind).put("priority", priority);
        r.set("when", when); r.set("actions", json.readTree(actions));
    }

    @Test
    void literalsCannotInjectDrlAndBusinessResultsKeepJsonTypes() throws Exception {
        String text = "\" ); System.exit(0); //\n\\中文";
        ObjectNode c = order(); c.remove("outputs"); ArrayNode rules = c.putArray("rules");
        ObjectNode when = json.createObjectNode().put("field", "customerLevel").put("operator", "EQ").put("value", text);
        ObjectNode value = json.createObjectNode().put("message", text).put("enabled", true);
        ArrayNode actions = json.createArrayNode(); actions.addObject().put("type", "SET_RESULT").put("key", "payload").set("value", value);
        actions.addObject().put("type", "SET_RESULT").put("key", "empty").putNull("value");
        addRule(rules, "compute", "BASE", 0, when, actions.toString());
        service.publish("order_approval", c);
        ObjectNode req = request("{\"orderAmount\":100,\"customerLevel\":\"X\",\"overdue\":false}");
        ((ObjectNode) req.path("document").path("data")).put("customerLevel", text);
        JsonNode response = execute(req);
        assertThat(response.path("plan").path("decision").asText()).isEqualTo("MATCHED");
        assertThat(response.path("results").path("payload")).isEqualTo(value);
        assertThat(response.path("results").has("empty")).isTrue();
        assertThat(response.path("results").path("empty").isNull()).isTrue();
    }

    static Stream<Arguments> operators() {
        return Stream.of(
            Arguments.of("NUMBER", "GT", "10", "10.001", true),
            Arguments.of("NUMBER", "GE", "10", "10.0", true),
            Arguments.of("NUMBER", "LT", "10", "10", false),
            Arguments.of("NUMBER", "LE", "10", "10.0", true),
            Arguments.of("NUMBER", "EQ", "10", "10.00", true),
            Arguments.of("NUMBER", "NE", "10", "10.00", false),
            Arguments.of("NUMBER", "IN", "[5,10]", "10.00", true),
            Arguments.of("BOOLEAN", "NOT_IN", "[true]", "false", true),
            Arguments.of("STRING", "IN", "[\"VIP\",\"SVIP\"]", "\"VIP\"", true),
            Arguments.of("STRING", "CONTAINS", "\"VIP\"", "\"SVIP\"", true),
            Arguments.of("STRING_LIST", "CONTAINS", "\"RISK\"", "[\"HIGH_RISK\"]", false),
            Arguments.of("STRING_LIST", "CONTAINS_ANY", "[\"RISK\",\"CROSS_BORDER\"]", "[\"CROSS_BORDER\"]", true),
            Arguments.of("STRING", "EXISTS", "null", "null", false),
            Arguments.of("STRING", "NOT_EXISTS", "null", "null", true),
            Arguments.of("STRING", "NE", "\"VIP\"", "null", false),
            Arguments.of("STRING", "NOT_IN", "[\"VIP\"]", "null", false)
        );
    }
    @ParameterizedTest
    @MethodSource("operators")
    void typedOperatorsMatchThroughActualDrl(String type, String op, String right, String left, boolean matches) throws Exception {
        ObjectNode c = json.createObjectNode().put("ruleName", "operator_test").put("businessType", "CUSTOM");
        c.putArray("fields").addObject().put("name", "value").put("type", type).put("required", false);
        ObjectNode when = json.createObjectNode().put("field", "value").put("operator", op);
        if (!"EXISTS".equals(op) && !"NOT_EXISTS".equals(op)) when.set("value", json.readTree(right));
        addRule(c.putArray("rules"), "rule", "BASE", 0, when, "[{\"type\":\"SET_RESULT\",\"key\":\"matched\",\"value\":true}]");
        service.publish("operator_test", c);
        ObjectNode r = json.createObjectNode().put("rule_name", "operator_test");
        r.putObject("document").put("id", "OPERATOR").putObject("data").set("value", json.readTree(left));
        JsonNode response = execute(r);
        assertThat(response.path("results").has("matched")).isEqualTo(matches);
        assertThat(response.path("plan").path("decision").asText()).isEqualTo(matches ? "MATCHED" : "NO_MATCH");
    }

    @Test
    void oldStoredDrlStillExecutesWithItsOriginalFactType() throws Exception {
        PriceRuleConfig c = json.readValue(file("examples/cost-config.json"), PriceRuleConfig.class);
        String drl = "package example.generated\nimport example.rules.DocumentFact;\n"
                + "global example.rules.ApprovalPlan plan;\nrule \"legacy\" when $d : DocumentFact() then "
                + "plan.base(\"legacy\",true,\"SERIAL\",new String[]{}); end\n";
        RuleStore.Version v = store.publish(c.getRuleName(), json.writeValueAsString(c), drl);
        RuleService.ExecutionResult result = service.execute(new RuleService.ExecutionRequest(c.getRuleName(), v.getVersion(),
                new RuleService.Document("LEGACY", Collections.emptyList(), null, java.math.BigDecimal.TEN)));
        assertThat(result.getPlan().getDecision()).isEqualTo("AUTO_APPROVE");
        assertThat(result.getPlan().getMatchedRules()).containsExactly("legacy");
    }

    @Test
    void genericPriceConfigUsesCommonExecuteEndpoint() throws Exception {
        ObjectNode c = json.createObjectNode().put("ruleName", "generic_sales").put("businessType", "SALES_PRICE");
        ArrayNode fields = c.putArray("fields");
        fields.addObject().put("name", "grossMarginPct").put("type", "NUMBER").put("required", true);
        fields.addObject().put("name", "salePrice").put("type", "NUMBER").put("required", true);
        fields.addObject().put("name", "costPrice").put("type", "NUMBER").put("required", true);
        fields.addObject().put("name", "productId").put("type", "STRING").put("required", true);
        c.putObject("calculation").put("type", "GROSS_MARGIN").put("baseField", "salePrice")
                .put("compareField", "costPrice").put("resultField", "grossMarginPct");
        ArrayNode rules = c.putArray("rules");
        addRule(rules, "low", "BASE", 0, json.readTree("{\"field\":\"grossMarginPct\",\"operator\":\"LE\",\"value\":10}"), "[{\"type\":\"AUTO_APPROVE\"}]");
        addRule(rules, "high", "BASE", 0, json.readTree("{\"field\":\"grossMarginPct\",\"operator\":\"GT\",\"value\":10}"),
                "[{\"type\":\"REQUIRE_APPROVAL\",\"mode\":\"SERIAL\",\"targets\":[\"ROLE:MANAGER\"]}]");
        addRule(rules, "raw_price", "SUPPLEMENT", 0,
                json.readTree("{\"logic\":\"ALL\",\"conditions\":[{\"field\":\"salePrice\",\"operator\":\"GE\",\"value\":1000},{\"field\":\"productId\",\"operator\":\"EQ\",\"value\":\"SKU\"}]}"),
                "[{\"type\":\"SET_RESULT\",\"key\":\"largePrice\",\"value\":true}]");
        service.publish("generic_sales", c);
        http.perform(post("/api/rules/execute").contentType(MediaType.APPLICATION_JSON)
                .content("{\"rule_name\":\"generic_sales\",\"document\":{\"id\":\"GENERIC-PRICE\",\"data\":{\"salePrice\":1000,\"costPrice\":899.999999999999,\"productId\":\"SKU\"}}}"))
                .andExpect(status().isOk()).andExpect(jsonPath("plan.decision").value("MANUAL_APPROVAL"))
                .andExpect(jsonPath("results.largePrice").value(true));
        Files.write(Paths.get("examples/generic-sales-config.json"), (json.writerWithDefaultPrettyPrinter().writeValueAsString(c) + "\n").getBytes(StandardCharsets.UTF_8));
    }
}
