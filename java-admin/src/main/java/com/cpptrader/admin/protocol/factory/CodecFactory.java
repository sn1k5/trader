package com.cpptrader.admin.protocol.factory;

import com.cpptrader.admin.protocol.ProtocolMessage;
import com.cpptrader.admin.protocol.client.ProtocolDecoder;
import com.cpptrader.admin.protocol.client.ProtocolEncoder;
import com.cpptrader.admin.protocol.exception.ProtocolErrorHandler;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.handler.codec.MessageToByteEncoder;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;
import java.util.function.Consumer;

public class CodecFactory {

    public static class CodecResult {
        private final boolean success;
        private final byte[] data;
        private final String error;

        private CodecResult(boolean success, byte[] data, String error) {
            this.success = success;
            this.data = data;
            this.error = error;
        }

        public static CodecResult success(byte[] data) {
            return new CodecResult(true, data, null);
        }

        public static CodecResult failure(String error) {
            return new CodecResult(false, null, error);
        }

        public boolean isSuccess() {
            return success;
        }

        public byte[] getData() {
            return data;
        }

        public String getError() {
            return error;
        }
    }

    public static byte[] encodeMessage(ProtocolMessage message) {
        try {
            return message.toBytes();
        } catch (Exception e) {
            return null;
        }
    }

    public static CodecResult tryEncode(ProtocolMessage message) {
        try {
            byte[] data = message.toBytes();
            return CodecResult.success(data);
        } catch (Exception e) {
            return CodecResult.failure("Encode failed: " + e.getMessage());
        }
    }

    public static ProtocolMessage decodeMessage(byte[] data) {
        try {
            ByteBuffer buf = ByteBuffer.wrap(data);
            buf.order(ByteOrder.LITTLE_ENDIAN);
            buf.getShort();
            buf.get();
            byte msgType = buf.get();
            buf.get();
            buf.get();
            buf.getShort();

            ProtocolMessageFactory factory = ProtocolMessageFactory.getInstance();
            return factory.parseMessage(data);
        } catch (Exception e) {
            return null;
        }
    }

    public static CodecResult tryDecode(byte[] data) {
        try {
            ByteBuffer buf = ByteBuffer.wrap(data);
            buf.order(ByteOrder.LITTLE_ENDIAN);
            buf.getShort();
            buf.get();
            byte msgType = buf.get();
            buf.get();
            buf.get();
            buf.getShort();

            ProtocolMessageFactory factory = ProtocolMessageFactory.getInstance();
            ProtocolMessage message = factory.parseMessage(data);
            return CodecResult.success(data);
        } catch (Exception e) {
            return CodecResult.failure("Decode failed: " + e.getMessage());
        }
    }

    public static ByteToMessageDecoder createNettyDecoder() {
        return new ProtocolDecoder();
    }

    public static MessageToByteEncoder<ProtocolMessage> createNettyEncoder() {
        return new ProtocolEncoder();
    }

    public static ChannelInitializer<SocketChannel> createChannelInitializer() {
        return new ChannelInitializer<SocketChannel>() {
            @Override
            protected void initChannel(SocketChannel ch) throws Exception {
                ch.pipeline().addLast(
                        createNettyEncoder(),
                        createNettyDecoder(),
                        new SimpleChannelInboundHandler<byte[]>() {
                            @Override
                            protected void channelRead0(ChannelHandlerContext ctx, byte[] msg) throws Exception {
                            }
                        }
                );
            }
        };
    }

