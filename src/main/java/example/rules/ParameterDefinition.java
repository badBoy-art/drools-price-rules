/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.fasterxml.jackson.annotation.JsonCreator
 *  com.fasterxml.jackson.annotation.JsonInclude
 *  com.fasterxml.jackson.annotation.JsonInclude$Include
 *  com.fasterxml.jackson.annotation.JsonProperty
 *  com.fasterxml.jackson.databind.JsonNode
 */
package example.rules;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import example.rules.RuleConfig;

@JsonInclude(value=JsonInclude.Include.NON_NULL)
public class ParameterDefinition {
    private final String name;
    private final RuleConfig.FieldType type;
    private final boolean required;
    private final String description;
    private final JsonNode example;

    @JsonCreator
    public ParameterDefinition(@JsonProperty(value="name") String name, @JsonProperty(value="type") RuleConfig.FieldType type, @JsonProperty(value="required") boolean required, @JsonProperty(value="description") String description, @JsonProperty(value="example") JsonNode example) {
        this.name = name;
        this.type = type;
        this.required = required;
        this.description = description;
        this.example = example;
    }

    public String getName() {
        return this.name;
    }

    public RuleConfig.FieldType getType() {
        return this.type;
    }

    public boolean getRequired() {
        return this.required;
    }

    public String getDescription() {
        return this.description;
    }

    public JsonNode getExample() {
        return this.example;
    }
}

