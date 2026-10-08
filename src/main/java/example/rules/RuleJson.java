/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.fasterxml.jackson.core.JsonProcessingException
 *  com.fasterxml.jackson.databind.DeserializationFeature
 *  com.fasterxml.jackson.databind.JsonNode
 *  com.fasterxml.jackson.databind.ObjectMapper
 */
package example.rules;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

final class RuleJson {
    static final ObjectMapper MAPPER = new ObjectMapper().enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);

    private RuleJson() {
    }

    static String encode(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        }
        catch (JsonProcessingException e) {
            throw new IllegalArgumentException("\u89c4\u5219\u5e38\u91cf\u65e0\u6cd5\u7f16\u7801", e);
        }
    }

    static JsonNode parse(String value) {
        try {
            return MAPPER.readTree(value);
        }
        catch (JsonProcessingException e) {
            throw new IllegalArgumentException("\u89c4\u5219\u5e38\u91cf\u635f\u574f", e);
        }
    }
}

