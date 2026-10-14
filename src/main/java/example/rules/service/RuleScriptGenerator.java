package example.rules.service;

import example.rules.model.RuleConfig;
import example.rules.model.RuleConfig.Assignment;
import example.rules.model.RuleConfig.Condition;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/** 将页面条件配置生成QLExpress脚本；命中后由受控函数应用结果赋值。 */
@Component
public class RuleScriptGenerator {
    private final RuleValidator validator;

    public RuleScriptGenerator(RuleValidator validator) { this.validator = validator; }

    public Map<String, String> generateFragments(RuleConfig config) {
        validator.validate(config);
        String costField = config.conditions.stream()
                .filter(c -> "COST_INCREASE".equals(c.conditionType)).findFirst().get().valueField;
        String defaultScope = defaultScope(config.conditions);
        List<Condition> ordered = new ArrayList<>(config.conditions);
        ordered.sort(Comparator.comparingInt((Condition c) -> c.ruleSort)
                .thenComparingInt(c -> "INTERVAL".equals(c.predicateType) ? 0 : 1)
                .thenComparingInt(c -> c.conditionSort).thenComparing(c -> c.conditionCode));
        Map<String, String> fragments = new LinkedHashMap<>();
        for (Condition c : ordered) {
            String scope = "COST_INCREASE".equals(c.conditionType) ? "true"
                    : c.isDefault ? defaultScope : scope(c);
            String predicate = "INTERVAL".equals(c.predicateType)
                    ? "inBand(params," + quote(c.valueField) + "," + decimal(c.lowerBound) + ","
                            + c.lowerInclusive + "," + decimal(c.upperBound) + "," + c.upperInclusive + ")"
                    : "compare(params," + quote(c.valueField) + "," + quote(c.compType) + ","
                            + decimal(c.thresholdVal) + ")";
            boolean conditional = c.resultAssignments.stream()
                    .anyMatch(a -> "COST_INCREASE".equals(a.when));
            String costIncreased = conditional ? "positive(params," + quote(costField) + ")" : "false";
            int kindOrder = "INTERVAL".equals(c.predicateType) ? 0 : 1;
            StringBuilder script = new StringBuilder();
            script.append("if (").append(scope).append(" && ").append(predicate).append(") {\n")
                    .append("  recordHit(hits, ").append(c.ruleSort).append(", ")
                    .append(kindOrder).append(", ")
                    .append(c.conditionSort).append(", ").append(quote(c.ruleName)).append(", ")
                    .append(quote(c.conditionCode)).append(", ").append(quote(c.conditionType)).append(", ")
                    .append(quote(RuleJson.encode(c.resultAssignments)))
                    .append(", ").append(costIncreased).append(");\n}\n");
            fragments.put(key(c), script.toString());
        }
        return fragments;
    }

    private String key(Condition c) { return c.ruleName + "\u0000" + c.conditionCode; }

    private String defaultScope(List<Condition> conditions) {
        Set<String> coveredGroups = new LinkedHashSet<>();
        List<String> scopes = new ArrayList<>();
        for (Condition c : conditions) {
            if (!c.isDefault && "CATEGORY_INTERVAL".equals(c.conditionType)
                    && coveredGroups.add(c.ruleName)) scopes.add(scope(c));
        }
        return scopes.isEmpty() ? "true" : "!(" + String.join(" || ", scopes) + ")";
    }

    private String scope(Condition c) {
        List<String> parts = new ArrayList<>();
        if (c.categoryId1 != null && c.categoryId1 > 0) parts.add("categoryMatches(params,\"categoryId1\"," + quote(c.categoryId1.toString()) + ")");
        if (c.categoryId2 != null && c.categoryId2 > 0) parts.add("categoryMatches(params,\"categoryId2\"," + quote(c.categoryId2.toString()) + ")");
        if (c.categoryId3 != null && c.categoryId3 > 0) parts.add("categoryMatches(params,\"categoryId3\"," + quote(c.categoryId3.toString()) + ")");
        return "(" + String.join(" && ", parts) + ")";
    }

    private String decimal(BigDecimal value) { return value == null ? "null" : quote(value.toPlainString()); }
    private String quote(String value) { return RuleJson.encode(value); }
}
