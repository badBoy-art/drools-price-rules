package example.rules.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import example.rules.model.ExecuteRequest;
import example.rules.model.RuleConfig;
import example.rules.model.RuleConfig.Condition;
import example.rules.result.ExecuteResult;
import example.rules.result.RuleState;
import example.rules.service.RuleService;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.NoSuchElementException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RuleControllerTest {
    private final ObjectMapper json = new ObjectMapper();
    private final RecordingRuleService service = new RecordingRuleService();
    private MockMvc http;

    @BeforeEach
    void setUp() {
        http = MockMvcBuilders.standaloneSetup(new RuleController(service))
                .setControllerAdvice(new ApiErrors())
                .build();
    }

    @Test
    void createReturnsCreatedAndPassesConfigurationToService() throws Exception {
        http.perform(post("/api/rule-sets").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"businessCode\":\"price\",\"businessName\":\"Price\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(17))
                .andExpect(jsonPath("$.config.businessCode").value("price"));

        assertThat(service.call).isEqualTo("create");
        assertThat(service.config.businessCode).isEqualTo("price");
    }

    @Test
    void readAndListConditionsPassBusinessCodeAndReturnServiceData() throws Exception {
        http.perform(get("/api/rule-sets/line-a"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.config.businessCode").value("line-a"));
        assertThat(service.call).isEqualTo("read");
        assertThat(service.businessCode).isEqualTo("line-a");

        http.perform(get("/api/rule-sets/line-b/conditions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].conditionCode").value("band1"));
        assertThat(service.call).isEqualTo("conditions");
        assertThat(service.businessCode).isEqualTo("line-b");
    }

    @Test
    void updateRuleSetPassesPathCodeAndBody() throws Exception {
        http.perform(put("/api/rule-sets/line-a").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"businessCode\":\"line-a\",\"businessName\":\"Updated\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.config.businessName").value("Updated"));

        assertThat(service.call).isEqualTo("update");
        assertThat(service.businessCode).isEqualTo("line-a");
        assertThat(service.config.businessName).isEqualTo("Updated");
    }

    @Test
    void conditionCreateAndUpdateUseExpectedHttpStatusAndPathKeys() throws Exception {
        String condition = "{\"ruleCode\":\"sales\",\"conditionCode\":\"band1\","
                + "\"conditionType\":\"CATEGORY_INTERVAL\",\"field\":\"margin\"}";
        http.perform(post("/api/rule-sets/line-a/conditions").contentType(MediaType.APPLICATION_JSON)
                        .content(condition))
                .andExpect(status().isCreated());
        assertThat(service.call).isEqualTo("insertCondition");
        assertThat(service.businessCode).isEqualTo("line-a");
        assertThat(service.condition.conditionCode).isEqualTo("band1");

        http.perform(put("/api/rule-sets/line-a/conditions/sales/band2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(condition.replace("band1", "band2")))
                .andExpect(status().isOk());
        assertThat(service.call).isEqualTo("updateCondition");
        assertThat(service.businessCode).isEqualTo("line-a");
        assertThat(service.ruleCode).isEqualTo("sales");
        assertThat(service.conditionCode).isEqualTo("band2");
        assertThat(service.condition.conditionCode).isEqualTo("band2");
    }

    @Test
    void executeAcceptsBusinessCodeAliasAndReturnsEngineResult() throws Exception {
        http.perform(post("/api/rules/execute").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"business_code\":\"line-a\",\"params\":{\"margin\":8}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.businessCode").value("line-a"))
                .andExpect(jsonPath("$.results.margin").value(8))
                .andExpect(jsonPath("$.matches.length()").value(0));
        assertThat(service.call).isEqualTo("execute");
        assertThat(service.request.businessCode).isEqualTo("line-a");
        assertThat(service.request.params.path("margin").asInt()).isEqualTo(8);
    }

    @Test
    void serviceErrorsAndMalformedJsonAreMappedToBadRequestOrNotFound() throws Exception {
        service.failure = new IllegalArgumentException("invalid rule");
        http.perform(get("/api/rule-sets/invalid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("invalid rule"));

        service.failure = new NoSuchElementException("rule not found");
        http.perform(get("/api/rule-sets/missing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("rule not found"));

        service.failure = null;
        http.perform(post("/api/rules/execute").contentType(MediaType.APPLICATION_JSON)
                        .content("{not-json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").exists());
    }

    private static RuleState state(String businessCode, String businessName) {
        RuleConfig config = new RuleConfig();
        config.businessCode = businessCode;
        config.businessName = businessName;
        config.conditions = Collections.emptyList();
        return new RuleState(17, config);
    }

    private static class RecordingRuleService extends RuleService {
        String call;
        String businessCode;
        String ruleCode;
        String conditionCode;
        RuleConfig config;
        Condition condition;
        ExecuteRequest request;
        RuntimeException failure;

        RecordingRuleService() { super(null, null, null, null); }

        private void record(String name) {
            call = name;
            if (failure != null) throw failure;
        }

        @Override public RuleState create(RuleConfig config) {
            record("create"); this.config = config;
            return state(config.businessCode, config.businessName);
        }

        @Override public RuleState read(String code) {
            record("read"); businessCode = code;
            return state(code, "Read");
        }

        @Override public RuleState update(String code, RuleConfig config) {
            record("update"); businessCode = code; this.config = config;
            return state(code, config.businessName);
        }

        @Override public List<Condition> conditions(String code) {
            record("conditions"); businessCode = code;
            Condition item = new Condition(); item.conditionCode = "band1";
            return Arrays.asList(item);
        }

        @Override public RuleState insertCondition(String code, Condition item) {
            record("insertCondition"); businessCode = code; condition = item;
            return state(code, "Insert");
        }

        @Override public RuleState updateCondition(String code, String rule, String conditionKey, Condition item) {
            record("updateCondition"); businessCode = code; ruleCode = rule;
            conditionCode = conditionKey; condition = item;
            return state(code, "Condition update");
        }

        @Override public ExecuteResult execute(ExecuteRequest input) {
            record("execute"); request = input;
            return new ExecuteResult(input.businessCode, input.params, Collections.emptyList());
        }
    }
}
