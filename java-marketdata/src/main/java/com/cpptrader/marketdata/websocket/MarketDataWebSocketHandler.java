package com.cpptrader.marketdata.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class MarketDataWebSocketHandler extends TextWebSocketHandler {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final ConcurrentHashMap<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Set<Integer>> sessionSubscriptions = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Set<String>> sessionKlineSubscriptions = new ConcurrentHashMap<>();
    private final ApplicationContext applicationContext;

    public MarketDataWebSocketHandler(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String token = extractTokenFromUri(session);
        if (token != null && !validateToken(token)) {
            session.close(CloseStatus.NOT_ACCEPTABLE);
            log.warn("WebSocket connection rejected: invalid token");
            return;
        }
        sessions.put(session.getId(), session);
        sessionSubscriptions.put(session.getId(), ConcurrentHashMap.newKeySet());
        sessionKlineSubscriptions.put(session.getId(), ConcurrentHashMap.newKeySet());
        log.info("WebSocket connected: {}", session.getId());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session.getId());
        sessionSubscriptions.remove(session.getId());
        sessionKlineSubscriptions.remove(session.getId());
        log.info("WebSocket disconnected: {} status={}", session.getId(), status);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> msg = objectMapper.readValue(message.getPayload(), Map.class);
            String action = (String) msg.get("action");
            Integer symbolId = ((Number) msg.get("symbolId")).intValue();

            if ("subscribe".equals(action)) {
                Set<Integer> subs = sessionSubscriptions.get(session.getId());
                if (subs != null) {
                    subs.add(symbolId);
                }
                log.info("WebSocket {} subscribed symbolId={}", session.getId(), symbolId);
            } else if ("unsubscribe".equals(action)) {
                Set<Integer> subs = sessionSubscriptions.get(session.getId());
                if (subs != null) {
                    subs.remove(symbolId);
                }
                log.info("WebSocket {} unsubscribed symbolId={}", session.getId(), symbolId);
            } else if ("subscribeKline".equals(action)) {
                String klineKey = symbolId + ":" + msg.get("period");
                Set<String> klineSubs = sessionKlineSubscriptions.get(session.getId());
                if (klineSubs != null) {
                    klineSubs.add(klineKey);
                }
            } else if ("unsubscribeKline".equals(action)) {
                String klineKey = symbolId + ":" + msg.get("period");
                Set<String> klineSubs = sessionKlineSubscriptions.get(session.getId());
                if (klineSubs != null) {
                    klineSubs.remove(klineKey);
                }
            }
        } catch (Exception e) {
            log.error("Error handling WebSocket message", e);
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.error("WebSocket transport error: {}", session.getId(), exception);
    }

    public void broadcastToSymbol(int symbolId, Map<String, Object> data) {
        String json;
        try {
            json = objectMapper.writeValueAsString(data);
        } catch (Exception e) {
            log.error("Error serializing broadcast message", e);
            return;
        }

        TextMessage message = new TextMessage(json);
        for (Map.Entry<String, Set<Integer>> entry : sessionSubscriptions.entrySet()) {
            if (entry.getValue().contains(symbolId)) {
                WebSocketSession session = sessions.get(entry.getKey());
                if (session != null && session.isOpen()) {
                    try {
                        session.sendMessage(message);
                    } catch (IOException e) {
                        log.error("Error sending to WebSocket {}", entry.getKey(), e);
                    }
                }
            }
        }
    }

    public void broadcastAll(Map<String, Object> data) {
        String json;
        try {
            json = objectMapper.writeValueAsString(data);
        } catch (Exception e) {
            log.error("Error serializing broadcast message", e);
            return;
        }

        TextMessage message = new TextMessage(json);
        for (WebSocketSession session : sessions.values()) {
            if (session.isOpen()) {
                try {
                    session.sendMessage(message);
                } catch (IOException e) {
                    log.error("Error sending to WebSocket {}", session.getId(), e);
                }
            }
        }
    }

    public void broadcastKline(int symbolId, String period, Map<String, Object> data) {
        String klineKey = symbolId + ":" + period;
        String json;
        try {
            json = objectMapper.writeValueAsString(data);
        } catch (Exception e) {
            log.error("Error serializing kline message", e);
            return;
        }
        TextMessage message = new TextMessage(json);
        for (Map.Entry<String, Set<String>> entry : sessionKlineSubscriptions.entrySet()) {
            if (entry.getValue().contains(klineKey)) {
                WebSocketSession session = sessions.get(entry.getKey());
                if (session != null && session.isOpen()) {
                    try {
                        session.sendMessage(message);
                    } catch (IOException e) {
                        log.error("Error sending kline to WebSocket {}", entry.getKey(), e);
                    }
                }
            }
        }
    }

    public int getConnectionCount() {
        return sessions.size();
    }

    private String extractTokenFromUri(WebSocketSession session) {
        String query = session.getUri().getQuery();
        if (query != null) {
            for (String param : query.split("&")) {
                String[] kv = param.split("=");
                if ("token".equals(kv[0]) && kv.length > 1) {
                    return kv[1];
                }
            }
        }
        return null;
    }

    private boolean validateToken(String token) {
        try {
            com.cpptrader.marketdata.auth.MarketDataJwtTokenProvider provider =
                    applicationContext.getBean(com.cpptrader.marketdata.auth.MarketDataJwtTokenProvider.class);
            return provider.validateToken(token);
        } catch (Exception e) {
            return false;
        }
    }
}
