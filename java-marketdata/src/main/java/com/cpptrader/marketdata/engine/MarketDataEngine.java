package com.cpptrader.marketdata.engine;

import com.cpptrader.marketdata.config.MarketDataConfig;
import com.cpptrader.marketdata.protocol.events.OrderBookUpdateEvent;
import com.cpptrader.marketdata.protocol.events.OrderUpdateEvent;
import com.cpptrader.marketdata.kline.KlineEngine;
import com.cpptrader.marketdata.websocket.MarketDataWebSocketHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;

@Slf4j
@Service
public class MarketDataEngine {

    private final MarketDataConfig config;
    private final MarketDataWebSocketHandler wsHandler;
    private final KlineEngine klineEngine;

    private final ConcurrentHashMap<Integer, OrderBookManager> orderBooks = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Integer, LinkedBlockingQueue<TradeRecord>> tradeHistory = new ConcurrentHashMap<>();

    private long tradeIdCounter = 0;

    public MarketDataEngine(MarketDataConfig config, MarketDataWebSocketHandler wsHandler, KlineEngine klineEngine) {
        this.config = config;
        this.wsHandler = wsHandler;
        this.klineEngine = klineEngine;
    }

    public void onOrderBookUpdate(OrderBookUpdateEvent event) {
        int symbolId = event.getSymbolId();
        OrderBookManager ob = orderBooks.computeIfAbsent(symbolId, OrderBookManager::new);
        ob.onLevelUpdate(event.getUpdateType(), event.getLevelType(), event.getLevel());

        Map<String, Object> wsMsg = new LinkedHashMap<>();
        wsMsg.put("type", "orderBookUpdate");
        wsMsg.put("symbolId", symbolId);
        wsMsg.put("updateType", event.getUpdateTypeName());
        wsMsg.put("levelType", event.getLevelTypeName());
        wsMsg.put("isTop", event.isTop());
        wsMsg.put("price", event.getLevel().price);
        wsMsg.put("totalVolume", event.getLevel().totalVolume);
        wsMsg.put("visibleVolume", event.getLevel().visibleVolume);
        wsMsg.put("orders", event.getLevel().orders);
        wsHandler.broadcastToSymbol(symbolId, wsMsg);
    }

    public void onOrderUpdate(OrderUpdateEvent event) {
        int symbolId = event.getOrder().symbolId;

        if (event.isExecution()) {
            OrderBookManager ob = orderBooks.computeIfAbsent(symbolId, OrderBookManager::new);
            ob.onTrade(event.getExecutePrice(), event.getExecuteQuantity(), event.getOrder().orderSide);

            TradeRecord trade = new TradeRecord();
            trade.setTradeId(++tradeIdCounter);
            trade.setSymbolId(symbolId);
            trade.setPrice(event.getExecutePrice());
            trade.setQuantity(event.getExecuteQuantity());
            trade.setSide(event.getOrder().orderSide);
            trade.setTimestamp(System.currentTimeMillis());

            LinkedBlockingQueue<TradeRecord> history = tradeHistory.computeIfAbsent(symbolId,
                    k -> new LinkedBlockingQueue<>(config.getTradeHistorySize()));
            while (!history.offer(trade)) {
                history.poll();
            }

            Map<String, Object> wsMsg = new LinkedHashMap<>();
            wsMsg.put("type", "trade");
            wsMsg.put("symbolId", symbolId);
            wsMsg.put("tradeId", trade.getTradeId());
            wsMsg.put("price", trade.getPrice());
            wsMsg.put("quantity", trade.getQuantity());
            wsMsg.put("side", trade.getSide() == 0 ? "BUY" : "SELL");
            wsMsg.put("timestamp", trade.getTimestamp());
            wsHandler.broadcastToSymbol(symbolId, wsMsg);
            klineEngine.onTrade(symbolId, event.getExecutePrice(), event.getExecuteQuantity(), System.currentTimeMillis() / 1000);
        }
    }

    public QuoteSnapshot getQuote(int symbolId, int depth) {
        OrderBookManager ob = orderBooks.get(symbolId);
        if (ob == null) return null;
        return ob.getSnapshot(depth);
    }

    public LevelEntry getBestBid(int symbolId) {
        OrderBookManager ob = orderBooks.get(symbolId);
        return ob != null ? ob.getBestBid() : null;
    }

    public LevelEntry getBestAsk(int symbolId) {
        OrderBookManager ob = orderBooks.get(symbolId);
        return ob != null ? ob.getBestAsk() : null;
    }

    public long getSpread(int symbolId) {
        OrderBookManager ob = orderBooks.get(symbolId);
        return ob != null ? ob.getSpread() : -1;
    }

    public long getMidPrice(int symbolId) {
        OrderBookManager ob = orderBooks.get(symbolId);
        return ob != null ? ob.getMidPrice() : -1;
    }

    public List<TradeRecord> getTradeHistory(int symbolId, int limit) {
        LinkedBlockingQueue<TradeRecord> history = tradeHistory.get(symbolId);
        if (history == null) return Collections.emptyList();
        List<TradeRecord> result = new ArrayList<>();
        for (TradeRecord t : history) {
            result.add(t.copy());
        }
        if (result.size() > limit) {
            return result.subList(result.size() - limit, result.size());
        }
        return result;
    }

    public Set<Integer> getActiveSymbols() {
        return Collections.unmodifiableSet(orderBooks.keySet());
    }
}
