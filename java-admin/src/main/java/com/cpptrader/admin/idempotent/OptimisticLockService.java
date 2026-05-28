package com.cpptrader.admin.idempotent;

import com.cpptrader.admin.balance.AccountBalance;
import com.cpptrader.admin.balance.AccountBalanceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.function.Supplier;

@Slf4j
@Service
@RequiredArgsConstructor
public class OptimisticLockService {

    private final AccountBalanceRepository accountBalanceRepository;

    public <T> T executeWithOptimisticLock(Supplier<T> action, int maxRetries) {
        int retryCount = 0;
        while (retryCount < maxRetries) {
            try {
                return action.get();
            } catch (ObjectOptimisticLockingFailureException e) {
                retryCount++;
                log.warn("Optimistic lock conflict, retry {}/{}", retryCount, maxRetries);
                if (retryCount >= maxRetries) {
                    throw new RuntimeException("Optimistic lock retry exhausted after " + maxRetries + " attempts", e);
                }
                try {
                    Thread.sleep(50L * retryCount);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Interrupted during optimistic lock retry", ie);
                }
            }
        }
        throw new RuntimeException("Should not reach here");
    }

    public boolean deductBalance(Long userId, BigDecimal amount) {
        return executeWithOptimisticLock(() -> {
            AccountBalance account = accountBalanceRepository.findByUserId(userId)
                    .orElseThrow(() -> new RuntimeException("Account not found: " + userId));
            if (account.getAvailable().compareTo(amount) < 0) {
                throw new RuntimeException("Insufficient balance for user: " + userId);
            }
            account.setAvailable(account.getAvailable().subtract(amount));
            accountBalanceRepository.save(account);
            return true;
        }, 3);
    }
}
