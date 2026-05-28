package com.cpptrader.marketdata.engine;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class TradeRecord {
    private long tradeId;
    private int symbolId;
    private long price;
    private long quantity;
    private int side;
    private long timestamp;

    public TradeRecord copy() {
        TradeRecord t = new TradeRecord();
        t.tradeId = this.tradeId;
        t.symbolId = this.symbolId;
        t.price = this.price;
        t.quantity = this.quantity;
        t.side = this.side;
        t.timestamp = this.timestamp;
        return t;
    }
}
