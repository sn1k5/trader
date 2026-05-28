package com.cpptrader.admin.user;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface TradingAccountRepository extends JpaRepository<TradingAccount, Long> {
    List<TradingAccount> findByUserId(Long userId);
}
