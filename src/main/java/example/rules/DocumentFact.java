/*
 * Decompiled with CFR 0.152.
 */
package example.rules;

import java.math.BigDecimal;

public final class DocumentFact {
    private final String businessType;
    private final String policyId;
    private final BigDecimal percentage;
    private final BigDecimal percentageNumerator;
    private final BigDecimal percentageDenominator;

    public DocumentFact(String businessType, String policyId, BigDecimal percentage) {
        this(businessType, policyId, percentage, percentage, BigDecimal.ONE);
    }

    public DocumentFact(String businessType, String policyId, BigDecimal percentage, BigDecimal percentageNumerator, BigDecimal percentageDenominator) {
        this.businessType = businessType;
        this.policyId = policyId;
        this.percentage = percentage;
        this.percentageNumerator = percentageNumerator;
        this.percentageDenominator = percentageDenominator;
    }

    public String getBusinessType() {
        return this.businessType;
    }

    public String getPolicyId() {
        return this.policyId;
    }

    public BigDecimal getPercentage() {
        return this.percentage;
    }

    public BigDecimal getPercentageNumerator() {
        return this.percentageNumerator;
    }

    public BigDecimal getPercentageDenominator() {
        return this.percentageDenominator;
    }

    public int comparePercentage(BigDecimal threshold) {
        return this.percentageNumerator.compareTo(threshold.multiply(this.percentageDenominator));
    }
}

