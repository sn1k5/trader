package com.cpptrader.admin.protocol;

public final class ProtocolConstants {

    private ProtocolConstants() {}

    public static final short MAGIC = (short) 0x5452;
    public static final byte VERSION = 2;
    public static final int HEADER_SIZE = 16;

    public static final byte ADD_SYMBOL_REQ = 0x01;
    public static final byte DELETE_SYMBOL_REQ = 0x02;
    public static final byte GET_SYMBOL_REQ = 0x03;
    public static final byte ADD_ORDER_BOOK_REQ = 0x04;
    public static final byte DELETE_ORDER_BOOK_REQ = 0x05;
    public static final byte GET_ORDER_BOOK_REQ = 0x06;
    public static final byte ADD_ORDER_REQ = 0x07;
    public static final byte REDUCE_ORDER_REQ = 0x08;
    public static final byte MODIFY_ORDER_REQ = 0x09;
    public static final byte MITIGATE_ORDER_REQ = 0x0A;
    public static final byte REPLACE_ORDER_REQ = 0x0B;
    public static final byte DELETE_ORDER_REQ = 0x0C;
    public static final byte EXECUTE_ORDER_REQ = 0x0D;
    public static final byte GET_ORDER_REQ = 0x0E;
    public static final byte ENABLE_MATCHING_REQ = 0x0F;
    public static final byte DISABLE_MATCHING_REQ = 0x10;
    public static final byte SUBSCRIBE_ORDER_BOOK_REQ = 0x11;
    public static final byte SUBSCRIBE_ORDERS_REQ = 0x12;
    public static final byte SNAPSHOT_REQUEST = 0x13;

    public static final byte SYMBOL_RESP = 0x41;
    public static final byte ORDER_BOOK_RESP = 0x42;
    public static final byte ORDER_RESP = 0x43;
    public static final byte SIMPLE_RESP = 0x44;
    public static final byte SNAPSHOT_RESPONSE = 0x45;

    public static final byte ORDER_BOOK_UPDATE_EVT = (byte) 0x81;
    public static final byte ORDER_UPDATE_EVT = (byte) 0x82;

    public static final byte HEARTBEAT_REQ = (byte) 0xC0;
    public static final byte HEARTBEAT_RESP = (byte) 0xC1;
    public static final byte SHUTDOWN_NOTIFY = (byte) 0xCF;
    public static final byte EVENT_ACK = (byte) 0xE0;
    public static final byte RECONCILE_REQUEST = (byte) 0xE1;
    public static final byte RECONCILE_RESPONSE = (byte) 0xE2;

    public static final byte AUTH_REQUEST = (byte) 0xD0;
    public static final byte AUTH_RESPONSE = (byte) 0xD1;

    public static final int AUTH_REQUEST_BODY_SIZE = 120;
    public static final int AUTH_RESPONSE_BODY_SIZE = 42;
    public static final int AUTH_API_KEY_ID_SIZE = 32;
    public static final int AUTH_NONCE_SIZE = 16;
    public static final int AUTH_SIGNATURE_SIZE = 32;
    public static final int AUTH_SESSION_TOKEN_SIZE = 32;
    public static final int AUTH_RECOVERY_TOKEN_SIZE = 32;

    public static final byte FLAG_REQUEST = 0x01;
    public static final byte FLAG_RESPONSE = 0x02;
    public static final byte FLAG_PUSH = 0x04;
    public static final byte FLAG_ERROR = 0x08;
    public static final byte FLAG_HEARTBEAT = 0x10;

    public static final int SYMBOL_NAME_SIZE = 8;
    public static final int SYMBOL_PROTO_SIZE = 12;
    public static final int ORDER_PROTO_SIZE = 97;
    public static final int LEVEL_PROTO_SIZE = 32;

    public static final long NO_SLIPPAGE = -1L;

    public static final class ErrorCode {
        private ErrorCode() {}
        public static final byte OK = 0;
        public static final byte SYMBOL_DUPLICATE = 1;
        public static final byte SYMBOL_NOT_FOUND = 2;
        public static final byte ORDER_BOOK_DUPLICATE = 3;
        public static final byte ORDER_BOOK_NOT_FOUND = 4;
        public static final byte ORDER_DUPLICATE = 5;
        public static final byte ORDER_NOT_FOUND = 6;
        public static final byte ORDER_ID_INVALID = 7;
        public static final byte ORDER_TYPE_INVALID = 8;
        public static final byte ORDER_PARAMETER_INVALID = 9;
        public static final byte ORDER_QUANTITY_INVALID = 10;

        public static final byte NOT_AUTHENTICATED = 20;
        public static final byte NOT_AUTHORIZED = 21;
        public static final byte AUTH_EXPIRED = 22;
        public static final byte INVALID_SIGNATURE = 23;
        public static final byte REPLAY_DETECTED = 24;
        public static final byte RATE_LIMITED = 25;
        public static final byte CONNECTION_REJECTED = 26;
        public static final byte SERVER_SHUTTING_DOWN = 27;
        public static final byte SELF_TRADE_PREVENTED = 28;

