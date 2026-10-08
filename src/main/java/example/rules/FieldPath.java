/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.fasterxml.jackson.databind.JsonNode
 */
package example.rules;

import com.fasterxml.jackson.databind.JsonNode;
import example.rules.ConfigValidator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class FieldPath {
    private static final Pattern SEGMENT = Pattern.compile("([A-Za-z_][A-Za-z0-9_]{0,63})((?:\\[(?:0|[1-9][0-9]{0,9})\\])*)");
    private static final Pattern INDEX = Pattern.compile("\\[([0-9]+)\\]");
    private final String path;
    private final List<Token> tokens;

    private FieldPath(String path, List<Token> tokens) {
        this.path = path;
        this.tokens = Collections.unmodifiableList(tokens);
    }

    public static FieldPath parse(String path) {
        ConfigValidator.require(path != null && !path.isEmpty() && path.length() <= 512, "\u5b57\u6bb5\u8def\u5f84\u5fc5\u586b\uff0c\u6700\u591a512\u5b57\u7b26");
        ArrayList<Token> tokens = new ArrayList<Token>();
        for (String segment : path.split("\\.", -1)) {
            Matcher match = SEGMENT.matcher(segment);
            ConfigValidator.require(match.matches(), "\u5b57\u6bb5\u8def\u5f84\u683c\u5f0f\u9519\u8bef\uff1a" + path + "\uff0c\u4f7f\u7528\u5c5e\u6027\u540d\u3001\u70b9\u53f7\u53ca\u975e\u8d1f\u6570\u7ec4\u4e0b\u6807");
            tokens.add(new Token(match.group(1), null));
            Matcher indexes = INDEX.matcher(match.group(2));
            while (indexes.find()) {
                long value = Long.parseLong(indexes.group(1));
                ConfigValidator.require(value <= Integer.MAX_VALUE, "\u5b57\u6bb5\u8def\u5f84\u6570\u7ec4\u4e0b\u6807\u8fc7\u5927\uff1a" + path);
                tokens.add(new Token(null, (int)value));
            }
            ConfigValidator.require(tokens.size() <= 20, "\u5b57\u6bb5\u8def\u5f84\u6700\u591a20\u6b65\uff1a" + path);
        }
        return new FieldPath(path, tokens);
    }

    public JsonNode read(Map<String, JsonNode> data) {
        Token root = this.tokens.get(0);
        JsonNode value = data.get(root.name);
        String position = root.name;
        for (int i = 1; i < this.tokens.size(); ++i) {
            if (value == null || value.isNull() || value.isMissingNode()) {
                return null;
            }
            Token token = this.tokens.get(i);
            if (token.name != null) {
                ConfigValidator.require(value.isObject(), "\u5b57\u6bb5\u8def\u5f84" + this.path + "\u4e2d\u7684" + position + "\u5fc5\u987b\u4e3a\u5bf9\u8c61");
                value = value.get(token.name);
                position = position + "." + token.name;
                continue;
            }
            ConfigValidator.require(value.isArray(), "\u5b57\u6bb5\u8def\u5f84" + this.path + "\u4e2d\u7684" + position + "\u5fc5\u987b\u4e3a\u6570\u7ec4");
            value = value.get(token.index.intValue());
            position = position + "[" + token.index + "]";
        }
        return value;
    }

    private static final class Token {
        final String name;
        final Integer index;

        Token(String name, Integer index) {
            this.name = name;
            this.index = index;
        }
    }
}

