package com.cpptrader.admin.protocol.responses;

import com.cpptrader.admin.protocol.ProtocolConstants;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class AuthResponse {

    private byte error;
    private byte[] sessionToken;

    public AuthResponse() {
        this.sessionToken = new byte[ProtocolConstants.AUTH_SESSION_TOKEN_SIZE];
    }

    public static AuthResponse fromBytes(byte[] body) {
        if (body == null || body.length < ProtocolConstants.AUTH_RESPONSE_BODY_SIZE) {
            throw new IllegalArgumentException("AuthResponse body must be at least " + 
                ProtocolConstants.AUTH_RESPONSE_BODY_SIZE + " bytes");
        }

        ByteBuffer buf = ByteBuffer.wrap(body);
        buf.order(ByteOrder.LITTLE_ENDIAN);

        AuthResponse response = new AuthResponse();
        response.error = buf.get();
        buf.get(response.sessionToken);

        return response;
    }

    public boolean isSuccess() {
        return error == ProtocolConstants.ErrorCode.OK;
    }

    public byte getError() {
        return error;
    }

    public void setError(byte error) {
        this.error = error;
    }

    public byte[] getSessionToken() {
        return sessionToken.clone();
    }

    public String getSessionTokenHex() {
        StringBuilder sb = new StringBuilder();
        for (byte b : sessionToken) {
            sb.append(String.format("%02x", b & 0xFF));
        }
        return sb.toString();
    }

    public String getErrorName() {
        return ProtocolConstants.ErrorCode.name(error);
    }

    @Override
    public String toString() {
        if (isSuccess()) {
            return String.format("AuthResponse[success=true, sessionToken=%s]", getSessionTokenHex());
        } else {
            return String.format("AuthResponse[success=false, error=%d (%s)]", 
                error & 0xFF, getErrorName());
        }
    }
}
