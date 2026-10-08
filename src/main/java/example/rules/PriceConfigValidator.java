/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.springframework.stereotype.Component
 */
package example.rules;

import example.rules.PriceRuleConfig;
import java.util.HashSet;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class PriceConfigValidator {
    public void validate(PriceRuleConfig c) {
        PriceConfigValidator.require(c != null, "\u914d\u7f6e\u4e0d\u80fd\u4e3a\u7a7a");
        PriceConfigValidator.identifier(c.getRuleName());
        PriceConfigValidator.require(c.getBusinessType() != null, "businessType \u5fc5\u586b");
        PriceConfigValidator.require(c.getPolicies() != null && !c.getPolicies().isEmpty(), "policies \u5fc5\u586b");
        PriceConfigValidator.require(c.getPolicies().size() <= 100, "\u6700\u591a100\u7ec4\u54c1\u7c7b\u7b56\u7565");
        HashSet<String> policyIds = new HashSet<String>();
        HashSet<String> categories = new HashSet<String>();
        int defaults = 0;
        for (PriceRuleConfig.Policy p : c.getPolicies()) {
            PriceConfigValidator.require(p != null, "\u7b56\u7565\u4e0d\u80fd\u4e3a\u7a7a");
            PriceConfigValidator.identifier(p.getId());
            PriceConfigValidator.require(policyIds.add(p.getId()), "\u7b56\u7565ID\u91cd\u590d");
            PriceConfigValidator.require(p.getName() != null && !p.getName().trim().isEmpty(), "\u7b56\u7565\u540d\u79f0\u5fc5\u586b");
            PriceConfigValidator.require(p.getCategoryIds() != null, "categoryIds \u5fc5\u586b");
            if (p.getDefaultPolicy()) {
                ++defaults;
                PriceConfigValidator.require(p.getCategoryIds().isEmpty(), "\u9ed8\u8ba4\u7b56\u7565\u4e0d\u80fd\u6307\u5b9a\u54c1\u7c7b");
            } else {
                PriceConfigValidator.require(!p.getCategoryIds().isEmpty(), "\u81ea\u5b9a\u4e49\u7b56\u7565\u5fc5\u987b\u6307\u5b9a\u54c1\u7c7b");
                for (String id : p.getCategoryIds()) {
                    PriceConfigValidator.identifier(id);
                    PriceConfigValidator.require(categories.add(id), "\u540c\u4e00\u54c1\u7c7b\u4e0d\u80fd\u5c5e\u4e8e\u591a\u4e2a\u7b56\u7565");
                }
            }
            PriceConfigValidator.require(p.getBands() != null && !p.getBands().isEmpty() && p.getBands().size() <= 100, "\u6bcf\u7ec4\u7b56\u7565\u9700\u89811\u5230100\u4e2a\u533a\u95f4");
            PriceConfigValidator.require(p.getRisks() != null && p.getRisks().size() <= 100, "risks \u5fc5\u586b\u4e14\u6700\u591a100\u6761");
            HashSet<String> ids = new HashSet<String>();
            PriceRuleConfig.Band previous = null;
            for (PriceRuleConfig.Band b : p.getBands()) {
                PriceConfigValidator.require(b != null, "\u533a\u95f4\u4e0d\u80fd\u4e3a\u7a7a");
                PriceConfigValidator.identifier(b.getId());
                PriceConfigValidator.require(ids.add(b.getId()), "\u533a\u95f4/\u98ce\u63a7ID\u91cd\u590d");
                PriceConfigValidator.require(b.getMin() == null || b.getMax() == null || b.getMin().compareTo(b.getMax()) < 0, "\u533a\u95f4\u4e0b\u9650\u5fc5\u987b\u5c0f\u4e8e\u4e0a\u9650");
                PriceConfigValidator.require(b.getMode() != null, "\u533a\u95f4mode\u5fc5\u586b");
                this.selectors(b.getApprovers());
                PriceConfigValidator.require(b.getAutoApprove() ? b.getApprovers().isEmpty() : !b.getApprovers().isEmpty(), "\u81ea\u52a8\u901a\u8fc7\u4e0d\u80fd\u914d\u7f6e\u5ba1\u6279\u4eba\uff0c\u4eba\u5de5\u5ba1\u6279\u5fc5\u987b\u914d\u7f6e\u5ba1\u6279\u4eba");
                if (previous != null) {
                    PriceConfigValidator.require(previous.getMax() != null && b.getMin() != null, "\u65e0\u7a77\u8fb9\u754c\u5fc5\u987b\u4f4d\u4e8e\u9996\u5c3e");
                    int gap = previous.getMax().compareTo(b.getMin());
                    PriceConfigValidator.require(gap < 0 || gap == 0 && (!previous.getMaxInclusive() || !b.getMinInclusive()), "\u57fa\u7840\u533a\u95f4\u4e0d\u80fd\u91cd\u53e0\uff0c\u5fc5\u987b\u6309\u4e0b\u9650\u6392\u5e8f");
                    if (c.getBusinessType() == PriceRuleConfig.BusinessType.SALES_PRICE) {
                        PriceConfigValidator.require(gap == 0 && previous.getMaxInclusive() != b.getMinInclusive(), "\u9500\u552e\u4ef7\u533a\u95f4\u5fc5\u987b\u8fde\u7eed\u8986\u76d6");
                    }
                }
                previous = b;
            }
            if (c.getBusinessType() == PriceRuleConfig.BusinessType.SALES_PRICE) {
                PriceConfigValidator.require(p.getBands().get(0).getMin() == null && p.getBands().get(p.getBands().size() - 1).getMax() == null, "\u9500\u552e\u4ef7\u533a\u95f4\u5fc5\u987b\u8986\u76d6\u6574\u4e2a\u6bdb\u5229\u7387\u8303\u56f4");
            }
            for (PriceRuleConfig.Risk r : p.getRisks()) {
                PriceConfigValidator.require(r != null, "\u98ce\u63a7\u4e0d\u80fd\u4e3a\u7a7a");
                PriceConfigValidator.identifier(r.getId());
                PriceConfigValidator.require(ids.add(r.getId()), "\u533a\u95f4/\u98ce\u63a7ID\u91cd\u590d");
                PriceConfigValidator.require(r.getOperator() != null && r.getThreshold() != null && r.getMode() != null, "\u98ce\u63a7\u6bd4\u8f83\u7b26\u3001\u9608\u503c\u3001mode\u5fc5\u586b");
                this.selectors(r.getAdditionalApprovers());
                this.selectors(r.getCcAfterApproval());
            }
        }
        PriceConfigValidator.require(defaults == 1, "\u5fc5\u987b\u6709\u4e14\u53ea\u6709\u4e00\u4e2a\u9ed8\u8ba4\u7b56\u7565");
    }

    public static void identifier(String id) {
        PriceConfigValidator.require(id != null && id.matches("[A-Za-z0-9_:-]{1,64}"), "\u6807\u8bc6\u53ea\u80fd\u4f7f\u7528\u5b57\u6bcd\u3001\u6570\u5b57\u3001\u4e0b\u5212\u7ebf\u3001\u5192\u53f7\u3001\u77ed\u6a2a\u7ebf\uff0c\u957f\u5ea61\u523064");
    }

    private void selectors(List<String> selectors) {
        PriceConfigValidator.require(selectors != null && selectors.size() <= 100, "\u5ba1\u6279/\u6284\u9001\u5bf9\u8c61\u5217\u8868\u5fc5\u586b\u4e14\u6700\u591a100\u4e2a");
        for (String selector : selectors) {
            PriceConfigValidator.require(selector != null && selector.matches("(?:ROLE|USER):[A-Za-z0-9_:-]{1,64}"), "\u5bf9\u8c61\u683c\u5f0f\u4e3a ROLE:\u89d2\u8272\u7f16\u7801 \u6216 USER:\u7528\u6237ID");
        }
    }

    static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }
}

