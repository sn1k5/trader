package com.cpptrader.admin.consistency;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface OutboxMessageRepository extends JpaRepository<OutboxMessage, Long> {
    List<OutboxMessage> findByStatusAndNextRetryTimeBefore(Integer status, LocalDateTime time);
    Page<OutboxMessage> findByStatusAndNextRetryTimeBefore(int status, LocalDateTime nextRetryTime, Pageable pageable);
    List<OutboxMessage> findByStatus(Integer status);
    Optional<OutboxMessage> findByMessageId(String messageId);
}
