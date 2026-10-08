package example.rules;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:rules;MODE=MySQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa", "spring.datasource.password=",
    "spring.datasource.driver-class-name=org.h2.Driver", "spring.sql.init.mode=always"
})
@AutoConfigureMockMvc
class SharedParametersIntegrationTest {
    @Autowired ObjectMapper json;
    @Autowired RuleService service;
    @Autowired RuleStore store;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc http;

    private ObjectNode order(String name) throws Exception {
        ObjectNode c = (ObjectNode) json.readTree(Files.readAllBytes(Paths.get("examples/order-config.json")));
        return c.put("ruleName", name);
    }
    private ObjectNode shared(String name, String source) throws Exception {
        ObjectNode c = order(name); c.remove("fields"); c.remove("outputs");
        ObjectNode ref = c.putArray("parameterSources").addObject().put("ruleName", source);
        ref.putArray("inputs").add("orderAmount").add("customerLevel").add("overdue").add("tags").add("region");
        ref.putArray("outputs").add("discountRate"); return c;
    }
    private ObjectNode request(String name) throws Exception {
        ObjectNode r = (ObjectNode) json.readTree(Files.readAllBytes(Paths.get("examples/execute-order.json")));
        return r.put("rule_name", name);
    }
    private JsonNode execute(JsonNode r) throws Exception {
        return json.readTree(http.perform(post("/api/rules/execute").contentType(MediaType.APPLICATION_JSON)
                .content(r.toString())).andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    @Test
    void sharedParametersPersistAsIndependentVersionSnapshotsAndKeepHttpShapes() throws Exception {
        String source = "shared_source_A", target = "shared_target_A";
        RuleStore.Version sourceVersion = service.publish(source, order(source));
        JsonNode publication = json.readTree(http.perform(post("/api/rules/" + target + "/publish")
                .contentType(MediaType.APPLICATION_JSON).content(shared(target, source).toString()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
        assertThat(publication.has("ruleName")).isTrue(); assertThat(publication.has("rule_name")).isFalse();
        long version = publication.path("version").asLong();
        String saved = jdbc.queryForObject("SELECT config_json FROM rule_version WHERE rule_name=? AND version=?", String.class, target, version);
        JsonNode config = json.readTree(saved);
        assertThat(config.path("fields").size()).isEqualTo(5);
        assertThat(config.path("fields").get(0).path("description").asText()).contains("订单总金额");
        assertThat(config.path("fields").get(0).path("example").asInt()).isEqualTo(15000);
        assertThat(config.path("outputs").get(0).path("name").asText()).isEqualTo("discountRate");
        assertThat(config.path("parameterSources").get(0).path("version").asLong()).isEqualTo(sourceVersion.getVersion());
        assertThat(config.has("document")).isFalse();
        ObjectNode updated = order(source);
        ((ObjectNode) updated.path("fields").get(0)).put("required", false).put("description", "来源第二版说明");
        RuleStore.Version secondSource = service.publish(source, updated);
        assertThat(secondSource.getVersion()).isEqualTo(sourceVersion.getVersion() + 1);
        assertThat(store.load(target, version).getConfigJson()).isEqualTo(saved);
        // 编辑/重发已展开快照时保持固定引用；完全相同的定义允许合并，不能重复添加字段。
        RuleStore.Version repeated = service.publish(target, config);
        assertThat(json.readTree(repeated.getConfigJson()).path("fields").size()).isEqualTo(5);
        assertThat(json.readTree(repeated.getConfigJson()).path("parameterSources").get(0).path("version").asLong())
                .isEqualTo(sourceVersion.getVersion());
        // 删除测试数据库内的来源，验证执行不再依赖来源查询。
        jdbc.update("DELETE FROM rule_version WHERE rule_name=?", source);
        ObjectNode r = request(target); r.put("version", version);
        JsonNode response = execute(r);
        assertThat(response.path("results").path("discountRate").decimalValue()).isEqualByComparingTo("0.95");
        ((ObjectNode) r.path("document").path("data")).remove("orderAmount");
        http.perform(post("/api/rules/execute").contentType(MediaType.APPLICATION_JSON).content(r.toString()))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("detail").value("缺少必填字段：orderAmount"));
    }

    @Test
    void invalidReferencesAndConflictingDefinitionsDoNotPublish() throws Exception {
        String source = "shared_source_B", target = "shared_target_B";
        service.publish(source, order(source));
        ObjectNode missing = shared(target, source);
        ((ArrayNode) missing.path("parameterSources").get(0).path("inputs")).add("missing");
        assertThatThrownBy(() -> service.publish(target, missing)).hasMessageContaining("不存在入参");
        ObjectNode conflict = shared(target, source);
        conflict.putArray("fields").addObject().put("name", "orderAmount").put("type", "STRING").put("required", true);
        assertThatThrownBy(() -> service.publish(target, conflict)).hasMessageContaining("定义冲突");
        ObjectNode duplicate = shared(target, source);
        ((ArrayNode) duplicate.path("parameterSources").get(0).path("inputs")).add("orderAmount");
        assertThatThrownBy(() -> service.publish(target, duplicate)).hasMessageContaining("不能重复");
        assertThatThrownBy(() -> store.load(target, null)).isInstanceOf(NoSuchElementException.class);
        ObjectNode unknown = shared(target, "unpublished_source");
        http.perform(post("/api/rules/" + target + "/publish").contentType(MediaType.APPLICATION_JSON).content(unknown.toString()))
                .andExpect(status().isNotFound()).andExpect(jsonPath("type").value("about:blank"))
                .andExpect(jsonPath("status").value(404));
    }

    @Test
    void outputDefinitionsValidateConstantsAndKeepJsonValuesAndExamples() throws Exception {
        String name = "json_output_schema"; ObjectNode c = order(name);
        ArrayNode outputs = c.putArray("outputs");
        ObjectNode definition = outputs.addObject().put("name", "payload").put("type", "JSON").put("required", true).put("description", "业务计算结果对象");
        JsonNode payload = json.readTree("{\"enabled\":true,\"levels\":[\"VIP\"],\"amount\":15000.0000000000000000001}");
        definition.set("example", payload);
        ObjectNode rule = c.putArray("rules").addObject().put("id", "compute").put("kind", "BASE");
        rule.putObject("when").put("field", "orderAmount").put("operator", "EXISTS");
        rule.putArray("actions").addObject().put("type", "SET_RESULT").put("key", "payload").set("value", payload);
        RuleStore.Version v = service.publish(name, c);
        assertThat(json.readTree(v.getConfigJson()).path("outputs").get(0).path("example")).isEqualTo(payload);
        assertThat(execute(request(name)).path("results").path("payload")).isEqualTo(payload);
        ObjectNode invalid = c.deepCopy(); ((ObjectNode) invalid.path("outputs").get(0)).put("type", "BOOLEAN").remove("example");
        assertThatThrownBy(() -> service.publish(name, invalid)).hasMessageContaining("出参常量类型");
        assertThat(store.load(name, null).getVersion()).isEqualTo(v.getVersion());
        ObjectNode undeclared = c.deepCopy(); ((ObjectNode) undeclared.path("rules").get(0).path("actions").get(0)).put("key", "unknown");
        assertThatThrownBy(() -> service.publish(name, undeclared)).hasMessageContaining("出参未声明");
    }

    @Test
    void requiredOutputFailureReturnsTypedServerErrorRatherThanInputError() throws Exception {
        String name = "required_output_schema"; ObjectNode c = order(name);
        ((ObjectNode) c.path("outputs").get(0)).put("required", true);
        service.publish(name, c);
        ObjectNode r = request(name); ((ObjectNode) r.path("document").path("data")).put("customerLevel", "NORMAL");
        http.perform(post("/api/rules/execute").contentType(MediaType.APPLICATION_JSON).content(r.toString()))
                .andExpect(status().isInternalServerError()).andExpect(jsonPath("status").value(500))
                .andExpect(jsonPath("detail").value("规则执行或存储失败"));
        ((ObjectNode) r.path("document").path("data")).put("overdue", true);
        assertThat(execute(r).path("plan").path("decision").asText()).isEqualTo("REJECTED");
    }

    @Test
    void exportedSharedExamplePublishesAndComputes() throws Exception {
        service.publish("order_approval", order("order_approval"));
        JsonNode shared = json.readTree(Files.readAllBytes(Paths.get("examples/order-shared-config.json")));
        RuleStore.Version v = service.publish("order_shared_approval", shared);
        JsonNode response = execute(json.readTree(Files.readAllBytes(Paths.get("examples/execute-shared-order.json"))));
        assertThat(response.path("plan").path("decision").asText()).isEqualTo("MANUAL_APPROVAL");
        Files.write(Paths.get("examples/order-shared-snapshot.json"), (json.writerWithDefaultPrettyPrinter()
                .writeValueAsString(json.readTree(v.getConfigJson())) + "\n").getBytes(StandardCharsets.UTF_8));
    }
}
