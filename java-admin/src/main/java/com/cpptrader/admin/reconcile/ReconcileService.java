package com.cpptrader.admin.reconcile;

import com.cpptrader.admin.balance.AccountBalance;
import com.cpptrader.admin.balance.AccountBalanceRepository;
import com.cpptrader.admin.balance.BalanceRedisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReconcileService {

    private final StringRedisTemplate redisTemplate;
    private final BalanceRedisService balanceRedisService;
    private final AccountBalanceRepository accountBalanceRepository;
    private final ReconcileDiffRecordRepository reconcileDiffRecordRepository;

    private static final String BALANCE_KEY_PREFIX = "balance:";

    public List<ReconcileDiffRecord> reconcile() {
        List<ReconcileDiffRecord> diffs = new ArrayList<>();

        Set<String> keys = redisTemplate.keys(BALANCE_KEY_PREFIX + "*");
        if (keys == null || keys.isEmpty()) {
            log.info("No balance keys found in Redis");
            return diffs;
        }

        for (String key : keys) {
            String userIdStr = key.substring(BALANCE_KEY_PREFIX.length());
            Long userId;
            try {
                userId = Long.valueOf(userIdStr);
            } catch (NumberFormatException e) {
                continue;
            }

            BigDecimal redisAvailable = balanceRedisService.getAvailable(userId);
            if (redisAvailable == null) continue;

            AccountBalance account = accountBalanceRepository.findByUserId(userId).orElse(null);
            BigDecimal mysqlAvailable = account != null ? account.getAvailable() : BigDecimal.ZERO;

            if (redisAvailable.compareTo(mysqlAvailable) != 0) {
                BigDecimal diff = redisAvailable.subtract(mysqlAvailable);
                ReconcileDiffRecord record = new ReconcileDiffRecord();
                record.setUserId(userId);
                record.setRedisAvailable(redisAvailable);
                record.setMysqlAvailable(mysqlAvailable);
                record.setDiffAmount(diff);
                record.setFixed(false);
                reconcileDiffRecordRepository.save(record);
                diffs.add(record);

                log.warn("Balance mismatch: userId={}, redis={}, mysql={}, diff={}", userId, redisAvailable, mysqlAvailable, diff);
            }
        }

        log.info("Reconciliation completed: {} mismatches found", diffs.size());
        return diffs;
    }

    @Transactional
    public void autoFix(Long userId) {
        BigDecimal redisAvailable = balanceRedisService.getAvailable(userId);
        if (redisAvailable == null) {
            log.error("Cannot auto-fix: Redis balance not found for userId={}", userId);
            return;
        }

        AccountBalance account = accountBalanceRepository.findByUserId(userId).orElse(null);
        if (account == null) {
            account = new AccountBalance();
            account.setUserId(userId);
            account.setFrozen(BigDecimal.ZERO);
        }
        account.setAvailable(redisAvailable);
        accountBalanceRepository.save(account);

        List<ReconcileDiffRecord> unfixed = reconcileDiffRecordRepository.findByFixedFalse();
        unfixed.stream()
                .filter(r -> r.getUserId().equals(userId))
                .forEach(r -> {
                    r.setFixed(true);
                    reconcileDiffRecordRepository.save(r);
                });

        log.info("Auto-fix completed: userId={}, set mysql available to {}", userId, redisAvailable);
    }

    public List<ReconcileDiffRecord> getUnfixedDiffs() {
        return reconcileDiffRecordRepository.findByFixedFalse();
    }
}
