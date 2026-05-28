package com.cpptrader.admin.balance;

import com.cpptrader.admin.consistency.EventMessage;
import com.cpptrader.admin.consistency.RabbitMQConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class BalanceChangeProducer {

    private final RabbitTemplate rabbitTemplate;

    public void sendChangeLog(String changeId, Long userId, BigDecimal amount, int type, String bizId) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("changeId", changeId);
        payload.put("userId", userId);
        payload.put("amount", amount.toPlainString());
        payload.put("type", type);
        payload.put("bizId", bizId);

        EventMessage event = EventMessage.create("BALANCE_CHANGE", payload);
        event.setMessageId(changeId);

        try {
            rabbitTemplate.convertAndSend(RabbitMQConfig.EVENT_EXCHANGE, "balance.change", event);
            log.info("Balance change event sent: changeId={}, userId={}, amount={}", changeId, userId, amount);
        } catch (Exception e) {
            log.error("Failed to send balance change event: changeId={}", changeId, e);
            throw e;
        }
    }
}
