package com.cpptrader.admin.protocol.responses;

import com.cpptrader.admin.protocol.ProtocolConstants;
import com.cpptrader.admin.protocol.ProtocolMessage;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

public class OrderBookResponse extends ProtocolMessage {

    private int symbolId;
    private LevelHolder bestBid = new LevelHolder();
    private LevelHolder bestAsk = new LevelHolder();
    private List<LevelHolder> bids = new ArrayList<>();
    private List<LevelHolder> asks = new ArrayList<>();

    public OrderBookResponse() {
        super();
    }

    public OrderBookResponse(int symbolId) {
        super(ProtocolConstants.ORDER_BOOK_RESP, ProtocolConstants.FLAG_RESPONSE);
        this.symbolId = symbolId;
    }

    @Override
    public int getBodySize() {
        return 4 + ProtocolConstants.LEVEL_PROTO_SIZE + ProtocolConstants.LEVEL_PROTO_SIZE
                + 2 + bids.size() * ProtocolConstants.LEVEL_PROTO_SIZE
                + 2 + asks.size() * ProtocolConstants.LEVEL_PROTO_SIZE;
    }

    @Override
    public void encode(ByteBuffer buf) {
        buf.putInt(symbolId);
        writeLevelProto(buf, bestBid);
        writeLevelProto(buf, bestAsk);
        buf.putShort((short) bids.size());
        for (LevelHolder level : bids) {
            writeLevelProto(buf, level);
        }
        buf.putShort((short) asks.size());
        for (LevelHolder level : asks) {
            writeLevelProto(buf, level);
        }
    }

    @Override
    public void decode(ByteBuffer buf) {
        this.symbolId = buf.getInt();
        readLevelProto(buf, bestBid);
        readLevelProto(buf, bestAsk);
        short bidCount = buf.getShort();
        bids.clear();
        for (int i = 0; i < bidCount; i++) {
            LevelHolder level = new LevelHolder();
            readLevelProto(buf, level);
            bids.add(level);
        }
        short askCount = buf.getShort();
        asks.clear();
        for (int i = 0; i < askCount; i++) {
            LevelHolder level = new LevelHolder();
            readLevelProto(buf, level);
            asks.add(level);
        }
    }

    public int getSymbolId() {
        return symbolId;
    }

    public void setSymbolId(int symbolId) {
        this.symbolId = symbolId;
    }

    public LevelHolder getBestBid() {
        return bestBid;
    }

    public LevelHolder getBestAsk() {
        return bestAsk;
    }

    public List<LevelHolder> getBids() {
        return bids;
    }

    public List<LevelHolder> getAsks() {
        return asks;
    }
}
