package com.cpptrader.admin.protocol.requests;

import com.cpptrader.admin.protocol.ProtocolConstants;
import com.cpptrader.admin.protocol.ProtocolMessage;

import java.nio.ByteBuffer;

public class SubscribeOrdersRequest extends ProtocolMessage {

    private int symbolId;

    public SubscribeOrdersRequest() {
        super();
    }

    public SubscribeOrdersRequest(int symbolId) {
        super(ProtocolConstants.SUBSCRIBE_ORDERS_REQ, ProtocolConstants.FLAG_REQUEST);
        this.symbolId = symbolId;
    }

    @Override
    public int getBodySize() {
        return 4;
    }

    @Override
    public void encode(ByteBuffer buf) {
        buf.putInt(symbolId);
    }

    @Override
    public void decode(ByteBuffer buf) {
        this.symbolId = buf.getInt();
    }

    public int getSymbolId() {
        return symbolId;
    }

    public void setSymbolId(int symbolId) {
        this.symbolId = symbolId;
    }
}
