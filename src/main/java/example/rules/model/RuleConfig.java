package example.rules.model;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** 页面配置：一份业务主表及其所有条件行。 */
public class RuleConfig {
    public String businessCode;
    public String businessName;
    public List<Condition> conditions = new ArrayList<>();

    public static class Condition {
        public Long id;
        public String ruleName;
        public boolean isDefault;
        public Long categoryId1;
        public Long categoryId2;
        public Long categoryId3;
        public int ruleSort;
        public String conditionCode;
        public String conditionType;
        public String valueField;
        public String predicateType;
        public BigDecimal lowerBound;
        public boolean lowerInclusive;
        public BigDecimal upperBound;
        public boolean upperInclusive;
        public String compType;
        public BigDecimal thresholdVal;
        public List<Assignment> resultAssignments = new ArrayList<>();
        public int conditionSort;
        public String scriptContent;
    }

    public static class Assignment {
        public String field;
        public JsonNode value;
        /** ALWAYS（默认）或 COST_INCREASE。 */
        public String when;
    }
}
