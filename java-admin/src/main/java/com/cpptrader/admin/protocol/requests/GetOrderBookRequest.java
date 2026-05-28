package com.cpptrader.admin.protocol.requests;

import com.cpptrader.admin.protocol.ProtocolConstants;
import com.cpptrader.admin.protocol.ProtocolMessage;

import java.nio.ByteBuffer;

public class GetOrderBookRequest extends ProtocolMessage {

    private int symbolId;
    private int depth;

    public GetOrderBookRequest() {
        super();
    }

    public GetOrderBookRequest(int symbolId, int depth) {
        super(ProtocolConstants.GET_ORDER_BOOK_REQ, ProtocolConstants.FLAG_REQUEST);
        this.symbolId = symbolId;
        this.depth = depth;
    }

    @Override
    public int getBodySize() {
        return 8;
    }

    @Override
    public void encode(ByteBuffer buf) {
        buf.putInt(symbolId);
        buf.putInt(depth);
    }

    @Override
    public void decode(ByteBuffer buf) {
        this.symbolId = buf.getInt();
        this.depth = buf.getInt();
    }

    public int getSymbolId() {
        return symbolId;
    }

    public void setSymbolId(int symbolId) {
        this.symbolId = symbolId;
    }

    public int getDepth() {
        return depth;
    }

    public void setDepth(int depth) {
        this.depth = depth;
    }
}
