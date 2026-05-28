package com.cpptrader.marketdata.kline;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class KlineScheduler {

    private final KlineEngine klineEngine;

    @Scheduled(fixedDelay = 1000)
    public void checkAndCloseExpiredBars() {
        long now = Instant.now().getEpochSecond();
        Map<String, KlineBar> activeBars = klineEngine.getActiveBars();
        List<String> toClose = new ArrayList<>();

        for (Map.Entry<String, KlineBar> entry : activeBars.entrySet()) {
            KlineBar bar = entry.getValue();
            long closeTime = bar.getPeriod().calculateCloseTime(bar.getOpenTimeSeconds());
            if (now > closeTime) {
                toClose.add(entry.getKey());
            }
        }

        for (String key : toClose) {
            klineEngine.closeBar(key);
        }
    }
}
