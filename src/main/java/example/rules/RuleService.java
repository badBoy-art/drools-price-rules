/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.fasterxml.jackson.annotation.JsonCreator
 *  com.fasterxml.jackson.annotation.JsonProperty
 *  com.fasterxml.jackson.core.JsonProcessingException
 *  com.fasterxml.jackson.core.TreeNode
 *  com.fasterxml.jackson.databind.JsonNode
 *  com.fasterxml.jackson.databind.ObjectMapper
 *  org.slf4j.Logger
 *  org.slf4j.LoggerFactory
 *  org.springframework.stereotype.Service
 */
package example.rules;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.TreeNode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import example.rules.ApiModels;
import example.rules.ApprovalPlan;
import example.rules.ConfigValidator;
import example.rules.DocumentFact;
import example.rules.DrlGenerator;
import example.rules.DroolsEngine;
import example.rules.GenericFact;
import example.rules.GenericRuleResult;
import example.rules.ParameterResolver;
import example.rules.PricePolicyResolver;
import example.rules.PriceRuleAdapter;
import example.rules.PriceRuleConfig;
import example.rules.RuleConfig;
import example.rules.RuleJson;
import example.rules.RuleStore;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class RuleService {
    private static final Logger LOG = LoggerFactory.getLogger(RuleService.class);
    private final ObjectMapper json;
    private final DrlGenerator generator;
    private final DroolsEngine engine;
    private final RuleStore store;
    private final PricePolicyResolver resolver;
    private final PriceRuleAdapter adapter;
    private final ConfigValidator validator;
    private final ParameterResolver parameters;

    public RuleService(ObjectMapper json, DrlGenerator generator, DroolsEngine engine, RuleStore store, PricePolicyResolver resolver, PriceRuleAdapter adapter, ConfigValidator validator, ParameterResolver parameters) {
        this.json = json;
        this.generator = generator;
        this.engine = engine;
        this.store = store;
        this.resolver = resolver;
        this.adapter = adapter;
        this.validator = validator;
        this.parameters = parameters;
    }

    public RuleStore.Version publish(String name, RuleConfig config) {
        return this.publishValidated(name, config, config);
    }

    public RuleStore.Version publish(String name, PriceRuleConfig config) {
        return this.publishValidated(name, this.adapter.toGeneric(config), config);
    }

    public RuleStore.Version publish(String name, JsonNode config) {
        ConfigValidator.require(config != null && config.isObject(), "\u914d\u7f6e\u5fc5\u987b\u662fJSON\u5bf9\u8c61");
        boolean price = config.has("policies");
        ConfigValidator.require(!price || !config.has("fields") && !config.has("rules"), "\u4e0d\u80fd\u6df7\u7528policies\u548c\u901a\u7528fields/rules");
        return price ? this.publish(name, this.convert(config, PriceRuleConfig.class)) : this.publish(name, this.convert(config, RuleConfig.class));
    }

    private RuleStore.Version publishValidated(String name, RuleConfig config, Object originalConfig) {
        ConfigValidator.identifier(name);
        ConfigValidator.require(config != null && name.equals(config.getRuleName()), "URL\u4e0e\u914d\u7f6eruleName\u5fc5\u987b\u4e00\u81f4");
        config = this.parameters.resolve(config);
        String drl = this.generator.generate(config);
        this.engine.compile(drl);
        return this.store.publish(name, this.encode(originalConfig instanceof RuleConfig ? config : originalConfig), drl);
    }

    public RuleStore.Version read(String name, Long version) {
        ConfigValidator.identifier(name);
        return this.store.load(name, version);
    }

    public ExecutionResult execute(ExecutionRequest request) {
        ConfigValidator.require(request != null, "\u8bf7\u6c42\u4e0d\u80fd\u4e3a\u7a7a");
        return this.executePrepared(request, this.prepare(request.getRuleName(), request.getVersion()), null, null, request);
    }

    PreparedRule prepare(String ruleName, Long version) {
        ConfigValidator.identifier(ruleName);
        ConfigValidator.require(version == null || version > 0L, "version\u5fc5\u987b\u5927\u4e8e0");
        RuleStore.Version stored = this.store.load(ruleName, version);
        JsonNode tree = RuleJson.parse(stored.getConfigJson());
        PriceRuleConfig price = tree.has("policies") ? this.convert(tree, PriceRuleConfig.class) : null;
        RuleConfig config = price == null ? this.convert(tree, RuleConfig.class) : this.adapter.toGeneric(price);
        this.validator.validate(config);
        return new PreparedRule(stored, config, price);
    }

    ExecutionResult executePrepared(ExecutionRequest request, PreparedRule prepared, BigDecimal numerator, BigDecimal denominator, Object auditInput) {
        GenericRuleResult.Result evaluation;
        ConfigValidator.identifier(request.getRuleName());
        Document d = request.getDocument();
        ConfigValidator.require(d != null && d.getId() != null && !d.getId().trim().isEmpty() && d.getId().length() <= 128, "document.id\u5fc5\u586b\uff0c\u957f\u5ea6\u6700\u591a128");
        if (d.getCategoryPath() != null) {
            ConfigValidator.require(d.getCategoryPath().size() <= 20, "categoryPath\u6700\u591a20\u7ea7");
            d.getCategoryPath().forEach(ConfigValidator::identifier);
        }
        RuleStore.Version version = prepared.getVersion();
        RuleConfig config = prepared.getConfig();
        LinkedHashMap<String, JsonNode> data = new LinkedHashMap<String, JsonNode>();
        if (d.getData() != null) {
            data.putAll(d.getData());
        }
        this.putCanonical(data, "documentId", this.json.valueToTree((Object)d.getId()));
        if (d.getGrossMarginPct() != null) {
            this.putCanonical(data, "grossMarginPct", this.json.valueToTree((Object)d.getGrossMarginPct()));
        }
        if (d.getCostIncreasePct() != null) {
            this.putCanonical(data, "costIncreasePct", this.json.valueToTree((Object)d.getCostIncreasePct()));
        }
        if (d.getCategoryPath() != null) {
            this.putCanonical(data, "categoryPath", this.json.valueToTree(d.getCategoryPath()));
        }
        PriceRuleConfig.Policy policy = null;
        if (prepared.priceConfig != null) {
            ConfigValidator.require(d.getCategoryPath() != null, "categoryPath\u5fc5\u586b\uff0c\u6309\u4e00\u7ea7\u5230\u53f6\u5b50\u6392\u5e8f");
            policy = this.resolver.resolve(prepared.priceConfig, d.getCategoryPath());
            this.putCanonical(data, "policyId", this.json.valueToTree((Object)policy.getId()));
        }
        boolean hasLegacyMetric = d.getGrossMarginPct() != null || d.getCostIncreasePct() != null;
        boolean oldDrl = prepared.priceConfig != null && version.getDrl().contains("global example.rules.ApprovalPlan plan;");
        CalculationValue calculation = !oldDrl && numerator == null && !hasLegacyMetric
                ? this.calculate(config.getCalculation(), data) : null;
        if (calculation != null) {
            numerator = calculation.numerator;
            denominator = calculation.denominator;
        }
        GenericFact fact = new GenericFact(config.getBusinessType(),
                oldDrl || hasLegacyMetric ? data : this.validator.validateData(config, data));
        if (calculation != null) {
            fact.exactNumber(config.getCalculation().getResultField(), numerator, denominator);
        } else if (numerator != null) {
            fact.exactNumber(PriceRuleAdapter.metric(config.getBusinessType()), numerator, denominator);
        }
        if (oldDrl) {
            BigDecimal percentage = ((JsonNode)data.get(PriceRuleAdapter.metric(config.getBusinessType()))).decimalValue();
            DocumentFact old = numerator == null ? new DocumentFact(config.getBusinessType(), policy.getId(), percentage) : new DocumentFact(config.getBusinessType(), policy.getId(), percentage, numerator, denominator);
            evaluation = new GenericRuleResult.Result(this.engine.execute(version.getDrl(), old), Collections.emptyMap(), Collections.emptyList());
        } else {
            evaluation = this.engine.evaluate(version.getDrl(), fact);
        }
        this.validator.validateOutput(config, evaluation);
        Map<String, JsonNode> calculated = calculation == null ? Collections.emptyMap()
                : Collections.singletonMap(config.getCalculation().getResultField(), data.get(config.getCalculation().getResultField()));
        ExecutionResult result = new ExecutionResult(UUID.randomUUID().toString(), version.getRuleName(), version.getVersion(), policy == null ? null : policy.getId(), policy == null ? null : policy.getName(), evaluation.getPlan(), evaluation.getResults(), evaluation.getReasons(), calculated);
        if (LOG.isInfoEnabled()) {
            LinkedHashMap<String, Object> executionLog = new LinkedHashMap<String, Object>();
            executionLog.put("executionId", result.getExecutionId());
            executionLog.put("documentId", d.getId());
            executionLog.put("rule_name", version.getRuleName());
            executionLog.put("version", version.getVersion());
            executionLog.put("input", auditInput);
            executionLog.put("result", result);
            LOG.info("\u89c4\u5219\u6267\u884c\u5b8c\u6210 {}", (Object)this.encode(executionLog));
        }
        return result;
    }

    private String encode(Object value) {
        try {
            return this.json.writeValueAsString(value);
        }
        catch (JsonProcessingException e) {
            throw new IllegalStateException("JSON\u5e8f\u5217\u5316\u5931\u8d25", e);
        }
    }

    private <T> T convert(JsonNode value, Class<T> type) {
        try {
            return (T)RuleJson.MAPPER.treeToValue((TreeNode)value, type);
        }
        catch (JsonProcessingException e) {
            throw new IllegalArgumentException("\u89c4\u5219\u914d\u7f6e\u683c\u5f0f\u9519\u8bef\uff1a" + e.getOriginalMessage(), e);
        }
    }

    private void putCanonical(Map<String, JsonNode> data, String key, JsonNode value) {
        JsonNode previous = data.put(key, value);
        ConfigValidator.require(previous == null || previous.equals((Object)value), "data\u4e0e\u5355\u636e/\u4ef7\u683c\u9002\u914d\u5b57\u6bb5\u51b2\u7a81\uff1a" + key);
    }

    /** 根据受控计算类型生成可参与规则匹配的指标，不执行页面传入的公式代码。 */
    private CalculationValue calculate(RuleConfig.Calculation config, Map<String, JsonNode> data) {
        if (config == null) return null;
        String resultField = config.getResultField();
        ConfigValidator.require(!data.containsKey(resultField), "calculation.resultField由规则引擎计算，业务请求不能传入：" + resultField);
        JsonNode baseNode = FieldPath.parse(config.getBaseField()).read(data);
        JsonNode compareNode = FieldPath.parse(config.getCompareField()).read(data);
        ConfigValidator.require(baseNode != null && baseNode.isNumber(), "计算必填数值缺失或类型错误：" + config.getBaseField());
        ConfigValidator.require(compareNode != null && compareNode.isNumber(), "计算必填数值缺失或类型错误：" + config.getCompareField());
        BigDecimal base = baseNode.decimalValue();
        BigDecimal compare = compareNode.decimalValue();
        ConfigValidator.require(base.signum() > 0, "计算分母必须大于0：" + config.getBaseField());
        ConfigValidator.require(compare.signum() >= 0, "参与计算的价格不能小于0：" + config.getCompareField());
        BigDecimal delta = config.getType() == RuleConfig.Calculation.Type.GROSS_MARGIN
                ? base.subtract(compare) : compare.subtract(base);
        BigDecimal exactNumerator = delta.multiply(new BigDecimal("100"));
        BigDecimal displayed = exactNumerator.divide(base, 12, RoundingMode.HALF_UP).stripTrailingZeros();
        data.put(resultField, this.json.valueToTree(displayed));
        return new CalculationValue(exactNumerator, base);
    }

    private static final class CalculationValue {
        private final BigDecimal numerator;
        private final BigDecimal denominator;
        private CalculationValue(BigDecimal numerator, BigDecimal denominator) {
            this.numerator = numerator;
            this.denominator = denominator;
        }
    }

    static final class PreparedRule {
        private final RuleStore.Version version;
        private final RuleConfig config;
        private final PriceRuleConfig priceConfig;

        PreparedRule(RuleStore.Version version, RuleConfig config, PriceRuleConfig priceConfig) {
            this.version = version;
            this.config = config;
            this.priceConfig = priceConfig;
        }

        RuleStore.Version getVersion() {
            return this.version;
        }

        RuleConfig getConfig() {
            return this.config;
        }
    }

    public static final class ExecutionResult
    extends ApiModels.RuleOutcome {
        private final String executionId;
        private final String ruleName;
        private final long version;
        private final String policyId;
        private final String policyName;
        private final Map<String, JsonNode> calculations;

        public ExecutionResult(String executionId, String ruleName, long version, String policyId, String policyName, ApprovalPlan.Result plan) {
            this(executionId, ruleName, version, policyId, policyName, plan, Collections.emptyMap(), Collections.emptyList(), Collections.emptyMap());
        }

        public ExecutionResult(String executionId, String ruleName, long version, String policyId, String policyName,
                               ApprovalPlan.Result plan, Map<String, JsonNode> results, List<String> reasons) {
            this(executionId, ruleName, version, policyId, policyName, plan, results, reasons, Collections.emptyMap());
        }

        @JsonCreator
        public ExecutionResult(@JsonProperty(value="executionId") String executionId, @JsonProperty(value="rule_name") String ruleName, @JsonProperty(value="version") long version, @JsonProperty(value="policyId") String policyId, @JsonProperty(value="policyName") String policyName, @JsonProperty(value="plan") ApprovalPlan.Result plan, @JsonProperty(value="results") Map<String, JsonNode> results, @JsonProperty(value="reasons") List<String> reasons, @JsonProperty(value="calculations") Map<String, JsonNode> calculations) {
            super(plan, results, reasons);
            this.executionId = executionId;
            this.ruleName = ruleName;
            this.version = version;
            this.policyId = policyId;
            this.policyName = policyName;
            this.calculations = calculations == null ? Collections.emptyMap() : calculations;
        }

        @JsonProperty(value="executionId")
        public String getExecutionId() {
            return this.executionId;
        }

        @JsonProperty(value="rule_name")
        public String getRuleName() {
            return this.ruleName;
        }

        @JsonProperty(value="version")
        public long getVersion() {
            return this.version;
        }

        @JsonProperty(value="policyId")
        public String getPolicyId() {
            return this.policyId;
        }

        @JsonProperty(value="policyName")
        public String getPolicyName() {
            return this.policyName;
        }

        @JsonProperty(value="calculations")
        public Map<String, JsonNode> getCalculations() { return calculations; }
    }

    public static final class ExecutionRequest
    extends ApiModels.RuleRequest {
        private final Document document;

        @JsonCreator
        public ExecutionRequest(@JsonProperty(value="rule_name") String ruleName, @JsonProperty(value="version") Long version, @JsonProperty(value="document") Document document) {
            super(ruleName, version);
            this.document = document;
        }

        @JsonProperty(value="document")
        public Document getDocument() {
            return this.document;
        }
    }

    public static final class Document
    extends ApiModels.PricePercentages {
        private final String id;
        private final List<String> categoryPath;
        private final Map<String, JsonNode> data;

        public Document(String id, List<String> categoryPath, BigDecimal grossMarginPct, BigDecimal costIncreasePct) {
            this(id, categoryPath, grossMarginPct, costIncreasePct, null);
        }

        @JsonCreator
        public Document(@JsonProperty(value="id") String id, @JsonProperty(value="categoryPath") List<String> categoryPath, @JsonProperty(value="grossMarginPct") BigDecimal grossMarginPct, @JsonProperty(value="costIncreasePct") BigDecimal costIncreasePct, @JsonProperty(value="data") Map<String, JsonNode> data) {
            super(grossMarginPct, costIncreasePct);
            this.id = id;
            this.categoryPath = categoryPath;
            this.data = data;
        }

        @JsonProperty(value="id")
        public String getId() {
            return this.id;
        }

        @JsonProperty(value="categoryPath")
        public List<String> getCategoryPath() {
            return this.categoryPath;
        }

        public Map<String, JsonNode> getData() {
            return this.data;
        }
    }
}
