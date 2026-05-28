package com.cpptrader.admin.balance;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface AccountBalanceRepository extends JpaRepository<AccountBalance, Long> {
    Optional<AccountBalance> findByUserId(Long userId);
}
