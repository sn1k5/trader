package com.cpptrader.admin.consistency;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "outbox_message")
public class OutboxMessage {

    public enum Status {
        PENDING(0), SENT(1), CONSUMED(2), DEAD(3);
        private final int value;
        Status(int value) { this.value = value; }
        public int getValue() { return value; }
        public static Status fromValue(int value) {
            for (Status s : values()) { if (s.value == value) return s; }
            return PENDING;
        }
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "message_id", nullable = false, unique = true, length = 64)
    private String messageId;

    @Column(nullable = false, length = 128)
    private String topic;

    @Column(columnDefinition = "TEXT")
    private String payload;

    @Column(nullable = false)
    private Integer status = 0;

    @Column(name = "retry_count", nullable = false)
    private Integer retryCount = 0;

    @Column(name = "max_retry", nullable = false)
    private Integer maxRetry = 10;

    @Column(name = "next_retry_time", nullable = false)
    private LocalDateTime nextRetryTime;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (nextRetryTime == null) {
            nextRetryTime = createdAt;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
