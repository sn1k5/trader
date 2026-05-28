package com.cpptrader.admin.protocol.requests;

import com.cpptrader.admin.protocol.ProtocolConstants;
import com.cpptrader.admin.protocol.ProtocolMessage;

import java.nio.ByteBuffer;

public class MitigateOrderRequest extends ProtocolMessage {

    private long id;
    private long newPrice;
    private long newQuantity;

    public MitigateOrderRequest() {
        super();
    }

    public MitigateOrderRequest(long id, long newPrice, long newQuantity) {
        super(ProtocolConstants.MITIGATE_ORDER_REQ, ProtocolConstants.FLAG_REQUEST);
        this.id = id;
        this.newPrice = newPrice;
        this.newQuantity = newQuantity;
    }

    @Override
    public int getBodySize() {
        return 24;
    }

    @Override
    public void encode(ByteBuffer buf) {
        buf.putLong(id);
        buf.putLong(newPrice);
        buf.putLong(newQuantity);
    }

    @Override
    public void decode(ByteBuffer buf) {
        this.id = buf.getLong();
        this.newPrice = buf.getLong();
        this.newQuantity = buf.getLong();
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public long getNewPrice() {
        return newPrice;
    }

    public void setNewPrice(long newPrice) {
        this.newPrice = newPrice;
    }

    public long getNewQuantity() {
        return newQuantity;
    }

    public void setNewQuantity(long newQuantity) {
        this.newQuantity = newQuantity;
    }
}