    public static Bootstrap createClientBootstrap(String host, int port,
                                                   Consumer<byte[]> messageCallback,
                                                   Consumer<Channel> channelCallback) {
        EventLoopGroup group = new NioEventLoopGroup();
        Bootstrap bootstrap = new Bootstrap();
        bootstrap.group(group)
                .channel(NioSocketChannel.class)
                .option(ChannelOption.SO_KEEPALIVE, true)
                .option(ChannelOption.TCP_NODELAY, true)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) throws Exception {
                        ch.pipeline().addLast(
                                createNettyEncoder(),
                                createNettyDecoder(),
                                new SimpleChannelInboundHandler<byte[]>() {
                                    @Override
                                    protected void channelRead0(ChannelHandlerContext ctx, byte[] msg) throws Exception {
                                        messageCallback.accept(msg);
                                    }

                                    @Override
                                    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
                                        super.channelInactive(ctx);
                                    }

                                    @Override
                                    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
                                    }
                                }
                        );
                        if (channelCallback != null) {
                            channelCallback.accept(ch);
                        }
                    }
                });

        return bootstrap;
    }

    public static byte[] encodeMessageWithHeader(byte msgType, byte flags, ByteBuffer body) {
        int bodySize = body != null ? body.remaining() : 0;
        int totalSize = 8 + bodySize;
        ByteBuffer buf = ByteBuffer.allocate(totalSize);
        buf.order(ByteOrder.LITTLE_ENDIAN);
        buf.putShort((short) 0x5452);
        buf.put((byte) 1);
        buf.put(msgType);
        buf.put(flags);
        buf.put((byte) 0);
        buf.putShort((short) bodySize);
        if (body != null) {
            buf.put(body);
        }
        return buf.array();
    }

    public static byte[] buildHeartbeatRequest() {
        return encodeMessageWithHeader((byte) 0xC0, (byte) 0x10, null);
    }

    public static byte[] buildHeartbeatResponse() {
        return encodeMessageWithHeader((byte) 0xC1, (byte) 0x10, null);
    }

    public static boolean isHeartbeat(byte[] data) {
        if (data == null || data.length < 8) {
            return false;
        }
        ByteBuffer buf = ByteBuffer.wrap(data);
        buf.order(ByteOrder.LITTLE_ENDIAN);
        buf.getShort();
        buf.get();
        byte msgType = buf.get();
        byte flags = buf.get();
        return (flags & 0x10) != 0 && (msgType == (byte) 0xC0 || msgType == (byte) 0xC1);
    }

    public static boolean isRequest(byte[] data) {
        if (data == null || data.length < 8) {
            return false;
        }
        ByteBuffer buf = ByteBuffer.wrap(data);
        buf.order(ByteOrder.LITTLE_ENDIAN);
        buf.getShort();
        buf.get();
        buf.get();
        byte flags = buf.get();
        return (flags & 0x01) != 0;
    }

    public static boolean isResponse(byte[] data) {
        if (data == null || data.length < 8) {
            return false;
        }
        ByteBuffer buf = ByteBuffer.wrap(data);
        buf.order(ByteOrder.LITTLE_ENDIAN);
        buf.getShort();
        buf.get();
        buf.get();
        byte flags = buf.get();
        return (flags & 0x02) != 0;
    }

    public static boolean isPush(byte[] data) {
        if (data == null || data.length < 8) {
            return false;
        }
        ByteBuffer buf = ByteBuffer.wrap(data);
        buf.order(ByteOrder.LITTLE_ENDIAN);
        buf.getShort();
        buf.get();
        buf.get();
        byte flags = buf.get();
        return (flags & 0x04) != 0;
    }

    public static String getMessageInfo(byte[] data) {
        if (data == null || data.length < 8) {
            return "Invalid data";
        }
        try {
            ByteBuffer buf = ByteBuffer.wrap(data);
            buf.order(ByteOrder.LITTLE_ENDIAN);
            short magic = buf.getShort();
            byte version = buf.get();
            byte msgType = buf.get();
            byte flags = buf.get();
            buf.get();
            short bodyLen = buf.getShort();

            ProtocolMessageFactory factory = ProtocolMessageFactory.getInstance();
            String typeName = factory.getMessageTypeName(msgType);
            String flagStr = getFlagsString(flags);

            return String.format("Message[%s, flags=%s, bodyLen=%d]",
                    typeName, flagStr, bodyLen);
        } catch (Exception e) {
            return "Parse error: " + e.getMessage();
        }
    }

    private static String getFlagsString(byte flags) {
        StringBuilder sb = new StringBuilder();
        if ((flags & 0x01) != 0) sb.append("REQ ");
        if ((flags & 0x02) != 0) sb.append("RESP ");
        if ((flags & 0x04) != 0) sb.append("PUSH ");
        if ((flags & 0x08) != 0) sb.append("ERR ");
        if ((flags & 0x10) != 0) sb.append("HB ");
        if (sb.length() == 0) sb.append("NONE");
        return sb.toString().trim();
    }

    private static CodecFactory instance;

    public static synchronized CodecFactory getInstance() {
        if (instance == null) {
            instance = new CodecFactory();
        }
        return instance;
    }
}