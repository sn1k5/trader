package com.cpptrader.admin.idempotent;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class TokenService {

    private static final String TOKEN_PREFIX = "idempotent:token:";
    private static final long DEFAULT_TOKEN_EXPIRE_MINUTES = 30;

    private final StringRedisTemplate redisTemplate;

    public String createToken() {
        String token = UUID.randomUUID().toString();
        String key = TOKEN_PREFIX + token;
        redisTemplate.opsForValue().set(key, "1", DEFAULT_TOKEN_EXPIRE_MINUTES, TimeUnit.MINUTES);
        log.info("Token created: {}", token);
        return token;
    }

    public boolean consumeToken(String token) {
        String key = TOKEN_PREFIX + token;
        Boolean deleted = redisTemplate.delete(key);
        if (Boolean.TRUE.equals(deleted)) {
            log.info("Token consumed: {}", token);
            return true;
        }
        log.warn("Token not found or already consumed: {}", token);
        return false;
    }

    public boolean validateAndConsume(String token) {
        if (token == null || token.isEmpty()) {
            return false;
        }
        return consumeToken(token);
    }
}
