/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.fasterxml.jackson.annotation.JsonCreator
 *  com.fasterxml.jackson.annotation.JsonProperty
 */
package example.rules;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.util.List;

public final class PriceRuleConfig {
    private final String ruleName;
    private final BusinessType businessType;
    private final List<Policy> policies;
    private final Calculation calculation;

    public PriceRuleConfig(String ruleName, BusinessType businessType, List<Policy> policies) {
        this(ruleName, businessType, policies, null);
    }

    @JsonCreator
    public PriceRuleConfig(@JsonProperty(value="ruleName") String ruleName, @JsonProperty(value="businessType") BusinessType businessType, @JsonProperty(value="policies") List<Policy> policies, @JsonProperty(value="calculation") Calculation calculation) {
        this.ruleName = ruleName;
        this.businessType = businessType;
        this.policies = policies;
        this.calculation = calculation;
    }

    @JsonProperty(value="ruleName")
    public String getRuleName() {
        return this.ruleName;
    }

    @JsonProperty(value="businessType")
    public BusinessType getBusinessType() {
        return this.businessType;
    }

    @JsonProperty(value="policies")
    public List<Policy> getPolicies() {
        return this.policies;
    }

    public Calculation getCalculation() { return calculation; }

    /** 兼容价格页面使用的受控计算配置。 */
    public static final class Calculation {
        private final RuleConfig.Calculation.Type type;
        private final String baseField;
        private final String compareField;
        private final String resultField;
        @JsonCreator
        public Calculation(@JsonProperty("type") RuleConfig.Calculation.Type type,
                           @JsonProperty("baseField") String baseField,
                           @JsonProperty("compareField") String compareField,
                           @JsonProperty("resultField") String resultField) {
            this.type = type; this.baseField = baseField; this.compareField = compareField; this.resultField = resultField;
        }
        public RuleConfig.Calculation.Type getType() { return type; }
        public String getBaseField() { return baseField; }
        public String getCompareField() { return compareField; }
        public String getResultField() { return resultField; }
    }

    public static final class Risk {
        private final String id;
        private final Operator operator;
        private final BigDecimal threshold;
        private final Mode mode;
        private final List<String> additionalApprovers;
        private final List<String> ccAfterApproval;

        @JsonCreator
        public Risk(@JsonProperty(value="id") String id, @JsonProperty(value="operator") Operator operator, @JsonProperty(value="threshold") BigDecimal threshold, @JsonProperty(value="mode") Mode mode, @JsonProperty(value="additionalApprovers") List<String> additionalApprovers, @JsonProperty(value="ccAfterApproval") List<String> ccAfterApproval) {
            this.id = id;
            this.operator = operator;
            this.threshold = threshold;
            this.mode = mode;
            this.additionalApprovers = additionalApprovers;
            this.ccAfterApproval = ccAfterApproval;
        }

        @JsonProperty(value="id")
        public String getId() {
            return this.id;
        }

        @JsonProperty(value="operator")
        public Operator getOperator() {
            return this.operator;
        }

        @JsonProperty(value="threshold")
        public BigDecimal getThreshold() {
            return this.threshold;
        }

        @JsonProperty(value="mode")
        public Mode getMode() {
            return this.mode;
        }

        @JsonProperty(value="additionalApprovers")
        public List<String> getAdditionalApprovers() {
            return this.additionalApprovers;
        }

        @JsonProperty(value="ccAfterApproval")
        public List<String> getCcAfterApproval() {
            return this.ccAfterApproval;
        }
    }

    public static final class Band {
        private final String id;
        private final BigDecimal min;
        private final boolean minInclusive;
        private final BigDecimal max;
        private final boolean maxInclusive;
        private final boolean autoApprove;
        private final Mode mode;
        private final List<String> approvers;

        @JsonCreator
        public Band(@JsonProperty(value="id") String id, @JsonProperty(value="min") BigDecimal min, @JsonProperty(value="minInclusive") boolean minInclusive, @JsonProperty(value="max") BigDecimal max, @JsonProperty(value="maxInclusive") boolean maxInclusive, @JsonProperty(value="autoApprove") boolean autoApprove, @JsonProperty(value="mode") Mode mode, @JsonProperty(value="approvers") List<String> approvers) {
            this.id = id;
            this.min = min;
            this.minInclusive = minInclusive;
            this.max = max;
            this.maxInclusive = maxInclusive;
            this.autoApprove = autoApprove;
            this.mode = mode;
            this.approvers = approvers;
        }

        @JsonProperty(value="id")
        public String getId() {
            return this.id;
        }

        @JsonProperty(value="min")
        public BigDecimal getMin() {
            return this.min;
        }

        @JsonProperty(value="minInclusive")
        public boolean getMinInclusive() {
            return this.minInclusive;
        }

        @JsonProperty(value="max")
        public BigDecimal getMax() {
            return this.max;
        }

        @JsonProperty(value="maxInclusive")
        public boolean getMaxInclusive() {
            return this.maxInclusive;
        }

        @JsonProperty(value="autoApprove")
        public boolean getAutoApprove() {
            return this.autoApprove;
        }

        @JsonProperty(value="mode")
        public Mode getMode() {
            return this.mode;
        }

        @JsonProperty(value="approvers")
        public List<String> getApprovers() {
            return this.approvers;
        }
    }

    public static final class Policy {
        private final String id;
        private final String name;
        private final boolean defaultPolicy;
        private final List<String> categoryIds;
        private final List<Band> bands;
        private final List<Risk> risks;

        @JsonCreator
        public Policy(@JsonProperty(value="id") String id, @JsonProperty(value="name") String name, @JsonProperty(value="defaultPolicy") boolean defaultPolicy, @JsonProperty(value="categoryIds") List<String> categoryIds, @JsonProperty(value="bands") List<Band> bands, @JsonProperty(value="risks") List<Risk> risks) {
            this.id = id;
            this.name = name;
            this.defaultPolicy = defaultPolicy;
            this.categoryIds = categoryIds;
            this.bands = bands;
            this.risks = risks;
        }

        @JsonProperty(value="id")
        public String getId() {
            return this.id;
        }

        @JsonProperty(value="name")
        public String getName() {
            return this.name;
        }

        @JsonProperty(value="defaultPolicy")
        public boolean getDefaultPolicy() {
            return this.defaultPolicy;
        }

        @JsonProperty(value="categoryIds")
        public List<String> getCategoryIds() {
            return this.categoryIds;
        }

        @JsonProperty(value="bands")
        public List<Band> getBands() {
            return this.bands;
        }

        @JsonProperty(value="risks")
        public List<Risk> getRisks() {
            return this.risks;
        }
    }

    public static enum Operator {
        LT,
        LE,
        EQ,
        GE,
        GT;

    }

    public static enum Mode {
        SERIAL,
        PARALLEL;

    }

    public static enum BusinessType {
        SALES_PRICE,
        COST_PRICE;

    }
}
