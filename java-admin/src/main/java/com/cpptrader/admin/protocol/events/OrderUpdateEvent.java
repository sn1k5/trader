package com.cpptrader.admin.protocol.events;

import com.cpptrader.admin.protocol.ProtocolConstants;
import com.cpptrader.admin.protocol.ProtocolMessage;

import java.nio.ByteBuffer;

public class OrderUpdateEvent extends ProtocolMessage {

    private byte action;
    private OrderHolder order = new OrderHolder();
    private long executePrice;
    private long executeQuantity;

    public OrderUpdateEvent() {
        super();
    }

    public OrderUpdateEvent(byte action, OrderHolder order,
                            long executePrice, long executeQuantity) {
        super(ProtocolConstants.ORDER_UPDATE_EVT, ProtocolConstants.FLAG_PUSH);
        this.action = action;
        this.order = order;
        this.executePrice = executePrice;
        this.executeQuantity = executeQuantity;
    }

    @Override
    public int getBodySize() {
        return 1 + ProtocolConstants.ORDER_PROTO_SIZE + 8 + 8;
    }

    @Override
    public void encode(ByteBuffer buf) {
        buf.put(action);
        writeOrderProto(buf, order);
        buf.putLong(executePrice);
        buf.putLong(executeQuantity);
    }

    @Override
    public void decode(ByteBuffer buf) {
        this.action = buf.get();
        readOrderProto(buf, order);
        this.executePrice = buf.getLong();
        this.executeQuantity = buf.getLong();
    }

    public byte getAction() {
        return action;
    }

    public String getActionName() {
        return ProtocolConstants.ActionType.name(action);
    }

    public void setAction(byte action) {
        this.action = action;
    }

    public OrderHolder getOrder() {
        return order;
    }

    public void setOrder(OrderHolder order) {
        this.order = order;
    }

    public long getExecutePrice() {
        return executePrice;
    }

    public void setExecutePrice(long executePrice) {
        this.executePrice = executePrice;
    }

    public long getExecuteQuantity() {
        return executeQuantity;
    }

    public void setExecuteQuantity(long executeQuantity) {
        this.executeQuantity = executeQuantity;
    }
}
