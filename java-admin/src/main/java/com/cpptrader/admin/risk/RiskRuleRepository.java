package com.cpptrader.admin.risk;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface RiskRuleRepository extends JpaRepository<RiskRule, Long> {
    List<RiskRule> findByEnabled(Integer enabled);
}
