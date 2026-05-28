package com.cpptrader.admin.protocol.validation;

import com.cpptrader.admin.protocol.ProtocolConstants;
import com.cpptrader.admin.protocol.ProtocolMessage;
import com.cpptrader.admin.protocol.exception.ProtocolException;
import com.cpptrader.admin.protocol.security.HmacSigner;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class ProtocolValidator {

    public static class ValidationResult {
        private final boolean valid;
        private final List<String> errors;
        private final List<String> warnings;

        private ValidationResult(boolean valid) {
            this.valid = valid;
            this.errors = new ArrayList<>();
            this.warnings = new ArrayList<>();
        }

        public static ValidationResult success() {
            return new ValidationResult(true);
        }

        public static ValidationResult failure(String error) {
            ValidationResult result = new ValidationResult(false);
            result.errors.add(error);
            return result;
        }

        public ValidationResult withError(String error) {
            this.errors.add(error);
            return this;
        }

        public ValidationResult withWarning(String warning) {
            this.warnings.add(warning);
            return this;
        }

        public boolean isValid() {
            return valid && errors.isEmpty();
        }

        public List<String> getErrors() {
            return errors;
        }

        public List<String> getWarnings() {
            return warnings;
        }

        public String getErrorMessage() {
            return String.join("; ", errors);
        }

        public String getWarningMessage() {
            return String.join("; ", warnings);
        }
    }

    public static ValidationResult validateHeader(byte[] data) {
        ValidationResult result = new ValidationResult(true);

        if (data == null) {
            return result.withError("Data buffer is null");
        }

        if (data.length < ProtocolConstants.HEADER_SIZE) {
            return result.withError(String.format(
                    "Data too short: %d bytes, minimum required: %d",
                    data.length, ProtocolConstants.HEADER_SIZE));
        }

        ByteBuffer buf = ByteBuffer.wrap(data);
        buf.order(java.nio.ByteOrder.LITTLE_ENDIAN);

        short magic = buf.getShort();
        if (magic != ProtocolConstants.MAGIC) {
            result.withError(String.format("Invalid magic: 0x%04X, expected: 0x%04X",
                    magic, ProtocolConstants.MAGIC));
        }

        byte version = buf.get();
        if (version != ProtocolConstants.VERSION) {
            result.withError(String.format("Unsupported version: %d, supported: %d",
                    version, ProtocolConstants.VERSION));
        }

        byte msgType = buf.get();
        if (!isValidMessageType(msgType)) {
            result.withWarning(String.format("Unknown message type: 0x%02X", msgType));
        }

        byte flags = buf.get();
        if (!isValidFlags(flags)) {
            result.withWarning(String.format("Invalid flags combination: 0x%02X", flags));
        }

        buf.get();
        short bodyLen = buf.getShort();
        if (bodyLen < 0 || bodyLen > 65535) {
            result.withError(String.format("Invalid body length: %d", bodyLen));
        }

        return result;
    }

    public static ValidationResult validateCompleteMessage(byte[] data) {
        ValidationResult headerResult = validateHeader(data);
        if (!headerResult.isValid()) {
            return headerResult;
        }

        ByteBuffer buf = ByteBuffer.wrap(data);
        buf.order(java.nio.ByteOrder.LITTLE_ENDIAN);
        buf.getShort();
        buf.get();
        buf.get();
        buf.get();
        buf.get();
        short bodyLen = buf.getShort();
        buf.getInt();
        buf.getShort();
        buf.getShort();

        if (data.length < ProtocolConstants.HEADER_SIZE + bodyLen) {
            return headerResult.withError(String.format(
                    "Incomplete message: expected %d bytes, got %d",
                    ProtocolConstants.HEADER_SIZE + bodyLen, data.length));
        }

        return headerResult;
    }

    public static ValidationResult validateSymbolId(int symbolId) {
        ValidationResult result = ValidationResult.success();
        if (symbolId <= 0) {
            result.withWarning(String.format("Symbol ID should be positive: %d", symbolId));
        }
        if (symbolId > 0x7FFFFFFF) {
            result.withError(String.format("Symbol ID too large: %d", symbolId));
        }
        return result;
    }

    public static ValidationResult validateOrderId(long orderId) {
        ValidationResult result = ValidationResult.success();
        if (orderId <= 0) {
            result.withWarning(String.format("Order ID should be positive: %d", orderId));
        }
        return result;
    }

    public static ValidationResult validatePrice(long price) {
        ValidationResult result = ValidationResult.success();
        if (price < 0) {
            result.withError(String.format("Price cannot be negative: %d", price));
        }
        if (price > 9999999999999999L) {
            result.withWarning(String.format("Price value is very large: %d", price));
        }
        return result;
    }

    public static ValidationResult validateQuantity(long quantity) {
        ValidationResult result = ValidationResult.success();
        if (quantity <= 0) {
            result.withError(String.format("Quantity must be positive: %d", quantity));
        }
        if (quantity > 9999999999999999L) {
            result.withWarning(String.format("Quantity value is very large: %d", quantity));
        }
        return result;
    }

    public static ValidationResult validateSymbolName(String name) {
        ValidationResult result = ValidationResult.success();
        if (name == null || name.isEmpty()) {
            result.withError("Symbol name cannot be null or empty");
            return result;
        }
        if (name.length() > ProtocolConstants.SYMBOL_NAME_SIZE) {
            result.withError(String.format("Symbol name too long: %d, max: %d",
                    name.length(), ProtocolConstants.SYMBOL_NAME_SIZE));
        }
        byte[] bytes = name.getBytes(StandardCharsets.UTF_8);
        for (byte b : bytes) {
            if (b < 32 || b > 126) {
                result.withWarning("Symbol name contains non-printable characters");
                break;
            }
        }
        return result;
    }

    public static ValidationResult validateOrderType(byte orderType) {
        ValidationResult result = ValidationResult.success();
        switch (orderType) {
            case ProtocolConstants.OrderType.LIMIT,
                 ProtocolConstants.OrderType.MARKET,
                 ProtocolConstants.OrderType.STOP,
                 ProtocolConstants.OrderType.STOP_LIMIT,
                 ProtocolConstants.OrderType.TRAILING_STOP,
                 ProtocolConstants.OrderType.TRAILING_STOP_LIMIT -> {}
            default -> result.withError(String.format("Invalid order type: %d", orderType));
        }
        return result;
    }

    public static ValidationResult validateErrorCode(byte errorCode) {
        ValidationResult result = ValidationResult.success();
        switch (errorCode) {
            case ProtocolConstants.ErrorCode.OK,
                 ProtocolConstants.ErrorCode.SYMBOL_DUPLICATE,
                 ProtocolConstants.ErrorCode.SYMBOL_NOT_FOUND,
                 ProtocolConstants.ErrorCode.ORDER_BOOK_DUPLICATE,
                 ProtocolConstants.ErrorCode.ORDER_BOOK_NOT_FOUND,
                 ProtocolConstants.ErrorCode.ORDER_DUPLICATE,
                 ProtocolConstants.ErrorCode.ORDER_NOT_FOUND,
                 ProtocolConstants.ErrorCode.ORDER_ID_INVALID,
                 ProtocolConstants.ErrorCode.ORDER_TYPE_INVALID,
                 ProtocolConstants.ErrorCode.ORDER_PARAMETER_INVALID,
                 ProtocolConstants.ErrorCode.ORDER_QUANTITY_INVALID,
                 ProtocolConstants.ErrorCode.NOT_AUTHENTICATED,
                 ProtocolConstants.ErrorCode.NOT_AUTHORIZED,
                 ProtocolConstants.ErrorCode.AUTH_EXPIRED,
                 ProtocolConstants.ErrorCode.INVALID_SIGNATURE,
                 ProtocolConstants.ErrorCode.REPLAY_DETECTED,
                 ProtocolConstants.ErrorCode.RATE_LIMITED,
                 ProtocolConstants.ErrorCode.CONNECTION_REJECTED,
                 ProtocolConstants.ErrorCode.SERVER_SHUTTING_DOWN -> {}
            default -> result.withError(String.format("Invalid error code: %d", errorCode));
        }
        return result;
    }

    public static ValidationResult validateOrderSide(byte orderSide) {
        ValidationResult result = ValidationResult.success();
        switch (orderSide) {
            case ProtocolConstants.OrderSide.BUY,
                 ProtocolConstants.OrderSide.SELL -> {}
            default -> result.withError(String.format("Invalid order side: %d", orderSide));
        }
        return result;
    }

    public static ValidationResult validateTimeInForce(byte tif) {
        ValidationResult result = ValidationResult.success();
        switch (tif) {
            case ProtocolConstants.TimeInForce.GTC,
                 ProtocolConstants.TimeInForce.IOC,
                 ProtocolConstants.TimeInForce.FOK,
                 ProtocolConstants.TimeInForce.AON -> {}
            default -> result.withError(String.format("Invalid time-in-force: %d", tif));
        }
        return result;
    }

    public static ValidationResult validateMessage(ProtocolMessage message) {
        ValidationResult result = ValidationResult.success();

        if (message == null) {
            return result.withError("Message cannot be null");
        }

        byte msgType = message.getMsgType();
        if (!isValidMessageType(msgType)) {
            result.withError(String.format("Invalid message type: 0x%02X", msgType));
        }

        int bodySize = message.getBodySize();
        if (bodySize < 0) {
            result.withError(String.format("Invalid body size: %d", bodySize));
        }

        if (bodySize > 65535) {
            result.withWarning(String.format("Body size very large: %d bytes", bodySize));
        }

        return result;
    }

    public static ValidationResult validateByteBuffer(ByteBuffer buf, int requiredBytes, String operation) {
        ValidationResult result = ValidationResult.success();
        if (buf == null) {
            return result.withError("ByteBuffer is null");
        }
        if (buf.remaining() < requiredBytes) {
            return result.withError(String.format("Buffer underflow during %s: need %d bytes, have %d",
                    operation, requiredBytes, buf.remaining()));
        }
        return result;
    }

    private static boolean isValidMessageType(byte msgType) {
        return msgType == ProtocolConstants.ADD_SYMBOL_REQ ||
               msgType == ProtocolConstants.DELETE_SYMBOL_REQ ||
               msgType == ProtocolConstants.GET_SYMBOL_REQ ||
               msgType == ProtocolConstants.ADD_ORDER_BOOK_REQ ||
               msgType == ProtocolConstants.DELETE_ORDER_BOOK_REQ ||
               msgType == ProtocolConstants.GET_ORDER_BOOK_REQ ||
               msgType == ProtocolConstants.ADD_ORDER_REQ ||
               msgType == ProtocolConstants.REDUCE_ORDER_REQ ||
               msgType == ProtocolConstants.MODIFY_ORDER_REQ ||
               msgType == ProtocolConstants.MITIGATE_ORDER_REQ ||
               msgType == ProtocolConstants.REPLACE_ORDER_REQ ||
               msgType == ProtocolConstants.DELETE_ORDER_REQ ||
               msgType == ProtocolConstants.EXECUTE_ORDER_REQ ||
               msgType == ProtocolConstants.GET_ORDER_REQ ||
               msgType == ProtocolConstants.ENABLE_MATCHING_REQ ||
               msgType == ProtocolConstants.DISABLE_MATCHING_REQ ||
               msgType == ProtocolConstants.SUBSCRIBE_ORDER_BOOK_REQ ||
               msgType == ProtocolConstants.SUBSCRIBE_ORDERS_REQ ||
               msgType == ProtocolConstants.SYMBOL_RESP ||
               msgType == ProtocolConstants.ORDER_BOOK_RESP ||
               msgType == ProtocolConstants.ORDER_RESP ||
               msgType == ProtocolConstants.SIMPLE_RESP ||
               msgType == ProtocolConstants.ORDER_BOOK_UPDATE_EVT ||
               msgType == ProtocolConstants.ORDER_UPDATE_EVT ||
               msgType == ProtocolConstants.HEARTBEAT_REQ ||
               msgType == ProtocolConstants.HEARTBEAT_RESP ||
               msgType == ProtocolConstants.SHUTDOWN_NOTIFY ||
               msgType == ProtocolConstants.EVENT_ACK ||
               msgType == ProtocolConstants.RECONCILE_REQUEST ||
               msgType == ProtocolConstants.RECONCILE_RESPONSE ||
               msgType == ProtocolConstants.AUTH_REQUEST ||
               msgType == ProtocolConstants.AUTH_RESPONSE;
    }

    private static boolean isValidFlags(byte flags) {
        if (flags == 0) {
            return true;
        }

        int flagCount = 0;
        if ((flags & ProtocolConstants.FLAG_REQUEST) != 0) flagCount++;
        if ((flags & ProtocolConstants.FLAG_RESPONSE) != 0) flagCount++;
        if ((flags & ProtocolConstants.FLAG_PUSH) != 0) flagCount++;
        if ((flags & ProtocolConstants.FLAG_ERROR) != 0) flagCount++;
        if ((flags & ProtocolConstants.FLAG_HEARTBEAT) != 0) flagCount++;

        if (flagCount > 1 && (flags & (ProtocolConstants.FLAG_REQUEST | ProtocolConstants.FLAG_RESPONSE | ProtocolConstants.FLAG_PUSH)) != 0) {
            return false;
        }

        return true;
    }

    public static ValidationResult validateHmacPrefix(byte[] sessionKey, short hmacPrefix, int sequence, byte msgType, byte flags, short bodyLen, byte[] body) {
        ValidationResult result = ValidationResult.success();
        if (sessionKey == null) {
            return result;
        }
        if (hmacPrefix == 0 && msgType != ProtocolConstants.AUTH_REQUEST && msgType != ProtocolConstants.AUTH_RESPONSE) {
            result.withWarning(String.format("HmacPrefix is zero for non-auth message type 0x%02X", msgType));
        }
        if (hmacPrefix != 0 && sessionKey != null) {
            boolean valid = HmacSigner.verifyHmacPrefix(sessionKey, hmacPrefix, sequence, msgType, flags, bodyLen, body);
            if (!valid) {
                result.withError(String.format("HMAC prefix verification failed for message type 0x%02X, sequence %d", msgType, sequence));
            }
        }
        return result;
    }

    public static void requireValid(ValidationResult result) {
        if (!result.isValid()) {
            throw new ProtocolException(result.getErrorMessage());
        }
    }

    public static void requireValidOrThrow(ValidationResult result, String customMessage) {
        if (!result.isValid()) {
            throw new ProtocolException(
                    customMessage != null ? customMessage : result.getErrorMessage());
        }
    }
}