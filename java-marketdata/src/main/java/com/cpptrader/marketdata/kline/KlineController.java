package com.cpptrader.marketdata.kline;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/marketdata/kline")
@RequiredArgsConstructor
public class KlineController {

    private final KlineService klineService;

    @GetMapping("/{symbolId}")
    public Map<String, Object> getKlineHistory(@PathVariable Integer symbolId,
                                                @RequestParam(defaultValue = "1m") String period,
                                                @RequestParam(defaultValue = "100") int limit) {
        List<?> klines = klineService.getKlineHistory(symbolId, period, limit);
        Map<String, Object> result = new HashMap<>();
        result.put("symbolId", symbolId);
        result.put("period", period);
        result.put("data", klines);
        return result;
    }

    @GetMapping("/{symbolId}/latest")
    public Map<String, Object> getLatestKline(@PathVariable Integer symbolId,
                                               @RequestParam(defaultValue = "1m") String period) {
        Object kline = klineService.getLatestKline(symbolId, period);
        Map<String, Object> result = new HashMap<>();
        result.put("symbolId", symbolId);
        result.put("period", period);
        result.put("data", kline);
        return result;
    }
}
