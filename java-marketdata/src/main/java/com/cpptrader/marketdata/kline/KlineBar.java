package com.cpptrader.marketdata.kline;

import lombok.Data;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

@Data
public class KlineBar {
    private int symbolId;
    private KlinePeriod period;
    private long openTimeSeconds;
    private long openPrice;
    private long highPrice;
    private long lowPrice;
    private long closePrice;
    private long volume;
    private boolean closed;

    public static KlineBar create(int symbolId, KlinePeriod period, long openTimeSeconds, long price, long quantity) {
        KlineBar bar = new KlineBar();
        bar.setSymbolId(symbolId);
        bar.setPeriod(period);
        bar.setOpenTimeSeconds(openTimeSeconds);
        bar.setOpenPrice(price);
        bar.setHighPrice(price);
        bar.setLowPrice(price);
        bar.setClosePrice(price);
        bar.setVolume(quantity);
        bar.setClosed(false);
        return bar;
    }

    public void update(long price, long quantity) {
        if (price > highPrice) highPrice = price;
        if (price < lowPrice) lowPrice = price;
        closePrice = price;
        volume += quantity;
    }

    public LocalDateTime getOpenTime() {
        return LocalDateTime.ofInstant(Instant.ofEpochSecond(openTimeSeconds), ZoneId.systemDefault());
    }

    public LocalDateTime getCloseTime() {
        return LocalDateTime.ofInstant(Instant.ofEpochSecond(period.calculateCloseTime(openTimeSeconds)), ZoneId.systemDefault());
    }
}
