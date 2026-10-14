package example.rules.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

public final class RuleJson {
    public static final ObjectMapper MAPPER =
            new ObjectMapper().enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);

    /** 使用传入参数创建并初始化该对象。 */
    private RuleJson() {}

    public static String encode(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException(
                    "\u89c4\u5219\u5e38\u91cf\u65e0\u6cd5\u7f16\u7801", e);
        }
    }

}
