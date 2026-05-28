package com.cpptrader.admin.protocol.responses;

import com.cpptrader.admin.protocol.ProtocolConstants;
import com.cpptrader.admin.protocol.ProtocolMessage;

import java.nio.ByteBuffer;

public class SimpleResponse extends ProtocolMessage {

    private byte errorCode;

    public SimpleResponse() {
        super();
    }

    public SimpleResponse(byte errorCode) {
        super(ProtocolConstants.SIMPLE_RESP, ProtocolConstants.FLAG_RESPONSE);
        this.errorCode = errorCode;
    }

    @Override
    public int getBodySize() {
        return 1;
    }

    @Override
    public void encode(ByteBuffer buf) {
        buf.put(errorCode);
    }

    @Override
    public void decode(ByteBuffer buf) {
        this.errorCode = buf.get();
    }

    public byte getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(byte errorCode) {
        this.errorCode = errorCode;
    }
}
