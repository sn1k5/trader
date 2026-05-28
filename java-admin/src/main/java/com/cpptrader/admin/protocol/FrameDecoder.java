package com.cpptrader.admin.protocol;

import com.cpptrader.admin.protocol.exception.ProtocolErrorHandler;
import com.cpptrader.admin.protocol.exception.ProtocolException;
import com.cpptrader.admin.protocol.security.HmacSigner;
import lombok.extern.slf4j.Slf4j;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

@Slf4j
public class FrameDecoder {

    public enum State {
        HEAD,
        BODY
    }

    private State state = State.HEAD;
    private final byte[] headerBuffer = new byte[ProtocolConstants.HEADER_SIZE];
    private int headerReceived = 0;
    private int bodyNeeded = 0;
    private byte[] bodyBuffer;
    private int bodyReceived = 0;

    private short pendingMagic;
    private byte pendingVersion;
    private byte pendingMsgType;
    private byte pendingFlags;
    private byte pendingReserved;
    private int pendingSequence;
    private short pendingHmacPrefix;

    private ProtocolErrorHandler errorHandler;
    private int maxFrameSize = 64 * 1024 * 1024;
    private long frameCount = 0;
    private long errorCount = 0;
    private List<byte[]> completedFrames = new ArrayList<>();
    private volatile byte[] sessionKey = null;
    private HmacVerificationCallback hmacCallback = null;

    public interface HmacVerificationCallback {
        void onHmacVerificationFailed(short hmacPrefix, int sequence, byte msgType);
    }

    public FrameDecoder() {
        this.errorHandler = ProtocolErrorHandler.createDefault();
    }

    public FrameDecoder(ProtocolErrorHandler errorHandler) {
        this.errorHandler = errorHandler;
    }

    public void setSessionKey(byte[] key) {
        this.sessionKey = key;
    }

    public void clearSessionKey() {
        this.sessionKey = null;
    }

    public void setHmacVerificationCallback(HmacVerificationCallback callback) {
        this.hmacCallback = callback;
    }

    public void feed(byte[] data, int offset, int len) {
        if (data == null || len <= 0) {
            return;
        }

        int actualEnd = Math.min(offset + len, data.length);
        int pos = offset;
        int end = actualEnd;

        log.trace("[FRAME_DECODER] Feeding {} bytes, starting at state: {}", len, state);

        while (pos < end) {
            if (state == State.HEAD) {
                int toRead = Math.min(ProtocolConstants.HEADER_SIZE - headerReceived, end - pos);
                System.arraycopy(data, pos, headerBuffer, headerReceived, toRead);
                headerReceived += toRead;
                pos += toRead;

                if (headerReceived == ProtocolConstants.HEADER_SIZE) {
                    log.trace("[FRAME_DECODER] Complete header received");
                    if (parseHeader()) {
                        state = State.BODY;
                        bodyReceived = 0;
                        if (bodyNeeded > 0) {
                            if (bodyNeeded > maxFrameSize) {
                                log.warn("[FRAME_DECODER] Frame body size {} exceeds max {}, rejecting", bodyNeeded, maxFrameSize);
                                state = State.HEAD;
                                headerReceived = 0;
                                errorCount++;
                                continue;
                            }
                            bodyBuffer = new byte[bodyNeeded];
                            log.trace("[FRAME_DECODER] Expecting {} bytes for body", bodyNeeded);
                        } else {
                            bodyBuffer = new byte[0];
                            log.trace("[FRAME_DECODER] No body needed");
                        }
                    } else {
                        log.warn("[FRAME_DECODER] Header parsing failed, resetting");
                        state = State.HEAD;
                        headerReceived = 0;
                        errorCount++;
                    }
                }
            } else if (state == State.BODY) {
                int toRead = Math.min(bodyNeeded - bodyReceived, end - pos);
                if (toRead > 0) {
                    System.arraycopy(data, pos, bodyBuffer, bodyReceived, toRead);
                    bodyReceived += toRead;
                    pos += toRead;
                    log.trace("[FRAME_DECODER] Body progress: {}/{} bytes", bodyReceived, bodyNeeded);
                }

                if (bodyReceived == bodyNeeded) {
                    log.trace("[FRAME_DECODER] Complete frame assembled");

                    frameCount++;
                    // 立即提取完整的帧并添加到内部队列
                    int totalLen = ProtocolConstants.HEADER_SIZE + bodyNeeded;
                    byte[] frame = new byte[totalLen];
                    System.arraycopy(headerBuffer, 0, frame, 0, ProtocolConstants.HEADER_SIZE);
                    if (bodyNeeded > 0) {
                        System.arraycopy(bodyBuffer, 0, frame, ProtocolConstants.HEADER_SIZE, bodyNeeded);
                    }
                    
                    // 将帧存储到临时列表，供 decodeAll() 返回
                    if (completedFrames == null) {
                        completedFrames = new ArrayList<>();
                    }
                    completedFrames.add(frame);
                    
                    // 重置状态
                    state = State.HEAD;
                    headerReceived = 0;
                    bodyNeeded = 0;
                    bodyReceived = 0;
                    bodyBuffer = null;
                }
            }
        }
    }

