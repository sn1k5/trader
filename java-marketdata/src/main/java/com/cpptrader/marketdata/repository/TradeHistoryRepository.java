package com.cpptrader.marketdata.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface TradeHistoryRepository extends JpaRepository<TradeHistoryEntity, Long> {
    List<TradeHistoryEntity> findListBySymbolIdOrderByTradeTimeDesc(Integer symbolId, Pageable pageable);
    Page<TradeHistoryEntity> findBySymbolIdOrderByTradeTimeDesc(Integer symbolId, Pageable pageable);
}
