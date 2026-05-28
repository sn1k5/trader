package com.cpptrader.admin.balance;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/balance")
@RequiredArgsConstructor
public class BalanceController {

    private final BalanceService balanceService;

    @PostMapping("/init")
    public Map<String, Object> initAccount(@RequestParam Long userId,
                                            @RequestParam(defaultValue = "10000") BigDecimal initialBalance) {
        balanceService.initAccount(userId, initialBalance);
        Map<String, Object> result = new HashMap<>();
        result.put("userId", userId);
        result.put("initialBalance", initialBalance);
        result.put("status", "initialized");
        return result;
    }

    @PostMapping("/deduct")
    public Map<String, Object> deduct(@RequestParam Long userId,
                                       @RequestParam BigDecimal amount,
                                       @RequestParam String bizId) {
        boolean success = balanceService.deduct(userId, amount, bizId);
        Map<String, Object> result = new HashMap<>();
        result.put("userId", userId);
        result.put("amount", amount);
        result.put("success", success);
        result.put("message", success ? "Deduction successful" : "Insufficient balance or system error");
        return result;
    }

    @PostMapping("/add")
    public Map<String, Object> add(@RequestParam Long userId,
                                    @RequestParam BigDecimal amount,
                                    @RequestParam String bizId) {
        boolean success = balanceService.add(userId, amount, bizId);
        Map<String, Object> result = new HashMap<>();
        result.put("userId", userId);
        result.put("amount", amount);
        result.put("success", success);
        return result;
    }

    @PostMapping("/freeze")
    public Map<String, Object> freeze(@RequestParam Long userId,
                                       @RequestParam BigDecimal amount,
                                       @RequestParam String bizId) {
        boolean success = balanceService.freeze(userId, amount, bizId);
        Map<String, Object> result = new HashMap<>();
        result.put("userId", userId);
        result.put("amount", amount);
        result.put("success", success);
        return result;
    }

    @PostMapping("/unfreeze")
    public Map<String, Object> unfreeze(@RequestParam Long userId,
                                         @RequestParam BigDecimal amount,
                                         @RequestParam String bizId) {
        boolean success = balanceService.unfreeze(userId, amount, bizId);
        Map<String, Object> result = new HashMap<>();
        result.put("userId", userId);
        result.put("amount", amount);
        result.put("success", success);
        return result;
    }

    @GetMapping("/{userId}")
    public Map<String, Object> getBalance(@PathVariable Long userId) {
        BigDecimal available = balanceService.getAvailableBalance(userId);
        Map<String, Object> result = new HashMap<>();
        result.put("userId", userId);
        result.put("available", available);
        return result;
    }
}
