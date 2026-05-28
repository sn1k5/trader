package com.cpptrader.admin.saga;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "saga_instance")
public class SagaInstance {

    public enum SagaStatus { RUNNING(0), COMPLETED(1), COMPENSATING(2), COMPENSATED(3), COMPENSATE_FAILED(4);
        private final int value;
        SagaStatus(int value) { this.value = value; }
        public int getValue() { return value; }
        public static SagaStatus fromValue(int value) {
            for (SagaStatus s : values()) { if (s.value == value) return s; }
            return RUNNING;
        }
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "saga_name", nullable = false, length = 128)
    private String sagaName;

    @Column(name = "saga_id", nullable = false, unique = true, length = 64)
    private String sagaId;

    @Column(name = "current_step", nullable = false)
    private Integer currentStep = 0;

    @Column(nullable = false)
    private Integer status = 0;

    @Column(columnDefinition = "TEXT")
    private String context;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
