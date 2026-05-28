package com.cpptrader.marketdata.protocol.client;

import com.cpptrader.marketdata.config.MatchingEngineConfig;
import com.cpptrader.marketdata.engine.MarketDataEngine;
import com.cpptrader.marketdata.protocol.ProtocolConstants;
import com.cpptrader.marketdata.protocol.events.OrderBookUpdateEvent;
import com.cpptrader.marketdata.protocol.events.OrderUpdateEvent;
import com.cpptrader.marketdata.protocol.requests.SubscribeOrderBookRequest;
import com.cpptrader.marketdata.protocol.requests.SubscribeOrdersRequest;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.ByteOrder;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Service
public class MarketDataClient {

    private final MatchingEngineConfig config;
    private final MarketDataEngine engine;

    private EventLoopGroup workerGroup;
    private Channel channel;
    private final AtomicBoolean connected = new AtomicBoolean(false);
    private final AtomicBoolean running = new AtomicBoolean(false);

    private final Set<Integer> subscribedSymbols = new CopyOnWriteArraySet<>();

    private volatile long lastRecvTime = System.currentTimeMillis();
    private volatile long lastSendTime = 0;

    public MarketDataClient(MatchingEngineConfig config, MarketDataEngine engine) {
        this.config = config;
        this.engine = engine;
    }

    @PostConstruct
    public void init() {
        running.set(true);
        connect();
        startHeartbeatLoop();
    }

    @PreDestroy
    public void shutdown() {
        running.set(false);
        if (channel != null) {
            channel.close();
        }
        if (workerGroup != null) {
            workerGroup.shutdownGracefully();
        }
        log.info("MarketDataClient shutdown");
    }

