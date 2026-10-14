package example.rules.model;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;

/** 规则表达式读取业务JSON；缺失路径视为不命中，非数值参与数值判断时拒绝执行。 */
public class JsonRuleFact {
    private final JsonNode params;

    public JsonRuleFact(JsonNode params) { this.params = params; }

    private JsonNode value(String field) {
        JsonNode current = params;
        for (String segment : field.split("\\.")) {
            if (current == null || !current.isObject()) return null;
            current = current.get(segment);
        }
        return current == null || current.isNull() ? null : current;
    }

    public boolean categoryMatches(String field, String expected) {
        JsonNode actual = value(field);
        if (actual == null) return false;
        if (actual.isTextual()) return expected.equals(actual.textValue());
        if (actual.isNumber()) return actual.decimalValue().compareTo(new BigDecimal(expected)) == 0;
        return false;
    }

    private BigDecimal number(String field) {
        JsonNode actual = value(field);
        if (actual == null) return null;
        if (!actual.isNumber()) throw new IllegalArgumentException("业务参数 " + field + " 必须是JSON数值");
        return actual.decimalValue();
    }

    public boolean positive(String field) {
        BigDecimal value = number(field);
        return value != null && value.signum() > 0;
    }

    public boolean inBand(String field, String lower, boolean lowerInclusive, String upper, boolean upperInclusive) {
        BigDecimal value = number(field);
        if (value == null) return false;
        if (lower != null) {
            int cmp = value.compareTo(new BigDecimal(lower));
            if (cmp < 0 || cmp == 0 && !lowerInclusive) return false;
        }
        if (upper != null) {
            int cmp = value.compareTo(new BigDecimal(upper));
            if (cmp > 0 || cmp == 0 && !upperInclusive) return false;
        }
        return true;
    }

    public boolean compare(String field, String operator, String threshold) {
        BigDecimal value = number(field);
        if (value == null) return false;
        int cmp = value.compareTo(new BigDecimal(threshold));
        switch (operator) {
            case "LT": return cmp < 0;
            case "LE": return cmp <= 0;
            case "EQ": return cmp == 0;
            case "GE": return cmp >= 0;
            case "GT": return cmp > 0;
            default: throw new IllegalArgumentException("未知比较符：" + operator);
        }
    }
}
