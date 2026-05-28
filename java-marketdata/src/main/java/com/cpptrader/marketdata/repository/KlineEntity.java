package com.cpptrader.marketdata.repository;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "kline", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"symbol_id", "period", "open_time"})
})
public class KlineEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "symbol_id", nullable = false)
    private Integer symbolId;

    @Column(nullable = false, length = 8)
    private String period;

    @Column(name = "open_time", nullable = false)
    private LocalDateTime openTime;

    @Column(name = "open_price", nullable = false)
    private Long openPrice;

    @Column(name = "high_price", nullable = false)
    private Long highPrice;

    @Column(name = "low_price", nullable = false)
    private Long lowPrice;

    @Column(name = "close_price", nullable = false)
    private Long closePrice;

    @Column(nullable = false)
    private Long volume = 0L;

    @Column(name = "close_time")
    private LocalDateTime closeTime;
}
