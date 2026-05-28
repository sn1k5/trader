package com.cpptrader.admin.risk;

import com.cpptrader.admin.user.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class RiskCheckService {

    private final RiskRuleRepository riskRuleRepository;
    private final RiskAlertRepository riskAlertRepository;
    private final StringRedisTemplate redisTemplate;
    private final UserService userService;
    private final ObjectMapper objectMapper;

    public RiskCheckResult check(Long userId, Long amount, Integer symbolId) {
        List<RiskRule> rules = riskRuleRepository.findByEnabled(1);
        for (RiskRule rule : rules) {
            try {
                Map<String, Object> params = objectMapper.readValue(rule.getParams(), Map.class);
                switch (rule.getRuleType()) {
                    case "SINGLE_LIMIT" -> checkSingleLimit(rule, userId, amount, params);
                    case "DAILY_LIMIT" -> checkDailyLimit(rule, userId, amount, params);
                    case "POSITION_LIMIT" -> checkPositionLimit(rule, userId, symbolId, params);
                    case "FREQ_LIMIT" -> checkFreqLimit(rule, userId, params);
                }
            } catch (RiskRejectException e) {
                alert(userId, rule.getRuleName(), e.getMessage());
                return new RiskCheckResult(false, e.getMessage());
            } catch (Exception e) {
                log.error("Risk check error for rule: {}", rule.getRuleName(), e);
            }
        }
        return new RiskCheckResult(true, "OK");
    }

    private void checkSingleLimit(RiskRule rule, Long userId, Long amount, Map<String, Object> params) {
        Object maxAmountObj = params.get("maxAmount");
        if (maxAmountObj != null) {
            long maxAmount = Long.parseLong(maxAmountObj.toString());
            if (amount > maxAmount) {
                throw new RiskRejectException("Single order amount " + amount + " exceeds limit " + maxAmount);
            }
        }
    }

    private void checkDailyLimit(RiskRule rule, Long userId, Long amount, Map<String, Object> params) {
        Object maxDailyObj = params.get("maxDailyAmount");
        if (maxDailyObj != null) {
            long maxDaily = Long.parseLong(maxDailyObj.toString());
            String key = "risk:daily:" + userId;
            String currentStr = redisTemplate.opsForValue().get(key);
            long current = currentStr != null ? Long.parseLong(currentStr) : 0;
            if (current + amount > maxDaily) {
                throw new RiskRejectException("Daily accumulated amount " + (current + amount) + " exceeds limit " + maxDaily);
            }
            redisTemplate.opsForValue().increment(key, amount);
            redisTemplate.expire(key, 1, TimeUnit.DAYS);
        }
    }

    private void checkPositionLimit(RiskRule rule, Long userId, Integer symbolId, Map<String, Object> params) {
        Object maxPosObj = params.get("maxPosition");
        if (maxPosObj != null) {
            long maxPosition = Long.parseLong(maxPosObj.toString());
            String key = "risk:position:" + userId + ":" + symbolId;
            String currentStr = redisTemplate.opsForValue().get(key);
            long current = currentStr != null ? Long.parseLong(currentStr) : 0;
            if (current >= maxPosition) {
                throw new RiskRejectException("Position " + current + " exceeds limit " + maxPosition);
            }
        }
    }

    private void checkFreqLimit(RiskRule rule, Long userId, Map<String, Object> params) {
        Object maxFreqObj = params.get("maxOrdersPerMinute");
        if (maxFreqObj != null) {
            int maxFreq = Integer.parseInt(maxFreqObj.toString());
            String key = "risk:freq:" + userId;
            String currentStr = redisTemplate.opsForValue().get(key);
            int current = currentStr != null ? Integer.parseInt(currentStr) : 0;
            if (current >= maxFreq) {
                throw new RiskRejectException("Order frequency " + current + " exceeds limit " + maxFreq + " per minute");
            }
            redisTemplate.opsForValue().increment(key);
            redisTemplate.expire(key, 1, TimeUnit.MINUTES);
        }
    }

    private void alert(Long userId, String ruleName, String detail) {
        RiskAlert alert = new RiskAlert();
        alert.setUserId(userId);
        alert.setRuleName(ruleName);
        alert.setDetail(detail);
        riskAlertRepository.save(alert);
        log.warn("Risk alert: userId={}, rule={}, detail={}", userId, ruleName, detail);
    }

    public static class RiskCheckResult {
        public final boolean passed;
        public final String message;
        public RiskCheckResult(boolean passed, String message) {
            this.passed = passed;
            this.message = message;
        }
    }

    private static class RiskRejectException extends RuntimeException {
        public RiskRejectException(String message) {
            super(message);
        }
    }

}
