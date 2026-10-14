package example.rules.result;

import example.rules.model.RuleConfig;

public class RuleState {
    public final long id;
    public final RuleConfig config;

    public RuleState(long id, RuleConfig config) {
        this.id = id;
        this.config = config;
    }
}
