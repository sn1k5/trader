package com.cpptrader.admin.protocol.requests;

import com.cpptrader.admin.protocol.ProtocolConstants;
import com.cpptrader.admin.protocol.ProtocolMessage;

import java.nio.ByteBuffer;

public class GetSymbolRequest extends ProtocolMessage {

    private int id;

    public GetSymbolRequest() {
        super();
    }

    public GetSymbolRequest(int id) {
        super(ProtocolConstants.GET_SYMBOL_REQ, ProtocolConstants.FLAG_REQUEST);
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
