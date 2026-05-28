package com.cpptrader.marketdata.protocol.events;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class OrderBookUpdateEvent {

    private int symbolId;
    private boolean isTop;
    private int updateType;
    private int levelType;
    private LevelData level = new LevelData();

    public void decode(byte[] data) {
        if (data == null || data.length < ProtocolConstants.HEADER_SIZE + 40) {
            return;
        }
        ByteBuffer buf = ByteBuffer.wrap(data);
        buf.order(ByteOrder.LITTLE_ENDIAN);
        buf.position(ProtocolConstants.HEADER_SIZE);
        symbolId = buf.getInt();
        isTop = buf.get() == 1;
        updateType = buf.get() & 0xFF;
        levelType = buf.get() & 0xFF;
        buf.get();
        level.price = buf.getLong();
        level.totalVolume = buf.getLong();
        level.visibleVolume = buf.getLong();
        level.orders = buf.getLong();
    }

    public int getSymbolId() { return symbolId; }
    public boolean isTop() { return isTop; }
    public int getUpdateType() { return updateType; }
    public int getLevelType() { return levelType; }
    public LevelData getLevel() { return level; }

    public String getUpdateTypeName() {
        return switch (updateType) {
            case 1 -> "ADD";
            case 2 -> "UPDATE";
            case 3 -> "DELETE";
            default -> "UNKNOWN";
        };
    }

    public String getLevelTypeName() {
        return levelType == 0 ? "BID" : "ASK";
    }

    public static class LevelData {
        public long price;
        public long totalVolume;
        public long visibleVolume;
        public long orders;
    }
}
