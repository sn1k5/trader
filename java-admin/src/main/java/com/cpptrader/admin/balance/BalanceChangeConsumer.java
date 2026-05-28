package com.cpptrader.admin.balance;

import com.cpptrader.admin.consistency.RabbitMQConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class BalanceChangeConsumer {

    private final AccountBalanceRepository accountBalanceRepository;
    private final BalanceChangeLogRepository balanceChangeLogRepository;
    private final ObjectMapper objectMapper;

    @RabbitListener(queues = RabbitMQConfig.BALANCE_DEDUCT_QUEUE)
    @Transactional
    public void onBalanceChange(Message message) {
        try {
            String body = new String(message.getBody());
            Map<String, Object> payload = objectMapper.readValue(body, Map.class);
            Map<String, Object> eventPayload = (Map<String, Object>) payload.get("payload");
            if (eventPayload == null) {
                eventPayload = payload;
            }

            String changeId = (String) eventPayload.get("changeId");
            if (balanceChangeLogRepository.existsByChangeId(changeId)) {
                log.info("Duplicate balance change, skipping: changeId={}", changeId);
                return;
            }

            Long userId = Long.valueOf(eventPayload.get("userId").toString());
            BigDecimal amount = new BigDecimal(eventPayload.get("amount").toString());
            int type = Integer.parseInt(eventPayload.get("type").toString());
            String bizId = (String) eventPayload.get("bizId");

            BalanceChangeLog changeLog = new BalanceChangeLog();
            changeLog.setChangeId(changeId);
            changeLog.setUserId(userId);
            changeLog.setAmount(amount);
            changeLog.setType(type);
            changeLog.setBizId(bizId);
            changeLog.setStatus(BalanceChangeLog.ChangeStatus.CONFIRMED.getValue());
            balanceChangeLogRepository.save(changeLog);

            AccountBalance account = accountBalanceRepository.findByUserId(userId).orElse(null);
            if (account == null) {
                account = new AccountBalance();
                account.setUserId(userId);
                account.setAvailable(BigDecimal.ZERO);
                account.setFrozen(BigDecimal.ZERO);
            }
            account.setAvailable(account.getAvailable().add(amount));
            accountBalanceRepository.save(account);

            log.info("Balance change persisted: changeId={}, userId={}, amount={}", changeId, userId, amount);
        } catch (Exception e) {
            log.error("Failed to process balance change message", e);
            throw new RuntimeException(e);
        }
    }
}