    public void feed(byte[] data) {
        feed(data, 0, data.length);
    }

    private boolean parseHeader() {
        try {
            ByteBuffer buf = ByteBuffer.wrap(headerBuffer);
            buf.order(ByteOrder.LITTLE_ENDIAN);
            pendingMagic = buf.getShort();

            if (pendingMagic != ProtocolConstants.MAGIC) {
                errorHandler.handleWarning(String.format("Invalid magic: 0x%04X", pendingMagic));
                log.warn("[FRAME_DECODER] Invalid magic: 0x%04X", pendingMagic);
                return false;
            }

            pendingVersion = buf.get();
            if (pendingVersion != ProtocolConstants.VERSION) {
                errorHandler.handleWarning(String.format("Unsupported version: %d", pendingVersion));
                log.warn("[FRAME_DECODER] Unsupported version: %d", pendingVersion);
                return false;
            }

            pendingMsgType = buf.get();
            pendingFlags = buf.get();
            pendingReserved = buf.get();
            bodyNeeded = buf.getShort() & 0xFFFF;
            pendingSequence = buf.getInt();
            pendingHmacPrefix = buf.getShort();
            buf.getShort();

            log.trace("[FRAME_DECODER] Parsed header: msgType=0x{}, flags=0x{}, bodyLen={}, sequence={}, hmacPrefix={}", 
                String.format("%02X", pendingMsgType),
                String.format("%02X", pendingFlags),
                bodyNeeded,
                pendingSequence,
                String.format("%04X", pendingHmacPrefix));

            return true;
        } catch (Exception e) {
            errorHandler.handleException(new ProtocolException("Header parse error: " + e.getMessage(), e));
            log.error("[FRAME_DECODER] Header parse error", e);
            return false;
        }
    }

    public boolean hasCompleteFrame() {
        return state == State.BODY && bodyReceived == bodyNeeded;
    }

    public byte[] tryDecode() {
        if (!hasCompleteFrame()) {
            return null;
        }

        int totalLen = ProtocolConstants.HEADER_SIZE + bodyNeeded;
        byte[] frame = new byte[totalLen];
        System.arraycopy(headerBuffer, 0, frame, 0, ProtocolConstants.HEADER_SIZE);
        if (bodyNeeded > 0) {
            System.arraycopy(bodyBuffer, 0, frame, ProtocolConstants.HEADER_SIZE, bodyNeeded);
        }

        state = State.HEAD;
        headerReceived = 0;
        bodyNeeded = 0;
        bodyReceived = 0;
        bodyBuffer = null;

        return frame;
    }

    public List<byte[]> decodeAll() {
        // 返回所有已完成的帧
        List<byte[]> frames = new ArrayList<>(completedFrames);
        completedFrames.clear();
        
        // 也检查当前状态是否有完整的帧（向后兼容）
        byte[] frame;
        while ((frame = tryDecode()) != null) {
            frames.add(frame);
        }
        return frames;
    }

    public byte getPendingMsgType() {
        return pendingMsgType;
    }

    public byte getPendingFlags() {
        return pendingFlags;
    }

    public int getPendingBodyLength() {
        return bodyNeeded;
    }

    public int getPendingSequence() {
        return pendingSequence;
    }

    public short getPendingHmacPrefix() {
        return pendingHmacPrefix;
    }

    public State getState() {
        return state;
    }

    public void reset() {
        state = State.HEAD;
        headerReceived = 0;
        bodyNeeded = 0;
        bodyReceived = 0;
        bodyBuffer = null;
        completedFrames.clear();
    }

    public void setErrorHandler(ProtocolErrorHandler errorHandler) {
        this.errorHandler = errorHandler;
    }

    public void setMaxFrameSize(int maxFrameSize) {
        this.maxFrameSize = maxFrameSize;
    }

    public int getMaxFrameSize() {
        return maxFrameSize;
    }

    public long getFrameCount() {
        return frameCount;
    }

    public long getErrorCount() {
        return errorCount;
    }

    public ProtocolErrorHandler getErrorHandler() {
        return errorHandler;
    }

    public void clearStats() {
        frameCount = 0;
        errorCount = 0;
    }

    public String getStats() {
        return String.format("FrameDecoder[frames=%d, errors=%d, state=%s]",
                frameCount, errorCount, state);
    }
}
