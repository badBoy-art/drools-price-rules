/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.fasterxml.jackson.annotation.JsonCreator
 *  com.fasterxml.jackson.annotation.JsonInclude
 *  com.fasterxml.jackson.annotation.JsonInclude$Include
 *  com.fasterxml.jackson.annotation.JsonProperty
 *  com.fasterxml.jackson.databind.JsonNode
 */
package example.rules;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import example.rules.ParameterDefinition;
import java.util.List;

@JsonInclude(value=JsonInclude.Include.NON_NULL)
public final class RuleConfig {
    private final String ruleName;
    private final String businessType;
    private final List<Field> fields;
    private final List<Rule> rules;
    private final List<ParameterDefinition> outputs;
    private final List<ParameterSource> parameterSources;
    private final Calculation calculation;

    public RuleConfig(String ruleName, String businessType, List<Field> fields, List<Rule> rules) {
        this(ruleName, businessType, fields, rules, null, null, null);
    }

    public RuleConfig(String ruleName, String businessType, List<Field> fields, List<Rule> rules,
                      List<ParameterDefinition> outputs, List<ParameterSource> parameterSources) {
        this(ruleName, businessType, fields, rules, outputs, parameterSources, null);
    }

    @JsonCreator
    public RuleConfig(@JsonProperty(value="ruleName") String ruleName, @JsonProperty(value="businessType") String businessType, @JsonProperty(value="fields") List<Field> fields, @JsonProperty(value="rules") List<Rule> rules, @JsonProperty(value="outputs") List<ParameterDefinition> outputs, @JsonProperty(value="parameterSources") List<ParameterSource> parameterSources, @JsonProperty(value="calculation") Calculation calculation) {
        this.ruleName = ruleName;
        this.businessType = businessType;
        this.fields = fields;
        this.rules = rules;
        this.outputs = outputs;
        this.parameterSources = parameterSources;
        this.calculation = calculation;
    }

    public String getRuleName() {
        return this.ruleName;
    }

    public String getBusinessType() {
        return this.businessType;
    }

    public List<Field> getFields() {
        return this.fields;
    }

    public List<Rule> getRules() {
        return this.rules;
    }

    public List<ParameterDefinition> getOutputs() {
        return this.outputs;
    }

    public List<ParameterSource> getParameterSources() {
        return this.parameterSources;
    }

    public Calculation getCalculation() { return calculation; }

    /** 服务端固定公式的受控计算配置。 */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static final class Calculation {
        public enum Type { GROSS_MARGIN, COST_INCREASE }
        private final Type type;
        private final String baseField;
        private final String compareField;
        private final String resultField;
        @JsonCreator
        public Calculation(@JsonProperty("type") Type type, @JsonProperty("baseField") String baseField,
                           @JsonProperty("compareField") String compareField, @JsonProperty("resultField") String resultField) {
            this.type = type; this.baseField = baseField; this.compareField = compareField; this.resultField = resultField;
        }
        public Type getType() { return type; }
        public String getBaseField() { return baseField; }
        public String getCompareField() { return compareField; }
        public String getResultField() { return resultField; }
    }

    @JsonInclude(value=JsonInclude.Include.NON_NULL)
    public static final class Action {
        private final ActionType type;
        private final Mode mode;
        private final List<String> targets;
        private final String key;
        private final JsonNode value;
        private final String reason;

        @JsonCreator
        public Action(@JsonProperty(value="type") ActionType type, @JsonProperty(value="mode") Mode mode, @JsonProperty(value="targets") List<String> targets, @JsonProperty(value="key") String key, @JsonProperty(value="value") JsonNode value, @JsonProperty(value="reason") String reason) {
            this.type = type;
            this.mode = mode;
            this.targets = targets;
            this.key = key;
            this.value = value;
            this.reason = reason;
        }

        public ActionType getType() {
            return this.type;
        }

        public Mode getMode() {
            return this.mode;
        }

        public List<String> getTargets() {
            return this.targets;
        }

        public String getKey() {
            return this.key;
        }

        public JsonNode getValue() {
            return this.value;
        }

        public String getReason() {
            return this.reason;
        }
    }

