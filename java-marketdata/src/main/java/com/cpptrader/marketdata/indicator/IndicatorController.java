package com.cpptrader.marketdata.indicator;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/marketdata/indicators")
@RequiredArgsConstructor
public class IndicatorController {

    private final IndicatorService indicatorService;

    @GetMapping("/{symbolId}")
    public Map<String, Object> getMA(@PathVariable Integer symbolId,
                                      @RequestParam(defaultValue = "1m") String period,
                                      @RequestParam(defaultValue = "5,10,20") String params) {
        List<Integer> paramList = Arrays.stream(params.split(",")).map(Integer::parseInt).toList();
        return indicatorService.calculateMA(symbolId, period, paramList);
    }

    @GetMapping("/{symbolId}/macd")
    public Map<String, Object> getMACD(@PathVariable Integer symbolId,
                                        @RequestParam(defaultValue = "1m") String period) {
        return indicatorService.calculateMACD(symbolId, period);
    }

    @GetMapping("/{symbolId}/rsi")
    public Map<String, Object> getRSI(@PathVariable Integer symbolId,
                                       @RequestParam(defaultValue = "1m") String period) {
        return indicatorService.calculateRSI(symbolId, period);
    }

    @GetMapping("/{symbolId}/boll")
    public Map<String, Object> getBOLL(@PathVariable Integer symbolId,
                                        @RequestParam(defaultValue = "1m") String period) {
        return indicatorService.calculateBOLL(symbolId, period);
    }
}
