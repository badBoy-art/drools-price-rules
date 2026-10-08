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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;

public final class ApprovalPlan {
    private Base base;
    private final List<Risk> risks = new ArrayList<Risk>();

    public void base(String id, boolean auto, String mode, String[] approvers) {
        if (this.base != null) {
            throw new IllegalStateException("\u591a\u4e2a\u57fa\u7840\u533a\u95f4\u540c\u65f6\u547d\u4e2d");
        }
        this.base = new Base(id, auto, mode, ApprovalPlan.immutableCopy(Arrays.asList(approvers)));
    }

    public void risk(String id, int order, String mode, String[] approvers, String[] cc) {
        this.risks.add(new Risk(id, order, mode, ApprovalPlan.immutableCopy(Arrays.asList(approvers)), ApprovalPlan.immutableCopy(Arrays.asList(cc))));
    }

    public Result finish() {
        ArrayList<Step> steps = new ArrayList<Step>();
        ArrayList<String> matched = new ArrayList<String>();
        LinkedHashSet<String> cc = new LinkedHashSet<String>();
        if (this.base != null) {
            matched.add(this.base.getId());
            this.appendSteps(steps, this.base.getMode(), this.base.getApprovers(), this.base.getId());
        }
        this.risks.sort(Comparator.comparingInt(Risk::getOrder));
        for (Risk risk : this.risks) {
            matched.add(risk.getId());
            this.appendSteps(steps, risk.getMode(), risk.getApprovers(), risk.getId());
            cc.addAll(risk.getCc());
        }
        String decision = this.base == null ? "NO_MATCH" : (!steps.isEmpty() ? "MANUAL_APPROVAL" : (this.base.getAuto() ? "AUTO_APPROVE" : "NO_MATCH"));
        return new Result(decision, ApprovalPlan.immutableCopy(steps), ApprovalPlan.immutableCopy(cc), ApprovalPlan.immutableCopy(matched));
    }

    private void appendSteps(List<Step> steps, String mode, List<String> approvers, String source) {
        if ("PARALLEL".equals(mode) && !approvers.isEmpty()) {
            steps.add(new Step(steps.size() + 1, mode, approvers, source));
        } else {
            for (String approver : approvers) {
                steps.add(new Step(steps.size() + 1, mode, Collections.singletonList(approver), source));
            }
        }
    }

    private static <T> List<T> immutableCopy(Collection<T> values) {
        return Collections.unmodifiableList(new ArrayList<T>(values));
    }

    private static final class Risk {
        private final String id;
        private final int order;
        private final String mode;
        private final List<String> approvers;
        private final List<String> cc;

        @JsonCreator
        public Risk(@JsonProperty(value="id") String id, @JsonProperty(value="order") int order, @JsonProperty(value="mode") String mode, @JsonProperty(value="approvers") List<String> approvers, @JsonProperty(value="cc") List<String> cc) {
            this.id = id;
            this.order = order;
            this.mode = mode;
            this.approvers = approvers;
            this.cc = cc;
        }

        @JsonProperty(value="id")
        public String getId() {
            return this.id;
        }

        @JsonProperty(value="order")
        public int getOrder() {
            return this.order;
        }

        @JsonProperty(value="mode")
        public String getMode() {
            return this.mode;
        }

        @JsonProperty(value="approvers")
        public List<String> getApprovers() {
            return this.approvers;
        }

        @JsonProperty(value="cc")
        public List<String> getCc() {
            return this.cc;
        }
    }

    private static final class Base {
        private final String id;
        private final boolean auto;
        private final String mode;
        private final List<String> approvers;

        @JsonCreator
        public Base(@JsonProperty(value="id") String id, @JsonProperty(value="auto") boolean auto, @JsonProperty(value="mode") String mode, @JsonProperty(value="approvers") List<String> approvers) {
            this.id = id;
            this.auto = auto;
            this.mode = mode;
            this.approvers = approvers;
        }

        @JsonProperty(value="id")
        public String getId() {
            return this.id;
        }

        @JsonProperty(value="auto")
        public boolean getAuto() {
            return this.auto;
        }

        @JsonProperty(value="mode")
        public String getMode() {
            return this.mode;
        }

        @JsonProperty(value="approvers")
        public List<String> getApprovers() {
            return this.approvers;
        }
    }

    public static final class Result {
        private final String decision;
        private final List<Step> approvalSteps;
        private final List<String> ccAfterApproval;
        private final List<String> matchedRules;

        @JsonCreator
        public Result(@JsonProperty(value="decision") String decision, @JsonProperty(value="approvalSteps") List<Step> approvalSteps, @JsonProperty(value="ccAfterApproval") List<String> ccAfterApproval, @JsonProperty(value="matchedRules") List<String> matchedRules) {
            this.decision = decision;
            this.approvalSteps = approvalSteps;
            this.ccAfterApproval = ccAfterApproval;
            this.matchedRules = matchedRules;
        }

        @JsonProperty(value="decision")
        public String getDecision() {
            return this.decision;
        }

        @JsonProperty(value="approvalSteps")
        public List<Step> getApprovalSteps() {
            return this.approvalSteps;
        }

        @JsonProperty(value="ccAfterApproval")
        public List<String> getCcAfterApproval() {
            return this.ccAfterApproval;
        }

        @JsonProperty(value="matchedRules")
        public List<String> getMatchedRules() {
            return this.matchedRules;
        }
    }

    public static final class Step {
        private final int order;
        private final String mode;
        private final List<String> approvers;
        private final String sourceRule;

        @JsonCreator
        public Step(@JsonProperty(value="order") int order, @JsonProperty(value="mode") String mode, @JsonProperty(value="approvers") List<String> approvers, @JsonProperty(value="sourceRule") String sourceRule) {
            this.order = order;
            this.mode = mode;
            this.approvers = approvers;
            this.sourceRule = sourceRule;
        }

        @JsonProperty(value="order")
        public int getOrder() {
            return this.order;
        }

        @JsonProperty(value="mode")
        public String getMode() {
            return this.mode;
        }

        @JsonProperty(value="approvers")
        public List<String> getApprovers() {
            return this.approvers;
        }

        @JsonProperty(value="sourceRule")
        public String getSourceRule() {
            return this.sourceRule;
        }
    }
}

