package com.cpptrader.admin.balance;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class BalanceRedisService {

    private static final String BALANCE_KEY_PREFIX = "balance:";
    private static final long BALANCE_KEY_TTL_HOURS = 72;

    private final StringRedisTemplate redisTemplate;

    private static final String DEDUCT_SCRIPT =
            "local key = KEYS[1]\n" +
            "local amount = tonumber(ARGV[1])\n" +
            "local available = tonumber(redis.call('HGET', key, 'available'))\n" +
            "if available == nil then\n" +
            "    return -1\n" +
            "end\n" +
            "if available < amount then\n" +
            "    return -2\n" +
            "end\n" +
            "redis.call('HINCRBY', key, 'available', -amount)\n" +
            "return 1";

    private static final String ADD_SCRIPT =
            "local key = KEYS[1]\n" +
            "local amount = tonumber(ARGV[1])\n" +
            "redis.call('HINCRBY', key, 'available', amount)\n" +
            "return 1";

    private static final String FREEZE_SCRIPT =
            "local key = KEYS[1]\n" +
            "local amount = tonumber(ARGV[1])\n" +
            "local available = tonumber(redis.call('HGET', key, 'available'))\n" +
            "if available == nil or available < amount then\n" +
            "    return -2\n" +
            "end\n" +
            "redis.call('HINCRBY', key, 'available', -amount)\n" +
            "redis.call('HINCRBY', key, 'frozen', amount)\n" +
            "return 1";

    private static final String UNFREEZE_SCRIPT =
            "local key = KEYS[1]\n" +
            "local amount = tonumber(ARGV[1])\n" +
            "local frozen = tonumber(redis.call('HGET', key, 'frozen'))\n" +
            "if frozen == nil or frozen < amount then\n" +
            "    return -2\n" +
            "end\n" +
            "redis.call('HINCRBY', key, 'frozen', -amount)\n" +
            "redis.call('HINCRBY', key, 'available', amount)\n" +
            "return 1";

    public int deduct(Long userId, BigDecimal amount) {
        String key = BALANCE_KEY_PREFIX + userId;
        DefaultRedisScript<Long> script = new DefaultRedisScript<>(DEDUCT_SCRIPT, Long.class);
        Long result = redisTemplate.execute(script, Collections.singletonList(key), amount.toPlainString());
        if (result != null && result == 1) {
            log.info("Balance deducted in Redis: userId={}, amount={}", userId, amount);
            return 1;
        } else if (result != null && result == -2) {
            log.warn("Insufficient balance in Redis: userId={}, amount={}", userId, amount);
            return -2;
        } else {
            log.error("Balance key not found in Redis: userId={}", userId);
            return -1;
        }
    }

    public int add(Long userId, BigDecimal amount) {
        String key = BALANCE_KEY_PREFIX + userId;
        DefaultRedisScript<Long> script = new DefaultRedisScript<>(ADD_SCRIPT, Long.class);
        Long result = redisTemplate.execute(script, Collections.singletonList(key), amount.toPlainString());
        return result != null ? result.intValue() : -1;
    }

    public int freeze(Long userId, BigDecimal amount) {
        String key = BALANCE_KEY_PREFIX + userId;
        DefaultRedisScript<Long> script = new DefaultRedisScript<>(FREEZE_SCRIPT, Long.class);
        Long result = redisTemplate.execute(script, Collections.singletonList(key), amount.toPlainString());
        return result != null ? result.intValue() : -1;
    }

    public int unfreeze(Long userId, BigDecimal amount) {
        String key = BALANCE_KEY_PREFIX + userId;
        DefaultRedisScript<Long> script = new DefaultRedisScript<>(UNFREEZE_SCRIPT, Long.class);
        Long result = redisTemplate.execute(script, Collections.singletonList(key), amount.toPlainString());
        return result != null ? result.intValue() : -1;
    }

    public void initBalance(Long userId, BigDecimal available, BigDecimal frozen) {
        String key = BALANCE_KEY_PREFIX + userId;
        Map<String, String> fields = new HashMap<>();
        fields.put("available", available.toPlainString());
        fields.put("frozen", frozen.toPlainString());
        redisTemplate.opsForHash().putAll(key, fields);
        redisTemplate.expire(key, BALANCE_KEY_TTL_HOURS, TimeUnit.HOURS);
        log.info("Balance initialized in Redis: userId={}, available={}, frozen={}", userId, available, frozen);
    }

    public BigDecimal getAvailable(Long userId) {
        String key = BALANCE_KEY_PREFIX + userId;
        Object val = redisTemplate.opsForHash().get(key, "available");
        return val != null ? new BigDecimal(val.toString()) : null;
    }

    public BigDecimal getFrozen(Long userId) {
        String key = BALANCE_KEY_PREFIX + userId;
        Object val = redisTemplate.opsForHash().get(key, "frozen");
        return val != null ? new BigDecimal(val.toString()) : null;
    }
}
