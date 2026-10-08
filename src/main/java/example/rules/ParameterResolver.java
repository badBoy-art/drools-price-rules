/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.fasterxml.jackson.core.JsonProcessingException
 *  com.fasterxml.jackson.core.TreeNode
 *  com.fasterxml.jackson.databind.JsonNode
 *  org.springframework.stereotype.Component
 */
package example.rules;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.TreeNode;
import com.fasterxml.jackson.databind.JsonNode;
import example.rules.ConfigValidator;
import example.rules.ParameterDefinition;
import example.rules.PriceRuleAdapter;
import example.rules.PriceRuleConfig;
import example.rules.RuleConfig;
import example.rules.RuleJson;
import example.rules.RuleStore;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class ParameterResolver {
    private final RuleStore store;
    private final PriceRuleAdapter prices;

    public ParameterResolver(RuleStore store, PriceRuleAdapter prices) {
        this.store = store;
        this.prices = prices;
    }

    public RuleConfig resolve(RuleConfig config) {
        ConfigValidator.require(config != null, "\u914d\u7f6e\u4e0d\u80fd\u4e3a\u7a7a");
        if (config.getParameterSources() == null || config.getParameterSources().isEmpty()) {
            return config;
        }
        ConfigValidator.require(config.getParameterSources().size() <= 100, "parameterSources\u6700\u591a100\u9879");
        LinkedHashMap fields = new LinkedHashMap();
        LinkedHashMap outputs = new LinkedHashMap();
        if (config.getFields() != null) {
            for (RuleConfig.Field field : config.getFields()) {
                ConfigValidator.require(field != null && !fields.containsKey(field.getName()), "\u672c\u5730\u5165\u53c2\u5b9a\u4e49\u4e0d\u80fd\u4e3a\u7a7a\u6216\u91cd\u590d");
                this.merge(fields, field, "\u5165\u53c2");
            }
        }
        if (config.getOutputs() != null) {
            for (ParameterDefinition parameterDefinition : config.getOutputs()) {
                ConfigValidator.require(parameterDefinition != null && !outputs.containsKey(parameterDefinition.getName()), "\u672c\u5730\u51fa\u53c2\u5b9a\u4e49\u4e0d\u80fd\u4e3a\u7a7a\u6216\u91cd\u590d");
                this.merge(outputs, parameterDefinition, "\u51fa\u53c2");
            }
        }
        boolean outputContract = config.getOutputs() != null;
        ArrayList<RuleConfig.ParameterSource> arrayList = new ArrayList<RuleConfig.ParameterSource>();
        for (RuleConfig.ParameterSource source : config.getParameterSources()) {
            ParameterDefinition f;
            ConfigValidator.require(source != null, "\u53c2\u6570\u6765\u6e90\u4e0d\u80fd\u4e3a\u7a7a");
            ConfigValidator.identifier(source.getRuleName());
            ConfigValidator.require(source.getVersion() == null || source.getVersion() > 0L, "\u6765\u6e90version\u5fc5\u987b\u5927\u4e8e0");
            List<String> inputs = this.names(source.getInputs());
            List<String> out = this.names(source.getOutputs());
            ConfigValidator.require(!inputs.isEmpty() || !out.isEmpty(), "\u53c2\u6570\u6765\u6e90\u81f3\u5c11\u9009\u62e9\u4e00\u4e2a\u5165\u53c2\u6216\u51fa\u53c2");
            RuleStore.Version stored = this.store.load(source.getRuleName(), source.getVersion());
            RuleConfig original = this.decode(stored.getConfigJson());
            for (String name : inputs) {
                f = this.find(original.getFields(), name, "\u5165\u53c2");
                this.merge(fields, f, "\u5165\u53c2");
            }
            for (String name : out) {
                f = this.find(original.getOutputs(), name, "\u51fa\u53c2");
                this.merge(outputs, f, "\u51fa\u53c2");
            }
            outputContract |= !out.isEmpty();
            arrayList.add(new RuleConfig.ParameterSource(stored.getRuleName(), stored.getVersion(), inputs, out));
        }
        return new RuleConfig(config.getRuleName(), config.getBusinessType(), new ArrayList<RuleConfig.Field>(fields.values()), config.getRules(), outputContract ? new ArrayList(outputs.values()) : null, arrayList);
    }

    private List<String> names(List<String> names) {
        if (names == null) {
            return Collections.emptyList();
        }
        ConfigValidator.require(names.size() <= 200, "\u6bcf\u4e2a\u6765\u6e90\u6700\u591a\u9009\u62e9200\u4e2a\u53c2\u6570");
        ConfigValidator.require(new HashSet<String>(names).size() == names.size(), "\u540c\u4e00\u6765\u6e90\u7684\u53c2\u6570\u540d\u4e0d\u80fd\u91cd\u590d");
        return names;
    }

    private <T extends ParameterDefinition> T find(List<T> definitions, String name, String direction) {
        if (definitions != null) {
            for (ParameterDefinition f : definitions) {
                if (!f.getName().equals(name)) continue;
                return (T)f;
            }
        }
        throw new IllegalArgumentException("\u6765\u6e90\u89c4\u5219\u4e0d\u5b58\u5728" + direction + "\uff1a" + name);
    }

    private <T extends ParameterDefinition> void merge(Map<String, T> definitions, T f, String direction) {
        ConfigValidator.require(f != null, direction + "\u5b9a\u4e49\u4e0d\u80fd\u4e3a\u7a7a");
        ParameterDefinition previous = (ParameterDefinition)definitions.putIfAbsent(f.getName(), f);
        ConfigValidator.require(previous == null || RuleJson.encode(previous).equals(RuleJson.encode(f)), "\u5171\u7528" + direction + "\u5b9a\u4e49\u51b2\u7a81\uff1a" + f.getName());
    }

    private RuleConfig decode(String value) {
        JsonNode tree = RuleJson.parse(value);
        try {
            return tree.has("policies") ? this.prices.toGeneric((PriceRuleConfig)RuleJson.MAPPER.treeToValue((TreeNode)tree, PriceRuleConfig.class)) : (RuleConfig)RuleJson.MAPPER.treeToValue((TreeNode)tree, RuleConfig.class);
        }
        catch (JsonProcessingException e) {
            throw new IllegalStateException("\u6765\u6e90\u53c2\u6570\u914d\u7f6e\u635f\u574f", e);
        }
    }
}

