package com.cpptrader.admin.balance;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "balance_change_log")
public class BalanceChangeLog {

    public enum ChangeType { DEDUCT(1), ADD(2), FREEZE(3), UNFREEZE(4);
        private final int value;
        ChangeType(int value) { this.value = value; }
        public int getValue() { return value; }
    }

    public enum ChangeStatus { PROCESSING(0), CONFIRMED(1);
        private final int value;
        ChangeStatus(int value) { this.value = value; }
        public int getValue() { return value; }
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "change_id", nullable = false, unique = true, length = 64)
    private String changeId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, precision = 20, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false)
    private Integer type;

    @Column(name = "biz_id", length = 64)
    private String bizId;

    @Column(nullable = false)
    private Integer status = 0;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
