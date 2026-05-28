package com.cpptrader.admin.consistency;

import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDateTime;
import java.util.List;

public interface OutboxMessageRepository extends JpaRepository<OutboxMessage, Long> {
    List<OutboxMessage> findByStatusAndNextRetryTimeBefore(Integer status, LocalDateTime time);
    List<OutboxMessage> findByStatus(Integer status);
}
