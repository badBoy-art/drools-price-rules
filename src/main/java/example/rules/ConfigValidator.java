/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.fasterxml.jackson.databind.JsonNode
 *  org.springframework.stereotype.Component
 */
package example.rules;

import com.fasterxml.jackson.databind.JsonNode;
import example.rules.FieldPath;
import example.rules.GenericRuleResult;
import example.rules.ParameterDefinition;
import example.rules.RuleConfig;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class ConfigValidator {
    public static void require(boolean valid, String message) {
        if (!valid) {
            throw new IllegalArgumentException(message);
        }
    }

    public static void identifier(String value) {
        ConfigValidator.require(value != null && value.matches("[A-Za-z0-9_:-]{1,64}"), "\u6807\u8bc6\u5fc5\u987b\u4e3a1\u523064\u4e2a\u5b57\u6bcd\u3001\u6570\u5b57\u3001\u4e0b\u5212\u7ebf\u3001\u5192\u53f7\u6216\u77ed\u6a2a\u7ebf");
    }

    private static void fieldName(String value) {
        ConfigValidator.require(value != null && value.matches("[A-Za-z_][A-Za-z0-9_]{0,63}"), "\u5b57\u6bb5\u540d\u5fc5\u987b\u4e3a\u5b57\u6bcd\u6216\u4e0b\u5212\u7ebf\u5f00\u5934\uff0c\u6700\u591a64\u5b57\u7b26");
    }

    public void validate(RuleConfig c) {
        ConfigValidator.require(c != null, "\u914d\u7f6e\u4e0d\u80fd\u4e3a\u7a7a");
        ConfigValidator.identifier(c.getRuleName());
        ConfigValidator.identifier(c.getBusinessType());
        ConfigValidator.require(c.getFields() != null && !c.getFields().isEmpty() && c.getFields().size() <= 200, "fields\u5fc5\u987b\u5305\u542b1\u5230200\u4e2a\u5b57\u6bb5");
        LinkedHashMap<String, RuleConfig.FieldType> fields = new LinkedHashMap<String, RuleConfig.FieldType>();
        for (RuleConfig.Field field : c.getFields()) {
            this.parameter(field, true);
            ConfigValidator.require(fields.put(field.getName(), field.getType()) == null, "\u5b57\u6bb5\u540d\u91cd\u590d\uff1a" + field.getName());
        }
        RuleConfig.Calculation calculation = c.getCalculation();
        if (calculation != null) {
            ConfigValidator.require(calculation.getType() != null, "calculation.type必填");
            ConfigValidator.require(fields.get(calculation.getBaseField()) == RuleConfig.FieldType.NUMBER,
                    "calculation.baseField必须是已声明的NUMBER字段");
            ConfigValidator.require(fields.get(calculation.getCompareField()) == RuleConfig.FieldType.NUMBER,
                    "calculation.compareField必须是已声明的NUMBER字段");
            ConfigValidator.require(fields.get(calculation.getResultField()) == RuleConfig.FieldType.NUMBER,
                    "calculation.resultField必须是已声明的NUMBER字段");
            ConfigValidator.require(!calculation.getBaseField().equals(calculation.getCompareField())
                            && !calculation.getResultField().equals(calculation.getBaseField())
                            && !calculation.getResultField().equals(calculation.getCompareField()),
                    "calculation字段必须互不相同");
        }
        LinkedHashMap<String, ParameterDefinition> outputs = new LinkedHashMap<String, ParameterDefinition>();
        if (c.getOutputs() != null) {
            ConfigValidator.require(c.getOutputs().size() <= 200, "outputs\u6700\u591a200\u9879");
            for (ParameterDefinition f : c.getOutputs()) {
                this.parameter(f, false);
                ConfigValidator.require(outputs.put(f.getName(), f) == null, "\u51fa\u53c2\u540d\u91cd\u590d\uff1a" + f.getName());
            }
        }
        ConfigValidator.require(c.getRules() != null && !c.getRules().isEmpty() && c.getRules().size() <= 20000, "rules\u5fc5\u987b\u5305\u542b1\u523020000\u6761\u89c4\u5219");
        HashSet<String> hashSet = new HashSet<String>();
        for (RuleConfig.Rule r : c.getRules()) {
            ConfigValidator.require(r != null && r.getId() != null && r.getId().matches("[A-Za-z0-9_:-]{1,129}"), "\u89c4\u5219id\u683c\u5f0f\u9519\u8bef");
            ConfigValidator.require(hashSet.add(r.getId()), "\u89c4\u5219id\u91cd\u590d");
            ConfigValidator.require(r.getKind() != null, "kind\u5fc5\u586b");
            this.condition(r.getWhen(), fields, 1, new int[]{0});
            ConfigValidator.require(r.getActions() != null && r.getActions().size() <= 100, "actions\u5fc5\u586b\u4e14\u6700\u591a100\u4e2a\u52a8\u4f5c\uff0c[]\u4ec5\u8bb0\u5f55\u547d\u4e2d");
            int decisions = 0;
            for (RuleConfig.Action a : r.getActions()) {
                this.action(a);
                if (a.getType() == RuleConfig.ActionType.SET_RESULT && c.getOutputs() != null) {
                    ParameterDefinition output = (ParameterDefinition)outputs.get(a.getKey());
                    ConfigValidator.require(output != null, "SET_RESULT\u51fa\u53c2\u672a\u58f0\u660e\uff1a" + a.getKey());
                    ConfigValidator.require(ConfigValidator.validValue(a.getValue(), output), "\u51fa\u53c2\u5e38\u91cf\u7c7b\u578b\u6216\u5fc5\u586b\u7ea6\u675f\u4e0d\u5339\u914d\uff1a" + a.getKey());
                }
                if (a.getType() != RuleConfig.ActionType.AUTO_APPROVE && a.getType() != RuleConfig.ActionType.REQUIRE_APPROVAL) continue;
                ConfigValidator.require(r.getKind() == RuleConfig.Kind.BASE, "\u81ea\u52a8\u901a\u8fc7\u548c\u57fa\u7840\u5ba1\u6279\u53ea\u80fd\u914d\u7f6e\u5728BASE\u89c4\u5219");
                ++decisions;
            }
            ConfigValidator.require(decisions <= 1, "\u540c\u4e00\u6761\u89c4\u5219\u53ea\u80fd\u6709\u4e00\u4e2a\u57fa\u7840\u5ba1\u6279\u51b3\u7b56\u52a8\u4f5c");
        }
    }

    private void parameter(ParameterDefinition f, boolean input) {
        ConfigValidator.require(f != null && f.getType() != null, "\u53c2\u6570\u53catype\u5fc5\u586b");
        if (input) {
            FieldPath.parse(f.getName());
        } else {
            ConfigValidator.fieldName(f.getName());
        }
        ConfigValidator.require(f.getDescription() == null || f.getDescription().length() <= 2000, "\u53c2\u6570description\u6700\u591a2000\u5b57\u7b26");
        ConfigValidator.require(f.getExample() == null || ConfigValidator.validValue(f.getExample(), f), "\u53c2\u6570example\u7c7b\u578b\u6216\u5fc5\u586b\u7ea6\u675f\u4e0d\u5339\u914d\uff1a" + f.getName());
    }

    private static boolean validValue(JsonNode value, ParameterDefinition f) {
        return value == null || value.isNull() ? !f.getRequired() : ConfigValidator.typed(value, f.getType());
    }

    private void condition(RuleConfig.Condition c, Map<String, RuleConfig.FieldType> fields, int depth, int[] count) {
        ConfigValidator.require(c != null && depth <= 10 && (count[0] = count[0] + 1) <= 1000, "\u6761\u4ef6\u4e0d\u80fd\u4e3a\u7a7a\uff0c\u6700\u591a10\u5c42\u30011000\u8282\u70b9");
        if (c.getLogic() != null) {
            ConfigValidator.require(c.getField() == null && c.getOperator() == null && (c.getValue() == null || c.getValue().isNull()), "\u7ec4\u5408\u6761\u4ef6\u4e0d\u80fd\u586b\u5199field/operator/value");
            ConfigValidator.require(c.getConditions() != null && !c.getConditions().isEmpty(), "\u7ec4\u5408\u6761\u4ef6\u9700\u8981\u5b50\u6761\u4ef6");
            ConfigValidator.require(c.getLogic() != RuleConfig.Logic.NOT || c.getConditions().size() == 1, "NOT\u5fc5\u987b\u6070\u597d\u4e00\u4e2a\u5b50\u6761\u4ef6");
            for (RuleConfig.Condition child : c.getConditions()) {
                this.condition(child, fields, depth + 1, count);
            }
            return;
        }
        ConfigValidator.require(c.getConditions() == null, "\u53f6\u6761\u4ef6\u4e0d\u80fd\u586b\u5199conditions");
        ConfigValidator.require(fields.containsKey(c.getField()), "\u6761\u4ef6\u5b57\u6bb5\u672a\u58f0\u660e\uff1a" + c.getField());
        ConfigValidator.require(c.getOperator() != null, "operator\u5fc5\u586b");
        RuleConfig.Operator op = c.getOperator();
        RuleConfig.FieldType type = fields.get(c.getField());
        JsonNode v = c.getValue();
        if (op == RuleConfig.Operator.EXISTS || op == RuleConfig.Operator.NOT_EXISTS) {
            ConfigValidator.require(v == null || v.isNull(), "\u5b58\u5728\u6027\u6761\u4ef6\u4e0d\u9700\u8981value");
            return;
        }
        ConfigValidator.require(v != null && !v.isNull(), "\u6bd4\u8f83\u6761\u4ef6value\u5fc5\u586b\u4e14\u4e0d\u80fd\u4e3anull");
        ConfigValidator.require(type != RuleConfig.FieldType.JSON, "JSON\u53c2\u6570\u4ec5\u652f\u6301EXISTS/NOT_EXISTS");
        switch (op) {
            case GT: 
            case GE: 
            case LT: 
            case LE: {
                ConfigValidator.require(type == RuleConfig.FieldType.NUMBER && v.isNumber(), "\u5927\u5c0f\u6bd4\u8f83\u53ea\u652f\u6301NUMBER\u6570\u503c");
                break;
            }
            case EQ: 
            case NE: {
                ConfigValidator.require(type != RuleConfig.FieldType.STRING_LIST && ConfigValidator.typed(v, type), "EQ/NE\u9700\u8981\u540c\u7c7b\u578b\u6807\u91cf");
                break;
            }
            case IN: 
            case NOT_IN: {
                ConfigValidator.require(type != RuleConfig.FieldType.STRING_LIST && v.isArray() && v.size() > 0 && v.size() <= 1000, "IN/NOT_IN\u9700\u89811\u52301000\u4e2a\u540c\u7c7b\u578b\u6807\u91cf");
                for (JsonNode item : v) {
                    ConfigValidator.require(ConfigValidator.typed(item, type), "IN/NOT_IN\u6570\u7ec4\u7c7b\u578b\u4e0d\u5339\u914d");
                }
                break;
            }
            case CONTAINS: {
                ConfigValidator.require((type == RuleConfig.FieldType.STRING || type == RuleConfig.FieldType.STRING_LIST) && v.isTextual(), "CONTAINS\u9700\u8981\u5b57\u7b26\u4e32\u5b57\u6bb5\u6216\u5b57\u7b26\u4e32\u6570\u7ec4\u53ca\u5b57\u7b26\u4e32value");
                break;
            }
            case CONTAINS_ANY: {
                ConfigValidator.require((type == RuleConfig.FieldType.STRING || type == RuleConfig.FieldType.STRING_LIST) && v.isArray() && v.size() > 0 && v.size() <= 1000, "CONTAINS_ANY\u9700\u8981\u975e\u7a7a\u5b57\u7b26\u4e32\u6570\u7ec4");
                for (JsonNode item : v) {
                    ConfigValidator.require(item.isTextual(), "CONTAINS_ANY\u53ea\u80fd\u4f7f\u7528\u5b57\u7b26\u4e32");
                }
                break;
            }
            default: {
                throw new IllegalArgumentException("\u4e0d\u652f\u6301\u7684operator");
            }
        }
    }

    private void action(RuleConfig.Action a) {
        boolean targets;
        ConfigValidator.require(a != null && a.getType() != null, "\u52a8\u4f5c\u53catype\u5fc5\u586b");
        RuleConfig.ActionType t = a.getType();
        boolean approval = t == RuleConfig.ActionType.REQUIRE_APPROVAL || t == RuleConfig.ActionType.APPEND_APPROVAL;
        boolean bl = targets = approval || t == RuleConfig.ActionType.CC_AFTER_APPROVAL;
        ConfigValidator.require(approval ? a.getMode() != null : a.getMode() == null, "\u5ba1\u6279\u52a8\u4f5c\u9700\u8981mode\uff0c\u5176\u4ed6\u52a8\u4f5c\u4e0d\u80fd\u586b\u5199mode");
        if (targets) {
            ConfigValidator.require(a.getTargets() != null && !a.getTargets().isEmpty() && a.getTargets().size() <= 100, "\u5ba1\u6279/\u6284\u9001targets\u5fc5\u987b\u5305\u542b1\u5230100\u9879");
            HashSet<String> unique = new HashSet<String>();
            for (String target : a.getTargets()) {
                ConfigValidator.require(target != null && target.matches("(ROLE|USER):[A-Za-z0-9_:-]{1,64}"), "targets\u9700\u8981ROLE:\u89d2\u8272\u7f16\u7801\u6216USER:\u7528\u6237ID");
                ConfigValidator.require(unique.add(target), "targets\u4e0d\u80fd\u91cd\u590d");
            }
        } else {
            ConfigValidator.require(a.getTargets() == null, "\u8be5\u52a8\u4f5c\u4e0d\u63a5\u53d7targets");
        }
        if (t == RuleConfig.ActionType.SET_RESULT) {
            ConfigValidator.fieldName(a.getKey());
            ConfigValidator.require(a.getValue() != null, "SET_RESULT\u7684value\u5fc5\u586b");
        } else {
            ConfigValidator.require(a.getKey() == null && (a.getValue() == null || a.getValue().isNull()), "\u53ea\u6709SET_RESULT\u63a5\u53d7key/value");
        }
        if (t == RuleConfig.ActionType.REJECT) {
            ConfigValidator.require(a.getReason() != null && !a.getReason().trim().isEmpty() && a.getReason().length() <= 2000, "REJECT\u7684reason\u5fc5\u586b\uff0c\u6700\u591a2000\u5b57\u7b26");
        } else {
            ConfigValidator.require(a.getReason() == null, "\u53ea\u6709REJECT\u63a5\u53d7reason");
        }
    }

    static boolean typed(JsonNode v, RuleConfig.FieldType type) {
        if (v == null || v.isNull()) {
            return false;
        }
        switch (type) {
            case NUMBER: {
                return v.isNumber();
            }
            case STRING: {
                return v.isTextual();
            }
            case BOOLEAN: {
                return v.isBoolean();
            }
            case STRING_LIST: {
                if (!v.isArray()) {
                    return false;
                }
                for (JsonNode item : v) {
                    if (item.isTextual()) continue;
                    return false;
                }
                return true;
            }
            case JSON: {
                return true;
            }
        }
        return false;
    }

    public Map<String, JsonNode> validateData(RuleConfig c, Map<String, JsonNode> data) {
        LinkedHashMap<String, JsonNode> values = new LinkedHashMap<String, JsonNode>();
        for (RuleConfig.Field f : c.getFields()) {
            JsonNode v = FieldPath.parse(f.getName()).read(data);
            if (v == null || v.isNull()) {
                ConfigValidator.require(!f.getRequired(), "\u7f3a\u5c11\u5fc5\u586b\u5b57\u6bb5\uff1a" + f.getName());
                continue;
            }
            ConfigValidator.require(ConfigValidator.typed(v, f.getType()), "\u5b57\u6bb5\u7c7b\u578b\u4e0d\u5339\u914d\uff1a" + f.getName() + "\uff0c\u9700\u8981" + (Object)((Object)f.getType()));
            values.put(f.getName(), v.deepCopy());
        }
        return values;
    }

    public void validateOutput(RuleConfig c, GenericRuleResult.Result result) {
        if (c.getOutputs() == null) {
            return;
        }
        LinkedHashMap<String, ParameterDefinition> definitions = new LinkedHashMap<String, ParameterDefinition>();
        for (ParameterDefinition parameterDefinition : c.getOutputs()) {
            definitions.put(parameterDefinition.getName(), parameterDefinition);
        }
        for (Map.Entry entry : result.getResults().entrySet()) {
            ParameterDefinition f = (ParameterDefinition)definitions.get(entry.getKey());
            if (f != null && ConfigValidator.validValue((JsonNode)entry.getValue(), f)) continue;
            throw new IllegalStateException("\u89c4\u5219\u51fa\u53c2\u4e0d\u7b26\u5408\u5f53\u524d\u7248\u672c\u5b9a\u4e49\uff1a" + (String)entry.getKey());
        }
        String decision = result.getPlan().getDecision();
        if (!"NO_MATCH".equals(decision) && !"REJECTED".equals(decision)) {
            for (ParameterDefinition f : c.getOutputs()) {
                if (ConfigValidator.validValue(result.getResults().get(f.getName()), f)) continue;
                throw new IllegalStateException("\u89c4\u5219\u672a\u8fd4\u56de\u5fc5\u586b\u51fa\u53c2\uff1a" + f.getName());
            }
        }
    }
}