    private void connect() {
        if (connected.get()) {
            return;
        }

        try {
            workerGroup = new NioEventLoopGroup(1);
            Bootstrap bootstrap = new Bootstrap();
            bootstrap.group(workerGroup)
                    .channel(NioSocketChannel.class)
                    .option(ChannelOption.TCP_NODELAY, true)
                    .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000)
                    .handler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            ch.pipeline().addLast(new LengthFieldBasedFrameDecoder(
                                    ByteOrder.LITTLE_ENDIAN, 65535 + 8, 6, 2, 0, 0, true));
                            ch.pipeline().addLast(new MarketDataMessageDecoder());
                            ch.pipeline().addLast(new MarketDataMessageEncoder());
                        }
                    });

            ChannelFuture future = bootstrap.connect(config.getHost(), config.getPort()).sync();
            channel = future.channel();
            connected.set(true);
            lastRecvTime = System.currentTimeMillis();
            log.info("MarketDataClient connected to {}:{}", config.getHost(), config.getPort());

            restoreSubscriptions();
        } catch (Exception e) {
            log.error("MarketDataClient failed to connect to {}:{} - {}",
                    config.getHost(), config.getPort(), e.getMessage());
            scheduleReconnect();
        }
    }

    private void scheduleReconnect() {
        workerGroup.schedule(() -> {
            if (!connected.get() && running.get()) {
                log.info("Attempting to reconnect...");
                if (workerGroup != null) {
                    workerGroup.shutdownGracefully();
                }
                connect();
            }
        }, 3, TimeUnit.SECONDS);
    }

    private void startHeartbeatLoop() {
        Thread heartbeatThread = new Thread(() -> {
            int intervalMs = config.getHeartbeat().getIntervalSec() * 1000;
            int timeoutMs = config.getHeartbeat().getTimeoutSec() * 1000;
            while (running.get()) {
                try {
                    Thread.sleep(intervalMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
                if (!connected.get()) {
                    continue;
                }
                long now = System.currentTimeMillis();
                if (now - lastRecvTime > timeoutMs) {
                    log.warn("Heartbeat timeout, closing connection");
                    disconnect();
                    scheduleReconnect();
                    continue;
                }
                if (now - lastSendTime >= intervalMs) {
                    sendHeartbeat();
                }
            }
        });
        heartbeatThread.setDaemon(true);
        heartbeatThread.setName("marketdata-heartbeat");
        heartbeatThread.start();
    }

    private void sendHeartbeat() {
        if (channel != null && channel.isActive()) {
            ByteBuf buf = channel.alloc().buffer(ProtocolConstants.HEADER_SIZE);
            buf.order(ByteOrder.LITTLE_ENDIAN);
            buf.writeShort(ProtocolConstants.MAGIC);
            buf.writeByte(ProtocolConstants.VERSION);
            buf.writeByte(ProtocolConstants.HEARTBEAT_REQ);
            buf.writeByte(ProtocolConstants.FLAG_HEARTBEAT);
            buf.writeByte(0);
            buf.writeShort(0);
            channel.writeAndFlush(buf);
            lastSendTime = System.currentTimeMillis();
        }
    }

    public void subscribeSymbol(int symbolId) {
        subscribedSymbols.add(symbolId);
        if (connected.get() && channel != null && channel.isActive()) {
            SubscribeOrderBookRequest req1 = new SubscribeOrderBookRequest(symbolId);
            channel.writeAndFlush(req1.toBytes());
            SubscribeOrdersRequest req2 = new SubscribeOrdersRequest(symbolId);
            channel.writeAndFlush(req2.toBytes());
            log.info("Subscribed symbolId={}", symbolId);
        }
    }

    public void unsubscribeSymbol(int symbolId) {
        subscribedSymbols.remove(symbolId);
    }

    private void restoreSubscriptions() {
        for (int symbolId : subscribedSymbols) {
            SubscribeOrderBookRequest req1 = new SubscribeOrderBookRequest(symbolId);
            channel.writeAndFlush(req1.toBytes());
            SubscribeOrdersRequest req2 = new SubscribeOrdersRequest(symbolId);
            channel.writeAndFlush(req2.toBytes());
        }
        if (!subscribedSymbols.isEmpty()) {
            log.info("Restored {} symbol subscriptions", subscribedSymbols.size());
        }
    }

    private void disconnect() {
        connected.set(false);
        if (channel != null) {
            channel.close();
            channel = null;
        }
    }

    public boolean isConnected() {
        return connected.get();
    }

    public Set<Integer> getSubscribedSymbols() {
        return subscribedSymbols;
    }

    @ChannelHandler.Sharable
    private class MarketDataMessageDecoder extends ChannelInboundHandlerAdapter {

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            lastRecvTime = System.currentTimeMillis();
            if (!(msg instanceof ByteBuf buf)) {
                return;
            }

            try {
                byte[] data = new byte[buf.readableBytes()];
                buf.readBytes(data);

                if (data.length < ProtocolConstants.HEADER_SIZE) {
                    return;
                }

                ByteBufferWrapper wrapper = new ByteBufferWrapper(data);
                short magic = wrapper.readShort();
                if (magic != ProtocolConstants.MAGIC) {
                    return;
                }
                wrapper.readByte();
                byte msgType = wrapper.readByte();
                byte flags = wrapper.readByte();
                wrapper.readByte();
                wrapper.readShort();

                if ((flags & ProtocolConstants.FLAG_HEARTBEAT) != 0) {
                    return;
                }

                if ((flags & ProtocolConstants.FLAG_PUSH) != 0) {
                    handlePushMessage(msgType, data);
                }
            } finally {
                buf.release();
            }
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) {
            log.warn("Connection to matching engine lost");
            connected.set(false);
            scheduleReconnect();
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            log.error("Connection error", cause);
            ctx.close();
        }

        private void handlePushMessage(byte msgType, byte[] data) {
            if (msgType == ProtocolConstants.ORDER_BOOK_UPDATE_EVT) {
                OrderBookUpdateEvent event = new OrderBookUpdateEvent();
                event.decode(data);
                engine.onOrderBookUpdate(event);
            } else if (msgType == ProtocolConstants.ORDER_UPDATE_EVT) {
                OrderUpdateEvent event = new OrderUpdateEvent();
                event.decode(data);
                engine.onOrderUpdate(event);
            }
        }
    }

    private static class ByteBufferWrapper {
        private final byte[] data;
        private int pos = 0;

        ByteBufferWrapper(byte[] data) {
            this.data = data;
        }

        short readShort() {
            int v = (data[pos] & 0xFF) | ((data[pos + 1] & 0xFF) << 8);
            pos += 2;
            return (short) v;
        }

        byte readByte() {
            return data[pos++];
        }

        int readInt() {
            int v = (data[pos] & 0xFF) | ((data[pos + 1] & 0xFF) << 8)
                    | ((data[pos + 2] & 0xFF) << 16) | ((data[pos + 3] & 0xFF) << 24);
            pos += 4;
            return v;
        }

        long readLong() {
            long v = 0;
            for (int i = 0; i < 8; i++) {
                v |= ((long) (data[pos + i] & 0xFF)) << (i * 8);
            }
            pos += 8;
            return v;
        }
    }

    private class MarketDataMessageEncoder extends ChannelOutboundHandlerAdapter {
        @Override
        public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
            if (msg instanceof byte[] data) {
                ByteBuf buf = ctx.alloc().buffer(data.length);
                buf.writeBytes(data);
                ctx.write(buf, promise);
            } else {
                ctx.write(msg, promise);
            }
        }
    }
}
