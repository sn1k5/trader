package com.cpptrader.admin.protocol.events;

import com.cpptrader.admin.protocol.ProtocolConstants;
import com.cpptrader.admin.protocol.ProtocolMessage;

import java.nio.ByteBuffer;

public class OrderBookUpdateEvent extends ProtocolMessage {

    private int symbolId;
    private boolean isTop;
    private byte updateType;
    private byte levelType;
    private LevelHolder level = new LevelHolder();

    public OrderBookUpdateEvent() {
        super();
    }

    public OrderBookUpdateEvent(int symbolId, boolean isTop, byte updateType, byte levelType, LevelHolder level) {
        super(ProtocolConstants.ORDER_BOOK_UPDATE_EVT, ProtocolConstants.FLAG_PUSH);
        this.symbolId = symbolId;
        this.isTop = isTop;
        this.updateType = updateType;
        this.levelType = levelType;
        this.level = level;
    }

    @Override
    public int getBodySize() {
        return 4 + 1 + 1 + 1 + 1 + ProtocolConstants.LEVEL_PROTO_SIZE;
    }

    @Override
    public void encode(ByteBuffer buf) {
        buf.putInt(symbolId);
        buf.put((byte) (isTop ? 1 : 0));
        buf.put(updateType);
        buf.put(levelType);
        buf.put((byte) 0);
        writeLevelProto(buf, level);
    }

    @Override
    public void decode(ByteBuffer buf) {
        this.symbolId = buf.getInt();
        this.isTop = buf.get() == 1;
        this.updateType = buf.get();
        this.levelType = buf.get();
        buf.get();
        readLevelProto(buf, level);
    }

    public int getSymbolId() {
        return symbolId;
    }

    public void setSymbolId(int symbolId) {
        this.symbolId = symbolId;
    }

    public boolean isTop() {
        return isTop;
    }

    public void setTop(boolean top) {
        isTop = top;
    }

    public byte getUpdateType() {
        return updateType;
    }

    public String getUpdateTypeName() {
        return ProtocolConstants.UpdateType.name(updateType);
    }

    public void setUpdateType(byte updateType) {
        this.updateType = updateType;
    }

    public byte getLevelType() {
        return levelType;
    }

    public String getLevelTypeName() {
        return ProtocolConstants.LevelType.name(levelType);
    }

    public void setLevelType(byte levelType) {
        this.levelType = levelType;
    }

    public LevelHolder getLevel() {
        return level;
    }

    public void setLevel(LevelHolder level) {
        this.level = level;
    }
}
