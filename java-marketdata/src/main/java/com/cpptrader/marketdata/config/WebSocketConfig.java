package com.cpptrader.marketdata.config;

import com.cpptrader.marketdata.websocket.MarketDataWebSocketHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final MarketDataWebSocketHandler marketDataHandler;

    public WebSocketConfig(MarketDataWebSocketHandler marketDataHandler) {
        this.marketDataHandler = marketDataHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(marketDataHandler, "/ws/marketdata")
                .setAllowedOrigins("*");
    }
}
