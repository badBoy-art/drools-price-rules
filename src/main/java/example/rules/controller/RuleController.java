package example.rules.controller;

import example.rules.model.ExecuteRequest;
import example.rules.model.RuleConfig;
import example.rules.model.RuleConfig.Condition;
import example.rules.result.ExecuteResult;
import example.rules.result.RuleState;
import example.rules.service.RuleService;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class RuleController {
    private final RuleService service;
    public RuleController(RuleService service) { this.service = service; }

    @PostMapping("/rule-sets")
    @ResponseStatus(HttpStatus.CREATED)
    public RuleState create(@RequestBody RuleConfig config) { return service.create(config); }

    @GetMapping("/rule-sets/{businessCode}")
    public RuleState read(@PathVariable String businessCode) { return service.read(businessCode); }

    @PutMapping("/rule-sets/{businessCode}")
    public RuleState update(@PathVariable String businessCode, @RequestBody RuleConfig config) {
        return service.update(businessCode, config);
    }

    @GetMapping("/rule-sets/{businessCode}/conditions")
    public List<Condition> conditions(@PathVariable String businessCode) { return service.conditions(businessCode); }

    @PostMapping("/rule-sets/{businessCode}/conditions")
    @ResponseStatus(HttpStatus.CREATED)
    public RuleState insertCondition(@PathVariable String businessCode, @RequestBody Condition condition) {
        return service.insertCondition(businessCode, condition);
    }

    @PutMapping("/rule-sets/{businessCode}/conditions/{ruleName}/{conditionCode}")
    public RuleState updateCondition(@PathVariable String businessCode, @PathVariable String ruleName,
            @PathVariable String conditionCode, @RequestBody Condition condition) {
        return service.updateCondition(businessCode, ruleName, conditionCode, condition);
    }

    @PostMapping("/rules/execute")
    public ExecuteResult execute(@RequestBody ExecuteRequest request) { return service.execute(request); }
}
