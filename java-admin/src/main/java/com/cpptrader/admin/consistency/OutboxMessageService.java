package com.cpptrader.admin.consistency;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class OutboxMessageService {

    private final OutboxMessageRepository outboxMessageRepository;
    private final org.springframework.context.ApplicationContext applicationContext;

    @Transactional
    public OutboxMessage createMessage(String topic, String payload) {
        OutboxMessage message = new OutboxMessage();
        message.setMessageId(UUID.randomUUID().toString());
        message.setTopic(topic);
        message.setPayload(payload);
        message.setStatus(OutboxMessage.Status.PENDING.getValue());
        message.setRetryCount(0);
        message.setMaxRetry(10);
        message.setNextRetryTime(LocalDateTime.now());
        return outboxMessageRepository.save(message);
    }

    @Transactional
    public OutboxMessage createAndPublish(String topic, String payload) {
        OutboxMessage message = createMessage(topic, payload);

        org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
            new org.springframework.transaction.support.TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    try {
                        OutboxMessageScheduler scheduler = applicationContext.getBean(OutboxMessageScheduler.class);
                        scheduler.scanAndPublish();
                    } catch (Exception e) {
                        log.warn("Failed to trigger immediate outbox publish after commit", e);
                    }
                }
            });

        return message;
    }

    @Transactional
    public void markAsSent(Long id) {
        OutboxMessage message = outboxMessageRepository.findById(id).orElse(null);
        if (message != null) {
            message.setStatus(OutboxMessage.Status.SENT.getValue());
            outboxMessageRepository.save(message);
        }
    }

    @Transactional
    public void markAsConsumed(String messageId) {
        outboxMessageRepository.findByMessageId(messageId).ifPresent(m -> {
            if (m.getStatus() == OutboxMessage.Status.SENT.getValue()) {
                m.setStatus(OutboxMessage.Status.CONSUMED.getValue());
                outboxMessageRepository.save(m);
            }
        });
    }

    @Transactional
    public void incrementRetry(Long id) {
        OutboxMessage message = outboxMessageRepository.findById(id).orElse(null);
        if (message == null) return;

        message.setRetryCount(message.getRetryCount() + 1);
        if (message.getRetryCount() >= message.getMaxRetry()) {
            message.setStatus(OutboxMessage.Status.DEAD.getValue());
            log.error("Message marked as DEAD: id={}, messageId={}, topic={}", id, message.getMessageId(), message.getTopic());
        } else {
            long delaySeconds = (long) Math.pow(2, message.getRetryCount());
            message.setNextRetryTime(LocalDateTime.now().plusSeconds(delaySeconds));
            log.warn("Message retry scheduled: id={}, retryCount={}, nextRetry={}", id, message.getRetryCount(), message.getNextRetryTime());
        }
        outboxMessageRepository.save(message);
    }

    public List<OutboxMessage> findPendingMessages() {
        return outboxMessageRepository.findByStatusAndNextRetryTimeBefore(
                OutboxMessage.Status.PENDING.getValue(), LocalDateTime.now(),
                org.springframework.data.domain.PageRequest.of(0, 100)).getContent();
    }

    public List<OutboxMessage> findDeadMessages() {
        return outboxMessageRepository.findByStatus(OutboxMessage.Status.DEAD.getValue());
    }

    @Transactional
    public void reactivateDeadMessage(Long id) {
        OutboxMessage message = outboxMessageRepository.findById(id).orElse(null);
        if (message != null && message.getStatus() == OutboxMessage.Status.DEAD.getValue()) {
            message.setStatus(OutboxMessage.Status.PENDING.getValue());
            message.setRetryCount(0);
            message.setNextRetryTime(LocalDateTime.now());
            outboxMessageRepository.save(message);
            log.info("Dead message reactivated: id={}, messageId={}", id, message.getMessageId());
        }
    }
}
