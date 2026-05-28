package com.cpptrader.admin.protocol.exception;

import com.cpptrader.admin.protocol.ProtocolConstants;

public class ProtocolException extends RuntimeException {

    private final byte errorCode;
    private final String errorType;

    public ProtocolException(byte errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
        this.errorType = ProtocolConstants.ErrorCode.name(errorCode);
    }

    public ProtocolException(byte errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.errorType = ProtocolConstants.ErrorCode.name(errorCode);
    }

    public ProtocolException(String message) {
        super(message);
        this.errorCode = ProtocolConstants.ErrorCode.ORDER_PARAMETER_INVALID;
        this.errorType = "ERROR";
    }

    public ProtocolException(String message, Throwable cause) {
        super(message, cause);
        this.errorCode = ProtocolConstants.ErrorCode.ORDER_PARAMETER_INVALID;
        this.errorType = "ERROR";
    }

    public byte getErrorCode() {
        return errorCode;
    }

    public String getErrorType() {
        return errorType;
    }

    public static ProtocolException invalidMagic(short magic) {
        return new ProtocolException(
                ProtocolConstants.ErrorCode.ORDER_PARAMETER_INVALID,
                String.format("Invalid magic number: 0x%04X, expected: 0x%04X", magic, ProtocolConstants.MAGIC)
        );
    }

    public static ProtocolException unsupportedVersion(byte version) {
        return new ProtocolException(
                ProtocolConstants.ErrorCode.ORDER_PARAMETER_INVALID,
                String.format("Unsupported protocol version: %d, supported: %d", version, ProtocolConstants.VERSION)
        );
    }

    public static ProtocolException invalidMessageType(byte msgType) {
        return new ProtocolException(
                ProtocolConstants.ErrorCode.ORDER_PARAMETER_INVALID,
                String.format("Invalid message type: 0x%02X", msgType)
        );
    }

    public static ProtocolException invalidBodySize(int expected, int actual) {
        return new ProtocolException(
                ProtocolConstants.ErrorCode.ORDER_PARAMETER_INVALID,
                String.format("Invalid body size: expected %d, actual %d", expected, actual)
        );
    }

    public static ProtocolException bufferUnderflow(String operation) {
        return new ProtocolException(
                ProtocolConstants.ErrorCode.ORDER_PARAMETER_INVALID,
                String.format("Buffer underflow during: %s", operation)
        );
    }

    public static ProtocolException bufferOverflow(String operation) {
        return new ProtocolException(
                ProtocolConstants.ErrorCode.ORDER_PARAMETER_INVALID,
                String.format("Buffer overflow during: %s", operation)
        );
    }

    public static ProtocolException encodingError(String details) {
        return new ProtocolException(
                ProtocolConstants.ErrorCode.ORDER_PARAMETER_INVALID,
                String.format("Encoding error: %s", details)
        );
    }

    public static ProtocolException decodingError(String details) {
        return new ProtocolException(
                ProtocolConstants.ErrorCode.ORDER_PARAMETER_INVALID,
                String.format("Decoding error: %s", details)
        );
    }

    public static ProtocolException notFound(String resource) {
        return new ProtocolException(
                ProtocolConstants.ErrorCode.SYMBOL_NOT_FOUND,
                String.format("Resource not found: %s", resource)
        );
    }

    public static ProtocolException alreadyExists(String resource) {
        return new ProtocolException(
                ProtocolConstants.ErrorCode.SYMBOL_DUPLICATE,
                String.format("Resource already exists: %s", resource)
        );
    }

    @Override
    public String toString() {
        return String.format("ProtocolException[%s, code=%d]: %s", errorType, errorCode, getMessage());
    }
}