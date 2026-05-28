package com.cpptrader.admin.consistency;

import com.cpptrader.admin.idempotent.DedupTableService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxMessageConsumer {

    private final DedupTableService dedupTableService;
    private final OutboxMessageService outboxMessageService;

    @RabbitListener(queues = RabbitMQConfig.OUTBOX_QUEUE)
    public void onOutboxMessage(Message message) {
        String messageId = message.getMessageProperties().getMessageId();
        if (messageId == null || messageId.isEmpty()) {
            messageId = message.getMessageProperties().getHeader("messageId");
        }

        String consumerGroup = "outbox-consumer";

        if (dedupTableService.isProcessed(messageId, consumerGroup)) {
            log.info("Duplicate outbox message, skipping: messageId={}", messageId);
            return;
        }

        try {
            String body = new String(message.getBody());
            log.info("Processing outbox message: messageId={}, body={}", messageId, body);

            dedupTableService.tryAcquire(messageId, consumerGroup);
            outboxMessageService.markAsConsumed(messageId);

            log.info("Outbox message processed successfully: messageId={}", messageId);
        } catch (Exception e) {
            log.error("Failed to process outbox message: messageId={}", messageId, e);
            throw e;
        }
    }
}
