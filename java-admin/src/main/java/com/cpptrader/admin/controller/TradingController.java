package com.cpptrader.admin.controller;

import com.cpptrader.admin.protocol.ProtocolConstants;
import com.cpptrader.admin.protocol.client.ProtocolClientService;
import com.cpptrader.admin.protocol.requests.*;
import com.cpptrader.admin.protocol.responses.OrderBookResponse;
import com.cpptrader.admin.protocol.responses.OrderResponse;
import com.cpptrader.admin.protocol.responses.SimpleResponse;
import com.cpptrader.admin.protocol.responses.SymbolResponse;
import com.cpptrader.admin.risk.RiskCheckService;
import com.cpptrader.admin.stp.SelfTradePreventionService;
import com.cpptrader.admin.user.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api")
public class TradingController {

    private final ProtocolClientService protocolClient;
    private final RiskCheckService riskCheckService;
    private final SelfTradePreventionService stpService;
    private final UserService userService;

    public TradingController(ProtocolClientService protocolClient, RiskCheckService riskCheckService,
                              SelfTradePreventionService stpService, UserService userService) {
        this.protocolClient = protocolClient;
        this.riskCheckService = riskCheckService;
        this.stpService = stpService;
        this.userService = userService;
    }

    private static byte getMsgType(byte[] data) {
        if (data == null || data.length < ProtocolConstants.HEADER_SIZE) {
            return 0;
        }
        return data[3];
    }

    private static byte getFlags(byte[] data) {
        if (data == null || data.length < ProtocolConstants.HEADER_SIZE) {
            return 0;
        }
        return data[4];
    }

    private static boolean hasErrorFlag(byte[] data) {
        return (getFlags(data) & ProtocolConstants.FLAG_ERROR) != 0;
    }

    private static SimpleResponse parseSimpleResponse(byte[] data) {
        SimpleResponse resp = new SimpleResponse();
        if (data != null) {
            resp.fromBytes(data);
        }
        return resp;
    }

    private static boolean isBackendUnavailable(byte[] respBytes) {
        return respBytes == null;
    }

    private static Map<String, Object> backendUnavailableResult() {
        Map<String, Object> result = new HashMap<>();
        result.put("error", "BACKEND_UNAVAILABLE");
        result.put("message", "C++ trading core is not responding");
        return result;
    }

