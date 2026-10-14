package example.rules.result;

import com.fasterxml.jackson.databind.JsonNode;
import example.rules.model.RuleConfig.Assignment;
import java.util.List;

public class ExecuteResult {
    public final String businessCode;
    public final JsonNode results;
    public final List<?> matches;

    public ExecuteResult(String businessCode, JsonNode results, List<?> matches) {
        this.businessCode = businessCode;
        this.results = results;
        this.matches = matches;
    }

    public static class Match {
        public final String ruleName;
        public final String conditionCode;
        public final String conditionType;
        public final List<Assignment> resultAssignments;

        public Match(String ruleName, String conditionCode, String conditionType,
                List<Assignment> resultAssignments) {
            this.ruleName = ruleName;
            this.conditionCode = conditionCode;
            this.conditionType = conditionType;
            this.resultAssignments = resultAssignments;
        }
    }
}
