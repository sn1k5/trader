package com.cpptrader.marketdata.auth;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/marketdata/auth")
@RequiredArgsConstructor
public class MarketDataAuthController {

    private final MarketDataJwtTokenProvider jwtTokenProvider;

    @PostMapping("/validate")
    public Map<String, Object> validateToken(@RequestBody Map<String, String> request) {
        String token = request.get("token");
        Map<String, Object> result = new HashMap<>();
        if (token != null && jwtTokenProvider.validateToken(token)) {
            result.put("valid", true);
            result.put("username", jwtTokenProvider.getUsernameFromToken(token));
            result.put("userId", jwtTokenProvider.getUserIdFromToken(token));
            result.put("role", jwtTokenProvider.getRoleFromToken(token));
        } else {
            result.put("valid", false);
        }
        return result;
    }
}
