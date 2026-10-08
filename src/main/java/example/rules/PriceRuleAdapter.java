/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.fasterxml.jackson.databind.JsonNode
 *  com.fasterxml.jackson.databind.node.JsonNodeFactory
 *  org.springframework.stereotype.Component
 */
package example.rules;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import example.rules.DocumentFact;
import example.rules.GenericFact;
import example.rules.PriceConfigValidator;
import example.rules.PriceRuleConfig;
import example.rules.RuleConfig;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class PriceRuleAdapter {
    private final PriceConfigValidator validator;

    public PriceRuleAdapter(PriceConfigValidator validator) {
        this.validator = validator;
    }

    public RuleConfig toGeneric(PriceRuleConfig c) {
        this.validator.validate(c);
        String metric = PriceRuleAdapter.metric(c.getBusinessType().name());
        LinkedHashMap<String, RuleConfig.Field> fields = new LinkedHashMap<String, RuleConfig.Field>();
        fields.put("policyId", new RuleConfig.Field("policyId", RuleConfig.FieldType.STRING, true));
        PriceRuleConfig.Calculation sourceCalculation = c.getCalculation();
        RuleConfig.Calculation calculation = null;
        if (sourceCalculation == null) {
            fields.put(metric, new RuleConfig.Field(metric, RuleConfig.FieldType.NUMBER, true));
        } else {
            fields.put(sourceCalculation.getBaseField(), new RuleConfig.Field(sourceCalculation.getBaseField(), RuleConfig.FieldType.NUMBER, true));
            fields.put(sourceCalculation.getCompareField(), new RuleConfig.Field(sourceCalculation.getCompareField(), RuleConfig.FieldType.NUMBER, true));
            fields.put(sourceCalculation.getResultField(), new RuleConfig.Field(sourceCalculation.getResultField(), RuleConfig.FieldType.NUMBER, true));
            calculation = new RuleConfig.Calculation(sourceCalculation.getType(), sourceCalculation.getBaseField(),
                    sourceCalculation.getCompareField(), sourceCalculation.getResultField());
        }
        ArrayList<RuleConfig.Rule> rules = new ArrayList<RuleConfig.Rule>();
        for (PriceRuleConfig.Policy p : c.getPolicies()) {
            RuleConfig.Condition policy = PriceRuleAdapter.leaf("policyId", RuleConfig.Operator.EQ, (JsonNode)JsonNodeFactory.instance.textNode(p.getId()));
            for (PriceRuleConfig.Band b : p.getBands()) {
                ArrayList<RuleConfig.Condition> conditions = new ArrayList<RuleConfig.Condition>();
                conditions.add(policy);
                if (b.getMin() != null) {
                    conditions.add(PriceRuleAdapter.leaf(metric, b.getMinInclusive() ? RuleConfig.Operator.GE : RuleConfig.Operator.GT, (JsonNode)JsonNodeFactory.instance.numberNode(b.getMin())));
                }
                if (b.getMax() != null) {
                    conditions.add(PriceRuleAdapter.leaf(metric, b.getMaxInclusive() ? RuleConfig.Operator.LE : RuleConfig.Operator.LT, (JsonNode)JsonNodeFactory.instance.numberNode(b.getMax())));
                }
                RuleConfig.Action action = b.getAutoApprove() ? new RuleConfig.Action(RuleConfig.ActionType.AUTO_APPROVE, null, null, null, null, null) : PriceRuleAdapter.approval(RuleConfig.ActionType.REQUIRE_APPROVAL, b.getMode(), b.getApprovers());
                rules.add(new RuleConfig.Rule(p.getId() + ":" + b.getId(), p.getName(), RuleConfig.Kind.BASE, 0, PriceRuleAdapter.all(conditions), Collections.singletonList(action)));
            }
            for (PriceRuleConfig.Risk r : p.getRisks()) {
                ArrayList<RuleConfig.Action> actions = new ArrayList<RuleConfig.Action>();
                if (!r.getAdditionalApprovers().isEmpty()) {
                    actions.add(PriceRuleAdapter.approval(RuleConfig.ActionType.APPEND_APPROVAL, r.getMode(), r.getAdditionalApprovers()));
                }
                if (!r.getCcAfterApproval().isEmpty()) {
                    actions.add(new RuleConfig.Action(RuleConfig.ActionType.CC_AFTER_APPROVAL, null, r.getCcAfterApproval(), null, null, null));
                }
                rules.add(new RuleConfig.Rule(p.getId() + ":" + r.getId(), p.getName(), RuleConfig.Kind.SUPPLEMENT, 0, PriceRuleAdapter.all(Arrays.asList(policy, PriceRuleAdapter.leaf(metric, RuleConfig.Operator.valueOf(r.getOperator().name()), (JsonNode)JsonNodeFactory.instance.numberNode(r.getThreshold())))), actions));
            }
        }
        return new RuleConfig(c.getRuleName(), c.getBusinessType().name(), new ArrayList<RuleConfig.Field>(fields.values()), rules,
                null, null, calculation);
    }

    static String metric(String type) {
        return "SALES_PRICE".equals(type) ? "grossMarginPct" : "costIncreasePct";
    }

    static GenericFact fact(DocumentFact d) {
        LinkedHashMap<String, JsonNode> data = new LinkedHashMap<String, JsonNode>();
        data.put("policyId", (JsonNode)JsonNodeFactory.instance.textNode(d.getPolicyId()));
        String metric = PriceRuleAdapter.metric(d.getBusinessType());
        data.put(metric, (JsonNode)JsonNodeFactory.instance.numberNode(d.getPercentage()));
        GenericFact fact = new GenericFact(d.getBusinessType(), data);
        fact.exactNumber(metric, d.getPercentageNumerator(), d.getPercentageDenominator());
        return fact;
    }

    private static RuleConfig.Condition leaf(String field, RuleConfig.Operator operator, JsonNode value) {
        return new RuleConfig.Condition(field, operator, value, null, null);
    }

    private static RuleConfig.Condition all(List<RuleConfig.Condition> conditions) {
        return new RuleConfig.Condition(null, null, null, RuleConfig.Logic.ALL, conditions);
    }

    private static RuleConfig.Action approval(RuleConfig.ActionType type, PriceRuleConfig.Mode mode, List<String> targets) {
        return new RuleConfig.Action(type, RuleConfig.Mode.valueOf(mode.name()), targets, null, null, null);
    }
}
