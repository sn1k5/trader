package com.cpptrader.marketdata.controller;

import com.cpptrader.marketdata.engine.LevelEntry;
import com.cpptrader.marketdata.engine.MarketDataEngine;
import com.cpptrader.marketdata.engine.QuoteSnapshot;
import com.cpptrader.marketdata.engine.TradeRecord;
import com.cpptrader.marketdata.cache.MarketDataCacheService;
import com.cpptrader.marketdata.protocol.client.MarketDataClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@Slf4j
@RestController
@RequestMapping("/api/marketdata")
public class MarketDataController {

    private final MarketDataEngine engine;
    private final MarketDataClient client;
    private final MarketDataCacheService cacheService;

    public MarketDataController(MarketDataEngine engine, MarketDataClient client, MarketDataCacheService cacheService) {
        this.engine = engine;
        this.client = client;
        this.cacheService = cacheService;
    }

    @GetMapping("/status")
    public Map<String, Object> getStatus() {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("connected", client.isConnected());
        status.put("activeSymbols", engine.getActiveSymbols());
        status.put("subscribedSymbols", client.getSubscribedSymbols());
        status.put("timestamp", System.currentTimeMillis());
        return status;
    }

    @PostMapping("/subscribe/{symbolId}")
    public Map<String, Object> subscribe(@PathVariable int symbolId) {
        client.subscribeSymbol(symbolId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("action", "subscribe");
        result.put("symbolId", symbolId);
        result.put("status", "ok");
        return result;
    }

    @DeleteMapping("/subscribe/{symbolId}")
    public Map<String, Object> unsubscribe(@PathVariable int symbolId) {
        client.unsubscribeSymbol(symbolId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("action", "unsubscribe");
        result.put("symbolId", symbolId);
        result.put("status", "ok");
        return result;
    }

    @GetMapping("/quote/{symbolId}")
    public Map<String, Object> getQuote(
            @PathVariable int symbolId,
            @RequestParam(defaultValue = "5") int depth) {
        String cacheKey = "quote:" + symbolId + ":" + depth;
        QuoteSnapshot snapshot = (QuoteSnapshot) cacheService.get(cacheKey, () -> engine.getQuote(symbolId, depth));
        Map<String, Object> result = new LinkedHashMap<>();
        if (snapshot == null) {
            result.put("error", "NO_DATA");
            result.put("message", "No market data for symbolId=" + symbolId + ". Subscribe first.");
            return result;
        }

        result.put("symbolId", snapshot.getSymbolId());
        result.put("timestamp", snapshot.getTimestamp());

        if (snapshot.getBestBid() != null) {
            result.put("bestBidPrice", snapshot.getBestBid().getPrice());
            result.put("bestBidVolume", snapshot.getBestBid().getTotalVolume());
        }
        if (snapshot.getBestAsk() != null) {
            result.put("bestAskPrice", snapshot.getBestAsk().getPrice());
            result.put("bestAskVolume", snapshot.getBestAsk().getTotalVolume());
        }

        long spread = engine.getSpread(symbolId);
        long midPrice = engine.getMidPrice(symbolId);
        if (spread >= 0) result.put("spread", spread);
        if (midPrice >= 0) result.put("midPrice", midPrice);

        List<Map<String, Object>> bidList = new ArrayList<>();
        for (LevelEntry b : snapshot.getBids()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("price", b.getPrice());
            m.put("volume", b.getTotalVolume());
            m.put("orders", b.getOrders());
            bidList.add(m);
        }
        result.put("bids", bidList);

        List<Map<String, Object>> askList = new ArrayList<>();
        for (LevelEntry a : snapshot.getAsks()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("price", a.getPrice());
            m.put("volume", a.getTotalVolume());
            m.put("orders", a.getOrders());
            askList.add(m);
        }
        result.put("asks", askList);

        result.put("lastTradePrice", snapshot.getLastTradePrice());
        result.put("totalVolume", snapshot.getTotalVolume());
        result.put("tradeCount", snapshot.getTradeCount());

        return result;
    }

    @GetMapping("/depth/{symbolId}")
    public Map<String, Object> getDepth(
            @PathVariable int symbolId,
            @RequestParam(defaultValue = "10") int depth) {
        QuoteSnapshot snapshot = engine.getQuote(symbolId, depth);
        Map<String, Object> result = new LinkedHashMap<>();
        if (snapshot == null) {
            result.put("error", "NO_DATA");
            return result;
        }

        result.put("symbolId", symbolId);
        result.put("timestamp", snapshot.getTimestamp());

        List<Map<String, Object>> bidList = new ArrayList<>();
        for (LevelEntry b : snapshot.getBids()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("price", b.getPrice());
            m.put("volume", b.getTotalVolume());
            m.put("visibleVolume", b.getVisibleVolume());
            m.put("orders", b.getOrders());
            bidList.add(m);
        }
        result.put("bids", bidList);

        List<Map<String, Object>> askList = new ArrayList<>();
        for (LevelEntry a : snapshot.getAsks()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("price", a.getPrice());
            m.put("volume", a.getTotalVolume());
            m.put("visibleVolume", a.getVisibleVolume());
            m.put("orders", a.getOrders());
            askList.add(m);
        }
        result.put("asks", askList);

        return result;
    }

    @GetMapping("/trades/{symbolId}")
    public Map<String, Object> getTrades(
            @PathVariable int symbolId,
            @RequestParam(defaultValue = "50") int limit) {
        String cacheKey = "trades:" + symbolId + ":" + limit;
        @SuppressWarnings("unchecked")
        List<TradeRecord> trades = (List<TradeRecord>) cacheService.get(cacheKey, () -> engine.getTradeHistory(symbolId, limit));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("symbolId", symbolId);
        result.put("count", trades.size());

        List<Map<String, Object>> tradeList = new ArrayList<>();
        for (TradeRecord t : trades) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("tradeId", t.getTradeId());
            m.put("price", t.getPrice());
            m.put("quantity", t.getQuantity());
            m.put("side", t.getSide() == 0 ? "BUY" : "SELL");
            m.put("timestamp", t.getTimestamp());
            tradeList.add(m);
        }
        result.put("trades", tradeList);
        return result;
    }

    @GetMapping("/ticker/{symbolId}")
    public Map<String, Object> getTicker(@PathVariable int symbolId) {
        String cacheKey = "ticker:" + symbolId;
        QuoteSnapshot snapshot = (QuoteSnapshot) cacheService.get(cacheKey, () -> engine.getQuote(symbolId, 1));
        Map<String, Object> result = new LinkedHashMap<>();
        if (snapshot == null) {
            result.put("error", "NO_DATA");
            return result;
        }

        result.put("symbolId", symbolId);
        result.put("timestamp", snapshot.getTimestamp());

        LevelEntry bestBid = snapshot.getBestBid();
        LevelEntry bestAsk = snapshot.getBestAsk();

        if (bestBid != null) {
            result.put("bidPrice", bestBid.getPrice());
            result.put("bidVolume", bestBid.getTotalVolume());
        }
        if (bestAsk != null) {
            result.put("askPrice", bestAsk.getPrice());
            result.put("askVolume", bestAsk.getTotalVolume());
        }

        long spread = engine.getSpread(symbolId);
        long midPrice = engine.getMidPrice(symbolId);
        if (spread >= 0) result.put("spread", spread);
        if (midPrice >= 0) result.put("midPrice", midPrice);

        result.put("lastPrice", snapshot.getLastTradePrice());
        result.put("totalVolume", snapshot.getTotalVolume());
        result.put("tradeCount", snapshot.getTradeCount());

        return result;
    }

    @GetMapping("/symbols")
    public Map<String, Object> getActiveSymbols() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("symbols", engine.getActiveSymbols());
        result.put("count", engine.getActiveSymbols().size());
        return result;
    }
}
