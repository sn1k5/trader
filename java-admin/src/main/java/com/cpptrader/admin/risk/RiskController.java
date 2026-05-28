package com.cpptrader.admin.risk;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/risk")
@RequiredArgsConstructor
public class RiskController {

    private final RiskRuleService riskRuleService;

    @GetMapping("/rules")
    public List<RiskRule> getRules() {
        return riskRuleService.getAllRules();
    }

    @PostMapping("/rules")
    public RiskRule createRule(@RequestBody RiskRule rule) {
        return riskRuleService.createRule(rule);
    }

    @PutMapping("/rules/{id}")
    public RiskRule updateRule(@PathVariable Long id, @RequestBody RiskRule rule) {
        return riskRuleService.updateRule(id, rule);
    }

    @GetMapping("/alerts")
    public List<RiskAlert> getAlerts(@RequestParam Long userId) {
        return riskRuleService.getAlerts(userId);
    }
}
