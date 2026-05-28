package com.cpptrader.admin.report;

import com.cpptrader.admin.balance.AccountBalance;
import com.cpptrader.admin.balance.AccountBalanceRepository;
import com.cpptrader.admin.balance.BalanceChangeLog;
import com.cpptrader.admin.balance.BalanceChangeLogRepository;
import com.cpptrader.admin.user.Execution;
import com.cpptrader.admin.user.ExecutionRepository;
import com.cpptrader.admin.user.Position;
import com.cpptrader.admin.user.PositionRepository;
import com.cpptrader.admin.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ReportService {

    private final UserService userService;
    private final ExecutionRepository executionRepository;
    private final BalanceChangeLogRepository balanceChangeLogRepository;
    private final AccountBalanceRepository accountBalanceRepository;
    private final PositionRepository positionRepository;

    public Map<String, Object> getDailyReport(LocalDate date) {
        Long userId = userService.getCurrentUserId();
        LocalDateTime start = date.atStartOfDay();
        LocalDateTime end = date.plusDays(1).atStartOfDay();

        List<Execution> executions = executionRepository.findByUserIdOrderByExecutedAtDesc(userId, PageRequest.of(0, Integer.MAX_VALUE))
                .stream().filter(e -> e.getExecutedAt() != null && e.getExecutedAt().isAfter(start) && e.getExecutedAt().isBefore(end))
                .toList();

        long totalVolume = executions.stream().mapToLong(Execution::getQuantity).sum();
        long totalAmount = executions.stream().mapToLong(e -> e.getPrice() * e.getQuantity()).sum();

        Map<String, Object> result = new HashMap<>();
        result.put("date", date.toString());
        result.put("tradeCount", executions.size());
        result.put("totalVolume", totalVolume);
        result.put("totalAmount", totalAmount);
        return result;
    }

    public Map<String, Object> getPnlReport() {
        Long userId = userService.getCurrentUserId();
        List<Position> positions = positionRepository.findByUserId(userId);
        long unrealizedPnl = positions.stream().mapToLong(Position::getUnrealizedPnl).sum();

        Map<String, Object> result = new HashMap<>();
        result.put("unrealizedPnl", unrealizedPnl);
        result.put("positions", positions);
        return result;
    }

    public Map<String, Object> getFundFlow() {
        Long userId = userService.getCurrentUserId();
        List<BalanceChangeLog> logs = balanceChangeLogRepository.findAll();

        Map<String, Object> result = new HashMap<>();
        result.put("flows", logs);
        return result;
    }

    public Map<String, Object> getSummary() {
        Long userId = userService.getCurrentUserId();
        AccountBalance account = accountBalanceRepository.findByUserId(userId).orElse(null);
        List<Position> positions = positionRepository.findByUserId(userId);

        BigDecimal available = account != null ? account.getAvailable() : BigDecimal.ZERO;
        BigDecimal frozen = account != null ? account.getFrozen() : BigDecimal.ZERO;

        Map<String, Object> result = new HashMap<>();
        result.put("available", available);
        result.put("frozen", frozen);
        result.put("totalAssets", available.add(frozen));
        result.put("positionCount", positions.size());
        return result;
    }
}
