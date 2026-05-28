package com.cpptrader.admin.balance;

import com.cpptrader.admin.consistency.EventMessage;
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
public class ShardedQueueProducer {

    private final RabbitTemplate rabbitTemplate;
    private final ShardedQueueConfig shardedQueueConfig;

    public void sendDeductRequest(Long userId, BigDecimal amount, String bizId) {
        int shardIndex = (int) (Math.abs(userId) % shardedQueueConfig.getShardCount());
        String routingKey = String.valueOf(shardIndex);

        Map<String, Object> payload = new HashMap<>();
        payload.put("changeId", UUID.randomUUID().toString());
        payload.put("userId", userId);
        payload.put("amount", amount.toPlainString());
        payload.put("bizId", bizId);

        EventMessage event = EventMessage.create("BALANCE_DEDUCT_SHARDED", payload);

        rabbitTemplate.convertAndSend(ShardedQueueConfig.SHARD_EXCHANGE, routingKey, event);
        log.info("Sharded deduct request sent: userId={}, shard={}, amount={}", userId, shardIndex, amount);
    }
}
