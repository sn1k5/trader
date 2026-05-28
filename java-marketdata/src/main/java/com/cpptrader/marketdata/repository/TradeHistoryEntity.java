package com.cpptrader.marketdata.repository;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "trade_history")
public class TradeHistoryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "symbol_id", nullable = false)
    private Integer symbolId;

    @Column(name = "trade_id", nullable = false)
    private Long tradeId;

    @Column(nullable = false)
    private Long price;

    @Column(nullable = false)
    private Long quantity;

    @Column(nullable = false)
    private Integer side;

    @Column(name = "trade_time", nullable = false)
    private LocalDateTime tradeTime;
}
