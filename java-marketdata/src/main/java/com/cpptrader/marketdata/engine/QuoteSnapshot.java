package com.cpptrader.marketdata.engine;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class QuoteSnapshot {
    private int symbolId;
    private LevelEntry bestBid;
    private LevelEntry bestAsk;
    private List<LevelEntry> bids = new ArrayList<>();
    private List<LevelEntry> asks = new ArrayList<>();
    private long lastTradePrice;
    private long lastTradeQuantity;
    private long totalVolume;
    private long tradeCount;
    private long timestamp;

    public QuoteSnapshot deepCopy() {
        QuoteSnapshot s = new QuoteSnapshot();
        s.symbolId = this.symbolId;
        s.bestBid = this.bestBid != null ? this.bestBid.copy() : null;
        s.bestAsk = this.bestAsk != null ? this.bestAsk.copy() : null;
        for (LevelEntry b : this.bids) {
            s.bids.add(b.copy());
        }
        for (LevelEntry a : this.asks) {
            s.asks.add(a.copy());
        }
        s.lastTradePrice = this.lastTradePrice;
        s.lastTradeQuantity = this.lastTradeQuantity;
        s.totalVolume = this.totalVolume;
        s.tradeCount = this.tradeCount;
        s.timestamp = this.timestamp;
        return s;
    }
}
