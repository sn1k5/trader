package com.cpptrader.admin.protocol.exception;

import lombok.extern.slf4j.Slf4j;

import java.util.function.Consumer;

@Slf4j
public class ProtocolErrorHandler {

    public interface ErrorCallback {
        void onError(ProtocolException exception);
        void onWarning(String warning);
        void onDebug(String debug);
    }

    private ErrorCallback callback;
    private boolean logEnabled = true;
    private boolean throwOnError = false;

    public ProtocolErrorHandler() {
    }

    public ProtocolErrorHandler(ErrorCallback callback) {
        this.callback = callback;
    }

    public void setCallback(ErrorCallback callback) {
        this.callback = callback;
    }

    public void setLogEnabled(boolean logEnabled) {
        this.logEnabled = logEnabled;
    }

    public void setThrowOnError(boolean throwOnError) {
        this.throwOnError = throwOnError;
    }

    public void handleException(ProtocolException exception) {
        if (logEnabled) {
            log.error("Protocol error: {}", exception.toString(), exception);
        }

        if (callback != null) {
            callback.onError(exception);
        }

        if (throwOnError) {
            throw exception;
        }
    }

    public void handleWarning(String warning) {
        if (logEnabled) {
            log.warn("Protocol warning: {}", warning);
        }

        if (callback != null) {
            callback.onWarning(warning);
        }
    }

    public void handleDebug(String debug) {
        if (logEnabled) {
            log.debug("Protocol debug: {}", debug);
        }

        if (callback != null) {
            callback.onDebug(debug);
        }
    }

    public ProtocolException validateMagic(short magic) {
        if (magic != com.cpptrader.admin.protocol.ProtocolConstants.MAGIC) {
            ProtocolException ex = ProtocolException.invalidMagic(magic);
            handleException(ex);
            return ex;
        }
        return null;
    }

    public ProtocolException validateVersion(byte version) {
        if (version != com.cpptrader.admin.protocol.ProtocolConstants.VERSION) {
            ProtocolException ex = ProtocolException.unsupportedVersion(version);
            handleException(ex);
            return ex;
        }
        return null;
    }

    public ProtocolException validateMessageType(byte msgType) {
        if (!isValidMessageType(msgType)) {
            ProtocolException ex = ProtocolException.invalidMessageType(msgType);
            handleException(ex);
            return ex;
        }
        return null;
    }

    public void validateNotNull(Object obj, String fieldName) {
        if (obj == null) {
            handleWarning(String.format("Field '%s' is null", fieldName));
        }
    }

    public void validateRange(long value, long min, long max, String fieldName) {
        if (value < min || value > max) {
            handleWarning(String.format("Field '%s' value %d is out of range [%d, %d]", fieldName, value, min, max));
        }
    }

    public void validateStringLength(String value, int maxLength, String fieldName) {
        if (value != null && value.length() > maxLength) {
            handleWarning(String.format("Field '%s' length %d exceeds max %d", fieldName, value.length(), maxLength));
        }
    }

    private boolean isValidMessageType(byte msgType) {
        byte[] validTypes = {
                com.cpptrader.admin.protocol.ProtocolConstants.ADD_SYMBOL_REQ,
                com.cpptrader.admin.protocol.ProtocolConstants.DELETE_SYMBOL_REQ,
                com.cpptrader.admin.protocol.ProtocolConstants.GET_SYMBOL_REQ,
                com.cpptrader.admin.protocol.ProtocolConstants.ADD_ORDER_BOOK_REQ,
                com.cpptrader.admin.protocol.ProtocolConstants.DELETE_ORDER_BOOK_REQ,
                com.cpptrader.admin.protocol.ProtocolConstants.GET_ORDER_BOOK_REQ,
                com.cpptrader.admin.protocol.ProtocolConstants.ADD_ORDER_REQ,
                com.cpptrader.admin.protocol.ProtocolConstants.REDUCE_ORDER_REQ,
                com.cpptrader.admin.protocol.ProtocolConstants.MODIFY_ORDER_REQ,
                com.cpptrader.admin.protocol.ProtocolConstants.MITIGATE_ORDER_REQ,
                com.cpptrader.admin.protocol.ProtocolConstants.REPLACE_ORDER_REQ,
                com.cpptrader.admin.protocol.ProtocolConstants.DELETE_ORDER_REQ,
                com.cpptrader.admin.protocol.ProtocolConstants.EXECUTE_ORDER_REQ,
                com.cpptrader.admin.protocol.ProtocolConstants.GET_ORDER_REQ,
                com.cpptrader.admin.protocol.ProtocolConstants.ENABLE_MATCHING_REQ,
                com.cpptrader.admin.protocol.ProtocolConstants.DISABLE_MATCHING_REQ,
                com.cpptrader.admin.protocol.ProtocolConstants.SUBSCRIBE_ORDER_BOOK_REQ,
                com.cpptrader.admin.protocol.ProtocolConstants.SUBSCRIBE_ORDERS_REQ,
                com.cpptrader.admin.protocol.ProtocolConstants.SYMBOL_RESP,
                com.cpptrader.admin.protocol.ProtocolConstants.ORDER_BOOK_RESP,
                com.cpptrader.admin.protocol.ProtocolConstants.ORDER_RESP,
                com.cpptrader.admin.protocol.ProtocolConstants.SIMPLE_RESP,
                com.cpptrader.admin.protocol.ProtocolConstants.ORDER_BOOK_UPDATE_EVT,
                com.cpptrader.admin.protocol.ProtocolConstants.ORDER_UPDATE_EVT,
                com.cpptrader.admin.protocol.ProtocolConstants.HEARTBEAT_REQ,
                com.cpptrader.admin.protocol.ProtocolConstants.HEARTBEAT_RESP
        };

        for (byte type : validTypes) {
            if (type == msgType) {
                return true;
            }
        }
        return false;
    }

    public static ProtocolErrorHandler createDefault() {
        ProtocolErrorHandler handler = new ProtocolErrorHandler();
        handler.setLogEnabled(true);
        handler.setThrowOnError(false);
        return handler;
    }

    public static ProtocolErrorHandler createStrict() {
        ProtocolErrorHandler handler = new ProtocolErrorHandler();
        handler.setLogEnabled(true);
        handler.setThrowOnError(true);
        return handler;
    }

    public static ProtocolErrorHandler createSilent() {
        ProtocolErrorHandler handler = new ProtocolErrorHandler();
        handler.setLogEnabled(false);
        handler.setThrowOnError(false);
        return handler;
    }
}