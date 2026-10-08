/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.springframework.stereotype.Component
 */
package example.rules;

import example.rules.PriceRuleConfig;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class PricePolicyResolver {
    public PriceRuleConfig.Policy resolve(PriceRuleConfig config, List<String> categoryPath) {
        for (int i = categoryPath.size() - 1; i >= 0; --i) {
            String category = categoryPath.get(i);
            for (PriceRuleConfig.Policy policy : config.getPolicies()) {
                if (policy.getDefaultPolicy() || !policy.getCategoryIds().contains(category)) continue;
                return policy;
            }
        }
        return config.getPolicies().stream().filter(PriceRuleConfig.Policy::getDefaultPolicy).findFirst().orElseThrow(() -> new IllegalStateException("\u7f3a\u5c11\u9ed8\u8ba4\u7b56\u7565"));
    }
}

