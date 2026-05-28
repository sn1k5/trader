package com.cpptrader.admin.protocol.responses;

import com.cpptrader.admin.protocol.ProtocolConstants;
import com.cpptrader.admin.protocol.ProtocolMessage;

import java.nio.ByteBuffer;

public class SymbolResponse extends ProtocolMessage {

    private byte errorCode;
    private boolean hasSymbol;
    private int symbolId;
    private String symbolName;

    public SymbolResponse() {
        super();
    }

    public SymbolResponse(byte errorCode, int symbolId, String symbolName) {
        super(ProtocolConstants.SYMBOL_RESP, ProtocolConstants.FLAG_RESPONSE);
        this.errorCode = errorCode;
        this.hasSymbol = (errorCode == ProtocolConstants.ErrorCode.OK);
        this.symbolId = symbolId;
        this.symbolName = symbolName;
    }

    @Override
    public int getBodySize() {
        return 1 + ProtocolConstants.SYMBOL_PROTO_SIZE;
    }

    @Override
    public void encode(ByteBuffer buf) {
        buf.put(errorCode);
        writeSymbolProto(buf, symbolId, symbolName);
    }

    @Override
    public void decode(ByteBuffer buf) {
        this.errorCode = buf.get();
        SymbolHolder holder = new SymbolHolder();
        readSymbolProto(buf, holder);
        this.symbolId = holder.id;
        this.symbolName = holder.name;
        this.hasSymbol = (errorCode == ProtocolConstants.ErrorCode.OK);
    }

    public byte getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(byte errorCode) {
        this.errorCode = errorCode;
    }

    public boolean isHasSymbol() {
        return hasSymbol;
    }

    public void setHasSymbol(boolean hasSymbol) {
        this.hasSymbol = hasSymbol;
    }

    public int getSymbolId() {
        return symbolId;
    }

    public void setSymbolId(int symbolId) {
        this.symbolId = symbolId;
    }

    public String getSymbolName() {
        return symbolName;
    }

    public void setSymbolName(String symbolName) {
        this.symbolName = symbolName;
    }
}
