package com.cpptrader.admin.controller;

import com.cpptrader.admin.user.ExecutionService;
import com.cpptrader.admin.user.OrderHistoryService;
import com.cpptrader.admin.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderQueryController {

    private final OrderHistoryService orderHistoryService;
    private final ExecutionService executionService;
    private final UserService userService;

    @GetMapping("/history")
    public Map<String, Object> getOrderHistory(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Long userId = userService.getCurrentUserId();
        Page<?> orders = orderHistoryService.findByUserId(userId, page, size);
        Map<String, Object> result = new HashMap<>();
        result.put("content", orders.getContent());
        result.put("totalElements", orders.getTotalElements());
        result.put("totalPages", orders.getTotalPages());
        result.put("page", page);
        result.put("size", size);
        return result;
    }

    @GetMapping("/executions")
    public Map<String, Object> getExecutions(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Long userId = userService.getCurrentUserId();
        Page<?> executions = executionService.findByUserId(userId, page, size);
        Map<String, Object> result = new HashMap<>();
        result.put("content", executions.getContent());
        result.put("totalElements", executions.getTotalElements());
        result.put("totalPages", executions.getTotalPages());
        result.put("page", page);
        result.put("size", size);
        return result;
    }

    @GetMapping("/{id}/executions")
    public Map<String, Object> getOrderExecutions(@PathVariable Long id) {
        List<?> executions = executionService.findByOrderId(id);
        Map<String, Object> result = new HashMap<>();
        result.put("orderId", id);
        result.put("executions", executions);
        result.put("count", executions.size());
        return result;
    }
}
