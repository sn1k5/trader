package com.cpptrader.admin.balance;

import org.springframework.data.jpa.repository.JpaRepository;

public interface BalanceChangeLogRepository extends JpaRepository<BalanceChangeLog, Long> {
    boolean existsByChangeId(String changeId);
}
