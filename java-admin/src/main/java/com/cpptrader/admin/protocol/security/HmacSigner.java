package com.cpptrader.admin.protocol.security;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

public final class HmacSigner {

    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private HmacSigner() {}

    public static short computeHmacPrefix(byte[] sessionKey, int sequence, byte msgType, byte flags, short length, byte[] body) {
        byte[] full = computeFullHmac(sessionKey, sequence, msgType, flags, length, body);
        return (short) ((full[0] & 0xFF) | ((full[1] & 0xFF) << 8));
    }

    public static byte[] computeFullHmac(byte[] sessionKey, int sequence, byte msgType, byte flags, short length, byte[] body) {
        byte[] input = buildSignInput(sequence, msgType, flags, length, body);
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            SecretKeySpec keySpec = new SecretKeySpec(sessionKey, HMAC_ALGORITHM);
            mac.init(keySpec);
            return mac.doFinal(input);
        } catch (Exception e) {
            throw new RuntimeException("Failed to compute HMAC signature", e);
        }
    }

    public static boolean verifyHmacPrefix(byte[] sessionKey, short expectedPrefix, int sequence, byte msgType, byte flags, short length, byte[] body) {
        short computed = computeHmacPrefix(sessionKey, sequence, msgType, flags, length, body);
        return computed == expectedPrefix;
    }

    static byte[] buildSignInput(int sequence, byte msgType, byte flags, short length, byte[] body) {
        int bodyLen = (body != null) ? body.length : 0;
        ByteBuffer buf = ByteBuffer.allocate(8 + bodyLen);
        buf.order(ByteOrder.LITTLE_ENDIAN);
        buf.putInt(sequence);
        buf.put(msgType);
        buf.put(flags);
        buf.putShort(length);
        if (body != null && bodyLen > 0) {
            buf.put(body);
        }
        return buf.array();
    }
}
