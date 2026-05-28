package com.cpptrader.admin.protocol.client;

import com.cpptrader.admin.protocol.FrameDecoder;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

@Slf4j
public class ProtocolDecoder extends ByteToMessageDecoder {

    private final FrameDecoder frameDecoder = new FrameDecoder();

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        if (!in.isReadable()) {
            return;
        }

        int readableBytes = in.readableBytes();
        log.info("[DECODER] Received {} bytes from network", readableBytes);
        
        byte[] data = new byte[readableBytes];
        in.readBytes(data);
        
        // 打印接收到的原始数据（前32字节）
        StringBuilder hex = new StringBuilder();
        for (int i = 0; i < Math.min(data.length, 32); i++) {
            hex.append(String.format("%02X ", data[i]));
        }
        log.info("[DECODER] Raw data hex: {}", hex.toString());
        
        // 记录喂入前的状态
        log.info("[DECODER] FrameDecoder state before feed: {}", frameDecoder.getStats());
        
        frameDecoder.feed(data);
        
        // 记录喂入后的状态
        log.info("[DECODER] FrameDecoder state after feed: {}", frameDecoder.getStats());

        List<byte[]> frames = frameDecoder.decodeAll();
        log.info("[DECODER] Decoded {} complete frames", frames.size());
        
        for (byte[] frame : frames) {
            if (frame.length >= 16) {
                log.info("[DECODER] Frame: {} bytes, msgType=0x{}, flags=0x{}", 
                    frame.length,
                    String.format("%02X", frame[3]),
                    String.format("%02X", frame[4]));
            }
            out.add(frame);
        }
    }

    @Override
    protected void handlerRemoved0(ChannelHandlerContext ctx) throws Exception {
        frameDecoder.reset();
        super.handlerRemoved0(ctx);
    }
}
