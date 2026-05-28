package com.cpptrader.admin.protocol.requests;

import com.cpptrader.admin.protocol.ProtocolConstants;
import com.cpptrader.admin.protocol.ProtocolMessage;

import java.nio.ByteBuffer;

public class EnableMatchingRequest extends ProtocolMessage {

    public EnableMatchingRequest() {
        super(ProtocolConstants.ENABLE_MATCHING_REQ, ProtocolConstants.FLAG_REQUEST);
    }

    @Override
    public int getBodySize() {
        return 0;
    }

    @Override
    public void encode(ByteBuffer buf) {
    }

    @Override
    public void decode(ByteBuffer buf) {
    }
}
