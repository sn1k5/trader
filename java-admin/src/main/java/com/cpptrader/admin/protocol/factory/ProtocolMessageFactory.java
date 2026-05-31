package com.cpptrader.admin.protocol.factory;

import com.cpptrader.admin.protocol.ProtocolConstants;
import com.cpptrader.admin.protocol.ProtocolMessage;
import com.cpptrader.admin.protocol.events.OrderBookUpdateEvent;
import com.cpptrader.admin.protocol.events.OrderUpdateEvent;
import com.cpptrader.admin.protocol.exception.ProtocolException;
import com.cpptrader.admin.protocol.requests.*;
import com.cpptrader.admin.protocol.responses.OrderBookResponse;
import com.cpptrader.admin.protocol.responses.OrderResponse;
import com.cpptrader.admin.protocol.responses.SimpleResponse;
import com.cpptrader.admin.protocol.responses.SymbolResponse;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

public class ProtocolMessageFactory {

    private final Map<Byte, Supplier<ProtocolMessage>> messageCreators = new HashMap<>();
    private final Map<Byte, Class<? extends ProtocolMessage>> messageTypes = new HashMap<>();

    public ProtocolMessageFactory() {
        registerMessageTypes();
    }

    private void registerMessageTypes() {
        registerRequest(ProtocolConstants.ADD_SYMBOL_REQ, AddSymbolRequest::new);
        registerRequest(ProtocolConstants.DELETE_SYMBOL_REQ, DeleteSymbolRequest::new);
        registerRequest(ProtocolConstants.GET_SYMBOL_REQ, GetSymbolRequest::new);
        registerRequest(ProtocolConstants.ADD_ORDER_BOOK_REQ, AddOrderBookRequest::new);
        registerRequest(ProtocolConstants.DELETE_ORDER_BOOK_REQ, DeleteOrderBookRequest::new);
        registerRequest(ProtocolConstants.GET_ORDER_BOOK_REQ, GetOrderBookRequest::new);
        registerRequest(ProtocolConstants.ADD_ORDER_REQ, AddOrderRequest::new);
        registerRequest(ProtocolConstants.REDUCE_ORDER_REQ, ReduceOrderRequest::new);
        registerRequest(ProtocolConstants.MODIFY_ORDER_REQ, ModifyOrderRequest::new);
        registerRequest(ProtocolConstants.MITIGATE_ORDER_REQ, MitigateOrderRequest::new);
        registerRequest(ProtocolConstants.REPLACE_ORDER_REQ, ReplaceOrderRequest::new);
        registerRequest(ProtocolConstants.DELETE_ORDER_REQ, DeleteOrderRequest::new);
        registerRequest(ProtocolConstants.EXECUTE_ORDER_REQ, ExecuteOrderRequest::new);
        registerRequest(ProtocolConstants.GET_ORDER_REQ, GetOrderRequest::new);
        registerRequest(ProtocolConstants.ENABLE_MATCHING_REQ, EnableMatchingRequest::new);
        registerRequest(ProtocolConstants.DISABLE_MATCHING_REQ, DisableMatchingRequest::new);
        registerRequest(ProtocolConstants.SUBSCRIBE_ORDER_BOOK_REQ, SubscribeOrderBookRequest::new);
        registerRequest(ProtocolConstants.SUBSCRIBE_ORDERS_REQ, SubscribeOrdersRequest::new);

        registerResponse(ProtocolConstants.SYMBOL_RESP, SymbolResponse::new);
        registerResponse(ProtocolConstants.ORDER_BOOK_RESP, OrderBookResponse::new);
        registerResponse(ProtocolConstants.ORDER_RESP, OrderResponse::new);
        registerResponse(ProtocolConstants.SIMPLE_RESP, SimpleResponse::new);

        registerPush(ProtocolConstants.ORDER_BOOK_UPDATE_EVT, OrderBookUpdateEvent::new);
        registerPush(ProtocolConstants.ORDER_UPDATE_EVT, OrderUpdateEvent::new);
    }

