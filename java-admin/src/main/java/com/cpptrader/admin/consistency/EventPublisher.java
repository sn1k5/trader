package com.cpptrader.admin.consistency;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class EventPublisher {

    private final RabbitTemplate rabbitTemplate;

    public void publish(String routingKey, EventMessage event) {
        try {
            rabbitTemplate.convertAndSend(RabbitMQConfig.EVENT_EXCHANGE, routingKey, event);
            log.info("Published event: type={}, messageId={}", event.getEventType(), event.getMessageId());
        } catch (Exception e) {
            log.error("Failed to publish event: type={}, messageId={}", event.getEventType(), event.getMessageId(), e);
            throw e;
        }
    }
}
