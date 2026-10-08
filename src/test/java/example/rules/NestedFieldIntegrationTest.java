package example.rules;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.Arguments;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
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
class NestedFieldIntegrationTest {
    @Autowired ObjectMapper json;
    @Autowired RuleService service;
    @Autowired RuleStore store;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc http;

    private JsonNode file(String path) throws Exception { return json.readTree(Files.readAllBytes(Paths.get(path))); }
    private ObjectNode request(String rule, String data) throws Exception {
        ObjectNode r = json.createObjectNode().put("rule_name", rule);
        r.putObject("document").put("id", "NESTED-TEST").set("data", json.readTree(data)); return r;
    }
    private JsonNode execute(JsonNode r) throws Exception {
        return json.readTree(http.perform(post("/api/rules/execute").contentType(MediaType.APPLICATION_JSON)
                .content(r.toString())).andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
    }
    private ObjectNode config(String name, String field, String type, boolean required, String operator, String value) throws Exception {
        ObjectNode c = json.createObjectNode().put("ruleName", name).put("businessType", "ORDER");
        c.putArray("fields").addObject().put("name", field).put("type", type).put("required", required);
        c.putArray("outputs").addObject().put("name", "matched").put("type", "BOOLEAN").put("required", true);
        ObjectNode r = c.putArray("rules").addObject().put("id", "selected_path").put("kind", "BASE");
        ObjectNode when = r.putObject("when").put("field", field).put("operator", operator);
        if (value != null) when.set("value", json.readTree(value));
        r.putArray("actions").addObject().put("type", "SET_RESULT").put("key", "matched").put("value", true);
        return c;
    }

    @Test
    void pageSelectedPathPublishesExecutesAndKeepsNestedRequestInInfoLog() throws Exception {
        try (ExecutionLogCapture logs = new ExecutionLogCapture()) {
            JsonNode c = file("examples/nested-order-config.json");
            JsonNode published = json.readTree(http.perform(post("/api/rules/nested_order_approval/publish")
                    .contentType(MediaType.APPLICATION_JSON).content(c.toString())).andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
            ObjectNode r = (ObjectNode) file("examples/execute-nested-order.json");
            JsonNode result = execute(r);
            assertThat(result.path("plan").path("decision").asText()).isEqualTo("MANUAL_APPROVAL");
            assertThat(result.path("results").path("reviewRegion").asText()).isEqualTo("LOCAL");
            JsonNode record = logs.execution(result.path("executionId").asText());
            assertThat(record.path("input")).isEqualTo(json.valueToTree(json.treeToValue(r, RuleService.ExecutionRequest.class)));
            assertThat(record.path("result")).isEqualTo(result);
            assertThat(record.path("input").path("document").path("data").path("customer").path("address").path("province").asText()).isEqualTo("浙江");
            assertThat(record.path("input").path("document").path("data").has("customer.address.province")).isFalse();
            JsonNode saved = json.readTree(store.load("nested_order_approval", published.path("version").asLong()).getConfigJson());
            assertThat(saved.path("fields").get(0).path("name").asText()).isEqualTo("customer.address.province");
            assertThat(published.path("drl").asText()).contains("$fact.test(\"customer.address.province\"");
            Files.write(Paths.get("examples/nested-order.drl"), published.path("drl").asText().getBytes(StandardCharsets.UTF_8));
            Files.write(Paths.get("examples/result-nested-order.json"), (json.writerWithDefaultPrettyPrinter().writeValueAsString(result) + "\n").getBytes(StandardCharsets.UTF_8));
            ((ObjectNode) r.path("document").path("data").path("customer").path("address")).put("province", "江苏");
            assertThat(execute(r).path("results").path("reviewRegion").asText()).isEqualTo("REMOTE");
        }
    }

    static Stream<Arguments> nestedValues() {
        return Stream.of(
            Arguments.of("customer.address.province", "STRING", "IN", "[\"浙江\",\"江苏\"]", "{\"customer\":{\"address\":{\"province\":\"浙江\"}}}"),
            Arguments.of("customer.flags.active", "BOOLEAN", "EQ", "false", "{\"customer\":{\"flags\":{\"active\":false}}}"),
            Arguments.of("customer.tags", "STRING_LIST", "CONTAINS_ANY", "[\"VIP\",\"SVIP\"]", "{\"customer\":{\"tags\":[\"VIP\"]}}"),
            Arguments.of("orders[1].items[0].price", "NUMBER", "GT", "1000.0000000000000000001", "{\"orders\":[{}, {\"items\":[{\"price\":1000.0000000000000000002}]}]}"),
            Arguments.of("matrix[0][1]", "NUMBER", "EQ", "3", "{\"matrix\":[[2,3]]}"),
            Arguments.of("customer.address", "JSON", "EXISTS", null, "{\"customer\":{\"address\":{\"province\":\"浙江\"}}}")
        );
    }
    @ParameterizedTest @MethodSource("nestedValues")
    void nestedTypedValuesAndArrayIndexesMatchThroughActualDrl(String path, String type, String op, String value, String data) throws Exception {
        service.publish("nested_operator", config("nested_operator", path, type, true, op, value));
        assertThat(execute(request("nested_operator", data)).path("results").path("matched").asBoolean()).isTrue();
    }

    @Test
    void missingAndNullPathsRespectRequiredFlagAndExistenceOperators() throws Exception {
        String name = "nested_missing", path = "customer.address.province";
        ObjectNode c = config(name, path, "STRING", true, "EXISTS", null); service.publish(name, c);
        List<String> missing = Arrays.asList("{}", "{\"customer\":null}", "{\"customer\":{}}", "{\"customer\":{\"address\":null}}",
                "{\"customer\":{\"address\":{\"province\":null}}}", "{\"customer.address.province\":\"浙江\"}");
        for (String data : missing) http.perform(post("/api/rules/execute").contentType(MediaType.APPLICATION_JSON).content(request(name, data).toString()))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("detail").value("缺少必填字段：" + path));
        ((ObjectNode) c.path("fields").get(0)).put("required", false);
        ((ObjectNode) c.path("rules").get(0).path("when")).put("operator", "NOT_EXISTS"); service.publish(name, c);
        for (String data : missing) assertThat(execute(request(name, data)).path("results").path("matched").asBoolean()).isTrue();
        ((ObjectNode) c.path("rules").get(0).path("when")).put("operator", "NE").put("value", "浙江"); service.publish(name, c);
        assertThat(execute(request(name, "{}")).path("plan").path("decision").asText()).isEqualTo("NO_MATCH");
    }

    @Test
    void invalidContainerAndLeafTypesReturn400AndLiteralDottedKeysCannotOverrideNestedValues() throws Exception {
        String name = "nested_structure"; service.publish(name, config(name, "customer.address.province", "STRING", false, "EQ", "\"浙江\""));
        for (String data : Arrays.asList("{\"customer\":\"bad\"}", "{\"customer\":{\"address\":[]}}", "{\"customer\":{\"address\":{\"province\":123}}}")) {
            http.perform(post("/api/rules/execute").contentType(MediaType.APPLICATION_JSON).content(request(name, data).toString())).andExpect(status().isBadRequest());
        }
        JsonNode response = execute(request(name, "{\"customer.address.province\":\"江苏\",\"customer\":{\"address\":{\"province\":\"浙江\"}}}"));
        assertThat(response.path("results").path("matched").asBoolean()).isTrue();
        String array = "nested_array_structure"; service.publish(array, config(array, "items[0].price", "NUMBER", false, "EXISTS", null));
        assertThat(execute(request(array, "{\"items\":[]}")).path("plan").path("decision").asText()).isEqualTo("NO_MATCH");
        http.perform(post("/api/rules/execute").contentType(MediaType.APPLICATION_JSON).content(request(array, "{\"items\":{\"0\":{\"price\":100}}}").toString()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void pathsAreValidatedBeforePublicationAndOutputKeysKeepTheirExistingContract() throws Exception {
        String name = "invalid_path"; ObjectNode c = config(name, "customer.address.province", "STRING", true, "EXISTS", null);
        long first = service.publish(name, c).getVersion();
        List<String> invalid = new ArrayList<>(Arrays.asList("", ".customer", "customer.", "customer..province", "customer.*", "items[-1].price", "items[01].price",
                "items[2147483648].price", "items[99999999999].price", "items[].price", "items[0]price", "customer.getClass()", "customer['province']", "customer;System.exit(0)"));
        invalid.add(String.join(".", Collections.nCopies(21, "a")));
        invalid.add(String.join("", Collections.nCopies(65, "a")));
        invalid.add(String.join(".", Collections.nCopies(10, String.join("", Collections.nCopies(64, "a")))));
        for (String field : invalid) {
            ObjectNode bad = c.deepCopy(); ((ObjectNode) bad.path("fields").get(0)).put("name", field);
            ((ObjectNode) bad.path("rules").get(0).path("when")).put("field", field);
            assertThatThrownBy(() -> service.publish(name, bad)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("字段路径");
        }
        ObjectNode badOutput = c.deepCopy(); ((ObjectNode) badOutput.path("outputs").get(0)).put("name", "result.matched");
        assertThatThrownBy(() -> service.publish(name, badOutput)).isInstanceOf(IllegalArgumentException.class);
        assertThat(store.load(name, null).getVersion()).isEqualTo(first);
    }

    @Test
    void nestedParameterSharingAndHistoricalDrlUsePinnedSnapshots() throws Exception {
        String source = "nested_source", target = "nested_target", path = "customer.address.province";
        ObjectNode c = config(source, path, "STRING", true, "EXISTS", null); RuleStore.Version sourceVersion = service.publish(source, c);
        ObjectNode reused = config(target, path, "STRING", true, "EQ", "\"浙江\""); reused.remove("fields"); reused.remove("outputs");
        ObjectNode ref = reused.putArray("parameterSources").addObject().put("ruleName", source);
        ref.putArray("inputs").add(path); ref.putArray("outputs").add("matched"); RuleStore.Version targetVersion = service.publish(target, reused);
        JsonNode snapshot = json.readTree(targetVersion.getConfigJson());
        assertThat(snapshot.path("fields").get(0).path("name").asText()).isEqualTo(path);
        assertThat(snapshot.path("parameterSources").get(0).path("version").asLong()).isEqualTo(sourceVersion.getVersion());
        ((ObjectNode) c.path("fields").get(0)).put("required", false); service.publish(source, c);
        ObjectNode newer = (ObjectNode) snapshot.deepCopy(); ((ObjectNode) newer.path("rules").get(0).path("when")).put("value", "江苏"); service.publish(target, newer);
        jdbc.update("DELETE FROM rule_version WHERE rule_name=?", source);
        ObjectNode r = request(target, "{\"customer\":{\"address\":{\"province\":\"浙江\"}}}"); r.put("version", targetVersion.getVersion());
        assertThat(execute(r).path("results").path("matched").asBoolean()).isTrue();
        r.remove("version"); assertThat(execute(r).path("plan").path("decision").asText()).isEqualTo("NO_MATCH");
    }
}
