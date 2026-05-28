package com.cpptrader.admin.risk;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface RiskAlertRepository extends JpaRepository<RiskAlert, Long> {
    List<RiskAlert> findByUserIdOrderByCreatedAtDesc(Long userId);
}
