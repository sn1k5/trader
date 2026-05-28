package com.cpptrader.admin.consistency;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class EventMessage {
    private String messageId;
    private String eventType;
    private Long timestamp;
    private Map<String, Object> payload;

    public static EventMessage create(String eventType, Map<String, Object> payload) {
        EventMessage msg = new EventMessage();
        msg.setMessageId(java.util.UUID.randomUUID().toString());
        msg.setEventType(eventType);
        msg.setTimestamp(System.currentTimeMillis());
        msg.setPayload(payload);
        return msg;
    }
}
