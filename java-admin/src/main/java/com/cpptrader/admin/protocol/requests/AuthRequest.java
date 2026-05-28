package com.cpptrader.admin.protocol.requests;

import com.cpptrader.admin.protocol.ProtocolConstants;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.HexFormat;

public class AuthRequest {

    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final String apiKeyId;
    private final long timestampMs;
    private final byte[] nonce;
    private final byte[] signature;

    public AuthRequest(String apiKeyId, String apiKeySecret) {
        this.apiKeyId = apiKeyId;
        this.timestampMs = System.currentTimeMillis();
        this.nonce = generateNonce();
        this.signature = computeSignature(apiKeySecret, timestampMs, nonce, apiKeyId);
    }

    private static byte[] generateNonce() {
        SecureRandom random = new SecureRandom();
        byte[] nonce = new byte[ProtocolConstants.AUTH_NONCE_SIZE];
        random.nextBytes(nonce);
        return nonce;
    }

    private static byte[] computeSignature(String secret, long timestampMs, byte[] nonce, String apiKeyId) {
        String message = buildSignatureMessage(timestampMs, nonce, apiKeyId);
        
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            SecretKeySpec keySpec = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM);
            mac.init(keySpec);
            return mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new RuntimeException("Failed to compute HMAC signature", e);
        }
    }

    private static String buildSignatureMessage(long timestampMs, byte[] nonce, String apiKeyId) {
        StringBuilder sb = new StringBuilder();
        
        String timestampHex = String.format("%016x", timestampMs);
        sb.append(timestampHex);
        
        String nonceHex = HexFormat.of().formatHex(nonce);
        sb.append(nonceHex);
        
        sb.append(apiKeyId);
        
        return sb.toString();
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
