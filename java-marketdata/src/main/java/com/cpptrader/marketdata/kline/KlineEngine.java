package com.cpptrader.marketdata.kline;

import com.cpptrader.marketdata.repository.KlineEntity;
import com.cpptrader.marketdata.repository.KlineRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class KlineEngine {

    private final KlineRepository klineRepository;
    private final StringRedisTemplate redisTemplate;

    private final Map<String, KlineBar> activeBars = new ConcurrentHashMap<>();

    public void onTrade(int symbolId, long price, long quantity, long timestampSeconds) {
        for (KlinePeriod period : KlinePeriod.values()) {
            long openTime = period.calculateOpenTime(timestampSeconds);
            String key = symbolId + ":" + period.getCode() + ":" + openTime;

            KlineBar bar = activeBars.computeIfAbsent(key,
                    k -> KlineBar.create(symbolId, period, openTime, price, quantity));
            if (bar.isClosed()) {
                bar = KlineBar.create(symbolId, period, openTime, price, quantity);
                activeBars.put(key, bar);
            } else {
                bar.update(price, quantity);
            }

            cacheToRedis(bar);
        }
    }

    public void closeBar(String key) {
        KlineBar bar = activeBars.remove(key);
        if (bar != null && !bar.isClosed()) {
            bar.setClosed(true);
            persistToDatabase(bar);
            cacheToRedis(bar);
            log.info("Kline closed: symbolId={}, period={}, openTime={}", bar.getSymbolId(), bar.getPeriod().getCode(), bar.getOpenTime());
        }
    }

    public Map<String, KlineBar> getActiveBars() {
        return activeBars;
    }

    private void persistToDatabase(KlineBar bar) {
        try {
            KlineEntity entity = new KlineEntity();
            entity.setSymbolId(bar.getSymbolId());
            entity.setPeriod(bar.getPeriod().getCode());
            entity.setOpenTime(bar.getOpenTime());
            entity.setOpenPrice(bar.getOpenPrice());
            entity.setHighPrice(bar.getHighPrice());
            entity.setLowPrice(bar.getLowPrice());
            entity.setClosePrice(bar.getClosePrice());
            entity.setVolume(bar.getVolume());
            entity.setCloseTime(bar.getCloseTime());
            klineRepository.save(entity);
        } catch (Exception e) {
            log.error("Failed to persist kline: symbolId={}, period={}", bar.getSymbolId(), bar.getPeriod().getCode(), e);
        }
    }

    private void cacheToRedis(KlineBar bar) {
        try {
            String redisKey = "kline:" + bar.getSymbolId() + ":" + bar.getPeriod().getCode();
            String value = String.format("%d,%d,%d,%d,%d,%d,%b",
                    bar.getOpenTimeSeconds(), bar.getOpenPrice(), bar.getHighPrice(),
                    bar.getLowPrice(), bar.getClosePrice(), bar.getVolume(), bar.isClosed());
            redisTemplate.opsForList().rightPush(redisKey, value);
            redisTemplate.opsForList().trim(redisKey, -500, -1);
        } catch (Exception e) {
            log.error("Failed to cache kline to Redis", e);
        }
    }
}
