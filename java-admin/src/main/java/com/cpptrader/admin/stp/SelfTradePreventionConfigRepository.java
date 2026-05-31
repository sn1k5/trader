package com.cpptrader.admin.stp;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SelfTradePreventionConfigRepository extends JpaRepository<SelfTradePreventionConfig, Long> {

    Optional<SelfTradePreventionConfig> findBySymbolId(Integer symbolId);

    List<SelfTradePreventionConfig> findByEnabled(Integer enabled);
}
