package com.cpptrader.admin.saga;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface SagaInstanceRepository extends JpaRepository<SagaInstance, Long> {
    Optional<SagaInstance> findBySagaId(String sagaId);
    List<SagaInstance> findBySagaNameAndStatus(String sagaName, int status);
}
