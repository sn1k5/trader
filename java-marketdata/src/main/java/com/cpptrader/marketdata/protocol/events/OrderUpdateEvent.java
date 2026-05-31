package com.cpptrader.marketdata.protocol.events;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class OrderUpdateEvent {

    private int action;
    private OrderData order = new OrderData();
    private long executePrice;
    private long executeQuantity;

    public void decode(byte[] data) {
        if (data == null || data.length < ProtocolConstants.HEADER_SIZE + 114) {
            return;
        }
        ByteBuffer buf = ByteBuffer.wrap(data);
        buf.order(ByteOrder.LITTLE_ENDIAN);
        buf.position(ProtocolConstants.HEADER_SIZE);
        action = buf.get() & 0xFF;
        order.id = buf.getLong();
        order.symbolId = buf.getInt();
        order.accountId = buf.getLong();
        order.orderType = buf.get() & 0xFF;
        order.orderSide = buf.get() & 0xFF;
        order.price = buf.getLong();
        order.stopPrice = buf.getLong();
        order.quantity = buf.getLong();
        order.executedQuantity = buf.getLong();
        order.leavesQuantity = buf.getLong();
        order.timeInForce = buf.get() & 0xFF;
        buf.get();
        order.stpPolicy = buf.get() & 0xFF;
        order.maxVisibleQuantity = buf.getLong();
        order.slippage = buf.getLong();
        order.trailingDistance = buf.getLong();
        order.trailingStep = buf.getLong();
        executePrice = buf.getLong();
        executeQuantity = buf.getLong();
    }

    public int getAction() { return action; }
    public OrderData getOrder() { return order; }
    public long getExecutePrice() { return executePrice; }
    public long getExecuteQuantity() { return executeQuantity; }

    public String getActionName() {
        return switch (action) {
            case 1 -> "ADD";
            case 2 -> "UPDATE";
            case 3 -> "DELETE";
            case 4 -> "EXECUTE";
            default -> "UNKNOWN";
        };
    }

    public boolean isExecution() {
        return action == 4;
    }

    public static class OrderData {
        public long id;
        public int symbolId;
        public long accountId;
        public int orderType;
        public int orderSide;
        public long price;
        public long stopPrice;
        public long quantity;
        public long executedQuantity;
        public long leavesQuantity;
        public int timeInForce;
        public int stpPolicy;
        public long maxVisibleQuantity;
        public long slippage;
        public long trailingDistance;
        public long trailingStep;
    }
}
