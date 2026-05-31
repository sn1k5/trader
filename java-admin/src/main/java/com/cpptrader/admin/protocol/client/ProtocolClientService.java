package com.cpptrader.admin.protocol.client;

import com.cpptrader.admin.config.ProtocolConfig;
import com.cpptrader.admin.idempotent.DedupTableService;
import com.cpptrader.admin.protocol.ProtocolConstants;
import com.cpptrader.admin.protocol.events.OrderBookUpdateEvent;
import com.cpptrader.admin.protocol.events.OrderUpdateEvent;
import com.cpptrader.admin.protocol.requests.AuthRequest;
import com.cpptrader.admin.protocol.requests.SubscribeOrderBookRequest;
import com.cpptrader.admin.protocol.requests.SubscribeOrdersRequest;
import com.cpptrader.admin.protocol.responses.AuthResponse;
import com.cpptrader.admin.protocol.security.HmacSigner;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

@Slf4j
@Service
public class ProtocolClientService {

    private final ProtocolConfig config;
    private final DedupTableService dedupTableService;
    private INetworkBackend backend;
    private ProtocolStreamSubscriber streamSubscriber;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicBoolean connected = new AtomicBoolean(false);
    private final AtomicBoolean authenticated = new AtomicBoolean(false);
    private final AtomicInteger sequenceCounter = new AtomicInteger(0);
    private volatile byte[] sessionToken = null;
    private Thread recvThread;
    private Thread heartbeatThread;

    // For simple request/response without request ID in protocol header
    private volatile CompletableFuture<byte[]> currentRequestFuture = null;
    private final Object requestLock = new Object();

    private volatile long lastRecvTime = System.currentTimeMillis();
    private volatile long lastSendTime = 0;

    public ProtocolClientService(ProtocolConfig config, DedupTableService dedupTableService) {
        this.config = config;
        this.dedupTableService = dedupTableService;
    }

