package com.cpptrader.marketdata.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

@Slf4j
@Service
@RequiredArgsConstructor
public class MarketDataCacheService {

    private final StringRedisTemplate redisTemplate;

    private final Cache<String, Object> l1Cache = Caffeine.newBuilder()
            .maximumSize(100)
            .expireAfterWrite(5, TimeUnit.SECONDS)
            .build();

    public Object get(String key, Supplier<Object> loader) {
        Object l1Value = l1Cache.getIfPresent(key);
        if (l1Value != null) {
            return l1Value;
        }

        String l2Value = redisTemplate.opsForValue().get(key);
        if (l2Value != null) {
            l1Cache.put(key, l2Value);
            return l2Value;
        }

        Object loaded = loader.get();
        if (loaded != null) {
            l1Cache.put(key, loaded);
            redisTemplate.opsForValue().set(key, loaded.toString(), 30, TimeUnit.MINUTES);
        }
        return loaded;
    }

    public void put(String key, Object value) {
        l1Cache.put(key, value);
        try {
            redisTemplate.opsForValue().set(key, value.toString(), 30, TimeUnit.MINUTES);
        } catch (Exception e) {
            log.error("Failed to write to Redis cache: key={}", key, e);
        }
    }

    public void evict(String key) {
        l1Cache.invalidate(key);
        redisTemplate.delete(key);
    }
}
