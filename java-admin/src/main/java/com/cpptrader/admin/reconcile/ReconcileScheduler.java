package com.cpptrader.admin.reconcile;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReconcileScheduler {

    private final ReconcileService reconcileService;

    @Scheduled(fixedDelay = 60000)
    public void scheduledReconcile() {
        log.info("Starting scheduled reconciliation");
        try {
            List<ReconcileDiffRecord> diffs = reconcileService.reconcile();
            if (!diffs.isEmpty()) {
                log.warn("Reconciliation found {} mismatches, auto-fixing...", diffs.size());
                for (ReconcileDiffRecord diff : diffs) {
                    try {
                        reconcileService.autoFix(diff.getUserId());
                    } catch (Exception e) {
                        log.error("Auto-fix failed for userId={}", diff.getUserId(), e);
                    }
                }
            }
        } catch (Exception e) {
            log.error("Scheduled reconciliation failed", e);
        }
    }
}
