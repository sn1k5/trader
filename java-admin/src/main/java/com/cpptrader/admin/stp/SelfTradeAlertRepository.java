package com.cpptrader.admin.stp;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SelfTradeAlertRepository extends JpaRepository<SelfTradeAlert, Long> {

    Page<SelfTradeAlert> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    Page<SelfTradeAlert> findBySymbolIdOrderByCreatedAtDesc(Integer symbolId, Pageable pageable);

    Page<SelfTradeAlert> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
