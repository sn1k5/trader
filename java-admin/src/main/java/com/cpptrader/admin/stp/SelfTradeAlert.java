package com.cpptrader.admin.stp;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "stp_alert")
public class SelfTradeAlert {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "symbol_id", nullable = false)
    private Integer symbolId;

    @Column(name = "incoming_order_id", nullable = false)
    private Long incomingOrderId;

    @Column(name = "incoming_side", nullable = false)
    private Integer incomingSide;

    @Column(name = "incoming_price", nullable = false)
    private Long incomingPrice;

    @Column(name = "incoming_quantity", nullable = false)
    private Long incomingQuantity;

    @Column(name = "resting_order_id", nullable = false)
    private Long restingOrderId;

    @Column(name = "resting_side", nullable = false)
    private Integer restingSide;

    @Column(name = "resting_price", nullable = false)
    private Long restingPrice;

    @Column(name = "resting_quantity", nullable = false)
    private Long restingQuantity;

    @Column(name = "overlap_quantity", nullable = false)
    private Long overlapQuantity;

    @Column(name = "policy_applied", nullable = false, length = 32)
    private String policyApplied;

    @Column(name = "action_taken", nullable = false, length = 32)
    private String actionTaken;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
