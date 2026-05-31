package com.cpptrader.marketdata.protocol;

public final class ProtocolConstants {

    private ProtocolConstants() {}

    public static final short MAGIC = (short) 0x5452;
    public static final byte VERSION = 1;
    public static final int HEADER_SIZE = 8;

    public static final byte SUBSCRIBE_ORDER_BOOK_REQ = 0x11;
    public static final byte SUBSCRIBE_ORDERS_REQ = 0x12;

    public static final byte SYMBOL_RESP = 0x41;
    public static final byte ORDER_BOOK_RESP = 0x42;
    public static final byte ORDER_RESP = 0x43;
    public static final byte SIMPLE_RESP = 0x44;

    public static final byte ORDER_BOOK_UPDATE_EVT = (byte) 0x81;
    public static final byte ORDER_UPDATE_EVT = (byte) 0x82;

    public static final byte HEARTBEAT_REQ = (byte) 0xC0;
    public static final byte HEARTBEAT_RESP = (byte) 0xC1;

    public static final byte FLAG_REQUEST = 0x01;
    public static final byte FLAG_RESPONSE = 0x02;
    public static final byte FLAG_PUSH = 0x04;
    public static final byte FLAG_ERROR = 0x08;
    public static final byte FLAG_HEARTBEAT = 0x10;

    public static final int SYMBOL_NAME_SIZE = 8;
    public static final int ORDER_PROTO_SIZE = 97;
    public static final int LEVEL_PROTO_SIZE = 32;
}
