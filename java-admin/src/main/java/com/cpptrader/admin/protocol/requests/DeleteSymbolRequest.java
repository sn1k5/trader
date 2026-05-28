package com.cpptrader.admin.protocol.requests;

import com.cpptrader.admin.protocol.ProtocolConstants;
import com.cpptrader.admin.protocol.ProtocolMessage;

import java.nio.ByteBuffer;

public class DeleteSymbolRequest extends ProtocolMessage {

    private int id;

    public DeleteSymbolRequest() {
        super();
    }

    public DeleteSymbolRequest(int id) {
        super(ProtocolConstants.DELETE_SYMBOL_REQ, ProtocolConstants.FLAG_REQUEST);
        this.id = id;
    }

    @Override
    public int getBodySize() {
        return 4;
    }

    @Override
    public void encode(ByteBuffer buf) {
        buf.putInt(id);
    }

    @Override
    public void decode(ByteBuffer buf) {
        this.id = buf.getInt();
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }
}
