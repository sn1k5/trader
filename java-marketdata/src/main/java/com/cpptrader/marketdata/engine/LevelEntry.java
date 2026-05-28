package com.cpptrader.marketdata.engine;

import lombok.Data;

@Data
public class LevelEntry {
    private long price;
    private long totalVolume;
    private long visibleVolume;
    private long orders;

    public LevelEntry copy() {
        LevelEntry e = new LevelEntry();
        e.price = this.price;
        e.totalVolume = this.totalVolume;
        e.visibleVolume = this.visibleVolume;
        e.orders = this.orders;
        return e;
    }
}
