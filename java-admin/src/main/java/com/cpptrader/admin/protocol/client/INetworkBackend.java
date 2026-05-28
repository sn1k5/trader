package com.cpptrader.admin.protocol.client;

public interface INetworkBackend {

    boolean init();

    void send(byte[] data);

    byte[] recv();

    void close();
}
