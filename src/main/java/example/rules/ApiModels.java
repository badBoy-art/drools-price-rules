/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.fasterxml.jackson.annotation.JsonProperty
 *  com.fasterxml.jackson.databind.JsonNode
 */
package example.rules;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import example.rules.ApprovalPlan;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public final class ApiModels {
    private ApiModels() {
    }

    public static class PricePercentages {
        private final BigDecimal grossMarginPct;
        private final BigDecimal costIncreasePct;

        protected PricePercentages(BigDecimal grossMarginPct, BigDecimal costIncreasePct) {
            this.grossMarginPct = grossMarginPct;
            this.costIncreasePct = costIncreasePct;
        }

        public BigDecimal getGrossMarginPct() {
            return this.grossMarginPct;
        }

        public BigDecimal getCostIncreasePct() {
            return this.costIncreasePct;
        }
    }

    public static final class Problem {
        private final String type;
        private final String title;
        private final int status;
        private final String detail;

        public Problem(String type, String title, int status, String detail) {
            this.type = type;
            this.title = title;
            this.status = status;
            this.detail = detail;
        }

        public String getType() {
            return this.type;
        }

        public String getTitle() {
            return this.title;
        }

        public int getStatus() {
            return this.status;
        }

        public String getDetail() {
            return this.detail;
        }
    }

    public static class RuleOutcome {
        private final ApprovalPlan.Result plan;
        private final Map<String, JsonNode> results;
        private final List<String> reasons;

        protected RuleOutcome(ApprovalPlan.Result plan, Map<String, JsonNode> results, List<String> reasons) {
            this.plan = plan;
            this.results = results;
            this.reasons = reasons;
        }

        public ApprovalPlan.Result getPlan() {
            return this.plan;
        }

        public Map<String, JsonNode> getResults() {
            return this.results;
        }

        public List<String> getReasons() {
            return this.reasons;
        }
    }

    public static class RuleVersion {
        private final String ruleName;
        private final long version;

        protected RuleVersion(String ruleName, long version) {
            this.ruleName = ruleName;
            this.version = version;
        }

        @JsonProperty(value="rule_name")
        public String getRuleName() {
            return this.ruleName;
        }

        public long getVersion() {
            return this.version;
        }
    }

    public static class DocumentRequest
    extends RuleRequest {
        private final String documentId;

        protected DocumentRequest(String ruleName, Long version, String documentId) {
            super(ruleName, version);
            this.documentId = documentId;
        }

        public String getDocumentId() {
            return this.documentId;
        }
    }

    public static class RuleRequest {
        private final String ruleName;
        private final Long version;

        protected RuleRequest(String ruleName, Long version) {
            this.ruleName = ruleName;
            this.version = version;
        }

        @JsonProperty(value="rule_name")
        public String getRuleName() {
            return this.ruleName;
        }

        public Long getVersion() {
            return this.version;
        }
    }
}

