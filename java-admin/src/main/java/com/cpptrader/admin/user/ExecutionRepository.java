package com.cpptrader.admin.user;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ExecutionRepository extends JpaRepository<Execution, Long> {
    Page<Execution> findByUserIdOrderByExecutedAtDesc(Long userId, Pageable pageable);
    List<Execution> findByOrderIdOrderByExecutedAtDesc(Long orderId);
}
