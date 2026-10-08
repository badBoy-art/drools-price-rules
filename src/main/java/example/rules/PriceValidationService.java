/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.fasterxml.jackson.core.type.TypeReference
 *  com.fasterxml.jackson.databind.JsonNode
 *  org.springframework.stereotype.Service
 */
package example.rules;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import example.rules.ConfigValidator;
import example.rules.PriceValidationApi;
import example.rules.RuleJson;
import example.rules.RuleService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class PriceValidationService {
    public static final int MAX_BATCH_SIZE = 1000;
    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private final RuleService rules;

    public PriceValidationService(RuleService rules) {
        this.rules = rules;
    }

    public PriceValidationApi.ProductResult validate(PriceValidationApi.SingleRequest request) {
        ConfigValidator.require(request != null, "\u8bf7\u6c42\u4e0d\u80fd\u4e3a\u7a7a");
        this.validateDocumentId(request.getDocumentId());
        RuleService.PreparedRule prepared = this.rules.prepare(request.getRuleName(), request.getVersion());
        this.requirePriceType(prepared.getConfig().getBusinessType());
        Percentage value = this.percentage(request.getProduct(), prepared.getConfig().getBusinessType());
        return this.evaluate(request.getDocumentId(), request.getProduct(), prepared, value);
    }

    public PriceValidationApi.BatchResult validateBatch(PriceValidationApi.BatchRequest request) {
        ConfigValidator.require(request != null, "\u8bf7\u6c42\u4e0d\u80fd\u4e3a\u7a7a");
        this.validateDocumentId(request.getDocumentId());
        ConfigValidator.require(request.getProducts() != null && !request.getProducts().isEmpty() && request.getProducts().size() <= 1000, "products\u5fc5\u987b\u5305\u542b1\u52301000\u4e2a\u5546\u54c1");
        RuleService.PreparedRule prepared = this.rules.prepare(request.getRuleName(), request.getVersion());
        this.requirePriceType(prepared.getConfig().getBusinessType());
        ArrayList<PriceValidationApi.ItemResult> items = new ArrayList<PriceValidationApi.ItemResult>();
        int successCount = 0;
        for (int i = 0; i < request.getProducts().size(); ++i) {
            Percentage value;
            PriceValidationApi.Product product = request.getProducts().get(i);
            try {
                value = this.percentage(product, prepared.getConfig().getBusinessType());
            }
            catch (IllegalArgumentException e) {
                items.add(new PriceValidationApi.ItemResult(i + 1, product == null ? null : product.getProductId(), "INVALID_INPUT", null, new PriceValidationApi.ItemError("INVALID_PRODUCT", e.getMessage())));
                continue;
            }
            PriceValidationApi.ProductResult result = this.evaluate(request.getDocumentId(), product, prepared, value);
            items.add(new PriceValidationApi.ItemResult(i + 1, product.getProductId(), "SUCCESS", result, null));
            ++successCount;
        }
        return new PriceValidationApi.BatchResult(request.getDocumentId(), prepared.getVersion().getRuleName(), prepared.getVersion().getVersion(), items.size(), successCount, items.size() - successCount, items);
    }

    private PriceValidationApi.ProductResult evaluate(String documentId, PriceValidationApi.Product product, RuleService.PreparedRule prepared, Percentage value) {
        boolean sales = "SALES_PRICE".equals(prepared.getConfig().getBusinessType());
        BigDecimal margin = sales ? value.display : null;
        BigDecimal increase = sales ? null : value.display;
        Map data = (Map)RuleJson.MAPPER.convertValue((Object)product, (TypeReference)new TypeReference<Map<String, JsonNode>>(){});
        RuleService.Document document = new RuleService.Document(documentId, product.getCategoryPath(), margin, increase, data);
        RuleService.ExecutionRequest execution = new RuleService.ExecutionRequest(prepared.getVersion().getRuleName(), prepared.getVersion().getVersion(), document);
        PriceValidationApi.SingleRequest audit = new PriceValidationApi.SingleRequest(execution.getRuleName(), execution.getVersion(), documentId, product);
        RuleService.ExecutionResult evaluation = this.rules.executePrepared(execution, prepared, value.numerator, value.denominator, audit);
        return new PriceValidationApi.ProductResult(product.getProductId(), prepared.getConfig().getBusinessType(), margin, increase, evaluation);
    }

    private Percentage percentage(PriceValidationApi.Product product, String type) {
        BigDecimal numerator;
        BigDecimal denominator;
        ConfigValidator.require(product != null, "\u5546\u54c1\u4e0d\u80fd\u4e3a\u7a7a");
        ConfigValidator.require(product.getProductId() != null && !product.getProductId().trim().isEmpty() && product.getProductId().length() <= 128, "productId\u5fc5\u586b\uff0c\u957f\u5ea6\u6700\u591a128");
        ConfigValidator.require(product.getCategoryPath() != null && product.getCategoryPath().size() <= 20, "categoryPath\u5fc5\u586b\uff0c\u6309\u4e00\u7ea7\u5230\u53f6\u5b50\u6392\u5e8f\uff0c\u7a7a\u5217\u8868\u4f7f\u7528\u9ed8\u8ba4\u7b56\u7565");
        product.getCategoryPath().forEach(ConfigValidator::identifier);
        if ("SALES_PRICE".equals(type)) {
            this.amount(product.getSalePrice(), "salePrice", true);
            this.amount(product.getCostPrice(), "costPrice", false);
            denominator = product.getSalePrice();
            numerator = product.getSalePrice().subtract(product.getCostPrice()).multiply(HUNDRED);
        } else {
            this.amount(product.getOriginalCostPrice(), "originalCostPrice", true);
            this.amount(product.getNewCostPrice(), "newCostPrice", false);
            denominator = product.getOriginalCostPrice();
            numerator = product.getNewCostPrice().subtract(product.getOriginalCostPrice()).multiply(HUNDRED);
        }
        return new Percentage(numerator, denominator);
    }

    private void requirePriceType(String type) {
        ConfigValidator.require("SALES_PRICE".equals(type) || "COST_PRICE".equals(type), "\u5546\u54c1\u4ef7\u683c\u63a5\u53e3\u53ea\u652f\u6301SALES_PRICE/COST_PRICE\uff0c\u5176\u4ed6\u4e1a\u52a1\u8bf7\u8c03\u7528/api/rules/execute");
    }

    private void amount(BigDecimal amount, String name, boolean positive) {
        ConfigValidator.require(amount != null, name + "\u5fc5\u586b");
        ConfigValidator.require(positive ? amount.signum() > 0 : amount.signum() >= 0, name + (positive ? "\u5fc5\u987b\u5927\u4e8e0" : "\u4e0d\u80fd\u5c0f\u4e8e0"));
        ConfigValidator.require(amount.precision() <= 24 && amount.scale() >= -12 && amount.scale() <= 12 && amount.precision() - amount.scale() <= 12, name + "\u6700\u591a12\u4f4d\u6574\u6570\u548c12\u4f4d\u5c0f\u6570");
    }

    private void validateDocumentId(String id) {
        ConfigValidator.require(id != null && !id.trim().isEmpty() && id.length() <= 128, "documentId\u5fc5\u586b\uff0c\u957f\u5ea6\u6700\u591a128");
    }

    private static final class Percentage {
        private final BigDecimal numerator;
        private final BigDecimal denominator;
        private final BigDecimal display;

        Percentage(BigDecimal numerator, BigDecimal denominator) {
            this.numerator = numerator;
            this.denominator = denominator;
            this.display = numerator.divide(denominator, 12, RoundingMode.HALF_UP).stripTrailingZeros();
        }
    }
}