    @GetMapping("/status")
    public Map<String, Object> getStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("connected", protocolClient.isConnected());
        status.put("timestamp", System.currentTimeMillis());
        return status;
    }

    @PostMapping("/symbols")
    public Map<String, Object> addSymbol(@RequestParam int id, @RequestParam String name) {
        AddSymbolRequest req = new AddSymbolRequest(id, name);
        byte[] respBytes = protocolClient.sendSync(req.toBytes());
        if (isBackendUnavailable(respBytes)) return backendUnavailableResult();

        Map<String, Object> result = new HashMap<>();
        if (getMsgType(respBytes) == ProtocolConstants.SIMPLE_RESP) {
            SimpleResponse resp = parseSimpleResponse(respBytes);
            result.put("error", ProtocolConstants.ErrorCode.name(resp.getErrorCode()));
            return result;
        }

        SymbolResponse resp = new SymbolResponse();
        resp.fromBytes(respBytes);
        result.put("error", ProtocolConstants.ErrorCode.name(resp.getErrorCode()));
        if (resp.getErrorCode() == ProtocolConstants.ErrorCode.OK) {
            result.put("id", resp.getSymbolId());
            result.put("name", resp.getSymbolName());
        }
        return result;
    }

    @DeleteMapping("/symbols/{id}")
    public Map<String, Object> deleteSymbol(@PathVariable int id) {
        DeleteSymbolRequest req = new DeleteSymbolRequest(id);
        byte[] respBytes = protocolClient.sendSync(req.toBytes());
        if (isBackendUnavailable(respBytes)) return backendUnavailableResult();
        SimpleResponse resp = parseSimpleResponse(respBytes);
        Map<String, Object> result = new HashMap<>();
        result.put("error", ProtocolConstants.ErrorCode.name(resp.getErrorCode()));
        return result;
    }

    @GetMapping("/symbols/{id}")
    public Map<String, Object> getSymbol(@PathVariable int id) {
        GetSymbolRequest req = new GetSymbolRequest(id);
        byte[] respBytes = protocolClient.sendSync(req.toBytes());
        if (isBackendUnavailable(respBytes)) return backendUnavailableResult();
        Map<String, Object> result = new HashMap<>();

        if (getMsgType(respBytes) == ProtocolConstants.SIMPLE_RESP) {
            SimpleResponse resp = parseSimpleResponse(respBytes);
            result.put("error", ProtocolConstants.ErrorCode.name(resp.getErrorCode()));
            return result;
        }

        SymbolResponse resp = new SymbolResponse();
        resp.fromBytes(respBytes);
        result.put("error", ProtocolConstants.ErrorCode.name(resp.getErrorCode()));
        if (resp.getErrorCode() == ProtocolConstants.ErrorCode.OK) {
            result.put("id", resp.getSymbolId());
            result.put("name", resp.getSymbolName());
        }
        return result;
    }

    @PostMapping("/orderbooks")
    public Map<String, Object> addOrderBook(@RequestParam int symbolId) {
        AddOrderBookRequest req = new AddOrderBookRequest(symbolId);
        byte[] respBytes = protocolClient.sendSync(req.toBytes());
        if (isBackendUnavailable(respBytes)) return backendUnavailableResult();
        SimpleResponse resp = parseSimpleResponse(respBytes);
        Map<String, Object> result = new HashMap<>();
        result.put("error", ProtocolConstants.ErrorCode.name(resp.getErrorCode()));
        return result;
    }

    @DeleteMapping("/orderbooks/{symbolId}")
    public Map<String, Object> deleteOrderBook(@PathVariable int symbolId) {
        DeleteOrderBookRequest req = new DeleteOrderBookRequest(symbolId);
        byte[] respBytes = protocolClient.sendSync(req.toBytes());
        if (isBackendUnavailable(respBytes)) return backendUnavailableResult();
        SimpleResponse resp = parseSimpleResponse(respBytes);
        Map<String, Object> result = new HashMap<>();
        result.put("error", ProtocolConstants.ErrorCode.name(resp.getErrorCode()));
        return result;
    }

    @GetMapping("/orderbooks/{symbolId}")
    public Map<String, Object> getOrderBook(@PathVariable int symbolId, @RequestParam(defaultValue = "5") int depth) {
        GetOrderBookRequest req = new GetOrderBookRequest(symbolId, depth);
        byte[] respBytes = protocolClient.sendSync(req.toBytes());
        if (isBackendUnavailable(respBytes)) return backendUnavailableResult();
        Map<String, Object> result = new HashMap<>();

        if (getMsgType(respBytes) == ProtocolConstants.SIMPLE_RESP) {
            SimpleResponse resp = parseSimpleResponse(respBytes);
            result.put("error", ProtocolConstants.ErrorCode.name(resp.getErrorCode()));
            return result;
        }

        OrderBookResponse resp = new OrderBookResponse();
        resp.fromBytes(respBytes);
        result.put("symbolId", resp.getSymbolId());
        if (resp.getBestBid().totalVolume > 0) {
            result.put("bestBidPrice", resp.getBestBid().price);
            result.put("bestBidVolume", resp.getBestBid().totalVolume);
        }
        if (resp.getBestAsk().totalVolume > 0) {
            result.put("bestAskPrice", resp.getBestAsk().price);
            result.put("bestAskVolume", resp.getBestAsk().totalVolume);
        }
        return result;
    }

    @PostMapping("/orders")
    public Map<String, Object> addOrder(@RequestParam(required = false) Long id,
                                         @RequestParam int symbolId,
                                         @RequestParam String side,
                                         @RequestParam(defaultValue = "LIMIT") String type,
                                         @RequestParam long price,
                                         @RequestParam long quantity) {
        if (id == null) {
            id = System.currentTimeMillis() * 1000 + (long)(Math.random() * 1000);
        }

        Long currentUserId = userService.getCurrentUserId();
        var riskResult = riskCheckService.check(currentUserId, price * quantity, symbolId);
        if (!riskResult.passed) {
            Map<String, Object> result = new HashMap<>();
            result.put("error", "RISK_REJECTED");
            result.put("message", riskResult.message);
            return result;
        }

        byte orderSide = "SELL".equalsIgnoreCase(side) ? ProtocolConstants.OrderSide.SELL : ProtocolConstants.OrderSide.BUY;

        var stpResult = stpService.check(currentUserId, id, symbolId, orderSide, price, quantity);
        if (!stpResult.passed) {
            if (stpResult.shouldRejectIncoming()) {
                Map<String, Object> result = new HashMap<>();
                result.put("error", "SELF_TRADE_PREVENTED");
                result.put("message", stpResult.rejectReason);
                result.put("stpPolicy", stpResult.policy);
                result.put("stpAction", stpResult.actionTaken);
                result.put("overlapCount", stpResult.overlapCount);
                return result;
            }
            if (stpResult.shouldDecrement()) {
                long decrementQty = stpService.computeDecrementQuantity(
                        currentUserId, symbolId, orderSide, price, quantity);
                if (decrementQty >= quantity) {
                    Map<String, Object> result = new HashMap<>();
                    result.put("error", "SELF_TRADE_PREVENTED");
                    result.put("message", "Entire quantity would self-trade");
                    result.put("stpPolicy", stpResult.policy);
                    result.put("stpAction", "QUANTITY_DECREMENTED_FULL");
                    return result;
                }
                quantity = quantity - decrementQty;
                log.info("STP DECREMENT: orderId={}, originalQty={}, decrementQty={}, newQty={}",
                        id, quantity + decrementQty, decrementQty, quantity);
            }
        }

        byte orderType = switch (type.toUpperCase()) {
            case "LIMIT" -> ProtocolConstants.OrderType.LIMIT;
            case "MARKET" -> ProtocolConstants.OrderType.MARKET;
            case "STOP" -> ProtocolConstants.OrderType.STOP;
            case "STOP_LIMIT" -> ProtocolConstants.OrderType.STOP_LIMIT;
            case "TRAILING_STOP" -> ProtocolConstants.OrderType.TRAILING_STOP;
            case "TRAILING_STOP_LIMIT" -> ProtocolConstants.OrderType.TRAILING_STOP_LIMIT;
            default -> ProtocolConstants.OrderType.LIMIT;
        };

        long stopPrice = 0;
        long slippage = ProtocolConstants.NO_SLIPPAGE;
        long trailingDistance = 0;
        long trailingStep = 0;

        switch (orderType) {
            case ProtocolConstants.OrderType.MARKET:
                slippage = 0;
                break;
            case ProtocolConstants.OrderType.STOP:
                stopPrice = price;
                slippage = 0;
                break;
            case ProtocolConstants.OrderType.STOP_LIMIT:
                stopPrice = price;
                break;
            case ProtocolConstants.OrderType.TRAILING_STOP:
                trailingDistance = 100;
                trailingStep = 10;
                slippage = 0;
                break;
            case ProtocolConstants.OrderType.TRAILING_STOP_LIMIT:
                trailingDistance = 100;
                trailingStep = 10;
                break;
            default:
                break;
        }

        AddOrderRequest req = new AddOrderRequest(
                id,
                symbolId,
                0L,
                orderType,
                orderSide,
                price,
                stopPrice,
                quantity,
                ProtocolConstants.TimeInForce.GTC,
                (byte) 0,
                quantity,
                slippage,
                trailingDistance,
                trailingStep
        );

        log.info("Adding order: id={}, type={}, side={}, price={}, qty={}, slippage={}",
                id, type, side, price, quantity, slippage);

        byte[] respBytes = protocolClient.sendSync(req.toBytes());
        if (isBackendUnavailable(respBytes)) return backendUnavailableResult();
        Map<String, Object> result = new HashMap<>();

        if (getMsgType(respBytes) == ProtocolConstants.SIMPLE_RESP) {
            SimpleResponse resp = parseSimpleResponse(respBytes);
            result.put("error", ProtocolConstants.ErrorCode.name(resp.getErrorCode()));
            return result;
        }

        OrderResponse resp = new OrderResponse();
        resp.fromBytes(respBytes);
        result.put("error", ProtocolConstants.ErrorCode.name(resp.getErrorCode()));
        if (resp.getErrorCode() == ProtocolConstants.ErrorCode.OK) {
            stpService.addActiveOrder(currentUserId, resp.getOrder().id,
                    resp.getOrder().symbolId, resp.getOrder().orderSide,
                    resp.getOrder().price, resp.getOrder().leavesQuantity);

            result.put("orderId", resp.getOrder().id);
            result.put("symbolId", resp.getOrder().symbolId);
            result.put("side", ProtocolConstants.OrderSide.name(resp.getOrder().orderSide));
            result.put("price", resp.getOrder().price);
            result.put("quantity", resp.getOrder().quantity);
            result.put("leavesQty", resp.getOrder().leavesQuantity);
        }
        return result;
    }

    @DeleteMapping("/orders/{id}")
    public Map<String, Object> deleteOrder(@PathVariable long id) {
        DeleteOrderRequest req = new DeleteOrderRequest(id);
        byte[] respBytes = protocolClient.sendSync(req.toBytes());
        if (isBackendUnavailable(respBytes)) return backendUnavailableResult();
        Map<String, Object> result = new HashMap<>();

        if (getMsgType(respBytes) == ProtocolConstants.ORDER_RESP) {
            OrderResponse resp = new OrderResponse();
            resp.fromBytes(respBytes);
            result.put("error", ProtocolConstants.ErrorCode.name(resp.getErrorCode()));
            return result;
        }

        SimpleResponse resp = parseSimpleResponse(respBytes);
        result.put("error", ProtocolConstants.ErrorCode.name(resp.getErrorCode()));
        return result;
    }

    @GetMapping("/orders/{id}")
    public Map<String, Object> getOrder(@PathVariable long id) {
        GetOrderRequest req = new GetOrderRequest(id);
        byte[] respBytes = protocolClient.sendSync(req.toBytes());
        if (isBackendUnavailable(respBytes)) return backendUnavailableResult();
        Map<String, Object> result = new HashMap<>();

        if (getMsgType(respBytes) == ProtocolConstants.SIMPLE_RESP) {
            SimpleResponse resp = parseSimpleResponse(respBytes);
            result.put("error", ProtocolConstants.ErrorCode.name(resp.getErrorCode()));
            return result;
        }

        OrderResponse resp = new OrderResponse();
        resp.fromBytes(respBytes);
        result.put("error", ProtocolConstants.ErrorCode.name(resp.getErrorCode()));
        if (resp.getErrorCode() == ProtocolConstants.ErrorCode.OK) {
            result.put("orderId", resp.getOrder().id);
            result.put("symbolId", resp.getOrder().symbolId);
            result.put("side", ProtocolConstants.OrderSide.name(resp.getOrder().orderSide));
            result.put("price", resp.getOrder().price);
            result.put("quantity", resp.getOrder().quantity);
            result.put("executedQty", resp.getOrder().executedQuantity);
            result.put("leavesQty", resp.getOrder().leavesQuantity);
        }
        return result;
    }

    @PostMapping("/matching/enable")
    public Map<String, Object> enableMatching() {
        EnableMatchingRequest req = new EnableMatchingRequest();
        byte[] respBytes = protocolClient.sendSync(req.toBytes());
        if (isBackendUnavailable(respBytes)) return backendUnavailableResult();
        SimpleResponse resp = parseSimpleResponse(respBytes);
        Map<String, Object> result = new HashMap<>();
        result.put("error", ProtocolConstants.ErrorCode.name(resp.getErrorCode()));
        return result;
    }

    @PostMapping("/matching/disable")
    public Map<String, Object> disableMatching() {
        DisableMatchingRequest req = new DisableMatchingRequest();
        byte[] respBytes = protocolClient.sendSync(req.toBytes());
        if (isBackendUnavailable(respBytes)) return backendUnavailableResult();
        SimpleResponse resp = parseSimpleResponse(respBytes);
        Map<String, Object> result = new HashMap<>();
        result.put("error", ProtocolConstants.ErrorCode.name(resp.getErrorCode()));
        return result;
    }
}
