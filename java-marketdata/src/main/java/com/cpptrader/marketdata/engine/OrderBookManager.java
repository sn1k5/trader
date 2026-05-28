package com.cpptrader.marketdata.engine;

import lombok.extern.slf4j.Slf4j;

import java.util.*;

@Slf4j
public class OrderBookManager {

    private final int symbolId;
    private final TreeMap<Long, LevelEntry> bids = new TreeMap<>(Comparator.reverseOrder());
    private final TreeMap<Long, LevelEntry> asks = new TreeMap<>();

    private long lastTradePrice;
    private long lastTradeQuantity;
    private long totalVolume;
    private long tradeCount;

    public OrderBookManager(int symbolId) {
        this.symbolId = symbolId;
    }

    public synchronized void onLevelUpdate(int updateType, int levelType, OrderBookUpdateEvent.LevelData level) {
        TreeMap<Long, LevelEntry> book = (levelType == 0) ? bids : asks;

        switch (updateType) {
            case 1:
                LevelEntry existing = book.get(level.price);
                if (existing != null) {
                    existing.setTotalVolume(level.totalVolume);
                    existing.setVisibleVolume(level.visibleVolume);
                    existing.setOrders(level.orders);
                } else {
                    LevelEntry entry = new LevelEntry();
                    entry.setPrice(level.price);
                    entry.setTotalVolume(level.totalVolume);
                    entry.setVisibleVolume(level.visibleVolume);
                    entry.setOrders(level.orders);
                    book.put(level.price, entry);
                }
                break;
            case 2:
                LevelEntry toUpdate = book.get(level.price);
                if (toUpdate != null) {
                    toUpdate.setTotalVolume(level.totalVolume);
                    toUpdate.setVisibleVolume(level.visibleVolume);
                    toUpdate.setOrders(level.orders);
                } else {
                    LevelEntry entry = new LevelEntry();
                    entry.setPrice(level.price);
                    entry.setTotalVolume(level.totalVolume);
                    entry.setVisibleVolume(level.visibleVolume);
                    entry.setOrders(level.orders);
                    book.put(level.price, entry);
                }
                break;
            case 3:
                book.remove(level.price);
                break;
            default:
                log.warn("Unknown update type: {} for symbolId={}", updateType, symbolId);
        }
    }

    public synchronized void onTrade(long price, long quantity, int side) {
        lastTradePrice = price;
        lastTradeQuantity = quantity;
        totalVolume += quantity;
        tradeCount++;
    }

    public synchronized QuoteSnapshot getSnapshot(int maxDepth) {
        QuoteSnapshot snapshot = new QuoteSnapshot();
        snapshot.setSymbolId(symbolId);
        snapshot.setBestBid(bids.isEmpty() ? null : bids.firstEntry().getValue().copy());
        snapshot.setBestAsk(asks.isEmpty() ? null : asks.firstEntry().getValue().copy());

        int bidCount = 0;
        for (Map.Entry<Long, LevelEntry> entry : bids.entrySet()) {
            if (bidCount >= maxDepth) break;
            snapshot.getBids().add(entry.getValue().copy());
            bidCount++;
        }

        int askCount = 0;
        for (Map.Entry<Long, LevelEntry> entry : asks.entrySet()) {
            if (askCount >= maxDepth) break;
            snapshot.getAsks().add(entry.getValue().copy());
            askCount++;
        }

        snapshot.setLastTradePrice(lastTradePrice);
        snapshot.setLastTradeQuantity(lastTradeQuantity);
        snapshot.setTotalVolume(totalVolume);
        snapshot.setTradeCount(tradeCount);
        snapshot.setTimestamp(System.currentTimeMillis());
        return snapshot;
    }

    public synchronized LevelEntry getBestBid() {
        return bids.isEmpty() ? null : bids.firstEntry().getValue().copy();
    }

    public synchronized LevelEntry getBestAsk() {
        return asks.isEmpty() ? null : asks.firstEntry().getValue().copy();
    }

    public synchronized long getSpread() {
        if (bids.isEmpty() || asks.isEmpty()) return -1;
        return asks.firstKey() - bids.firstKey();
    }

    public synchronized long getMidPrice() {
        if (bids.isEmpty() || asks.isEmpty()) return -1;
        return (bids.firstKey() + asks.firstKey()) / 2;
    }

    public int getSymbolId() {
        return symbolId;
    }
}
