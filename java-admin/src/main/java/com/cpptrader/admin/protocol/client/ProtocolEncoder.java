package com.cpptrader.admin.protocol.client;

import com.cpptrader.admin.protocol.ProtocolMessage;
import com.cpptrader.admin.protocol.security.HmacSigner;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;

import java.util.concurrent.atomic.AtomicInteger;

public class ProtocolEncoder extends MessageToByteEncoder<ProtocolMessage> {

    private final AtomicInteger sequenceCounter = new AtomicInteger(0);
    private volatile byte[] sessionKey = null;

    public void setSessionKey(byte[] key) {
        this.sessionKey = key;
    }

    public void clearSessionKey() {
        this.sessionKey = null;
    }

    public void setSequenceBase(int base) {
        sequenceCounter.set(base);
    }

    @Override
    protected void encode(ChannelHandlerContext ctx, ProtocolMessage msg, ByteBuf out) throws Exception {
        msg.setSequence(sequenceCounter.incrementAndGet());
        if (sessionKey != null) {
            byte[] bodyBytes = msg.getBodyBytes();
            short prefix = HmacSigner.computeHmacPrefix(sessionKey, msg.getSequence(),
                    msg.getMsgType(), msg.getFlags(), (short) msg.getBodySize(), bodyBytes);
            msg.setHmacPrefix(prefix);
        } else {
            msg.setHmacPrefix((short) 0);
        }
        byte[] data = msg.toBytes();
        out.writeBytes(data);
    }
}
