package com.cpptrader.admin.reconcile;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "reconcile_diff_record")
public class ReconcileDiffRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "redis_available", nullable = false, precision = 20, scale = 2)
    private BigDecimal redisAvailable;

    @Column(name = "mysql_available", nullable = false, precision = 20, scale = 2)
    private BigDecimal mysqlAvailable;

    @Column(name = "diff_amount", nullable = false, precision = 20, scale = 2)
    private BigDecimal diffAmount;

    @Column(nullable = false)
    private Boolean fixed = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
