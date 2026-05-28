package com.cpptrader.admin.protocol.client;

import com.cpptrader.admin.protocol.ProtocolConstants;
import com.cpptrader.admin.protocol.ProtocolMessage;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.Consumer;

@Slf4j
public class NettyTcpBackend implements INetworkBackend {

    private final String host;
    private final int port;
    private final Consumer<byte[]> messageCallback;

    private EventLoopGroup workerGroup;
    private Channel channel;
    private ProtocolEncoder protocolEncoder;
    private final BlockingQueue<byte[]> recvQueue = new LinkedBlockingQueue<>();
    private volatile boolean connected = false;

    public NettyTcpBackend(String host, int port) {
        this(host, port, null);
    }

    public NettyTcpBackend(String host, int port, Consumer<byte[]> messageCallback) {
        this.host = host;
        this.port = port;
        this.messageCallback = messageCallback;
    }

    @Override
    public boolean init() {
        workerGroup = new NioEventLoopGroup();
        try {
            Bootstrap bootstrap = new Bootstrap();
            bootstrap.group(workerGroup)
                    .channel(NioSocketChannel.class)
                    .option(ChannelOption.SO_KEEPALIVE, true)
                    .option(ChannelOption.TCP_NODELAY, true)
                    .handler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) throws Exception {
                            protocolEncoder = new ProtocolEncoder();
                            ch.pipeline().addLast(
                                    protocolEncoder,
                                    new ProtocolDecoder(),
                                    new ClientHandler()
                            );
                        }
                    });

            ChannelFuture future = bootstrap.connect(host, port).sync();
            channel = future.channel();
            connected = true;
            log.info("Netty TCP backend connected to {}:{}", host, port);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Netty TCP backend init interrupted", e);
            return false;
        } catch (Exception e) {
            log.error("Netty TCP backend init failed", e);
            return false;
        }
    }

    @Override
    public void send(byte[] data) {
        if (channel != null && channel.isActive()) {
            ByteBuf buf = Unpooled.wrappedBuffer(data);
            channel.writeAndFlush(buf);
            log.info("[NETTY] Data sent: {} bytes, msgType=0x{}", data.length, String.format("%02X", data[3]));
        } else {
            log.warn("Netty TCP backend not connected, cannot send");
        }
    }

    @Override
    public byte[] recv() {
        try {
            return recvQueue.take();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    @Override
    public void close() {
        connected = false;
        if (channel != null) {
            channel.close();
        }
        if (workerGroup != null) {
            workerGroup.shutdownGracefully();
        }
        log.info("Netty TCP backend closed");
    }

    public boolean isConnected() {
        return connected && channel != null && channel.isActive();
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public ProtocolEncoder getProtocolEncoder() {
        return protocolEncoder;
    }

    private class ClientHandler extends SimpleChannelInboundHandler<byte[]> {

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, byte[] msg) throws Exception {
            log.info("[NETTY] channelRead0 called, msg length: {}", msg.length);
            
            // 打印前32个字节的十六进制
            StringBuilder hex = new StringBuilder();
            for (int i = 0; i < Math.min(msg.length, 32); i++) {
                hex.append(String.format("%02X ", msg[i]));
            }
            log.info("[NETTY] msg hex: {}", hex.toString());
            
            if (msg.length >= 8) {
                log.info("[NETTY] Data received: {} bytes, magic=0x{}, version={}, msgType=0x{}, flags=0x{}, bodyLen={}", 
                        msg.length, 
                        String.format("%04X", (msg[0] & 0xFF) | ((msg[1] & 0xFF) << 8)),
                        msg[2] & 0xFF,
                        String.format("%02X", msg[3]), 
                        String.format("%02X", msg[4]),
                        (msg[6] & 0xFF) | ((msg[7] & 0xFF) << 8));
                
                // 特别记录不同类型的响应
                byte msgType = msg[3];
                byte flags = msg[4];
                if (msgType == ProtocolConstants.SYMBOL_RESP) {
                    log.info("[NETTY] Received SYMBOL_RESPONSE: {} bytes, flags=0x{}", msg.length, String.format("%02X", flags));
                } else if (msgType == ProtocolConstants.HEARTBEAT_RESP) {
                    log.info("[NETTY] Received HEARTBEAT_RESPONSE: {} bytes, flags=0x{}", msg.length, String.format("%02X", flags));
                } else if (msgType == ProtocolConstants.ORDER_BOOK_RESP) {
                    log.info("[NETTY] Received ORDER_BOOK_RESPONSE: {} bytes, flags=0x{}", msg.length, String.format("%02X", flags));
                } else if (msgType == ProtocolConstants.ORDER_RESP) {
                    log.info("[NETTY] Received ORDER_RESPONSE: {} bytes, flags=0x{}", msg.length, String.format("%02X", flags));
                } else if (msgType == ProtocolConstants.SIMPLE_RESP) {
                    log.info("[NETTY] Received SIMPLE_RESPONSE: {} bytes, flags=0x{}", msg.length, String.format("%02X", flags));
                }
            } else {
                log.warn("[NETTY] Data received too short: {} bytes", msg.length);
            }
            
            if (messageCallback != null) {
                log.debug("[NETTY] Calling message callback with {} bytes", msg.length);
                messageCallback.accept(msg);
            } else {
                log.debug("[NETTY] Putting message to recvQueue: {} bytes", msg.length);
                recvQueue.put(msg);
            }
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) throws Exception {
            connected = false;
            log.warn("Netty TCP backend channel inactive");
            super.channelInactive(ctx);
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            log.error("Netty TCP backend exception", cause);
            ctx.close();
        }
    }
}
