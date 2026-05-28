package com.cpptrader.admin.reconcile;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/reconcile")
@RequiredArgsConstructor
public class ReconcileController {

    private final ReconcileService reconcileService;

    @PostMapping("/run")
    public Map<String, Object> runReconcile() {
        List<ReconcileDiffRecord> diffs = reconcileService.reconcile();
        Map<String, Object> result = new HashMap<>();
        result.put("mismatchCount", diffs.size());
        result.put("diffs", diffs);
        return result;
    }

    @GetMapping("/unfixed")
    public Map<String, Object> getUnfixedDiffs() {
        List<ReconcileDiffRecord> diffs = reconcileService.getUnfixedDiffs();
        Map<String, Object> result = new HashMap<>();
        result.put("count", diffs.size());
        result.put("diffs", diffs);
        return result;
    }

    @PostMapping("/fix/{userId}")
    public Map<String, Object> autoFix(@PathVariable Long userId) {
        reconcileService.autoFix(userId);
        Map<String, Object> result = new HashMap<>();
        result.put("userId", userId);
        result.put("status", "fixed");
        return result;
    }
}
