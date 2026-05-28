package com.cpptrader.admin.protocol.requests;

import com.cpptrader.admin.protocol.ProtocolConstants;
import com.cpptrader.admin.protocol.ProtocolMessage;

import java.nio.ByteBuffer;

public class DeleteOrderRequest extends ProtocolMessage {

    private long id;

    public DeleteOrderRequest() {
        super();
    }

    public DeleteOrderRequest(long id) {
        super(ProtocolConstants.DELETE_ORDER_REQ, ProtocolConstants.FLAG_REQUEST);
        this.id = id;
    }

    @Override
    public int getBodySize() {
        return 8;
    }

    @Override
    public void encode(ByteBuffer buf) {
        buf.putLong(id);
    }

    @Override
    public void decode(ByteBuffer buf) {
        this.id = buf.getLong();
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }
}
