package com.cpptrader.marketdata.engine;

import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.concurrent.locks.ReentrantReadWriteLock;

@Slf4j
public class OrderBookManager {

    private final int symbolId;
    private final TreeMap<Long, LevelEntry> bids = new TreeMap<>(Comparator.reverseOrder());
    private final TreeMap<Long, LevelEntry> asks = new TreeMap<>();
    private final ReentrantReadWriteLock rwLock = new ReentrantReadWriteLock();

    private long lastTradePrice;
    private long lastTradeQuantity;
    private long totalVolume;
    private long tradeCount;

    public OrderBookManager(int symbolId) {
        this.symbolId = symbolId;
    }

    public void onLevelUpdate(int updateType, int levelType, OrderBookUpdateEvent.LevelData level) {
        rwLock.writeLock().lock();
        try {
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
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    public void onTrade(long price, long quantity, int side) {
        rwLock.writeLock().lock();
        try {
            lastTradePrice = price;
            lastTradeQuantity = quantity;
            totalVolume += quantity;
            tradeCount++;
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    public QuoteSnapshot getSnapshot(int maxDepth) {
        rwLock.readLock().lock();
        try {
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
        } finally {
            rwLock.readLock().unlock();
        }
    }

    public LevelEntry getBestBid() {
        rwLock.readLock().lock();
        try {
            return bids.isEmpty() ? null : bids.firstEntry().getValue().copy();
        } finally {
            rwLock.readLock().unlock();
        }
    }

    public LevelEntry getBestAsk() {
        rwLock.readLock().lock();
        try {
            return asks.isEmpty() ? null : asks.firstEntry().getValue().copy();
        } finally {
            rwLock.readLock().unlock();
        }
    }

    public long getSpread() {
        rwLock.readLock().lock();
        try {
            if (bids.isEmpty() || asks.isEmpty()) return -1;
            return asks.firstKey() - bids.firstKey();
        } finally {
            rwLock.readLock().unlock();
        }
    }

    public long getMidPrice() {
        rwLock.readLock().lock();
        try {
            if (bids.isEmpty() || asks.isEmpty()) return -1;
            return (bids.firstKey() + asks.firstKey()) / 2;
        } finally {
            rwLock.readLock().unlock();
        }
    }

    public int getSymbolId() {
        return symbolId;
    }
}
