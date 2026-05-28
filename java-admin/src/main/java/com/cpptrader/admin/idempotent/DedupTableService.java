package com.cpptrader.admin.idempotent;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class DedupTableService {

    private final IdempotentRecordRepository idempotentRecordRepository;

    @Transactional
    public boolean tryAcquire(String messageId, String consumerGroup) {
        try {
            IdempotentRecord record = new IdempotentRecord();
            record.setMessageId(messageId);
            record.setConsumerGroup(consumerGroup);
            idempotentRecordRepository.saveAndFlush(record);
            return true;
        } catch (DataIntegrityViolationException e) {
            log.info("Duplicate message detected: messageId={}, consumerGroup={}", messageId, consumerGroup);
            return false;
        }
    }

    public boolean isProcessed(String messageId, String consumerGroup) {
        return idempotentRecordRepository.existsByMessageIdAndConsumerGroup(messageId, consumerGroup);
    }

    @Transactional
    public int cleanExpiredRecords(int retentionDays) {
        LocalDateTime threshold = LocalDateTime.now().minusDays(retentionDays);
        var expired = idempotentRecordRepository.findByCreatedAtBefore(threshold);
        int count = expired.size();
        if (count > 0) {
            idempotentRecordRepository.deleteAll(expired);
            log.info("Cleaned {} expired idempotent records older than {} days", count, retentionDays);
        }
        return count;
    }
}
