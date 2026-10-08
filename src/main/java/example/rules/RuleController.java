/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.fasterxml.jackson.databind.JsonNode
 *  org.springframework.web.bind.annotation.GetMapping
 *  org.springframework.web.bind.annotation.PathVariable
 *  org.springframework.web.bind.annotation.PostMapping
 *  org.springframework.web.bind.annotation.RequestBody
 *  org.springframework.web.bind.annotation.RequestMapping
 *  org.springframework.web.bind.annotation.RequestParam
 *  org.springframework.web.bind.annotation.RestController
 */
package example.rules;

import com.fasterxml.jackson.databind.JsonNode;
import example.rules.RuleService;
import example.rules.RuleStore;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value={"/api/rules"})
public class RuleController {
    private final RuleService service;

    public RuleController(RuleService service) {
        this.service = service;
    }

    @PostMapping(value={"/{name}/publish"})
    public RuleStore.Version publish(@PathVariable String name, @RequestBody JsonNode config) {
        return this.service.publish(name, config);
    }

    @GetMapping(value={"/{name}"})
    public RuleStore.Version read(@PathVariable String name, @RequestParam(required=false) Long version) {
        return this.service.read(name, version);
    }

    @PostMapping(value={"/execute"})
    public RuleService.ExecutionResult execute(@RequestBody RuleService.ExecutionRequest request) {
        return this.service.execute(request);
    }
}

