package com.cpptrader.admin.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "protocol")
public class ProtocolConfig {

    private String host = "38.76.219.145";
    private int port = 8080;
    private String backend = "netty";
    private TcpConfig tcp = new TcpConfig();
    private DpdkConfig dpdk = new DpdkConfig();
    private HeartbeatConfig heartbeat = new HeartbeatConfig();
    private CppConfig cpp = new CppConfig();

    @Data
    public static class TcpConfig {
        private String host = "38.76.219.145";
        private int port = 8080;
    }

    @Data
    public static class DpdkConfig {
        private String localIp = "0.0.0.0";
        private int localPort = 0;
        private String remoteIp = "38.76.219.145";
        private int remotePort = 8080;
    }

    @Data
    public static class HeartbeatConfig {
        private int intervalSec = 5;
        private int timeoutSec = 15;
    }

    @Data
    public static class CppConfig {
        private String apiKeyId = "";
        private String apiKeySecret = "";
        private int authTimeoutSec = 5;
    }
}
