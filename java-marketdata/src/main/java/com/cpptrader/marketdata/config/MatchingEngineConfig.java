package com.cpptrader.marketdata.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "matching-engine")
public class MatchingEngineConfig {

    private String host = "127.0.0.1";
    private int port = 50059;
    private String backend = "netty";
    private HeartbeatConfig heartbeat = new HeartbeatConfig();

    @Data
    public static class HeartbeatConfig {
        private int intervalSec = 10;
        private int timeoutSec = 15;
    }
}
