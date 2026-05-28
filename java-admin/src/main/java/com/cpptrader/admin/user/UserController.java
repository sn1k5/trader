package com.cpptrader.admin.user;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final AccountService accountService;

    @GetMapping("/users/me")
    public Map<String, Object> getCurrentUser() {
        SysUser user = userService.getCurrentUser();
        Map<String, Object> result = new HashMap<>();
        result.put("id", user.getId());
        result.put("username", user.getUsername());
        result.put("role", user.getRole());
        result.put("status", user.getStatus());
        return result;
    }

    @GetMapping("/accounts")
    public List<TradingAccount> getAccounts() {
        return accountService.getCurrentUserAccounts();
    }

    @GetMapping("/positions")
    public List<Position> getPositions() {
        return accountService.getCurrentUserPositions();
    }

    @GetMapping("/positions/{symbolId}")
    public Map<String, Object> getPosition(@PathVariable Integer symbolId, @RequestParam(defaultValue = "0") Integer side) {
        Position pos = accountService.getPositionBySymbol(symbolId, side);
        Map<String, Object> result = new HashMap<>();
        if (pos != null) {
            result.put("symbolId", pos.getSymbolId());
            result.put("side", pos.getSide());
            result.put("quantity", pos.getQuantity());
            result.put("avgPrice", pos.getAvgPrice());
            result.put("unrealizedPnl", pos.getUnrealizedPnl());
        }
        return result;
    }
}
