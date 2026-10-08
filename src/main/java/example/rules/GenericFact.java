/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.fasterxml.jackson.databind.JsonNode
 */
package example.rules;

import com.fasterxml.jackson.databind.JsonNode;
import example.rules.ConfigValidator;
import example.rules.RuleJson;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

public final class GenericFact {
    private final String businessType;
    private final Map<String, JsonNode> values;
    private final Map<String, JsonNode> constants = new HashMap<String, JsonNode>();
    private final Map<String, BigDecimal[]> ratios = new HashMap<String, BigDecimal[]>();

    public GenericFact(String businessType, Map<String, JsonNode> values) {
        this.businessType = businessType;
        LinkedHashMap copy = new LinkedHashMap();
        values.forEach((key, value) -> copy.put(key, value.deepCopy()));
        this.values = Collections.unmodifiableMap(copy);
    }

    public String getBusinessType() {
        return this.businessType;
    }

    public void exactNumber(String field, BigDecimal numerator, BigDecimal denominator) {
        ConfigValidator.require(numerator != null && denominator != null && denominator.signum() > 0, "\u7cbe\u786e\u6bd4\u4f8b\u9700\u8981\u5206\u5b50\u548c\u6b63\u5206\u6bcd");
        this.ratios.put(field, new BigDecimal[]{numerator, denominator});
    }

    public boolean test(String field, String operator, String literal) {
        boolean exists;
        JsonNode left = this.values.get(field);
        boolean bl = exists = left != null && !left.isNull();
        if ("EXISTS".equals(operator)) {
            return exists;
        }
        if ("NOT_EXISTS".equals(operator)) {
            return !exists;
        }
        if (!exists) {
            return false;
        }
        JsonNode right = this.constants.computeIfAbsent(literal, RuleJson::parse);
        switch (operator) {
            case "GT": {
                return this.compare(field, left, right) > 0;
            }
            case "GE": {
                return this.compare(field, left, right) >= 0;
            }
            case "LT": {
                return this.compare(field, left, right) < 0;
            }
            case "LE": {
                return this.compare(field, left, right) <= 0;
            }
            case "EQ": {
                return this.equal(field, left, right);
            }
            case "NE": {
                return !this.equal(field, left, right);
            }
            case "IN": {
                return this.in(field, left, right);
            }
            case "NOT_IN": {
                return !this.in(field, left, right);
            }
            case "CONTAINS": {
                return this.contains(left, right.asText());
            }
            case "CONTAINS_ANY": {
                for (JsonNode item : right) {
                    if (!this.contains(left, item.asText())) continue;
                    return true;
                }
                return false;
            }
        }
        throw new IllegalArgumentException("\u672a\u77e5\u6bd4\u8f83\u7b26\uff1a" + operator);
    }

    private int compare(String field, JsonNode left, JsonNode right) {
        BigDecimal[] ratio = this.ratios.get(field);
        return ratio == null ? left.decimalValue().compareTo(right.decimalValue()) : ratio[0].compareTo(right.decimalValue().multiply(ratio[1]));
    }

    private boolean equal(String field, JsonNode left, JsonNode right) {
        return left.isNumber() && right.isNumber() ? this.compare(field, left, right) == 0 : left.equals((Object)right);
    }

    private boolean in(String field, JsonNode left, JsonNode right) {
        for (JsonNode item : right) {
            if (!this.equal(field, left, item)) continue;
            return true;
        }
        return false;
    }

    private boolean contains(JsonNode left, String right) {
        if (left.isTextual()) {
            return left.asText().contains(right);
        }
        for (JsonNode item : left) {
            if (!right.equals(item.asText())) continue;
            return true;
        }
        return false;
    }
}

