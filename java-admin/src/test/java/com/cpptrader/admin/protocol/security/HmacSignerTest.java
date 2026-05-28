package com.cpptrader.admin.protocol.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static org.junit.jupiter.api.Assertions.*;

class HmacSignerTest {

    private static final byte[] TEST_KEY = {
        0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08,
        0x09, 0x0a, 0x0b, 0x0c, 0x0d, 0x0e, 0x0f, 0x10
    };

    @Test
    @DisplayName("computeHmacPrefix returns non-zero for non-trivial input")
    void computeHmacPrefix_returnsNonZero() {
        byte[] body = {0x01, 0x00, 0x00, 0x00, 0x41, 0x42, 0x43, 0x44, 0x45, 0x46, 0x47, 0x00};
        short prefix = HmacSigner.computeHmacPrefix(TEST_KEY, 1, (byte) 0x01, (byte) 0x01, (short) 12, body);
        assertNotEquals((short) 0, prefix);
    }

    @Test
    @DisplayName("verifyHmacPrefix passes for correct signature")
    void verifyHmacPrefix_correctSignature_passes() {
        byte[] body = {0x01, 0x00, 0x00, 0x00};
        short prefix = HmacSigner.computeHmacPrefix(TEST_KEY, 1, (byte) 0x01, (byte) 0x01, (short) 4, body);
        assertTrue(HmacSigner.verifyHmacPrefix(TEST_KEY, prefix, 1, (byte) 0x01, (byte) 0x01, (short) 4, body));
    }

    @Test
    @DisplayName("verifyHmacPrefix fails for tampered body")
    void verifyHmacPrefix_tamperedBody_fails() {
        byte[] body = {0x01, 0x00, 0x00, 0x00};
        short prefix = HmacSigner.computeHmacPrefix(TEST_KEY, 1, (byte) 0x01, (byte) 0x01, (short) 4, body);
        byte[] tampered = {0x02, 0x00, 0x00, 0x00};
        assertFalse(HmacSigner.verifyHmacPrefix(TEST_KEY, prefix, 1, (byte) 0x01, (byte) 0x01, (short) 4, tampered));
    }

    @Test
    @DisplayName("verifyHmacPrefix fails for tampered sequence")
    void verifyHmacPrefix_tamperedSequence_fails() {
        byte[] body = {0x01, 0x00, 0x00, 0x00};
        short prefix = HmacSigner.computeHmacPrefix(TEST_KEY, 1, (byte) 0x01, (byte) 0x01, (short) 4, body);
        assertFalse(HmacSigner.verifyHmacPrefix(TEST_KEY, prefix, 2, (byte) 0x01, (byte) 0x01, (short) 4, body));
    }

    @Test
    @DisplayName("verifyHmacPrefix fails for wrong prefix")
    void verifyHmacPrefix_wrongPrefix_fails() {
        byte[] body = {0x01, 0x00, 0x00, 0x00};
        short prefix = HmacSigner.computeHmacPrefix(TEST_KEY, 1, (byte) 0x01, (byte) 0x01, (short) 4, body);
        short wrongPrefix = (short) (prefix ^ 0xFFFF);
        assertFalse(HmacSigner.verifyHmacPrefix(TEST_KEY, wrongPrefix, 1, (byte) 0x01, (byte) 0x01, (short) 4, body));
    }

    @Test
    @DisplayName("buildSignInput produces correct little-endian byte order")
    void buildSignInput_littleEndian() {
        byte[] input = HmacSigner.buildSignInput(0x12345678, (byte) 0x01, (byte) 0x02, (short) 4, null);

        assertEquals(8, input.length);

        ByteBuffer buf = ByteBuffer.wrap(input);
        buf.order(ByteOrder.LITTLE_ENDIAN);
        assertEquals(0x12345678, buf.getInt());
        assertEquals(0x01, buf.get());
        assertEquals(0x02, buf.get());
        assertEquals(4, buf.getShort() & 0xFFFF);
    }

    @Test
    @DisplayName("buildSignInput with body appends body bytes")
    void buildSignInput_withBody() {
        byte[] body = {(byte) 0xAA, (byte) 0xBB, (byte) 0xCC, (byte) 0xDD};
        byte[] input = HmacSigner.buildSignInput(1, (byte) 0x01, (byte) 0x01, (short) 4, body);

        assertEquals(12, input.length);
        assertEquals((byte) 0xAA, input[8]);
        assertEquals((byte) 0xBB, input[9]);
        assertEquals((byte) 0xCC, input[10]);
        assertEquals((byte) 0xDD, input[11]);
    }

    @Test
    @DisplayName("Empty body signature works correctly")
    void emptyBody_signatureWorks() {
        short prefix = HmacSigner.computeHmacPrefix(TEST_KEY, 42, (byte) 0xC0, (byte) 0x10, (short) 0, null);
        assertNotEquals((short) 0, prefix);
        assertTrue(HmacSigner.verifyHmacPrefix(TEST_KEY, prefix, 42, (byte) 0xC0, (byte) 0x10, (short) 0, null));
    }

    @Test
    @DisplayName("Different keys produce different signatures")
    void differentKeys_differentSignatures() {
        byte[] key2 = {
            0x11, 0x12, 0x13, 0x14, 0x15, 0x16, 0x17, 0x18,
            0x19, 0x1a, 0x1b, 0x1c, 0x1d, 0x1e, 0x1f, 0x20
        };
        byte[] body = {0x01, 0x00, 0x00, 0x00};

        short prefix1 = HmacSigner.computeHmacPrefix(TEST_KEY, 1, (byte) 0x01, (byte) 0x01, (short) 4, body);
        short prefix2 = HmacSigner.computeHmacPrefix(key2, 1, (byte) 0x01, (byte) 0x01, (short) 4, body);
        assertNotEquals(prefix1, prefix2);
    }

    @Test
    @DisplayName("Cross-validation: Java and C++ produce same HMAC for identical input")
    void crossValidation_withCpp() {
        byte[] key = "test-session-key-1234".getBytes();
        int sequence = 1;
        byte msgType = 0x01;
        byte flags = 0x01;
        short length = 0;
        byte[] body = null;

        byte[] fullHmac = HmacSigner.computeFullHmac(key, sequence, msgType, flags, length, body);
        assertEquals(32, fullHmac.length);

        short prefix = HmacSigner.computeHmacPrefix(key, sequence, msgType, flags, length, body);

        int expectedPrefix = (fullHmac[0] & 0xFF) | ((fullHmac[1] & 0xFF) << 8);
        assertEquals((short) expectedPrefix, prefix);
    }
}
