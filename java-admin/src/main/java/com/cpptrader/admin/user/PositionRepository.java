package com.cpptrader.admin.user;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface PositionRepository extends JpaRepository<Position, Long> {
    List<Position> findByUserId(Long userId);
    Optional<Position> findByUserIdAndSymbolIdAndSide(Long userId, Integer symbolId, Integer side);
}
