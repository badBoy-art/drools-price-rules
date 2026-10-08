/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.fasterxml.jackson.annotation.JsonCreator
 *  com.fasterxml.jackson.annotation.JsonProperty
 */
package example.rules;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import example.rules.ApiModels;
import example.rules.RuleService;
import java.math.BigDecimal;
import java.util.List;

public final class PriceValidationApi {
    private PriceValidationApi() {
    }

    public static final class BatchResult
    extends ApiModels.RuleVersion {
        private final String documentId;
        private final int total;
        private final int successCount;
        private final int errorCount;
        private final List<ItemResult> items;

        @JsonCreator
        public BatchResult(@JsonProperty(value="documentId") String documentId, @JsonProperty(value="rule_name") String ruleName, @JsonProperty(value="version") long version, @JsonProperty(value="total") int total, @JsonProperty(value="successCount") int successCount, @JsonProperty(value="errorCount") int errorCount, @JsonProperty(value="items") List<ItemResult> items) {
            super(ruleName, version);
            this.documentId = documentId;
            this.total = total;
            this.successCount = successCount;
            this.errorCount = errorCount;
            this.items = items;
        }

        @JsonProperty(value="documentId")
        public String getDocumentId() {
            return this.documentId;
        }

        @JsonProperty(value="total")
        public int getTotal() {
            return this.total;
        }

        @JsonProperty(value="successCount")
        public int getSuccessCount() {
            return this.successCount;
        }

        @JsonProperty(value="errorCount")
        public int getErrorCount() {
            return this.errorCount;
        }

        @JsonProperty(value="items")
        public List<ItemResult> getItems() {
            return this.items;
        }
    }

    public static final class ItemResult {
        private final int index;
        private final String productId;
        private final String status;
        private final ProductResult result;
        private final ItemError error;

        @JsonCreator
        public ItemResult(@JsonProperty(value="index") int index, @JsonProperty(value="productId") String productId, @JsonProperty(value="status") String status, @JsonProperty(value="result") ProductResult result, @JsonProperty(value="error") ItemError error) {
            this.index = index;
            this.productId = productId;
            this.status = status;
            this.result = result;
            this.error = error;
        }

        @JsonProperty(value="index")
        public int getIndex() {
            return this.index;
        }

        @JsonProperty(value="productId")
        public String getProductId() {
            return this.productId;
        }

        @JsonProperty(value="status")
        public String getStatus() {
            return this.status;
        }

        @JsonProperty(value="result")
        public ProductResult getResult() {
            return this.result;
        }

        @JsonProperty(value="error")
        public ItemError getError() {
            return this.error;
        }
    }

    public static final class ItemError {
        private final String code;
        private final String message;

        @JsonCreator
        public ItemError(@JsonProperty(value="code") String code, @JsonProperty(value="message") String message) {
            this.code = code;
            this.message = message;
        }

        @JsonProperty(value="code")
        public String getCode() {
            return this.code;
        }

        @JsonProperty(value="message")
        public String getMessage() {
            return this.message;
        }
    }

    public static final class ProductResult
    extends ApiModels.PricePercentages {
        private final String productId;
        private final String businessType;
        private final RuleService.ExecutionResult evaluation;

        @JsonCreator
        public ProductResult(@JsonProperty(value="productId") String productId, @JsonProperty(value="businessType") String businessType, @JsonProperty(value="grossMarginPct") BigDecimal grossMarginPct, @JsonProperty(value="costIncreasePct") BigDecimal costIncreasePct, @JsonProperty(value="evaluation") RuleService.ExecutionResult evaluation) {
            super(grossMarginPct, costIncreasePct);
            this.productId = productId;
            this.businessType = businessType;
            this.evaluation = evaluation;
        }

        @JsonProperty(value="productId")
        public String getProductId() {
            return this.productId;
        }

        @JsonProperty(value="businessType")
        public String getBusinessType() {
            return this.businessType;
        }

        @JsonProperty(value="evaluation")
        public RuleService.ExecutionResult getEvaluation() {
            return this.evaluation;
        }
    }

    public static final class BatchRequest
    extends ApiModels.DocumentRequest {
        private final List<Product> products;

        @JsonCreator
        public BatchRequest(@JsonProperty(value="rule_name") String ruleName, @JsonProperty(value="version") Long version, @JsonProperty(value="documentId") String documentId, @JsonProperty(value="products") List<Product> products) {
            super(ruleName, version, documentId);
            this.products = products;
        }

        @JsonProperty(value="products")
        public List<Product> getProducts() {
            return this.products;
        }
    }

    public static final class SingleRequest
    extends ApiModels.DocumentRequest {
        private final Product product;

        @JsonCreator
        public SingleRequest(@JsonProperty(value="rule_name") String ruleName, @JsonProperty(value="version") Long version, @JsonProperty(value="documentId") String documentId, @JsonProperty(value="product") Product product) {
            super(ruleName, version, documentId);
            this.product = product;
        }

        @JsonProperty(value="product")
        public Product getProduct() {
            return this.product;
        }
    }

    public static final class Product {
        private final String productId;
        private final List<String> categoryPath;
        private final BigDecimal salePrice;
        private final BigDecimal costPrice;
        private final BigDecimal originalCostPrice;
        private final BigDecimal newCostPrice;

        @JsonCreator
        public Product(@JsonProperty(value="productId") String productId, @JsonProperty(value="categoryPath") List<String> categoryPath, @JsonProperty(value="salePrice") BigDecimal salePrice, @JsonProperty(value="costPrice") BigDecimal costPrice, @JsonProperty(value="originalCostPrice") BigDecimal originalCostPrice, @JsonProperty(value="newCostPrice") BigDecimal newCostPrice) {
            this.productId = productId;
            this.categoryPath = categoryPath;
            this.salePrice = salePrice;
            this.costPrice = costPrice;
            this.originalCostPrice = originalCostPrice;
            this.newCostPrice = newCostPrice;
        }

        @JsonProperty(value="productId")
        public String getProductId() {
            return this.productId;
        }

        @JsonProperty(value="categoryPath")
        public List<String> getCategoryPath() {
            return this.categoryPath;
        }

        @JsonProperty(value="salePrice")
        public BigDecimal getSalePrice() {
            return this.salePrice;
        }

        @JsonProperty(value="costPrice")
        public BigDecimal getCostPrice() {
            return this.costPrice;
        }

        @JsonProperty(value="originalCostPrice")
        public BigDecimal getOriginalCostPrice() {
            return this.originalCostPrice;
        }

        @JsonProperty(value="newCostPrice")
        public BigDecimal getNewCostPrice() {
            return this.newCostPrice;
        }
    }
}

