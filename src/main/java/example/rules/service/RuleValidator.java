package example.rules.service;

import example.rules.model.RuleConfig;
import example.rules.model.RuleConfig.Condition;
import example.rules.model.RuleConfig.Assignment;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/** 保存前统一校验三类条件的字段形状及页面规则组。 */
@Component
public class RuleValidator {
    private static final String PATH = "[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)*";
    private static final String CODE = "[A-Za-z0-9_-]{1,32}";

    public void businessCode(String value) {
        ConfigValidator.require(value != null && value.matches("[A-Za-z0-9_-]{1,36}"), "businessCode格式无效");
    }

    public void validate(RuleConfig config) {
        ConfigValidator.require(config != null, "规则配置不能为空");
        businessCode(config.businessCode);
        ConfigValidator.require(config.businessName != null && !config.businessName.trim().isEmpty()
                && config.businessName.length() <= 50, "businessName必填且最多50字符");
        ConfigValidator.require(config.conditions != null && !config.conditions.isEmpty()
                && config.conditions.size() <= 2000, "conditions必须包含1到2000条条件");
        Map<String, Group> groups = new HashMap<>();
        Set<String> conditionKeys = new HashSet<>();
        for (Condition c : config.conditions) {
            validateCondition(c);
            ConfigValidator.require(conditionKeys.add(c.conditionCode),
                    "同一规则配置中的conditionCode重复：" + c.conditionCode);
            Group group = groups.get(c.ruleName);
            if (group == null) {
                group = new Group(c);
                groups.put(c.ruleName, group);
            } else {
                ConfigValidator.require(group.name.equals(c.ruleName) && group.defaultRule == c.isDefault
                        && group.order == c.ruleSort && group.cost == isCost(c)
                        && group.categoryId1.equals(categoryOrZero(c.categoryId1))
                        && group.categoryId2.equals(categoryOrZero(c.categoryId2))
                        && group.categoryId3.equals(categoryOrZero(c.categoryId3)),
                        "同一ruleName的名称、品类范围、默认标志、排序或类型必须一致：" + c.ruleName);
            }
            ConfigValidator.require(group.orders.add(c.predicateType + ":" + c.conditionSort),
                    "同一规则组内conditionSort重复：" + c.ruleName);
            if ("INTERVAL".equals(c.predicateType)) group.intervals.add(c);
            if (group.cost) ConfigValidator.require(group.metricField.equals(c.valueField),
                    "成本上调幅度审核规则组内所有条件必须使用同一个field");
        }
        long defaults = groups.values().stream().filter(g -> g.defaultRule).count();
        long costs = groups.values().stream().filter(g -> g.cost).count();
        ConfigValidator.require(defaults == 1, "必须且只能有一个默认销售规则组");
        ConfigValidator.require(costs == 1, "成本上调幅度审核规则必须且只能有一个规则组，组内可有N条条件");
        for (Group group : groups.values()) validateIntervals(group);
    }

    private void validateCondition(Condition c) {
        ConfigValidator.require(c != null, "条件不能为空");
        ConfigValidator.require(matchesCode(c.conditionCode), "conditionCode格式无效");
        ConfigValidator.require(c.ruleName != null && !c.ruleName.trim().isEmpty()
                && c.ruleName.length() <= 50, "ruleName必填且最多50字符");
        ConfigValidator.require(c.ruleSort >= 0 && c.conditionSort >= 0,
                "ruleSort和conditionSort不能小于0");
        path(c.valueField);
        ConfigValidator.require(c.resultAssignments != null && !c.resultAssignments.isEmpty()
                && c.resultAssignments.size() <= 100, "resultAssignments必须包含1到100项");
        Set<String> assignmentKeys = new HashSet<>();
        boolean hasAlways = false;
        for (Assignment assignment : c.resultAssignments) {
            ConfigValidator.require(assignment != null, "结果赋值项不能为空");
            path(assignment.field);
            ConfigValidator.require(assignment.value != null, "结果赋值value必填；写入JSON null请显式传null");
            String when = assignment.when == null ? "ALWAYS" : assignment.when;
            ConfigValidator.require("ALWAYS".equals(when) || "COST_INCREASE".equals(when),
                    "结果赋值when仅支持ALWAYS或COST_INCREASE");
            ConfigValidator.require(assignmentKeys.add(when + "\u0000" + assignment.field),
                    "同一条件下相同when和field不能重复");
            if ("ALWAYS".equals(when)) hasAlways = true;
            if ("COST_INCREASE".equals(when)) ConfigValidator.require(
                    "CATEGORY_INTERVAL".equals(c.conditionType),
                    "COST_INCREASE赋值仅可用于销售毛利区间");
        }
        ConfigValidator.require(hasAlways, "每条条件至少需要一项ALWAYS结果赋值");
        boolean interval = "INTERVAL".equals(c.predicateType);
        boolean threshold = "THRESHOLD".equals(c.predicateType);
        ConfigValidator.require(("CATEGORY_INTERVAL".equals(c.conditionType) && interval)
                || ("CATEGORY_RISK".equals(c.conditionType) && threshold)
                || (isCost(c) && (interval || threshold)), "conditionType与predicateType不匹配");
        categoryId(c.categoryId1); categoryId(c.categoryId2); categoryId(c.categoryId3);
        if (c.isDefault || isCost(c)) {
            ConfigValidator.require(unsetCategory(c.categoryId1) && unsetCategory(c.categoryId2) && unsetCategory(c.categoryId3),
                    "默认规则和成本规则不能配置品类范围");
        } else ConfigValidator.require(!unsetCategory(c.categoryId1), "自定义销售规则至少需要categoryId1");
        ConfigValidator.require(unsetCategory(c.categoryId2) || !unsetCategory(c.categoryId1),
                "配置categoryId2时必须配置categoryId1");
        ConfigValidator.require(unsetCategory(c.categoryId3) || !unsetCategory(c.categoryId2),
                "配置categoryId3时必须配置categoryId2");
        ConfigValidator.require(!isCost(c) || !c.isDefault, "成本规则不能标为默认销售规则");
        if (interval) {
            ConfigValidator.require(c.compType == null && c.thresholdVal == null,
                    "区间条件不能填写comparison或thresholdValue");
            ConfigValidator.require(c.lowerBound == null || c.upperBound == null
                    || c.lowerBound.compareTo(c.upperBound) < 0, "区间下界必须小于上界");
            decimal(c.lowerBound); decimal(c.upperBound);
        } else {
            ConfigValidator.require(c.compType != null && c.compType.matches("LT|LE|EQ|GE|GT")
                    && c.thresholdVal != null, "阈值条件需要合法comparison和thresholdValue");
            ConfigValidator.require(c.lowerBound == null && c.upperBound == null
                    && !c.lowerInclusive && !c.upperInclusive,
                    "阈值条件不能填写区间字段");
            decimal(c.thresholdVal);
        }
    }

