package com.cpptrader.admin.websocket;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final OrderWebSocketHandler orderWebSocketHandler;
    private final TradeWebSocketHandler tradeWebSocketHandler;

    public WebSocketConfig(OrderWebSocketHandler orderWebSocketHandler, TradeWebSocketHandler tradeWebSocketHandler) {
        this.orderWebSocketHandler = orderWebSocketHandler;
        this.tradeWebSocketHandler = tradeWebSocketHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(orderWebSocketHandler, "/ws/orders").setAllowedOrigins("*");
        registry.addHandler(tradeWebSocketHandler, "/ws/trades").setAllowedOrigins("*");
    }
}
