package example.rules.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import example.rules.dao.RuleStore;
import example.rules.model.ExecuteRequest;
import example.rules.model.RuleConfig;
import example.rules.model.RuleConfig.Condition;
import example.rules.result.ExecuteResult;
import example.rules.result.RuleHits;
import example.rules.result.RuleState;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class RuleService {
    private final RuleStore store;
    private final RuleValidator validator;
    private final RuleScriptGenerator generator;
    private final QLExpressEngine engine;

    public RuleService(RuleStore store, RuleValidator validator, RuleScriptGenerator generator, QLExpressEngine engine) {
        this.store = store; this.validator = validator; this.generator = generator; this.engine = engine;
    }

    public RuleState create(RuleConfig config) { return store.create(config, this::prepareScripts); }
    public RuleState read(String businessCode) { return store.read(businessCode); }

    public RuleState update(String businessCode, RuleConfig config) {
        return store.update(businessCode, config, this::prepareScripts);
    }

    public List<Condition> conditions(String businessCode) { return read(businessCode).config.conditions; }

    public RuleState insertCondition(String businessCode, Condition condition) {
        ConfigValidator.require(condition != null, "condition不能为空");
        return store.insertCondition(businessCode, condition, this::prepareScripts);
    }

    public RuleState updateCondition(String businessCode, String ruleName, String conditionCode,
            Condition condition) {
        ConfigValidator.require(condition != null && ruleName.equals(condition.ruleName)
                && conditionCode.equals(condition.conditionCode), "路径与条件编码必须一致");
        return store.updateCondition(businessCode, ruleName, conditionCode, condition, this::prepareScripts);
    }

    public ExecuteResult execute(ExecuteRequest request) {
        ConfigValidator.require(request != null, "执行请求不能为空");
        validator.businessCode(request.businessCode);
        JsonNode params = request.params;
        ConfigValidator.require(params != null && (params.isObject() || params.isArray()),
                "params必须是JSON对象或对象数组");
        RuleState current = store.read(request.businessCode);
        String script = current.config.conditions.stream().map(c -> c.scriptContent)
                .collect(java.util.stream.Collectors.joining("\n"));
        if (params.isArray()) {
            ArrayNode output = RuleJson.MAPPER.createArrayNode();
            List<List<?>> itemMatches = new ArrayList<>();
            for (int i = 0; i < params.size(); i++) {
                JsonNode item = params.get(i);
                ConfigValidator.require(item.isObject(), "params[" + i + "]必须是JSON对象");
                ExecuteResult result = executeOne(request.businessCode, script, item);
                output.add(result.results);
                itemMatches.add(result.matches);
            }
            return new ExecuteResult(request.businessCode, output, itemMatches);
        }
        return executeOne(request.businessCode, script, params);
    }

    private ExecuteResult executeOne(String businessCode, String script, JsonNode params) {
        RuleHits hits = new RuleHits();
        engine.execute(script, params, hits);
        return hits.finish(businessCode, params);
    }

    private java.util.Map<String, String> prepareScripts(RuleConfig config) {
        java.util.Map<String, String> scripts = generator.generateFragments(config);
        engine.validate(String.join("\n", scripts.values()));
        return scripts;
    }
}
