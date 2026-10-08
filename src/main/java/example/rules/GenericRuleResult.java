/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.fasterxml.jackson.databind.JsonNode
 */
package example.rules;

import com.fasterxml.jackson.databind.JsonNode;
import example.rules.ApiModels;
import example.rules.ApprovalPlan;
import example.rules.RuleConfig;
import example.rules.RuleJson;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

public final class GenericRuleResult {
    private static final Comparator<Match> ORDER = Comparator.comparingInt((Match m) -> m.priority).reversed().thenComparingInt(m -> m.index);
    private final List<Match> matches = new ArrayList<Match>();

    public void match(String id, String kind, int priority, int index, String actionsJson) {
        try {
            this.matches.add(new Match(id, RuleConfig.Kind.valueOf(kind), priority, index,
                    Arrays.asList(RuleJson.MAPPER.readValue(actionsJson, RuleConfig.Action[].class))));
        }
        catch (IOException e) {
            throw new IllegalStateException("DRL\u52a8\u4f5c\u5e38\u91cf\u635f\u574f", e);
        }
    }

    public Result finish() {
        this.matches.sort(ORDER);
        Match base = this.matches.stream().filter(m -> m.kind == RuleConfig.Kind.BASE).findFirst().orElse(null);
        ArrayList<Match> effective = new ArrayList<Match>();
        if (base != null) {
            effective.add(base);
        }
        this.matches.stream().filter(m -> m.kind == RuleConfig.Kind.SUPPLEMENT).forEach(effective::add);
        ArrayList<ApprovalPlan.Step> steps = new ArrayList<ApprovalPlan.Step>();
        ArrayList<String> matched = new ArrayList<String>();
        LinkedHashSet<String> cc = new LinkedHashSet<String>();
        LinkedHashSet<String> reasons = new LinkedHashSet<String>();
        boolean auto = false;
        for (Match match : effective) {
            matched.add(match.id);
            block8: for (RuleConfig.Action a : match.actions) {
                switch (a.getType()) {
                    case AUTO_APPROVE: {
                        auto = true;
                        break;
                    }
                    case REQUIRE_APPROVAL: 
                    case APPEND_APPROVAL: {
                        if (a.getMode() == RuleConfig.Mode.PARALLEL) {
                            steps.add(new ApprovalPlan.Step(steps.size() + 1, "PARALLEL", GenericRuleResult.immutable(a.getTargets()), match.id));
                            break;
                        }
                        for (String target : a.getTargets()) {
                            steps.add(new ApprovalPlan.Step(steps.size() + 1, "SERIAL", Collections.singletonList(target), match.id));
                        }
                        continue block8;
                    }
                    case CC_AFTER_APPROVAL: {
                        cc.addAll(a.getTargets());
                        break;
                    }
                    case REJECT: {
                        reasons.add(a.getReason());
                        break;
                    }
                    case SET_RESULT: {
                        break;
                    }
                    default: {
                        throw new IllegalStateException("\u672a\u77e5\u52a8\u4f5c");
                    }
                }
            }
        }
        LinkedHashMap<String, JsonNode> values = new LinkedHashMap<String, JsonNode>();
        effective.sort(ORDER);
        for (Match m3 : effective) {
            for (RuleConfig.Action a : m3.actions) {
                if (a.getType() != RuleConfig.ActionType.SET_RESULT) continue;
                values.putIfAbsent(a.getKey(), a.getValue().deepCopy());
            }
        }
        String string = !reasons.isEmpty() ? "REJECTED" : (base == null ? "NO_MATCH" : (!steps.isEmpty() ? "MANUAL_APPROVAL" : (auto ? "AUTO_APPROVE" : "MATCHED")));
        ApprovalPlan.Result plan = new ApprovalPlan.Result(string, GenericRuleResult.immutable(steps), GenericRuleResult.immutable(cc), GenericRuleResult.immutable(matched));
        return new Result(plan, values, GenericRuleResult.immutable(reasons));
    }

    private static <T> List<T> immutable(Collection<T> values) {
        return Collections.unmodifiableList(new ArrayList<T>(values));
    }

    private static final class Match {
        final String id;
        final RuleConfig.Kind kind;
        final int priority;
        final int index;
        final List<RuleConfig.Action> actions;

        Match(String id, RuleConfig.Kind kind, int priority, int index, List<RuleConfig.Action> actions) {
            this.id = id;
            this.kind = kind;
            this.priority = priority;
            this.index = index;
            this.actions = actions;
        }
    }

    public static final class Result
    extends ApiModels.RuleOutcome {
        Result(ApprovalPlan.Result plan, Map<String, JsonNode> results, List<String> reasons) {
            super(plan, Collections.unmodifiableMap(new LinkedHashMap<String, JsonNode>(results)), Collections.unmodifiableList(new ArrayList<String>(reasons)));
        }
    }
}
