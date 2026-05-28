package com.cpptrader.admin.protocol.client;

import com.cpptrader.admin.protocol.events.OrderBookUpdateEvent;
import com.cpptrader.admin.protocol.events.OrderUpdateEvent;
import lombok.extern.slf4j.Slf4j;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.function.Consumer;

@Slf4j
public class ProtocolStreamSubscriber {

    private final Set<Integer> orderBookSubscriptions = new CopyOnWriteArraySet<>();
    private final Set<Integer> ordersSubscriptions = new CopyOnWriteArraySet<>();

    private Consumer<OrderBookUpdateEvent> orderBookCallback;
    private Consumer<OrderUpdateEvent> ordersCallback;

    private final ProtocolClientService clientService;

    public ProtocolStreamSubscriber(ProtocolClientService clientService) {
        this.clientService = clientService;
    }

    public void setOrderBookCallback(Consumer<OrderBookUpdateEvent> callback) {
        this.orderBookCallback = callback;
    }

    public void setOrdersCallback(Consumer<OrderUpdateEvent> callback) {
        this.ordersCallback = callback;
    }

    public void subscribeOrderBook(int symbolId) {
        orderBookSubscriptions.add(symbolId);
        clientService.sendSubscribeOrderBook(symbolId);
        log.info("Subscribed OrderBook for symbolId={}", symbolId);
    }

    public void subscribeOrders(int symbolId) {
        ordersSubscriptions.add(symbolId);
        clientService.sendSubscribeOrders(symbolId);
        log.info("Subscribed Orders for symbolId={}", symbolId);
    }

    public void unsubscribeOrderBook(int symbolId) {
        orderBookSubscriptions.remove(symbolId);
        log.info("Unsubscribed OrderBook for symbolId={}", symbolId);
    }

    public void unsubscribeOrders(int symbolId) {
        ordersSubscriptions.remove(symbolId);
        log.info("Unsubscribed Orders for symbolId={}", symbolId);
    }

    public void onOrderBookUpdate(OrderBookUpdateEvent event) {
        if (orderBookCallback != null) {
            try {
                orderBookCallback.accept(event);
            } catch (Exception e) {
                log.error("Error in OrderBook callback", e);
            }
        }
    }

    public void onOrdersUpdate(OrderUpdateEvent event) {
        if (ordersCallback != null) {
            try {
                ordersCallback.accept(event);
            } catch (Exception e) {
                log.error("Error in Orders callback", e);
            }
        }
    }

    public void restoreSubscriptions() {
        if (!orderBookSubscriptions.isEmpty()) {
            log.info("Restoring {} OrderBook subscriptions", orderBookSubscriptions.size());
            for (int symbolId : orderBookSubscriptions) {
                clientService.sendSubscribeOrderBook(symbolId);
            }
        }
        if (!ordersSubscriptions.isEmpty()) {
            log.info("Restoring {} Orders subscriptions", ordersSubscriptions.size());
            for (int symbolId : ordersSubscriptions) {
                clientService.sendSubscribeOrders(symbolId);
            }
        }
    }

    public Set<Integer> getOrderBookSubscriptions() {
        return ConcurrentHashMap.newKeySet();
    }

    public Set<Integer> getOrdersSubscriptions() {
        return ConcurrentHashMap.newKeySet();
    }

    public void clear() {
        orderBookSubscriptions.clear();
        ordersSubscriptions.clear();
        orderBookCallback = null;
        ordersCallback = null;
    }
}