    private void validateIntervals(Group group) {
        if (group.defaultRule) ConfigValidator.require(!group.intervals.isEmpty(),
                "默认销售规则组至少需要一条区间条件");
        if (group.intervals.isEmpty()) return;
        group.intervals.sort(Comparator.comparingInt(c -> c.conditionSort));
        Condition first = group.intervals.get(0);
        Condition last = group.intervals.get(group.intervals.size() - 1);
        if (!group.cost) ConfigValidator.require(first.lowerBound == null && last.upperBound == null,
                "销售区间必须覆盖全部毛利率");
        for (int i = 1; i < group.intervals.size(); i++) {
            Condition previous = group.intervals.get(i - 1);
            Condition current = group.intervals.get(i);
            ConfigValidator.require(previous.upperBound != null && current.lowerBound != null,
                    "无穷边界只能在区间首尾");
            int cmp = previous.upperBound.compareTo(current.lowerBound);
            ConfigValidator.require(cmp < 0 || cmp == 0 && !(previous.upperInclusive && current.lowerInclusive),
                    "区间不能重叠且必须按conditionSort排序");
            if (!group.cost) ConfigValidator.require(cmp == 0 && previous.upperInclusive != current.lowerInclusive,
                    "销售区间必须连续");
        }
    }

    private boolean unsetCategory(Long id) { return id == null || id == 0L; }
    private boolean isCost(Condition c) { return "COST_INCREASE".equals(c.conditionType); }
    private Long categoryOrZero(Long value) { return value == null ? 0L : value; }
    private void categoryId(Long value) {
        ConfigValidator.require(value == null || value >= 0, "categoryId不能小于0");
    }
    private boolean matchesCode(String value) { return value != null && value.matches(CODE); }
    private void path(String value) {
        ConfigValidator.require(value != null && value.length() <= 30 && value.matches(PATH),
                "field必须是a.b.c格式的JSON对象路径");
    }
    private void decimal(BigDecimal value) {
        if (value == null) return;
        BigDecimal normalized = value.stripTrailingZeros();
        ConfigValidator.require(normalized.scale() <= 8 && normalized.precision() - normalized.scale() <= 12,
                "数值最多12位整数和8位小数");
    }

    private static class Group {
        final String name;
        final boolean defaultRule;
        final boolean cost;
        final int order;
        final Long categoryId1;
        final Long categoryId2;
        final Long categoryId3;
        final String metricField;
        final List<Condition> intervals = new ArrayList<>();
        final Set<String> orders = new HashSet<>();
        Group(Condition c) {
            name = c.ruleName; defaultRule = c.isDefault; cost = "COST_INCREASE".equals(c.conditionType);
            order = c.ruleSort;
            categoryId1 = c.categoryId1 == null ? 0L : c.categoryId1;
            categoryId2 = c.categoryId2 == null ? 0L : c.categoryId2;
            categoryId3 = c.categoryId3 == null ? 0L : c.categoryId3;
            metricField = c.valueField;
        }
    }
}
