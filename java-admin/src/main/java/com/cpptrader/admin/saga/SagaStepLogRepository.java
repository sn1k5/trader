package com.cpptrader.admin.saga;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface SagaStepLogRepository extends JpaRepository<SagaStepLog, Long> {
    List<SagaStepLog> findBySagaIdOrderByStepOrderAsc(String sagaId);
}
