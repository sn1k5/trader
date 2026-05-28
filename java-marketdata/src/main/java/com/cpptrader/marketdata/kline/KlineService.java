package com.cpptrader.marketdata.kline;

import com.cpptrader.marketdata.repository.KlineEntity;
import com.cpptrader.marketdata.repository.KlineRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class KlineService {

    private final KlineRepository klineRepository;
    private final StringRedisTemplate redisTemplate;

    public List<KlineEntity> getKlineHistory(Integer symbolId, String period, int limit) {
        return klineRepository.findBySymbolIdAndPeriodOrderByOpenTimeDesc(symbolId, period, PageRequest.of(0, limit));
    }

    public KlineEntity getLatestKline(Integer symbolId, String period) {
        List<KlineEntity> list = klineRepository.findBySymbolIdAndPeriodOrderByOpenTimeDesc(symbolId, period, PageRequest.of(0, 1));
        return list.isEmpty() ? null : list.get(0);
    }

    public List<double[]> getClosePrices(Integer symbolId, String period, int count) {
        List<KlineEntity> klines = getKlineHistory(symbolId, period, count);
        List<double[]> result = new ArrayList<>();
        for (KlineEntity k : klines) {
            result.add(new double[]{k.getClosePrice()});
        }
        return result;
    }
}
