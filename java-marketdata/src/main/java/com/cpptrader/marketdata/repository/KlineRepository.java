package com.cpptrader.marketdata.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface KlineRepository extends JpaRepository<KlineEntity, Long> {
    List<KlineEntity> findBySymbolIdAndPeriodOrderByOpenTimeDesc(Integer symbolId, String period);
    List<KlineEntity> findBySymbolIdAndPeriodOrderByOpenTimeDesc(Integer symbolId, String period, org.springframework.data.domain.Pageable pageable);
    Optional<KlineEntity> findBySymbolIdAndPeriodAndOpenTime(Integer symbolId, String period, java.time.LocalDateTime openTime);
}
