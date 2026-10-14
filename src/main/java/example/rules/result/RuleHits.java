package example.rules.result;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.node.ObjectNode;
import example.rules.model.RuleConfig.Assignment;
import example.rules.service.RuleJson;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** 记录表达式命中项；按页面配置顺序写入业务参数副本。 */
public class RuleHits {
    private final List<OrderedMatch> hits = new ArrayList<>();

    public void add(int ruleOrder, int kindOrder, int conditionOrder, String ruleCode,
            String conditionCode, String conditionType, String assignmentsJson, boolean costIncreased) {
        try {
            List<Assignment> configured = RuleJson.MAPPER.readValue(assignmentsJson,
                    new TypeReference<List<Assignment>>() {});
            List<Assignment> applied = new ArrayList<>();
            for (Assignment assignment : configured) if (assignment.when == null
                    || "ALWAYS".equals(assignment.when)) applied.add(assignment);
            if (costIncreased) for (Assignment assignment : configured)
                if ("COST_INCREASE".equals(assignment.when)) applied.add(assignment);
            hits.add(new OrderedMatch(ruleOrder, kindOrder, conditionOrder,
                    new ExecuteResult.Match(ruleCode, conditionCode, conditionType,
                            Collections.unmodifiableList(applied))));
        } catch (IOException invalid) {
            throw new IllegalStateException("规则脚本中的结果赋值JSON无效", invalid);
        }
    }

    public ExecuteResult finish(String businessCode, JsonNode inputParams) {
        hits.sort(Comparator.comparingInt((OrderedMatch hit) -> hit.ruleOrder)
                .thenComparingInt(hit -> hit.kindOrder)
                .thenComparingInt(hit -> hit.conditionOrder)
                .thenComparing(hit -> hit.match.conditionCode));
        List<ExecuteResult.Match> matches = new ArrayList<>();
        ObjectNode results = ((ObjectNode) inputParams).deepCopy();
        for (OrderedMatch hit : hits) {
            matches.add(hit.match);
            for (Assignment assignment : hit.match.resultAssignments)
                set(results, assignment.field, assignment.value);
        }
        return new ExecuteResult(businessCode, results, Collections.unmodifiableList(matches));
    }

    private void set(ObjectNode root, String path, JsonNode value) {
        String[] parts = path.split("\\.");
        ObjectNode parent = root;
        for (int i = 0; i < parts.length - 1; i++) {
            JsonNode next = parent.get(parts[i]);
            if (next == null) {
                ObjectNode created = RuleJson.MAPPER.createObjectNode();
                parent.set(parts[i], created);
                parent = created;
            } else if (next.isObject()) {
                parent = (ObjectNode) next;
            } else {
                throw new IllegalArgumentException("结果赋值field中间节点不是JSON对象：" + path);
            }
        }
        parent.set(parts[parts.length - 1], value.deepCopy());
    }

    private static class OrderedMatch {
        final int ruleOrder;
        final int kindOrder;
        final int conditionOrder;
        final ExecuteResult.Match match;
        OrderedMatch(int ruleOrder, int kindOrder, int conditionOrder, ExecuteResult.Match match) {
            this.ruleOrder = ruleOrder; this.kindOrder = kindOrder;
            this.conditionOrder = conditionOrder; this.match = match;
        }
    }
}
