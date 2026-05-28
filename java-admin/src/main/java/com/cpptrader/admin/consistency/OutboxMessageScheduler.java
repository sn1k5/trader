package com.cpptrader.admin.consistency;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxMessageScheduler {

    private final OutboxMessageService outboxMessageService;
    private final RabbitTemplate rabbitTemplate;

    @Scheduled(fixedDelay = 5000)
    public void scanAndPublish() {
        List<OutboxMessage> messages = outboxMessageService.findPendingMessages();
        if (messages.isEmpty()) {
            return;
        }

        log.info("Found {} pending outbox messages to publish", messages.size());

        for (OutboxMessage message : messages) {
            try {
                rabbitTemplate.convertAndSend(RabbitMQConfig.EVENT_EXCHANGE, "outbox." + message.getTopic(), message.getPayload());
                outboxMessageService.markAsSent(message.getId());
                log.info("Outbox message published: id={}, topic={}", message.getId(), message.getTopic());
            } catch (Exception e) {
                log.error("Failed to publish outbox message: id={}, topic={}", message.getId(), message.getTopic(), e);
                outboxMessageService.incrementRetry(message.getId());
            }
        }
    }
}
