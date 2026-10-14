package example.rules.service;

import com.alibaba.qlexpress4.Express4Runner;
import com.alibaba.qlexpress4.InitOptions;
import com.alibaba.qlexpress4.QLOptions;
import com.fasterxml.jackson.databind.JsonNode;
import example.rules.model.JsonRuleFact;
import example.rules.result.RuleHits;
import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

/** 执行由服务端从结构化配置生成的QLExpress脚本，仅开放固定业务函数。 */
@Component
public class QLExpressEngine {
    private static final QLOptions CACHED_OPTIONS = QLOptions.builder().cache(true).build();
    private final ThreadLocal<RunnerEntry> current = new ThreadLocal<>();

    public void validate(String script) {
        execute(script, RuleJson.MAPPER.createObjectNode(), new RuleHits());
    }

    public void execute(String script, JsonNode params, RuleHits hits) {
        Express4Runner runner = runner(script);
        Map<String, Object> context = new HashMap<>();
        context.put("params", params);
        context.put("hits", hits);
        try {
            runner.execute(script, context, CACHED_OPTIONS);
        } catch (Exception failure) {
            throw new IllegalArgumentException("QLExpress规则执行失败：" + failure.getMessage(), failure);
        }
    }

    private JsonRuleFact fact(Object params) { return new JsonRuleFact((JsonNode) params); }

    private Express4Runner runner(String script) {
        RunnerEntry entry = current.get();
        if (entry != null && entry.script.equals(script)) return entry.runner;
        Express4Runner runner = new Express4Runner(InitOptions.DEFAULT_OPTIONS);
        runner.addVarArgsFunction("categoryMatches", args -> fact(args[0]).categoryMatches(
                (String) args[1], (String) args[2]));
        runner.addVarArgsFunction("inBand", args -> fact(args[0]).inBand((String) args[1],
                (String) args[2], (Boolean) args[3], (String) args[4], (Boolean) args[5]));
        runner.addVarArgsFunction("compare", args -> fact(args[0]).compare(
                (String) args[1], (String) args[2], (String) args[3]));
        runner.addVarArgsFunction("positive", args -> fact(args[0]).positive((String) args[1]));
        runner.addVarArgsFunction("recordHit", args -> {
            ((RuleHits) args[0]).add(((Number) args[1]).intValue(), ((Number) args[2]).intValue(),
                    ((Number) args[3]).intValue(), (String) args[4], (String) args[5],
                    (String) args[6], (String) args[7], (Boolean) args[8]);
            return null;
        });
        runner.parseToDefinitionWithCache(script);
        current.set(new RunnerEntry(script, runner));
        return runner;
    }

    private static class RunnerEntry {
        final String script;
        final Express4Runner runner;
        RunnerEntry(String script, Express4Runner runner) {
            this.script = script;
            this.runner = runner;
        }
    }
}