    @JsonInclude(value=JsonInclude.Include.NON_NULL)
    public static final class Condition {
        private final String field;
        private final Operator operator;
        private final JsonNode value;
        private final Logic logic;
        private final List<Condition> conditions;

        @JsonCreator
        public Condition(@JsonProperty(value="field") String field, @JsonProperty(value="operator") Operator operator, @JsonProperty(value="value") JsonNode value, @JsonProperty(value="logic") Logic logic, @JsonProperty(value="conditions") List<Condition> conditions) {
            this.field = field;
            this.operator = operator;
            this.value = value;
            this.logic = logic;
            this.conditions = conditions;
        }

        public String getField() {
            return this.field;
        }

        public Operator getOperator() {
            return this.operator;
        }

        public JsonNode getValue() {
            return this.value;
        }

        public Logic getLogic() {
            return this.logic;
        }

        public List<Condition> getConditions() {
            return this.conditions;
        }
    }

    @JsonInclude(value=JsonInclude.Include.NON_NULL)
    public static final class Rule {
        private final String id;
        private final String name;
        private final Kind kind;
        private final int priority;
        private final Condition when;
        private final List<Action> actions;

        @JsonCreator
        public Rule(@JsonProperty(value="id") String id, @JsonProperty(value="name") String name, @JsonProperty(value="kind") Kind kind, @JsonProperty(value="priority") int priority, @JsonProperty(value="when") Condition when, @JsonProperty(value="actions") List<Action> actions) {
            this.id = id;
            this.name = name;
            this.kind = kind;
            this.priority = priority;
            this.when = when;
            this.actions = actions;
        }

        public String getId() {
            return this.id;
        }

        public String getName() {
            return this.name;
        }

        public Kind getKind() {
            return this.kind;
        }

        public int getPriority() {
            return this.priority;
        }

        public Condition getWhen() {
            return this.when;
        }

        public List<Action> getActions() {
            return this.actions;
        }
    }

    @JsonInclude(value=JsonInclude.Include.NON_NULL)
    public static final class ParameterSource {
        private final String ruleName;
        private final Long version;
        private final List<String> inputs;
        private final List<String> outputs;

        @JsonCreator
        public ParameterSource(@JsonProperty(value="ruleName") String ruleName, @JsonProperty(value="version") Long version, @JsonProperty(value="inputs") List<String> inputs, @JsonProperty(value="outputs") List<String> outputs) {
            this.ruleName = ruleName;
            this.version = version;
            this.inputs = inputs;
            this.outputs = outputs;
        }

        public String getRuleName() {
            return this.ruleName;
        }

        public Long getVersion() {
            return this.version;
        }

        public List<String> getInputs() {
            return this.inputs;
        }

        public List<String> getOutputs() {
            return this.outputs;
        }
    }

    @JsonInclude(value=JsonInclude.Include.NON_NULL)
    public static final class Field
    extends ParameterDefinition {
        public Field(String name, FieldType type, boolean required) {
            this(name, type, required, null, null);
        }

        @JsonCreator
        public Field(@JsonProperty(value="name") String name, @JsonProperty(value="type") FieldType type, @JsonProperty(value="required") boolean required, @JsonProperty(value="description") String description, @JsonProperty(value="example") JsonNode example) {
            super(name, type, required, description, example);
        }
    }

    public static enum ActionType {
        AUTO_APPROVE,
        REQUIRE_APPROVAL,
        APPEND_APPROVAL,
        CC_AFTER_APPROVAL,
        REJECT,
        SET_RESULT;

    }

    public static enum Mode {
        SERIAL,
        PARALLEL;

    }

    public static enum Operator {
        GT,
        GE,
        LT,
        LE,
        EQ,
        NE,
        IN,
        NOT_IN,
        CONTAINS,
        CONTAINS_ANY,
        EXISTS,
        NOT_EXISTS;

    }

    public static enum Logic {
        ALL,
        ANY,
        NOT;

    }

    public static enum Kind {
        BASE,
        SUPPLEMENT;

    }

    public static enum FieldType {
        NUMBER,
        STRING,
        BOOLEAN,
        STRING_LIST,
        JSON;

    }
}
