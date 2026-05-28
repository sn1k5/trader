package com.cpptrader.admin.balance;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class BalanceService {

    private final BalanceRedisService balanceRedisService;
    private final BalanceChangeProducer balanceChangeProducer;
    private final AccountBalanceRepository accountBalanceRepository;

    public boolean deduct(Long userId, BigDecimal amount, String bizId) {
        int result = balanceRedisService.deduct(userId, amount);
        if (result != 1) {
            return false;
        }

        String changeId = UUID.randomUUID().toString();
        try {
            balanceChangeProducer.sendChangeLog(changeId, userId, amount.negate(), BalanceChangeLog.ChangeType.DEDUCT.getValue(), bizId);
        } catch (Exception e) {
            log.error("Failed to send balance change to MQ, rolling back Redis: userId={}, amount={}", userId, amount, e);
            balanceRedisService.add(userId, amount);
            return false;
        }

        log.info("Balance deduction completed: userId={}, amount={}, changeId={}", userId, amount, changeId);
        return true;
    }

    public boolean add(Long userId, BigDecimal amount, String bizId) {
        int result = balanceRedisService.add(userId, amount);
        if (result != 1) {
            return false;
        }

        String changeId = UUID.randomUUID().toString();
        try {
            balanceChangeProducer.sendChangeLog(changeId, userId, amount, BalanceChangeLog.ChangeType.ADD.getValue(), bizId);
        } catch (Exception e) {
            log.error("Failed to send balance add to MQ: userId={}, amount={}", userId, amount, e);
            return false;
        }

        return true;
    }

    public boolean freeze(Long userId, BigDecimal amount, String bizId) {
        int result = balanceRedisService.freeze(userId, amount);
        if (result != 1) {
            return false;
        }

        String changeId = UUID.randomUUID().toString();
        try {
            balanceChangeProducer.sendChangeLog(changeId, userId, amount, BalanceChangeLog.ChangeType.FREEZE.getValue(), bizId);
        } catch (Exception e) {
            log.error("Failed to send balance freeze to MQ: userId={}, amount={}", userId, amount, e);
            balanceRedisService.unfreeze(userId, amount);
            return false;
        }

        return true;
    }

    public boolean unfreeze(Long userId, BigDecimal amount, String bizId) {
        int result = balanceRedisService.unfreeze(userId, amount);
        if (result != 1) {
            return false;
        }

        String changeId = UUID.randomUUID().toString();
        try {
            balanceChangeProducer.sendChangeLog(changeId, userId, amount, BalanceChangeLog.ChangeType.UNFREEZE.getValue(), bizId);
        } catch (Exception e) {
            log.error("Failed to send balance unfreeze to MQ: userId={}, amount={}", userId, amount, e);
            return false;
        }

        return true;
    }

    public void initAccount(Long userId, BigDecimal initialBalance) {
        balanceRedisService.initBalance(userId, initialBalance, BigDecimal.ZERO);

        AccountBalance account = accountBalanceRepository.findByUserId(userId).orElse(null);
        if (account == null) {
            account = new AccountBalance();
            account.setUserId(userId);
            account.setAvailable(initialBalance);
            account.setFrozen(BigDecimal.ZERO);
            accountBalanceRepository.save(account);
        }
    }

    public BigDecimal getAvailableBalance(Long userId) {
        BigDecimal redisBalance = balanceRedisService.getAvailable(userId);
        if (redisBalance != null) {
            return redisBalance;
        }
        return accountBalanceRepository.findByUserId(userId)
                .map(AccountBalance::getAvailable)
                .orElse(BigDecimal.ZERO);
    }
}
