package com.cpptrader.admin.websocket;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class TradeWebSocketHandler extends TextWebSocketHandler {

    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        String userId = extractUserId(session);
        if (userId != null) {
            sessions.put(userId + ":" + session.getId(), session);
            log.info("Trade WebSocket connected: userId={}", userId);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.entrySet().removeIf(entry -> entry.getValue().equals(session));
    }

    public void pushToUser(Long userId, String message) {
        String prefix = userId + ":";
        sessions.entrySet().stream()
                .filter(entry -> entry.getKey().startsWith(prefix))
                .forEach(entry -> {
                    try {
                        entry.getValue().sendMessage(new TextMessage(message));
                    } catch (IOException e) {
                        log.error("Failed to push trade notification to userId={}", userId, e);
                    }
                });
    }

    private String extractUserId(WebSocketSession session) {
        String query = session.getUri().getQuery();
        if (query != null) {
            for (String param : query.split("&")) {
                String[] kv = param.split("=");
                if ("userId".equals(kv[0]) && kv.length > 1) {
                    return kv[1];
                }
            }
        }
        return null;
    }
}