    private <T extends ProtocolMessage> void registerMessage(byte msgType, Class<T> clazz, Supplier<T> creator) {
        messageCreators.put(msgType, (Supplier<ProtocolMessage>) (Supplier<?>) creator);
        messageTypes.put(msgType, clazz);
    }

    private void registerRequest(byte msgType, Supplier<? extends ProtocolMessage> creator) {
        messageCreators.put(msgType, (Supplier<ProtocolMessage>) creator);
        messageTypes.put(msgType, ProtocolMessage.class);
    }

    private void registerResponse(byte msgType, Supplier<? extends ProtocolMessage> creator) {
        messageCreators.put(msgType, (Supplier<ProtocolMessage>) creator);
        messageTypes.put(msgType, ProtocolMessage.class);
    }

    private void registerPush(byte msgType, Supplier<? extends ProtocolMessage> creator) {
        messageCreators.put(msgType, (Supplier<ProtocolMessage>) creator);
        messageTypes.put(msgType, ProtocolMessage.class);
    }

    public ProtocolMessage createMessage(byte msgType) {
        Supplier<ProtocolMessage> creator = messageCreators.get(msgType);
        if (creator != null) {
            return creator.get();
        }
        throw ProtocolException.invalidMessageType(msgType);
    }

    public ProtocolMessage parseMessage(byte[] data) {
        if (data == null || data.length < ProtocolConstants.HEADER_SIZE) {
            throw ProtocolException.decodingError("Data too short to contain header");
        }

        ByteBuffer buf = ByteBuffer.wrap(data);
        buf.order(ByteOrder.LITTLE_ENDIAN);

        short magic = buf.getShort();
        if (magic != ProtocolConstants.MAGIC) {
            throw ProtocolException.invalidMagic(magic);
        }

        byte version = buf.get();
        if (version != ProtocolConstants.VERSION) {
            throw ProtocolException.unsupportedVersion(version);
        }

        byte msgType = buf.get();
        buf.get();
        buf.get();
        short bodyLen = buf.getShort();
        buf.getInt();
        buf.getShort();
        buf.getShort();

        ProtocolMessage message = createMessage(msgType);
        message.fromBytes(data);
        return message;
    }

    public boolean isRequest(byte msgType) {
        return msgType >= 0x01 && msgType <= 0x12;
    }

    public boolean isResponse(byte msgType) {
        return msgType >= 0x41 && msgType <= 0x44;
    }

    public boolean isPush(byte msgType) {
        return msgType == ProtocolConstants.ORDER_BOOK_UPDATE_EVT ||
               msgType == ProtocolConstants.ORDER_UPDATE_EVT;
    }

    public boolean isHeartbeat(byte msgType) {
        return msgType == ProtocolConstants.HEARTBEAT_REQ ||
               msgType == ProtocolConstants.HEARTBEAT_RESP;
    }

    public Class<? extends ProtocolMessage> getMessageType(byte msgType) {
        return messageTypes.get(msgType);
    }

    public String getMessageTypeName(byte msgType) {
        if (isHeartbeat(msgType)) {
            return msgType == ProtocolConstants.HEARTBEAT_REQ ? "HeartbeatRequest" : "HeartbeatResponse";
        }
        if (isRequest(msgType)) {
            return getRequestTypeName(msgType);
        }
        if (isResponse(msgType)) {
            return getResponseTypeName(msgType);
        }
        if (isPush(msgType)) {
            return getPushTypeName(msgType);
        }
        return String.format("Unknown(0x%02X)", msgType);
    }

