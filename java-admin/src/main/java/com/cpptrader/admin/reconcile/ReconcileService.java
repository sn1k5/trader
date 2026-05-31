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

        Set<String> keys = new java.util.HashSet<>();
        org.springframework.data.redis.core.Cursor<String> cursor = redisTemplate.scan(
            org.springframework.data.redis.core.ScanOptions.scanOptions().match(BALANCE_KEY_PREFIX + "*").count(100).build());
        try {
            while (cursor.hasNext()) {
                keys.add(cursor.next());
            }
        } finally {
            try { cursor.close(); } catch (Exception e) { }
        }
        if (keys.isEmpty()) {
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
        AccountBalance account = accountBalanceRepository.findByUserId(userId).orElse(null);
        if (account == null) {
            log.error("Cannot auto-fix: MySQL account not found for userId={}", userId);
            return;
        }

        balanceRedisService.initBalance(userId, account.getAvailable(), account.getFrozen());

        List<ReconcileDiffRecord> unfixed = reconcileDiffRecordRepository.findByFixedFalse();
        unfixed.stream()
                .filter(r -> r.getUserId().equals(userId))
                .forEach(r -> {
                    r.setFixed(true);
                    reconcileDiffRecordRepository.save(r);
                });

        log.info("Auto-fix completed: userId={}, set Redis available to {} from MySQL", userId, account.getAvailable());
    }

    public List<ReconcileDiffRecord> getUnfixedDiffs() {
        return reconcileDiffRecordRepository.findByFixedFalse();
    }
}
