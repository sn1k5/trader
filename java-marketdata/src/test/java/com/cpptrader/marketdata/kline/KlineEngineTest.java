package com.cpptrader.marketdata.kline;

import com.cpptrader.marketdata.repository.KlineRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ListOperations;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class KlineEngineTest {

    @Mock
    private KlineRepository klineRepository;
    @Mock
    private StringRedisTemplate redisTemplate;

    @InjectMocks
    private KlineEngine klineEngine;

    @Test
    void onTrade_shouldCreateNewBar() {
        when(redisTemplate.opsForList()).thenReturn(mock(ListOperations.class));

        klineEngine.onTrade(1, 100L, 10L, 1710000000L);

        Map<String, KlineBar> bars = klineEngine.getActiveBars();
        assertFalse(bars.isEmpty());
    }

    @Test
    void onTrade_shouldUpdateExistingBar() {
        when(redisTemplate.opsForList()).thenReturn(mock(ListOperations.class));

        klineEngine.onTrade(1, 100L, 10L, 1710000000L);
        klineEngine.onTrade(1, 120L, 5L, 1710000000L);

        Map<String, KlineBar> bars = klineEngine.getActiveBars();
        KlineBar bar = bars.values().stream()
                .filter(b -> b.getPeriod() == KlinePeriod.M1)
                .findFirst().orElse(null);

        assertNotNull(bar);
        assertEquals(100L, bar.getOpenPrice());
        assertEquals(120L, bar.getHighPrice());
        assertEquals(100L, bar.getLowPrice());
        assertEquals(120L, bar.getClosePrice());
        assertEquals(15L, bar.getVolume());
    }

    @Test
    void closeBar_shouldPersistToDatabase() {
        when(redisTemplate.opsForList()).thenReturn(mock(ListOperations.class));

        klineEngine.onTrade(1, 100L, 10L, 1710000000L);

        Map<String, KlineBar> bars = klineEngine.getActiveBars();
        String key = bars.keySet().iterator().next();

        klineEngine.closeBar(key);

        verify(klineRepository).save(any());
    }
}
