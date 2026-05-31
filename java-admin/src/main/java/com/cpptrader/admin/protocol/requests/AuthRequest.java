package com.cpptrader.admin.protocol.requests;

import com.cpptrader.admin.protocol.ProtocolConstants;
import com.cpptrader.admin.protocol.security.HmacSigner;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.HexFormat;

public class AuthRequest {

    private final String apiKeyId;
    private final long timestampMs;
    private final byte[] nonce;
    private final byte[] signature;

    public AuthRequest(String apiKeyId, String apiKeySecret) {
        this.apiKeyId = apiKeyId;
        this.timestampMs = System.currentTimeMillis();
        this.nonce = generateNonce();
        this.signature = HmacSigner.computeAuthSignature(
                apiKeySecret.getBytes(StandardCharsets.UTF_8), timestampMs, nonce, apiKeyId);
    }

    private static byte[] generateNonce() {
        SecureRandom random = new SecureRandom();
        byte[] nonce = new byte[ProtocolConstants.AUTH_NONCE_SIZE];
        random.nextBytes(nonce);
        return nonce;
    }

    public byte[] toBytes() {
        ByteBuffer buf = ByteBuffer.allocate(ProtocolConstants.AUTH_REQUEST_BODY_SIZE);
        buf.order(ByteOrder.LITTLE_ENDIAN);

        byte[] apiKeyIdBytes = new byte[ProtocolConstants.AUTH_API_KEY_ID_SIZE];
        byte[] apiKeyIdSrc = apiKeyId.getBytes(StandardCharsets.UTF_8);
        int copyLen = Math.min(apiKeyIdSrc.length, ProtocolConstants.AUTH_API_KEY_ID_SIZE);
        System.arraycopy(apiKeyIdSrc, 0, apiKeyIdBytes, 0, copyLen);
        buf.put(apiKeyIdBytes);

        buf.putLong(timestampMs);

        buf.put(nonce);

        buf.put(signature);

        return buf.array();
    }

    public String getApiKeyId() {
        return apiKeyId;
    }

    public long getTimestampMs() {
        return timestampMs;
    }

    public byte[] getNonce() {
        return nonce.clone();
    }

    public byte[] getSignature() {
        return signature.clone();
    }

    public String getNonceHex() {
        return HexFormat.of().formatHex(nonce);
    }

    public String getSignatureHex() {
        return HexFormat.of().formatHex(signature);
    }
}
