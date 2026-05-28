package com.cpptrader.admin.risk;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class RiskRuleService {

    private final RiskRuleRepository riskRuleRepository;
    private final RiskAlertRepository riskAlertRepository;

    public List<RiskRule> getAllRules() {
        return riskRuleRepository.findAll();
    }

    public RiskRule createRule(RiskRule rule) {
        return riskRuleRepository.save(rule);
    }

    public RiskRule updateRule(Long id, RiskRule updated) {
        RiskRule rule = riskRuleRepository.findById(id).orElseThrow(() -> new RuntimeException("Rule not found"));
        rule.setRuleName(updated.getRuleName());
        rule.setRuleType(updated.getRuleType());
        rule.setParams(updated.getParams());
        rule.setEnabled(updated.getEnabled());
        return riskRuleRepository.save(rule);
    }

    public List<RiskAlert> getAlerts(Long userId) {
        return riskAlertRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }
}
