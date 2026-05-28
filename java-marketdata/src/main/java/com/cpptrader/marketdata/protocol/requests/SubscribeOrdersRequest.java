package com.cpptrader.marketdata.protocol.requests;

import com.cpptrader.marketdata.protocol.ProtocolConstants;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class SubscribeOrdersRequest {

    private final int symbolId;

    public SubscribeOrdersRequest(int symbolId) {
        this.symbolId = symbolId;
    }

    public byte[] toBytes() {
        ByteBuffer buf = ByteBuffer.allocate(ProtocolConstants.HEADER_SIZE + 4);
        buf.order(ByteOrder.LITTLE_ENDIAN);
        buf.putShort(ProtocolConstants.MAGIC);
        buf.put(ProtocolConstants.VERSION);
        buf.put(ProtocolConstants.SUBSCRIBE_ORDERS_REQ);
        buf.put(ProtocolConstants.FLAG_REQUEST);
        buf.put((byte) 0);
        buf.putShort((short) 4);
        buf.putInt(symbolId);
        return buf.array();
    }
}
