package com.cpptrader.admin.protocol.requests;

import com.cpptrader.admin.protocol.ProtocolConstants;
import com.cpptrader.admin.protocol.ProtocolMessage;

import java.nio.ByteBuffer;

public class AddSymbolRequest extends ProtocolMessage {

    private int id;
    private String name;

    public AddSymbolRequest() {
        super();
    }

    public AddSymbolRequest(int id, String name) {
        super(ProtocolConstants.ADD_SYMBOL_REQ, ProtocolConstants.FLAG_REQUEST);
        this.id = id;
        this.name = name;
    }

    @Override
    public int getBodySize() {
        return ProtocolConstants.SYMBOL_PROTO_SIZE;
    }

    @Override
    public void encode(ByteBuffer buf) {
        writeSymbolProto(buf, id, name);
    }

    @Override
    public void decode(ByteBuffer buf) {
        SymbolHolder holder = new SymbolHolder();
        readSymbolProto(buf, holder);
        this.id = holder.id;
        this.name = holder.name;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
