package com.cpptrader.admin.protocol.requests;

import com.cpptrader.admin.protocol.ProtocolConstants;
import com.cpptrader.admin.protocol.ProtocolMessage;

import java.nio.ByteBuffer;

public class ReduceOrderRequest extends ProtocolMessage {

    private long id;
    private long quantity;

    public ReduceOrderRequest() {
        super();
    }

    public ReduceOrderRequest(long id, long quantity) {
        super(ProtocolConstants.REDUCE_ORDER_REQ, ProtocolConstants.FLAG_REQUEST);
        this.id = id;
        this.quantity = quantity;
    }

    @Override
    public int getBodySize() {
        return 16;
    }

    @Override
    public void encode(ByteBuffer buf) {
        buf.putLong(id);
        buf.putLong(quantity);
    }

    @Override
    public void decode(ByteBuffer buf) {
        this.id = buf.getLong();
        this.quantity = buf.getLong();
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public long getQuantity() {
        return quantity;
    }

    public void setQuantity(long quantity) {
        this.quantity = quantity;
    }
}