    @PostConstruct
    public void init() {
        streamSubscriber = new ProtocolStreamSubscriber(this, dedupTableService);
        // Connect asynchronously to avoid blocking Spring Boot startup
        Thread connectThread = new Thread(() -> {
            try {
                Thread.sleep(500); // Brief delay to let Spring finish init
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            connect();
        }, "protocol-init-connect");
        connectThread.setDaemon(true);
        connectThread.start();
    }

    @PreDestroy
    public void shutdown() {
        running.set(false);
        if (recvThread != null) {
            recvThread.interrupt();
        }
        if (heartbeatThread != null) {
            heartbeatThread.interrupt();
        }
        if (backend != null) {
            backend.close();
        }
        // Complete any pending request
        synchronized (requestLock) {
            if (currentRequestFuture != null && !currentRequestFuture.isDone()) {
                currentRequestFuture.completeExceptionally(new RuntimeException("Client shutdown"));
            }
        }
        log.info("ProtocolClientService shutdown");
    }

    private void connect() {
        if (connected.get()) {
            log.debug("Already connected, skipping connect()");
            return;
        }

        String backendType = config.getBackend();
        ProtocolConfig.TcpConfig tcp = config.getTcp();
        
        // Close existing backend if any (prevent connection leak)
        if (backend != null) {
            try {
                backend.close();
            } catch (Exception e) {
                log.warn("Error closing existing backend", e);
            }
        }
        
        INetworkBackend newBackend;
        if ("dpdk".equalsIgnoreCase(backendType)) {
            ProtocolConfig.DpdkConfig dpdk = config.getDpdk();
            newBackend = new DpdkJniBackend(dpdk.getLocalIp(), dpdk.getLocalPort(), dpdk.getRemoteIp(), dpdk.getRemotePort());
        } else {
            newBackend = new NettyTcpBackend(tcp.getHost(), tcp.getPort(), this::onMessageReceived);
        }

        log.info("Attempting connection to C++ trading server...");
        boolean success = newBackend.init();
        
        if (success) {
            backend = newBackend;
            connected.set(true);
            authenticated.set(false);
            sessionToken = null;
            lastRecvTime = System.currentTimeMillis();
            
            if (authenticateWithCpp()) {
                running.set(true);
                startRecvLoop();
                startHeartbeatLoop();
                streamSubscriber.restoreSubscriptions();
                log.info("✓ ProtocolClientService successfully connected and authenticated");
            } else {
                log.error("╔═══════════════════════════════════════════════════╗");
                log.error("║  ✗ C++ AUTHENTICATION FAILED                     ║");
                log.error("╠═══════════════════════════════════════════════════╣");
                log.error("║  Please check:                                     ║");
                log.error("║  1. API Key ID and Secret are configured correctly║");
                log.error("║  2. C++ server supports authentication           ║");
                log.error("║  3. C++ server API keys file is correct          ║");
                log.error("║                                                   ║");
                log.error("║  Scheduling automatic reconnection...             ║");
                log.error("╚═══════════════════════════════════════════════════╝");
                
                connected.set(false);
                scheduleReconnect();
            }
        } else {
            log.error("╔═══════════════════════════════════════════════════╗");
            log.error("║  ✗ INITIAL CONNECTION FAILED                      ║");
            log.error("╠═══════════════════════════════════════════════════╣");
            log.error("║  Server: {}:{}                          ║", 
                    tcp.getHost(), String.format("%-5d", tcp.getPort()));
            log.error("║                                                   ║");
            log.error("║  Scheduling automatic reconnection...             ║");
            log.error("╚═══════════════════════════════════════════════════╝");
            
            connected.set(false);
            scheduleReconnect();
        }
    }

    private void scheduleReconnect() {
        if (!running.get()) {
            log.debug("Service is not running, skipping reconnection");
            return;
        }
        
        Thread reconnectThread = new Thread(() -> {
            long delay = 1000;
            long maxDelay = 10000;
            int attemptCount = 0;
            
            log.info("Reconnection thread started");
            
            while (!connected.get() && running.get()) {
                attemptCount++;
                
                try {
                    log.info("↻ Reconnection attempt #{} in {}ms...", attemptCount, delay);
                    Thread.sleep(delay);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.info("Reconnection thread interrupted");
                    return;
                }
                
                if (!running.get()) {
                    log.info("Service stopped, canceling reconnection");
                    return;
                }
                
                log.info("Attempting reconnection (attempt #{})...", attemptCount);
                
                try {
                    connect();
                    
                    if (connected.get()) {
                        log.info("✓ Reconnection successful after {} attempts", attemptCount);
                        return;
                    } else {
                        delay = Math.min(delay * 2, maxDelay);
                        log.warn("Reconnection attempt {} failed, next attempt in {}ms", 
                                attemptCount, delay);
                    }
                } catch (Exception e) {
                    log.error("Reconnection attempt {} failed with exception: {}", 
                            attemptCount, e.getMessage());
                    delay = Math.min(delay * 2, maxDelay);
                }
            }
            
            if (connected.get()) {
                log.info("✓ Reconnection successful");
            } else {
                log.info("Reconnection thread exiting (connected={}, running={})", 
                        connected.get(), running.get());
            }
        });
        reconnectThread.setDaemon(true);
        reconnectThread.setName("protocol-reconnect");
        reconnectThread.start();
    }

    private boolean authenticateWithCpp() {
        ProtocolConfig.CppConfig cppConfig = config.getCpp();
        String apiKeyId = cppConfig.getApiKeyId();
        String apiKeySecret = cppConfig.getApiKeySecret();
        
        if (apiKeyId == null || apiKeyId.isEmpty() || apiKeySecret == null || apiKeySecret.isEmpty()) {
            log.warn("C++ API credentials not configured (cpp.apiKeyId or cpp.apiKeySecret is empty). Skipping authentication for backward compatibility.");
            log.warn("To enable authentication, configure protocol.cpp.apiKeyId and protocol.cpp.apiKeySecret in application.yml");
            authenticated.set(true);
            return true;
        }
        
        try {
            log.info("Starting C++ HMAC authentication with API Key ID: {}", apiKeyId);
            
            AuthRequest authRequest = new AuthRequest(apiKeyId, apiKeySecret);
            
            byte[] authBody = authRequest.toBytes();
            ByteBuffer headerBuf = ByteBuffer.allocate(ProtocolConstants.HEADER_SIZE);
            headerBuf.order(ByteOrder.LITTLE_ENDIAN);
            headerBuf.putShort(ProtocolConstants.MAGIC);
            headerBuf.put(ProtocolConstants.VERSION);
            headerBuf.put(ProtocolConstants.AUTH_REQUEST);
            headerBuf.put(ProtocolConstants.FLAG_REQUEST);
            headerBuf.put((byte) 0);
            headerBuf.putShort((short) ProtocolConstants.AUTH_REQUEST_BODY_SIZE);
            headerBuf.putInt(sequenceCounter.incrementAndGet());
            headerBuf.putShort((short) 0);
            headerBuf.putShort((short) 0);
            
            byte[] fullMessage = new byte[ProtocolConstants.HEADER_SIZE + ProtocolConstants.AUTH_REQUEST_BODY_SIZE];
            System.arraycopy(headerBuf.array(), 0, fullMessage, 0, ProtocolConstants.HEADER_SIZE);
            System.arraycopy(authBody, 0, fullMessage, ProtocolConstants.HEADER_SIZE, ProtocolConstants.AUTH_REQUEST_BODY_SIZE);
            
            log.info("[SEND] Authentication request - apiKeyId: {}, timestamp: {}, nonce: {}", 
                apiKeyId, authRequest.getTimestampMs(), authRequest.getNonceHex());
            
            int authTimeoutMs = cppConfig.getAuthTimeoutSec() * 1000;
            byte[] response = sendSync(fullMessage, authTimeoutMs, TimeUnit.MILLISECONDS);
            
            if (response == null) {
                log.error("Authentication timeout after {}ms - no response from C++ server", authTimeoutMs);
                return false;
            }
            
            if (response.length < ProtocolConstants.HEADER_SIZE + ProtocolConstants.AUTH_RESPONSE_BODY_SIZE) {
                log.error("Authentication response too short: {} bytes (expected at least {})", 
                    response.length, ProtocolConstants.HEADER_SIZE + ProtocolConstants.AUTH_RESPONSE_BODY_SIZE);
                return false;
            }
            
            byte[] responseBody = new byte[ProtocolConstants.AUTH_RESPONSE_BODY_SIZE];
            System.arraycopy(response, ProtocolConstants.HEADER_SIZE, responseBody, 0, ProtocolConstants.AUTH_RESPONSE_BODY_SIZE);
            
            AuthResponse authResponse = AuthResponse.fromBytes(responseBody);
            
            if (authResponse.isSuccess()) {
                sessionToken = authResponse.getSessionToken();
                authenticated.set(true);
                propagateSessionKey();
                log.info("✓ C++ authentication successful - session token: {}", authResponse.getSessionTokenHex());
                return true;
            } else {
                log.error("✗ C++ authentication failed - error code: {} ({})", 
                    authResponse.getError() & 0xFF, authResponse.getErrorName());
                return false;
            }
            
        } catch (Exception e) {
            log.error("C++ authentication error: {}", e.getMessage(), e);
            return false;
        }
    }

    private void startRecvLoop() {
        // Only start recv loop for non-Netty backends
        if (backend instanceof NettyTcpBackend) {
            log.info("Using Netty backend, skipping dedicated recv loop (using callback mechanism)");
            return;
        }
        
        // Prevent multiple recv threads
        if (recvThread != null && recvThread.isAlive()) {
            log.warn("Recv thread already running, skipping");
            return;
        }
        
        recvThread = new Thread(this::recvLoop);
        recvThread.setDaemon(true);
        recvThread.setName("protocol-recv");
        recvThread.start();
    }

    private void recvLoop() {
        log.info("Recv loop started");
        while (running.get()) {
            try {
                if (backend == null) {
                    Thread.sleep(100);
                    continue;
                }

                if (backend instanceof NettyTcpBackend) {
                    // Netty uses callback mechanism, no need to poll
                    log.debug("Netty backend detected in recvLoop, exiting");
                    break;
                }

                byte[] data = backend.recv();
                if (data != null) {
                    onMessageReceived(data);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.info("Recv loop interrupted");
                break;
            } catch (Exception e) {
                log.error("Recv loop error", e);
            }
        }
        log.info("Recv loop exited");
    }

    private void startHeartbeatLoop() {
        // Prevent multiple heartbeat threads
        if (heartbeatThread != null && heartbeatThread.isAlive()) {
            log.warn("Heartbeat thread already running, skipping");
            return;
        }
        
        heartbeatThread = new Thread(this::heartbeatLoop);
        heartbeatThread.setDaemon(true);
        heartbeatThread.setName("protocol-heartbeat");
        heartbeatThread.start();
    }

    private void heartbeatLoop() {
        int intervalMs = config.getHeartbeat().getIntervalSec() * 1000;
        int timeoutMs = config.getHeartbeat().getTimeoutSec() * 1000;

        log.info("Heartbeat loop started with interval={}ms, timeout={}ms", intervalMs, timeoutMs);

        while (running.get()) {
            try {
                Thread.sleep(intervalMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.info("Heartbeat loop interrupted");
                break;
            }

            if (!connected.get()) {
                log.debug("Not connected, skipping heartbeat");
                continue;
            }

            long now = System.currentTimeMillis();
            long timeSinceLastRecv = now - lastRecvTime;
            
            if (timeSinceLastRecv > timeoutMs) {
                log.warn("Heartbeat timeout: timeSinceLastRecv={}ms > timeout={}ms, closing connection", 
                        timeSinceLastRecv, timeoutMs);
                disconnect();
                scheduleReconnect();
                continue;
            }

            long timeSinceLastSend = now - lastSendTime;
            if (timeSinceLastSend >= intervalMs) {
                log.debug("Sending heartbeat: timeSinceLastSend={}ms, interval={}ms", 
                        timeSinceLastSend, intervalMs);
                sendHeartbeat();
            }
        }
        
        log.info("Heartbeat loop exited");
    }

    private void sendHeartbeat() {
        try {
            ByteBuffer buf = ByteBuffer.allocate(ProtocolConstants.HEADER_SIZE);
            buf.order(ByteOrder.LITTLE_ENDIAN);
            buf.putShort(ProtocolConstants.MAGIC);
            buf.put(ProtocolConstants.VERSION);
            buf.put(ProtocolConstants.HEARTBEAT_REQ);
            buf.put(ProtocolConstants.FLAG_HEARTBEAT);
            buf.put((byte) 0);
            buf.putShort((short) 0);
            int seq = sequenceCounter.incrementAndGet();
            buf.putInt(seq);
            buf.putShort((short) 0);
            buf.putShort((short) 0);

            byte[] token = this.sessionToken;
            if (token != null) {
                short prefix = HmacSigner.computeHmacPrefix(token, seq,
                        ProtocolConstants.HEARTBEAT_REQ, ProtocolConstants.FLAG_HEARTBEAT, (short) 0, null);
                buf.putShort(ProtocolConstants.HEADER_SIZE - 4, prefix);
            }

            backend.send(buf.array());
            lastSendTime = System.currentTimeMillis();
        } catch (Exception e) {
            log.error("Send heartbeat failed", e);
        }
    }

    private void onMessageReceived(byte[] data) {
        lastRecvTime = System.currentTimeMillis();
        
        // 打印所有接收到的原始数据
        StringBuilder hex = new StringBuilder();
        for (int i = 0; i < Math.min(data.length, 32); i++) {
            hex.append(String.format("%02X ", data[i]));
        }
        log.info("[RECV] Raw data received: {} bytes, hex: {}", data.length, hex.toString());

        if (data.length < ProtocolConstants.HEADER_SIZE) {
            log.warn("Received malformed message, too short: {} bytes", data.length);
            return;
        }

        ByteBuffer buf = ByteBuffer.wrap(data);
        buf.order(ByteOrder.LITTLE_ENDIAN);
        short magic = buf.getShort();
        
        if (magic != ProtocolConstants.MAGIC) {
            log.warn("Received message with invalid magic: 0x{} (expected: 0x{})", 
                    String.format("%04X", magic), String.format("%04X", ProtocolConstants.MAGIC));
            return;
        }

        byte version = buf.get();
        byte msgType = buf.get();
        byte flags = buf.get();
        buf.get();
        short bodyLen = buf.getShort();
        int sequence = buf.getInt();
        short hmacPrefix = buf.getShort();
        buf.getShort();

        log.info("[RECV] Parsed header - msgType=0x{} ({}), flags=0x{}, bodyLen={}, dataLen={}",
                String.format("%02X", msgType), getMsgTypeName(msgType),
                String.format("%02X", flags), bodyLen, data.length);

        // Only log non-heartbeat messages
        if ((flags & ProtocolConstants.FLAG_HEARTBEAT) == 0 && msgType != ProtocolConstants.HEARTBEAT_RESP) {
            log.info("[RECV] Processing non-heartbeat message: msgType=0x{} ({})", 
                    String.format("%02X", msgType), getMsgTypeName(msgType));
        }

        if ((flags & ProtocolConstants.FLAG_HEARTBEAT) != 0) {
            log.info("[RECV] Heartbeat message detected (flags=0x{}), msgType=0x{}", 
                    String.format("%02X", flags), String.format("%02X", msgType));
            if (msgType == ProtocolConstants.HEARTBEAT_REQ) {
                sendHeartbeatResponse();
            }
            // Heartbeat responses don't need handling
            log.info("[RECV] Skipping heartbeat message processing");
            return;
        }

        // Ignore heartbeat responses that might have FLAG_RESPONSE set
        if (msgType == ProtocolConstants.HEARTBEAT_RESP) {
            log.info("[RECV] Ignoring HEARTBEAT_RESP message");
            return;
        }

        if ((flags & ProtocolConstants.FLAG_PUSH) != 0) {
            handlePushMessage(msgType, data);
            return;
        }

        if ((flags & ProtocolConstants.FLAG_RESPONSE) != 0) {
            boolean isError = (flags & ProtocolConstants.FLAG_ERROR) != 0;
            synchronized (requestLock) {
                if (currentRequestFuture != null && !currentRequestFuture.isDone()) {
                    if (isError) {
                        log.info("Completing pending request with error response (msgType=0x{}, {} bytes)",
                                String.format("%02X", msgType), data.length);
                    } else {
                        log.info("Completing pending request with response (msgType=0x{}, {} bytes)",
                                String.format("%02X", msgType), data.length);
                    }
                    currentRequestFuture.complete(data);
                } else {
                    log.warn("Received response but no pending request (msgType=0x{}, flags=0x{}, bodyLen={})",
                            String.format("%02X", msgType), String.format("%02X", flags), bodyLen);
                }
            }
        } else {
            log.warn("Received message with unknown flags: 0x{}", String.format("%02X", flags));
        }
    }

    private void sendHeartbeatResponse() {
        try {
            ByteBuffer buf = ByteBuffer.allocate(ProtocolConstants.HEADER_SIZE);
            buf.order(ByteOrder.LITTLE_ENDIAN);
            buf.putShort(ProtocolConstants.MAGIC);
            buf.put(ProtocolConstants.VERSION);
            buf.put(ProtocolConstants.HEARTBEAT_RESP);
            buf.put(ProtocolConstants.FLAG_HEARTBEAT);
            buf.put((byte) 0);
            buf.putShort((short) 0);
            int seq = sequenceCounter.incrementAndGet();
            buf.putInt(seq);
            buf.putShort((short) 0);
            buf.putShort((short) 0);

            byte[] token = this.sessionToken;
            if (token != null) {
                short prefix = HmacSigner.computeHmacPrefix(token, seq,
                        ProtocolConstants.HEARTBEAT_RESP, ProtocolConstants.FLAG_HEARTBEAT, (short) 0, null);
                buf.putShort(ProtocolConstants.HEADER_SIZE - 4, prefix);
            }

            backend.send(buf.array());
        } catch (Exception e) {
            log.error("Send heartbeat response failed", e);
        }
    }

    private void handlePushMessage(byte msgType, byte[] data) {
        if (msgType == ProtocolConstants.ORDER_BOOK_UPDATE_EVT) {
            OrderBookUpdateEvent event = new OrderBookUpdateEvent();
            event.fromBytes(data);
            streamSubscriber.onOrderBookUpdate(event);
        } else if (msgType == ProtocolConstants.ORDER_UPDATE_EVT) {
            OrderUpdateEvent event = new OrderUpdateEvent();
            event.fromBytes(data);
            streamSubscriber.onOrdersUpdate(event);
        } else {
            log.warn("Unknown push message type: 0x{}", String.format("%02X", msgType));
        }
    }



    public CompletableFuture<byte[]> sendAsync(byte[] data) {
        if (!connected.get()) {
            String errorMsg = String.format("Not connected to C++ server (configured: %s:%d). Please check:\n" +
                    "1. C++ trading core is running\n" +
                    "2. Server address and port are correct\n" +
                    "3. Network connectivity and firewall settings",
                    config.getTcp().getHost(), config.getTcp().getPort());
            log.error(errorMsg);
            return CompletableFuture.failedFuture(new RuntimeException(errorMsg));
        }

        if (!authenticated.get()) {
            String errorMsg = "Not authenticated with C++ server. Please check authentication status.";
            log.error(errorMsg);
            return CompletableFuture.failedFuture(new RuntimeException(errorMsg));
        }

        CompletableFuture<byte[]> future = new CompletableFuture<>();
        
        synchronized (requestLock) {
            // If there's already a pending request, fail it first
            if (currentRequestFuture != null && !currentRequestFuture.isDone()) {
                log.warn("New request sent before previous one completed, failing previous request");
                currentRequestFuture.completeExceptionally(new RuntimeException("Request superseded"));
            }
            currentRequestFuture = future;
        }

        try {
            // 打印发送数据的详细信息
            StringBuilder hex = new StringBuilder();
            for (int i = 0; i < Math.min(data.length, 32); i++) {
                hex.append(String.format("%02X ", data[i]));
            }
            log.info("[SEND] Raw data hex: {}", hex.toString());
            if (data.length >= 16) {
                log.info("[SEND] Header details - magic=0x{}, version={}, msgType=0x{}, flags=0x{}, bodyLen={}, sequence={}",
                        String.format("%04X", (data[0] & 0xFF) | ((data[1] & 0xFF) << 8)),
                        data[2] & 0xFF,
                        String.format("%02X", data[3]),
                        String.format("%02X", data[4]),
                        (data[6] & 0xFF) | ((data[7] & 0xFF) << 8),
                        (data[8] & 0xFF) | ((data[9] & 0xFF) << 8) | ((data[10] & 0xFF) << 16) | ((data[11] & 0xFF) << 24));
            }
            
            backend.send(data);
            lastSendTime = System.currentTimeMillis();
            // Only log non-heartbeat messages
            byte msgType = data[3];
            byte flags = data[4];
            if ((flags & ProtocolConstants.FLAG_HEARTBEAT) == 0 && msgType != ProtocolConstants.HEARTBEAT_REQ && msgType != ProtocolConstants.HEARTBEAT_RESP) {
                log.info("[SEND] Request sent ({} bytes), msgType=0x{} ({})", data.length, String.format("%02X", msgType), getMsgTypeName(msgType));
            }
        } catch (Exception e) {
            synchronized (requestLock) {
                currentRequestFuture = null;
            }
            return CompletableFuture.failedFuture(e);
        }

        return future;
    }

    public byte[] sendSync(byte[] data) {
        // Wait for connection with retry (up to 5 seconds)
        long deadline = System.currentTimeMillis() + 5000;
        while (!connected.get() || !authenticated.get()) {
            long remaining = deadline - System.currentTimeMillis();
            if (remaining <= 0) {
                log.warn("sendSync: still not connected after 5s wait, returning null");
                return null;
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            }
        }

        try {
            return sendAsync(data).get(10, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            log.error("sendSync timeout after 10 seconds - C++ server did not respond. Check if trading core is processing requests correctly.");
            // Clean up the current request
            synchronized (requestLock) {
                if (currentRequestFuture != null && !currentRequestFuture.isDone()) {
                    currentRequestFuture.completeExceptionally(e);
                }
                currentRequestFuture = null;
            }
            return null;
        } catch (Exception e) {
            log.error("sendSync failed: {}", e.getClass().getSimpleName(), e);
            // Clean up the current request
            synchronized (requestLock) {
                if (currentRequestFuture != null && !currentRequestFuture.isDone()) {
                    currentRequestFuture.completeExceptionally(e);
                }
                currentRequestFuture = null;
            }
            return null;
        }
    }

    public byte[] sendSync(byte[] data, long timeout, TimeUnit unit) {
        try {
            return sendAsync(data).get(timeout, unit);
        } catch (TimeoutException e) {
            log.error("sendSync timeout after {} {} - C++ server did not respond", timeout, unit.toString());
            // Clean up the current request
            synchronized (requestLock) {
                if (currentRequestFuture != null && !currentRequestFuture.isDone()) {
                    currentRequestFuture.completeExceptionally(e);
                }
                currentRequestFuture = null;
            }
            return null;
        } catch (Exception e) {
            log.error("sendSync failed: {}", e.getClass().getSimpleName(), e);
            // Clean up the current request
            synchronized (requestLock) {
                if (currentRequestFuture != null && !currentRequestFuture.isDone()) {
                    currentRequestFuture.completeExceptionally(e);
                }
                currentRequestFuture = null;
            }
            return null;
        }
    }

    public void subscribeOrderBook(int symbolId, Consumer<OrderBookUpdateEvent> callback) {
        streamSubscriber.setOrderBookCallback(callback);
        streamSubscriber.subscribeOrderBook(symbolId);
    }

    public void subscribeOrders(int symbolId, Consumer<OrderUpdateEvent> callback) {
        streamSubscriber.setOrdersCallback(callback);
        streamSubscriber.subscribeOrders(symbolId);
    }

    void sendSubscribeOrderBook(int symbolId) {
        SubscribeOrderBookRequest req = new SubscribeOrderBookRequest(symbolId);
        sendAsync(req.toBytes());
    }

    void sendSubscribeOrders(int symbolId) {
        SubscribeOrdersRequest req = new SubscribeOrdersRequest(symbolId);
        sendAsync(req.toBytes());
    }

    private String getMsgTypeName(byte msgType) {
        return switch (msgType) {
            case ProtocolConstants.ADD_SYMBOL_REQ -> "ADD_SYMBOL_REQUEST";
            case ProtocolConstants.DELETE_SYMBOL_REQ -> "DELETE_SYMBOL_REQUEST";
            case ProtocolConstants.GET_SYMBOL_REQ -> "GET_SYMBOL_REQUEST";
            case ProtocolConstants.ADD_ORDER_BOOK_REQ -> "ADD_ORDER_BOOK_REQUEST";
            case ProtocolConstants.DELETE_ORDER_BOOK_REQ -> "DELETE_ORDER_BOOK_REQUEST";
            case ProtocolConstants.GET_ORDER_BOOK_REQ -> "GET_ORDER_BOOK_REQUEST";
            case ProtocolConstants.ADD_ORDER_REQ -> "ADD_ORDER_REQUEST";
            case ProtocolConstants.REDUCE_ORDER_REQ -> "REDUCE_ORDER_REQUEST";
            case ProtocolConstants.MODIFY_ORDER_REQ -> "MODIFY_ORDER_REQUEST";
            case ProtocolConstants.MITIGATE_ORDER_REQ -> "MITIGATE_ORDER_REQUEST";
            case ProtocolConstants.REPLACE_ORDER_REQ -> "REPLACE_ORDER_REQUEST";
            case ProtocolConstants.DELETE_ORDER_REQ -> "DELETE_ORDER_REQUEST";
            case ProtocolConstants.EXECUTE_ORDER_REQ -> "EXECUTE_ORDER_REQUEST";
            case ProtocolConstants.GET_ORDER_REQ -> "GET_ORDER_REQUEST";
            case ProtocolConstants.ENABLE_MATCHING_REQ -> "ENABLE_MATCHING_REQUEST";
            case ProtocolConstants.DISABLE_MATCHING_REQ -> "DISABLE_MATCHING_REQUEST";
            case ProtocolConstants.SUBSCRIBE_ORDER_BOOK_REQ -> "SUBSCRIBE_ORDER_BOOK_REQUEST";
            case ProtocolConstants.SUBSCRIBE_ORDERS_REQ -> "SUBSCRIBE_ORDERS_REQUEST";
            case ProtocolConstants.SYMBOL_RESP -> "SYMBOL_RESPONSE";
            case ProtocolConstants.ORDER_BOOK_RESP -> "ORDER_BOOK_RESPONSE";
            case ProtocolConstants.ORDER_RESP -> "ORDER_RESPONSE";
            case ProtocolConstants.SIMPLE_RESP -> "SIMPLE_RESPONSE";
            case ProtocolConstants.ORDER_BOOK_UPDATE_EVT -> "ORDER_BOOK_UPDATE_EVENT";
            case ProtocolConstants.ORDER_UPDATE_EVT -> "ORDER_UPDATE_EVENT";
            case ProtocolConstants.HEARTBEAT_REQ -> "HEARTBEAT_REQ";
            case ProtocolConstants.HEARTBEAT_RESP -> "HEARTBEAT_RESP";
            case ProtocolConstants.SHUTDOWN_NOTIFY -> "SHUTDOWN_NOTIFY";
            case ProtocolConstants.EVENT_ACK -> "EVENT_ACK";
            case ProtocolConstants.RECONCILE_REQUEST -> "RECONCILE_REQUEST";
            case ProtocolConstants.RECONCILE_RESPONSE -> "RECONCILE_RESPONSE";
            case ProtocolConstants.AUTH_REQUEST -> "AUTH_REQUEST";
            case ProtocolConstants.AUTH_RESPONSE -> "AUTH_RESPONSE";
            default -> "UNKNOWN(" + String.format("%02X", msgType) + ")";
        };
    }

    private void disconnect() {
        authenticated.set(false);
        sessionToken = null;
        clearSessionKeyFromEncoder();
        connected.set(false);
        if (backend != null) {
            backend.close();
            backend = null;
        }
    }

    private void propagateSessionKey() {
        if (backend instanceof NettyTcpBackend) {
            ProtocolEncoder encoder = ((NettyTcpBackend) backend).getProtocolEncoder();
            if (encoder != null && sessionToken != null) {
                encoder.setSessionKey(sessionToken);
                encoder.setSequenceBase(sequenceCounter.get());
                log.info("Session key propagated to ProtocolEncoder");
            }
        }
    }

    private void clearSessionKeyFromEncoder() {
        if (backend instanceof NettyTcpBackend) {
            ProtocolEncoder encoder = ((NettyTcpBackend) backend).getProtocolEncoder();
            if (encoder != null) {
                encoder.clearSessionKey();
            }
        }
    }

    public boolean isConnected() {
        if (!connected.get()) {
            return false;
        }
        
        if (backend == null) {
            return false;
        }
        
        if (backend instanceof NettyTcpBackend) {
            return ((NettyTcpBackend) backend).isConnected();
        }
        
        if (backend instanceof DpdkJniBackend) {
            return ((DpdkJniBackend) backend).isInitialized();
        }
        
        return connected.get();
    }

    public Map<String, Object> getConnectionStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("connectedFlag", connected.get());
        status.put("runningFlag", running.get());
        status.put("backendType", backend != null ? backend.getClass().getSimpleName() : "null");
        status.put("backendNull", backend == null);
        
        if (backend instanceof NettyTcpBackend) {
            NettyTcpBackend nettyBackend = (NettyTcpBackend) backend;
            status.put("nettyConnected", nettyBackend.isConnected());
            status.put("host", nettyBackend.getHost());
            status.put("port", nettyBackend.getPort());
        } else if (backend instanceof DpdkJniBackend) {
            DpdkJniBackend dpdkBackend = (DpdkJniBackend) backend;
            status.put("dpdkInitialized", dpdkBackend.isInitialized());
            status.put("remoteIp", dpdkBackend.getRemoteIp());
            status.put("remotePort", dpdkBackend.getRemotePort());
        }
        
        status.put("lastRecvTime", lastRecvTime);
        status.put("lastSendTime", lastSendTime);
        status.put("timeSinceLastRecv", System.currentTimeMillis() - lastRecvTime);
        
        return status;
    }

    public ProtocolStreamSubscriber getStreamSubscriber() {
        return streamSubscriber;
    }
}