        public static String name(byte code) {
            return switch (code) {
                case OK -> "OK";
                case SYMBOL_DUPLICATE -> "SYMBOL_DUPLICATE";
                case SYMBOL_NOT_FOUND -> "SYMBOL_NOT_FOUND";
                case ORDER_BOOK_DUPLICATE -> "ORDER_BOOK_DUPLICATE";
                case ORDER_BOOK_NOT_FOUND -> "ORDER_BOOK_NOT_FOUND";
                case ORDER_DUPLICATE -> "ORDER_DUPLICATE";
                case ORDER_NOT_FOUND -> "ORDER_NOT_FOUND";
                case ORDER_ID_INVALID -> "ORDER_ID_INVALID";
                case ORDER_TYPE_INVALID -> "ORDER_TYPE_INVALID";
                case ORDER_PARAMETER_INVALID -> "ORDER_PARAMETER_INVALID";
                case ORDER_QUANTITY_INVALID -> "ORDER_QUANTITY_INVALID";
                case NOT_AUTHENTICATED -> "NOT_AUTHENTICATED";
                case NOT_AUTHORIZED -> "NOT_AUTHORIZED";
                case AUTH_EXPIRED -> "AUTH_EXPIRED";
                case INVALID_SIGNATURE -> "INVALID_SIGNATURE";
                case REPLAY_DETECTED -> "REPLAY_DETECTED";
                case RATE_LIMITED -> "RATE_LIMITED";
                case CONNECTION_REJECTED -> "CONNECTION_REJECTED";
                case SERVER_SHUTTING_DOWN -> "SERVER_SHUTTING_DOWN";
                case SELF_TRADE_PREVENTED -> "SELF_TRADE_PREVENTED";
                default -> "UNKNOWN";
            };
        }
    }

    public static final class OrderType {
        private OrderType() {}
        public static final byte MARKET = 0;
        public static final byte LIMIT = 1;
        public static final byte STOP = 2;
        public static final byte STOP_LIMIT = 3;
        public static final byte TRAILING_STOP = 4;
        public static final byte TRAILING_STOP_LIMIT = 5;

        public static String name(byte type) {
            return switch (type) {
                case MARKET -> "MARKET";
                case LIMIT -> "LIMIT";
                case STOP -> "STOP";
                case STOP_LIMIT -> "STOP_LIMIT";
                case TRAILING_STOP -> "TRAILING_STOP";
                case TRAILING_STOP_LIMIT -> "TRAILING_STOP_LIMIT";
                default -> "UNKNOWN";
            };
        }
    }

    public static final class UpdateType {
        private UpdateType() {}
        public static final byte ADD = 1;
        public static final byte UPDATE = 2;
        public static final byte DELETE = 3;

        public static String name(byte type) {
            return switch (type) {
                case ADD -> "ADD";
                case UPDATE -> "UPDATE";
                case DELETE -> "DELETE";
                default -> "UNKNOWN";
            };
        }
    }

    public static final class LevelType {
        private LevelType() {}
        public static final byte BID = 0;
        public static final byte ASK = 1;

        public static String name(byte type) {
            return switch (type) {
                case BID -> "BID";
                case ASK -> "ASK";
                default -> "UNKNOWN";
            };
        }
    }

    public static final class ActionType {
        private ActionType() {}
        public static final byte ADD = 1;
        public static final byte UPDATE = 2;
        public static final byte DELETE = 3;
        public static final byte EXECUTE = 4;

        public static String name(byte action) {
            return switch (action) {
                case ADD -> "ADD";
                case UPDATE -> "UPDATE";
                case DELETE -> "DELETE";
                case EXECUTE -> "EXECUTE";
                default -> "UNKNOWN";
            };
        }
    }

    public static final class OrderSide {
        private OrderSide() {}
        public static final byte BUY = 0;
        public static final byte SELL = 1;

        public static String name(byte side) {
            return switch (side) {
                case BUY -> "BUY";
                case SELL -> "SELL";
                default -> "UNKNOWN";
            };
        }
    }

    public static final class TimeInForce {
        private TimeInForce() {}
        public static final byte GTC = 0;
        public static final byte IOC = 1;
        public static final byte FOK = 2;
        public static final byte AON = 3;

        public static String name(byte tif) {
            return switch (tif) {
                case GTC -> "GTC";
                case IOC -> "IOC";
                case FOK -> "FOK";
                case AON -> "AON";
                default -> "UNKNOWN";
            };
        }
    }

    public static final class STPPolicy {
        private STPPolicy() {}
        public static final byte CANCEL_NEW = 1;
        public static final byte CANCEL_OLD = 2;
        public static final byte CANCEL_BOTH = 3;
        public static final byte DECREMENT = 4;

        public static String name(byte policy) {
            return switch (policy) {
                case CANCEL_NEW -> "CANCEL_NEW";
                case CANCEL_OLD -> "CANCEL_OLD";
                case CANCEL_BOTH -> "CANCEL_BOTH";
                case DECREMENT -> "DECREMENT";
                default -> "UNKNOWN";
            };
        }
    }

    public static final class Role {
        private Role() {}
        public static final byte TRADER = 0;
        public static final byte ADMIN = 1;

        public static String name(byte role) {
            return switch (role) {
                case TRADER -> "TRADER";
                case ADMIN -> "ADMIN";
                default -> "UNKNOWN";
            };
        }
    }
}
