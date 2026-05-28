package com.cpptrader.admin.user;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "position", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"user_id", "symbol_id", "side"})
})
public class Position {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "symbol_id", nullable = false)
    private Integer symbolId;

    @Column(nullable = false)
    private Integer side;

    @Column(nullable = false)
    private Long quantity = 0L;

    @Column(name = "avg_price", nullable = false)
    private Long avgPrice = 0L;

    @Column(name = "unrealized_pnl", nullable = false)
    private Long unrealizedPnl = 0L;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
