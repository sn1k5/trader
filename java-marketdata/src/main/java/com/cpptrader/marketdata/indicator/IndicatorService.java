package com.cpptrader.marketdata.indicator;

import com.cpptrader.marketdata.kline.KlineService;
import com.cpptrader.marketdata.repository.KlineEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class IndicatorService {

    private final KlineService klineService;
    private final StringRedisTemplate redisTemplate;

    public Map<String, Object> calculateMA(Integer symbolId, String period, List<Integer> params) {
        List<Double> prices = getClosePrices(symbolId, period);
        Map<String, Object> result = new HashMap<>();
        for (int p : params) {
            List<Double> values = IndicatorCalculator.ma(prices, p);
            result.put("MA" + p, values);
        }
        return result;
    }

    public Map<String, Object> calculateMACD(Integer symbolId, String period) {
        List<Double> prices = getClosePrices(symbolId, period);
        IndicatorCalculator.MacdResult macd = IndicatorCalculator.macd(prices, 12, 26, 9);
        Map<String, Object> result = new HashMap<>();
        result.put("DIF", macd.dif);
        result.put("DEA", macd.dea);
        result.put("MACD", macd.macdBar);
        return result;
    }

    public Map<String, Object> calculateRSI(Integer symbolId, String period) {
        List<Double> prices = getClosePrices(symbolId, period);
        Map<String, Object> result = new HashMap<>();
        result.put("RSI6", IndicatorCalculator.rsi(prices, 6));
        result.put("RSI12", IndicatorCalculator.rsi(prices, 12));
        result.put("RSI24", IndicatorCalculator.rsi(prices, 24));
        return result;
    }

    public Map<String, Object> calculateBOLL(Integer symbolId, String period) {
        List<Double> prices = getClosePrices(symbolId, period);
        IndicatorCalculator.BollResult boll = IndicatorCalculator.boll(prices, 20, 2.0);
        Map<String, Object> result = new HashMap<>();
        result.put("upper", boll.upper);
        result.put("mid", boll.mid);
        result.put("lower", boll.lower);
        return result;
    }

    private List<Double> getClosePrices(Integer symbolId, String period) {
        List<KlineEntity> klines = klineService.getKlineHistory(symbolId, period, 200);
        return klines.stream()
                .map(k -> (double) k.getClosePrice())
                .collect(Collectors.toList());
    }
}
