package com.cpptrader.admin.protocol.responses;

import com.cpptrader.admin.protocol.ProtocolConstants;
import com.cpptrader.admin.protocol.ProtocolMessage;

import java.nio.ByteBuffer;

public class OrderResponse extends ProtocolMessage {

    private byte errorCode;
    private boolean hasOrder;
    private OrderHolder order = new OrderHolder();

    public OrderResponse() {
        super();
    }

    public OrderResponse(byte errorCode) {
        super(ProtocolConstants.ORDER_RESP, ProtocolConstants.FLAG_RESPONSE);
        this.errorCode = errorCode;
        this.hasOrder = (errorCode == ProtocolConstants.ErrorCode.OK);
    }

    @Override
    public int getBodySize() {
        return 1 + ProtocolConstants.ORDER_PROTO_SIZE;
    }

    @Override
    public void encode(ByteBuffer buf) {
        buf.put(errorCode);
        writeOrderProto(buf, order);
    }

    @Override
    public void decode(ByteBuffer buf) {
        this.errorCode = buf.get();
        this.hasOrder = (errorCode == ProtocolConstants.ErrorCode.OK);
        readOrderProto(buf, order);
    }

    public byte getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(byte errorCode) {
        this.errorCode = errorCode;
        this.hasOrder = (errorCode == ProtocolConstants.ErrorCode.OK);
    }

    public boolean isHasOrder() {
        return hasOrder;
    }

    public void setHasOrder(boolean hasOrder) {
        this.hasOrder = hasOrder;
    }

    public OrderHolder getOrder() {
        return order;
    }

    public void setOrder(OrderHolder order) {
        this.order = order;
    }
}
