/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.springframework.stereotype.Component
 */
package example.rules;

import example.rules.ConfigValidator;
import example.rules.RuleConfig;
import example.rules.RuleJson;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class DrlGenerator {
    private final ConfigValidator validator;

    public DrlGenerator(ConfigValidator validator) {
        this.validator = validator;
    }

    public String generate(RuleConfig config) {
        this.validator.validate(config);
        StringBuilder out = new StringBuilder("package example.generated\n\nimport example.rules.GenericFact;\nglobal example.rules.GenericRuleResult result;\ndialect \"java\"\n\n");
        for (int index = 0; index < config.getRules().size(); ++index) {
            RuleConfig.Rule r = config.getRules().get(index);
            String id = config.getRuleName() + ":" + r.getId();
            out.append("rule ").append(DrlGenerator.quote(id)).append("\nwhen\n    $fact : GenericFact(businessType == ").append(DrlGenerator.quote(config.getBusinessType())).append(")\n    eval(").append(this.condition(r.getWhen())).append(")\nthen\n    result.match(").append(DrlGenerator.quote(id)).append(", ").append(DrlGenerator.quote(r.getKind().name())).append(", ").append(r.getPriority()).append(", ").append(index).append(", ").append(DrlGenerator.quote(RuleJson.encode(r.getActions()))).append(");\nend\n\n");
        }
        return out.toString();
    }

    private String condition(RuleConfig.Condition c) {
        if (c.getLogic() == RuleConfig.Logic.NOT) {
            return "!(" + this.condition(c.getConditions().get(0)) + ")";
        }
        if (c.getLogic() != null) {
            String separator = c.getLogic() == RuleConfig.Logic.ALL ? " && " : " || ";
            return c.getConditions().stream().map(child -> "(" + this.condition((RuleConfig.Condition)child) + ")").collect(Collectors.joining(separator));
        }
        return "$fact.test(" + DrlGenerator.quote(c.getField()) + ", " + DrlGenerator.quote(c.getOperator().name()) + ", " + DrlGenerator.quote(RuleJson.encode(c.getValue())) + ")";
    }

    private static String quote(String value) {
        return RuleJson.encode(value);
    }
}

