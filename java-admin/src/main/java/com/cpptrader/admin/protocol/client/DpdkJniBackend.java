package com.cpptrader.admin.protocol.client;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DpdkJniBackend implements INetworkBackend {

    static {
        try {
            System.loadLibrary("cpptrader_dpdk_jni");
            log.info("Loaded native library: cpptrader_dpdk_jni");
        } catch (UnsatisfiedLinkError e) {
            log.error("Failed to load native library cpptrader_dpdk_jni", e);
        }
    }

    private final String localIp;
    private final int localPort;
    private final String remoteIp;
    private final int remotePort;

    private volatile boolean initialized = false;

    public DpdkJniBackend(String localIp, int localPort, String remoteIp, int remotePort) {
        this.localIp = localIp;
        this.localPort = localPort;
        this.remoteIp = remoteIp;
        this.remotePort = remotePort;
    }

    @Override
    public boolean init() {
        try {
            boolean result = dpdkInit(localIp, localPort, remoteIp, remotePort);
            initialized = result;
            if (result) {
                log.info("DPDK JNI backend initialized: {}:{} -> {}:{}", localIp, localPort, remoteIp, remotePort);
            } else {
                log.error("DPDK JNI backend init failed");
            }
            return result;
        } catch (UnsatisfiedLinkError e) {
            log.error("DPDK JNI native method not available", e);
            return false;
        }
    }

    @Override
    public void send(byte[] data) {
        if (!initialized) {
            log.warn("DPDK JNI backend not initialized, cannot send");
            return;
        }
        try {
            dpdkSend(data);
        } catch (UnsatisfiedLinkError e) {
            log.error("DPDK JNI send failed", e);
        }
    }

    @Override
    public byte[] recv() {
        if (!initialized) {
            log.warn("DPDK JNI backend not initialized, cannot recv");
            return null;
        }
        try {
            return dpdkRecv();
        } catch (UnsatisfiedLinkError e) {
            log.error("DPDK JNI recv failed", e);
            return null;
        }
    }

    @Override
    public void close() {
        if (!initialized) {
            return;
        }
        try {
            dpdkClose();
            initialized = false;
            log.info("DPDK JNI backend closed");
        } catch (UnsatisfiedLinkError e) {
            log.error("DPDK JNI close failed", e);
        }
    }

    public boolean isInitialized() {
        return initialized;
    }

    public String getLocalIp() {
        return localIp;
    }

    public int getLocalPort() {
        return localPort;
    }

    public String getRemoteIp() {
        return remoteIp;
    }

    public int getRemotePort() {
        return remotePort;
    }

    private native boolean dpdkInit(String localIp, int localPort, String remoteIp, int remotePort);

    private native void dpdkSend(byte[] data);

    private native byte[] dpdkRecv();

    private native void dpdkClose();
}
