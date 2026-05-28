package com.cpptrader.admin.protocol;

import com.cpptrader.admin.protocol.exception.ProtocolException;
import com.cpptrader.admin.protocol.validation.ProtocolValidator;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

public abstract class ProtocolMessage {

    protected byte msgType;
    protected byte flags;
    protected int sequence;
    protected short hmacPrefix;

    protected ProtocolMessage(byte msgType, byte flags) {
        this.msgType = msgType;
        this.flags = flags;
    }

    protected ProtocolMessage() {}

    public byte getMsgType() {
        return msgType;
    }

    public byte getFlags() {
        return flags;
    }

    public void setFlags(byte flags) {
        this.flags = flags;
    }

    public int getSequence() {
        return sequence;
    }

    public void setSequence(int sequence) {
        this.sequence = sequence;
    }

    public short getHmacPrefix() {
        return hmacPrefix;
    }

    public void setHmacPrefix(short hmacPrefix) {
        this.hmacPrefix = hmacPrefix;
    }

    public abstract void encode(ByteBuffer buf);

    public abstract void decode(ByteBuffer buf);

    public int getBodySize() {
        return 0;
    }

    public byte[] getBodyBytes() {
        ByteBuffer buf = ByteBuffer.allocate(getBodySize());
        buf.order(ByteOrder.LITTLE_ENDIAN);
        encode(buf);
        return buf.array();
    }

    public final int getTotalSize() {
        return ProtocolConstants.HEADER_SIZE + getBodySize();
    }

    public final byte[] toBytes() {
        ProtocolValidator.ValidationResult validation = validate();
        if (!validation.isValid()) {
            throw new ProtocolException("Message validation failed: " + validation.getErrorMessage());
        }

        ByteBuffer buf = ByteBuffer.allocate(getTotalSize());
        buf.order(ByteOrder.LITTLE_ENDIAN);
        writeHeader(buf);
        encode(buf);
        return buf.array();
    }

    public final void fromBytes(byte[] data) {
        if (data == null || data.length < ProtocolConstants.HEADER_SIZE) {
            throw ProtocolException.decodingError("Data too short for header");
        }

        ByteBuffer buf = ByteBuffer.wrap(data);
        buf.order(ByteOrder.LITTLE_ENDIAN);
        readHeader(buf);
        decode(buf);

        ProtocolValidator.ValidationResult validation = validate();
        if (!validation.isValid()) {
            throw new ProtocolException("Message validation failed: " + validation.getErrorMessage());
        }
    }

    public ProtocolValidator.ValidationResult validate() {
        return ProtocolValidator.validateMessage(this);
    }

    protected void writeHeader(ByteBuffer buf) {
        buf.putShort(ProtocolConstants.MAGIC);
        buf.put(ProtocolConstants.VERSION);
        buf.put(msgType);
        buf.put(flags);
        buf.put((byte) 0);
        buf.putShort((short) getBodySize());
        buf.putInt(sequence);
        buf.putShort(hmacPrefix);
        buf.putShort((short) 0);
    }

    protected void readHeader(ByteBuffer buf) {
        short magic = buf.getShort();
        if (magic != ProtocolConstants.MAGIC) {
            throw ProtocolException.invalidMagic(magic);
        }
        byte version = buf.get();
        if (version != ProtocolConstants.VERSION) {
            throw ProtocolException.unsupportedVersion(version);
        }
        msgType = buf.get();
        flags = buf.get();
        buf.get();
        short bodyLen = buf.getShort();
        sequence = buf.getInt();
        hmacPrefix = buf.getShort();
        buf.getShort();
    }

    protected static void writeString(ByteBuffer buf, String value, int fixedSize) {
        if (value == null) {
            value = "";
        }
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        int len = Math.min(bytes.length, fixedSize);
        buf.put(bytes, 0, len);
        for (int i = len; i < fixedSize; i++) {
            buf.put((byte) 0);
        }
    }

    protected static String readString(ByteBuffer buf, int fixedSize) {
        byte[] bytes = new byte[fixedSize];
        int actuallyRead = Math.min(fixedSize, buf.remaining());
        buf.get(bytes, 0, actuallyRead);
        int len = 0;
        while (len < actuallyRead && bytes[len] != 0) {
            len++;
        }
        return new String(bytes, 0, len, StandardCharsets.UTF_8);
    }

    protected static void writeSymbolProto(ByteBuffer buf, int id, String name) {
        buf.putInt(id);
        writeString(buf, name, ProtocolConstants.SYMBOL_NAME_SIZE);
    }

    protected static void readSymbolProto(ByteBuffer buf, SymbolHolder holder) {
        holder.id = buf.getInt();
        holder.name = readString(buf, ProtocolConstants.SYMBOL_NAME_SIZE);
    }

    protected static void writeOrderProto(ByteBuffer buf, OrderHolder o) {
        buf.putLong(o.id);
        buf.putInt(o.symbolId);
        buf.put(o.orderType);
        buf.put(o.orderSide);
        buf.putLong(o.price);
        buf.putLong(o.stopPrice);
        buf.putLong(o.quantity);
        buf.putLong(o.executedQuantity);
        buf.putLong(o.leavesQuantity);
        buf.put(o.timeInForce);
        buf.put((byte) 0); // Padding1
        buf.putLong(o.maxVisibleQuantity);
        buf.putLong(o.slippage);
        buf.putLong(o.trailingDistance);
        buf.putLong(o.trailingStep);
    }

    protected static void readOrderProto(ByteBuffer buf, OrderHolder o) {
        o.id = buf.getLong();
        o.symbolId = buf.getInt();
        o.orderType = buf.get();
        o.orderSide = buf.get();
        o.price = buf.getLong();
        o.stopPrice = buf.getLong();
        o.quantity = buf.getLong();
        o.executedQuantity = buf.getLong();
        o.leavesQuantity = buf.getLong();
        o.timeInForce = buf.get();
        buf.get(); // Padding1
        o.maxVisibleQuantity = buf.getLong();
        o.slippage = buf.getLong();
        o.trailingDistance = buf.getLong();
        o.trailingStep = buf.getLong();
    }

    protected static void writeLevelProto(ByteBuffer buf, LevelHolder l) {
        buf.putLong(l.price);
        buf.putLong(l.totalVolume);
        buf.putLong(l.visibleVolume);
        buf.putLong(l.orders);
    }

    protected static void readLevelProto(ByteBuffer buf, LevelHolder l) {
        l.price = buf.getLong();
        l.totalVolume = buf.getLong();
        l.visibleVolume = buf.getLong();
        l.orders = buf.getLong();
    }

    public String toDebugString() {
        return String.format("ProtocolMessage[type=0x%02X, flags=0x%02X, bodySize=%d]",
                msgType, flags, getBodySize());
    }

    public static class SymbolHolder {
        public int id;
        public String name;
    }

    public static class OrderHolder {
        public long id;
        public int symbolId;
        public byte orderType;
        public byte orderSide;
        public long price;
        public long stopPrice;
        public long quantity;
        public long executedQuantity;
        public long leavesQuantity;
        public byte timeInForce;
        public long maxVisibleQuantity;
        public long slippage;
        public long trailingDistance;
        public long trailingStep;
    }

    public static class LevelHolder {
        public long price;
        public long totalVolume;
        public long visibleVolume;
        public long orders;
    }
}
