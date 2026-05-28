package com.cpptrader.admin.idempotent;

import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDateTime;
import java.util.List;

public interface IdempotentRecordRepository extends JpaRepository<IdempotentRecord, Long> {
    boolean existsByMessageIdAndConsumerGroup(String messageId, String consumerGroup);
    List<IdempotentRecord> findByCreatedAtBefore(LocalDateTime time);
}
