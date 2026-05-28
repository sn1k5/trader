package com.cpptrader.admin.protocol.requests;

import com.cpptrader.admin.protocol.ProtocolConstants;
import com.cpptrader.admin.protocol.ProtocolMessage;

import java.nio.ByteBuffer;

public class ExecuteOrderRequest extends ProtocolMessage {

    private long id;
    private long price;
    private long quantity;

    public ExecuteOrderRequest() {
        super();
    }

    public ExecuteOrderRequest(long id, long price, long quantity) {
        super(ProtocolConstants.EXECUTE_ORDER_REQ, ProtocolConstants.FLAG_REQUEST);
        this.id = id;
        this.price = price;
        this.quantity = quantity;
    }

    @Override
    public int getBodySize() {
        return 24;
    }

    @Override
    public void encode(ByteBuffer buf) {
        buf.putLong(id);
        buf.putLong(price);
        buf.putLong(quantity);
    }

    @Override
    public void decode(ByteBuffer buf) {
        this.id = buf.getLong();
        this.price = buf.getLong();
        this.quantity = buf.getLong();
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public long getPrice() {
        return price;
    }

    public void setPrice(long price) {
        this.price = price;
    }

    public long getQuantity() {
        return quantity;
    }

    public void setQuantity(long quantity) {
        this.quantity = quantity;
    }
}