    private String getRequestTypeName(byte msgType) {
        return switch (msgType) {
            case ProtocolConstants.ADD_SYMBOL_REQ -> "AddSymbolRequest";
            case ProtocolConstants.DELETE_SYMBOL_REQ -> "DeleteSymbolRequest";
            case ProtocolConstants.GET_SYMBOL_REQ -> "GetSymbolRequest";
            case ProtocolConstants.ADD_ORDER_BOOK_REQ -> "AddOrderBookRequest";
            case ProtocolConstants.DELETE_ORDER_BOOK_REQ -> "DeleteOrderBookRequest";
            case ProtocolConstants.GET_ORDER_BOOK_REQ -> "GetOrderBookRequest";
            case ProtocolConstants.ADD_ORDER_REQ -> "AddOrderRequest";
            case ProtocolConstants.REDUCE_ORDER_REQ -> "ReduceOrderRequest";
            case ProtocolConstants.MODIFY_ORDER_REQ -> "ModifyOrderRequest";
            case ProtocolConstants.MITIGATE_ORDER_REQ -> "MitigateOrderRequest";
            case ProtocolConstants.REPLACE_ORDER_REQ -> "ReplaceOrderRequest";
            case ProtocolConstants.DELETE_ORDER_REQ -> "DeleteOrderRequest";
            case ProtocolConstants.EXECUTE_ORDER_REQ -> "ExecuteOrderRequest";
            case ProtocolConstants.GET_ORDER_REQ -> "GetOrderRequest";
            case ProtocolConstants.ENABLE_MATCHING_REQ -> "EnableMatchingRequest";
            case ProtocolConstants.DISABLE_MATCHING_REQ -> "DisableMatchingRequest";
            case ProtocolConstants.SUBSCRIBE_ORDER_BOOK_REQ -> "SubscribeOrderBookRequest";
            case ProtocolConstants.SUBSCRIBE_ORDERS_REQ -> "SubscribeOrdersRequest";
            default -> String.format("UnknownRequest(0x%02X)", msgType);
        };
    }

    private String getResponseTypeName(byte msgType) {
        return switch (msgType) {
            case ProtocolConstants.SYMBOL_RESP -> "SymbolResponse";
            case ProtocolConstants.ORDER_BOOK_RESP -> "OrderBookResponse";
            case ProtocolConstants.ORDER_RESP -> "OrderResponse";
            case ProtocolConstants.SIMPLE_RESP -> "SimpleResponse";
            default -> String.format("UnknownResponse(0x%02X)", msgType);
        };
    }

    private String getPushTypeName(byte msgType) {
        return switch (msgType) {
            case ProtocolConstants.ORDER_BOOK_UPDATE_EVT -> "OrderBookUpdateEvent";
            case ProtocolConstants.ORDER_UPDATE_EVT -> "OrderUpdateEvent";
            default -> String.format("UnknownPush(0x%02X)", msgType);
        };
    }

    public AddSymbolRequest createAddSymbolRequest(int id, String name) {
        return new AddSymbolRequest(id, name);
    }

    public DeleteSymbolRequest createDeleteSymbolRequest(int id) {
        return new DeleteSymbolRequest(id);
    }

    public GetSymbolRequest createGetSymbolRequest(int id) {
        return new GetSymbolRequest(id);
    }

    public AddOrderRequest createAddOrderRequest(long id, int symbolId, long accountId, byte orderType, byte orderSide,
                                                  long price, long stopPrice, long quantity,
                                                  byte timeInForce, byte stpPolicy, long maxVisibleQty,
                                                  long slippage, long trailingDistance, long trailingStep) {
        return new AddOrderRequest(id, symbolId, accountId, orderType, orderSide, price, stopPrice, quantity,
                timeInForce, stpPolicy, maxVisibleQty, slippage, trailingDistance, trailingStep);
    }

    public DeleteOrderRequest createDeleteOrderRequest(long id) {
        return new DeleteOrderRequest(id);
    }

    public GetOrderRequest createGetOrderRequest(long id) {
        return new GetOrderRequest(id);
    }

    public SimpleResponse createSimpleResponse(byte errorCode) {
        return new SimpleResponse(errorCode);
    }

    public SymbolResponse createSymbolResponse(byte errorCode, int symbolId, String symbolName) {
        return new SymbolResponse(errorCode, symbolId, symbolName);
    }

    private static ProtocolMessageFactory instance;

    public static synchronized ProtocolMessageFactory getInstance() {
        if (instance == null) {
            instance = new ProtocolMessageFactory();
        }
        return instance;
    }
}