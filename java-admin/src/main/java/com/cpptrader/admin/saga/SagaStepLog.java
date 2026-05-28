package com.cpptrader.admin.saga;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "saga_step_log")
public class SagaStepLog {

    public enum StepStatus { PENDING(0), SUCCESS(1), FAILED(2), COMPENSATED(3), COMPENSATE_FAILED(4);
        private final int value;
        StepStatus(int value) { this.value = value; }
        public int getValue() { return value; }
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "saga_id", nullable = false, length = 64)
    private String sagaId;

    @Column(name = "step_name", nullable = false, length = 128)
    private String stepName;

    @Column(name = "step_order", nullable = false)
    private Integer stepOrder;

    @Column(nullable = false)
    private Integer status = 0;

    @Column(name = "request_data", columnDefinition = "TEXT")
    private String requestData;

    @Column(name = "response_data", columnDefinition = "TEXT")
    private String responseData;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
