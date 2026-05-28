package com.cpptrader.admin.balance;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class ShardedQueueConsumer {

    private final BalanceService balanceService;
    private final ObjectMapper objectMapper;

    @RabbitListener(queuesToDeclare = {
        @Queue(name = "balance.deduct.shard.0", durable = "true"),
        @Queue(name = "balance.deduct.shard.1", durable = "true"),
        @Queue(name = "balance.deduct.shard.2", durable = "true"),
        @Queue(name = "balance.deduct.shard.3", durable = "true")
    })
    public void onShardedDeduct(Message message) {
        processDeduct(message);
    }

    private void processDeduct(Message message) {
        try {
            String body = new String(message.getBody());
            Map<String, Object> eventMap = objectMapper.readValue(body, Map.class);
            Map<String, Object> payload = (Map<String, Object>) eventMap.get("payload");
            if (payload == null) {
                payload = eventMap;
            }

            String changeId = (String) payload.get("changeId");
            Long userId = Long.valueOf(payload.get("userId").toString());
            BigDecimal amount = new BigDecimal(payload.get("amount").toString());
            String bizId = (String) payload.get("bizId");

            log.info("Processing sharded deduct: userId={}, amount={}, changeId={}", userId, amount, changeId);

            boolean success = balanceService.deduct(userId, amount, bizId);
            if (!success) {
                log.warn("Sharded deduct failed: userId={}, amount={}", userId, amount);
            }
        } catch (Exception e) {
            log.error("Failed to process sharded deduct message", e);
            throw new RuntimeException(e);
        }
    }
}
