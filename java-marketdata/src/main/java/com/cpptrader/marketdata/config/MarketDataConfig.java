package com.cpptrader.marketdata.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "market-data")
public class MarketDataConfig {

    private int maxDepth = 20;
    private int tradeHistorySize = 1000;
}
