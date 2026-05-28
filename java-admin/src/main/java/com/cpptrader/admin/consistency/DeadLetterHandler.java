package com.cpptrader.admin.consistency;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class DeadLetterHandler {

    @RabbitListener(queues = RabbitMQConfig.DEAD_LETTER_QUEUE)
    public void handleDeadLetter(Message message) {
        String messageId = message.getMessageProperties().getMessageId();
        String body = new String(message.getBody());
        log.error("Dead letter received: messageId={}, body={}", messageId, body);
    }
}
