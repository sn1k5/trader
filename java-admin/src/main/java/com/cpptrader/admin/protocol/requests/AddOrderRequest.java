package com.cpptrader.admin.protocol.requests;

import com.cpptrader.admin.protocol.ProtocolConstants;
import com.cpptrader.admin.protocol.ProtocolMessage;

import java.nio.ByteBuffer;

public class AddOrderRequest extends ProtocolMessage {

    private long id;
    private int symbolId;
    private byte orderType;
    private byte orderSide;
    private long price;
    private long stopPrice;
    private long quantity;
    private byte timeInForce;
    private long maxVisibleQuantity;
    private long slippage;
    private long trailingDistance;
    private long trailingStep;

    public AddOrderRequest() {
        super();
    }

    public AddOrderRequest(long id, int symbolId, byte orderType, byte orderSide,
                           long price, long stopPrice, long quantity, byte timeInForce,
                           long maxVisibleQuantity, long slippage, long trailingDistance, long trailingStep) {
        super(ProtocolConstants.ADD_ORDER_REQ, ProtocolConstants.FLAG_REQUEST);
        this.id = id;
        this.symbolId = symbolId;
        this.orderType = orderType;
        this.orderSide = orderSide;
        this.price = price;
        this.stopPrice = stopPrice;
        this.quantity = quantity;
        this.timeInForce = timeInForce;
        this.maxVisibleQuantity = maxVisibleQuantity;
        this.slippage = slippage;
        this.trailingDistance = trailingDistance;
        this.trailingStep = trailingStep;
    }

    @Override
    public int getBodySize() {
        return ProtocolConstants.ORDER_PROTO_SIZE;
    }

    @Override
    public void encode(ByteBuffer buf) {
        OrderHolder o = new OrderHolder();
        o.id = id;
        o.symbolId = symbolId;
        o.orderType = orderType;
        o.orderSide = orderSide;
        o.price = price;
        o.stopPrice = stopPrice;
        o.quantity = quantity;
        o.executedQuantity = 0;
        o.leavesQuantity = quantity;
        o.timeInForce = timeInForce;
        o.maxVisibleQuantity = maxVisibleQuantity;
        o.slippage = slippage;
        o.trailingDistance = trailingDistance;
        o.trailingStep = trailingStep;
        writeOrderProto(buf, o);
    }

    @Override
    public void decode(ByteBuffer buf) {
        OrderHolder o = new OrderHolder();
        readOrderProto(buf, o);
        this.id = o.id;
        this.symbolId = o.symbolId;
        this.orderType = o.orderType;
        this.orderSide = o.orderSide;
        this.price = o.price;
        this.stopPrice = o.stopPrice;
        this.quantity = o.quantity;
        this.timeInForce = o.timeInForce;
        this.maxVisibleQuantity = o.maxVisibleQuantity;
        this.slippage = o.slippage;
        this.trailingDistance = o.trailingDistance;
        this.trailingStep = o.trailingStep;
    }

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public int getSymbolId() { return symbolId; }
    public void setSymbolId(int symbolId) { this.symbolId = symbolId; }

    public byte getOrderType() { return orderType; }
    public void setOrderType(byte orderType) { this.orderType = orderType; }

    public byte getOrderSide() { return orderSide; }
    public void setOrderSide(byte orderSide) { this.orderSide = orderSide; }

    public long getPrice() { return price; }
    public void setPrice(long price) { this.price = price; }

    public long getStopPrice() { return stopPrice; }
    public void setStopPrice(long stopPrice) { this.stopPrice = stopPrice; }

    public long getQuantity() { return quantity; }
    public void setQuantity(long quantity) { this.quantity = quantity; }

    public byte getTimeInForce() { return timeInForce; }
    public void setTimeInForce(byte timeInForce) { this.timeInForce = timeInForce; }

    public long getMaxVisibleQuantity() { return maxVisibleQuantity; }
    public void setMaxVisibleQuantity(long maxVisibleQuantity) { this.maxVisibleQuantity = maxVisibleQuantity; }

    public long getSlippage() { return slippage; }
    public void setSlippage(long slippage) { this.slippage = slippage; }

    public long getTrailingDistance() { return trailingDistance; }
    public void setTrailingDistance(long trailingDistance) { this.trailingDistance = trailingDistance; }

    public long getTrailingStep() { return trailingStep; }
    public void setTrailingStep(long trailingStep) { this.trailingStep = trailingStep; }
}
