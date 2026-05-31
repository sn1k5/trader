/*!
    \file message.h
    \brief Protocol message definitions
    \author CppTrader Team
    \date 18.05.2026
    \copyright MIT License
*/

#ifndef CPPTRADER_PROTOCOL_MESSAGE_H
#define CPPTRADER_PROTOCOL_MESSAGE_H

#include "protocol.h"

#include <cstdint>
#include <limits>

namespace CppTrader
{
    namespace Protocol
    {

//! Symbol protocol structure (12 bytes)
#pragma pack(push, 1)
struct SymbolProto
{
    uint32_t Id;
    char Name[8];
};
static_assert(sizeof(SymbolProto) == 12, "SymbolProto must be exactly 12 bytes");
#pragma pack(pop)

//! Level protocol structure (32 bytes: 4 x uint64_t)
#pragma pack(push, 1)
struct LevelProto
{
    uint64_t Price;
    uint64_t TotalVolume;
    uint64_t VisibleVolume;
    uint64_t Orders;
};
static_assert(sizeof(LevelProto) == 32, "LevelProto must be exactly 32 bytes");
#pragma pack(pop)

//! Order side
enum class OrderSide : uint8_t
{
    BUY = 0,
    SELL = 1
};

//! Order type
enum class OrderType : uint8_t
{
    MARKET = 0,
    LIMIT = 1,
    STOP = 2,
    STOP_LIMIT = 3,
    TRAILING_STOP = 4,
    TRAILING_STOP_LIMIT = 5
};

//! Order time in force
enum class OrderTimeInForce : uint8_t
{
    GTC = 0,
    IOC = 1,
    FOK = 2,
    AON = 3
};

//! STP policy
enum class STPPolicy : uint8_t
{
    CANCEL_NEW = 1,
    CANCEL_OLD = 2,
    CANCEL_BOTH = 3,
    DECREMENT = 4
};

//! Error code
enum class ErrorCode : uint8_t
{
    OK = 0,
    SYMBOL_DUPLICATE = 1,
    SYMBOL_NOT_FOUND = 2,
    ORDER_BOOK_DUPLICATE = 3,
    ORDER_BOOK_NOT_FOUND = 4,
    ORDER_DUPLICATE = 5,
    ORDER_NOT_FOUND = 6,
    ORDER_ID_INVALID = 7,
    ORDER_TYPE_INVALID = 8,
    ORDER_PARAMETER_INVALID = 9,
    ORDER_QUANTITY_INVALID = 10,
    NOT_AUTHENTICATED = 20,
    NOT_AUTHORIZED = 21,
    AUTH_EXPIRED = 22,
    INVALID_SIGNATURE = 23,
    REPLAY_DETECTED = 24,
    RATE_LIMITED = 25,
    CONNECTION_REJECTED = 26,
    SERVER_SHUTTING_DOWN = 27,
    SELF_TRADE_PREVENTED = 28
};

//! Update type
enum class UpdateType : uint8_t
{
    NONE = 0,
    ADD = 1,
    UPDATE = 2,
    DELETE = 3
};

//! Order protocol structure
#pragma pack(push, 1)
struct OrderProto
{
    uint64_t Id;
    uint32_t SymbolId;
    uint64_t AccountId;
    uint8_t Type;
    uint8_t Side;
    uint64_t Price;
    uint64_t StopPrice;
    uint64_t Quantity;
    uint64_t ExecutedQuantity;
    uint64_t LeavesQuantity;
    uint8_t TimeInForce;
    uint8_t Padding1;
    uint8_t StpPolicy;
    uint64_t MaxVisibleQuantity;
    uint64_t Slippage;
    int64_t TrailingDistance;
    int64_t TrailingStep;
};
#pragma pack(pop)
static_assert(sizeof(OrderProto) == 97, "OrderProto must be exactly 97 bytes");

//! Add symbol request
#pragma pack(push, 1)
struct AddSymbolRequest
{
    SymbolProto Symbol;
};
#pragma pack(pop)
static_assert(sizeof(AddSymbolRequest) == 12, "AddSymbolRequest must be exactly 12 bytes");

//! Symbol response
#pragma pack(push, 1)
struct SymbolResponse
{
    uint8_t Error;
    SymbolProto Symbol;
};
#pragma pack(pop)
static_assert(sizeof(SymbolResponse) == 13, "SymbolResponse must be exactly 13 bytes");

//! Add order request
#pragma pack(push, 1)
struct AddOrderRequest
{
    OrderProto Order;
};
#pragma pack(pop)
static_assert(sizeof(AddOrderRequest) == 97, "AddOrderRequest must be exactly 97 bytes");

//! Order response
#pragma pack(push, 1)
struct OrderResponse
{
    uint8_t Error;
    OrderProto Order;
};
#pragma pack(pop)
static_assert(sizeof(OrderResponse) == 98, "OrderResponse must be exactly 98 bytes");

//! Order book snapshot (variable length)
#pragma pack(push, 1)
struct OrderBookSnapshot
{
    uint32_t SymbolId;
    LevelProto BestBid;
    LevelProto BestAsk;
    uint16_t BidCount;
    uint16_t AskCount;
};
#pragma pack(pop)
static_assert(sizeof(OrderBookSnapshot) == 72, "OrderBookSnapshot base must be exactly 72 bytes");

inline size_t OrderBookSnapshotSize(uint16_t bid_count, uint16_t ask_count) noexcept
{
    return sizeof(OrderBookSnapshot) + bid_count * sizeof(LevelProto) + ask_count * sizeof(LevelProto);
}

inline LevelProto *OrderBookSnapshotBids(OrderBookSnapshot *snapshot) noexcept
{
    return reinterpret_cast<LevelProto *>(snapshot + 1);
}

inline const LevelProto *OrderBookSnapshotBids(const OrderBookSnapshot *snapshot) noexcept
{
    return reinterpret_cast<const LevelProto *>(snapshot + 1);
}

inline LevelProto *OrderBookSnapshotAsks(OrderBookSnapshot *snapshot) noexcept
{
    return reinterpret_cast<LevelProto *>(reinterpret_cast<uint8_t *>(snapshot + 1) + snapshot->BidCount * sizeof(LevelProto));
}

inline const LevelProto *OrderBookSnapshotAsks(const OrderBookSnapshot *snapshot) noexcept
{
    return reinterpret_cast<const LevelProto *>(reinterpret_cast<const uint8_t *>(snapshot + 1) + snapshot->BidCount * sizeof(LevelProto));
}

//! Order book update event
#pragma pack(push, 1)
struct OrderBookUpdateEvent
{
    uint32_t SymbolId;
    uint8_t IsTop;
    uint8_t Type;
    uint8_t LevelType;
    uint8_t Padding1;
    LevelProto Level;
};
#pragma pack(pop)
static_assert(sizeof(OrderBookUpdateEvent) == 40, "OrderBookUpdateEvent must be exactly 40 bytes");

//! Order update event
#pragma pack(push, 1)
struct OrderUpdateEvent
{
    uint8_t Action;
    OrderProto Order;
    uint64_t ExecutePrice;
    uint64_t ExecuteQuantity;
};
#pragma pack(pop)
static_assert(sizeof(OrderUpdateEvent) == 114, "OrderUpdateEvent must be exactly 114 bytes");

//! Simple response (1 byte)
#pragma pack(push, 1)
struct SimpleResponse
{
    uint8_t Error;

    SimpleResponse() noexcept = default;
    explicit SimpleResponse(ErrorCode error) noexcept
        : Error(static_cast<uint8_t>(error))
    {
    }
};
#pragma pack(pop)
static_assert(sizeof(SimpleResponse) == 1, "SimpleResponse must be exactly 1 byte");

//! Delete symbol request (4 bytes)
#pragma pack(push, 1)
struct DeleteSymbolRequest
{
    uint32_t Id;
};
#pragma pack(pop)
static_assert(sizeof(DeleteSymbolRequest) == 4, "DeleteSymbolRequest must be exactly 4 bytes");

//! Get symbol request (4 bytes)
#pragma pack(push, 1)
struct GetSymbolRequest
{
    uint32_t Id;
};
#pragma pack(pop)
static_assert(sizeof(GetSymbolRequest) == 4, "GetSymbolRequest must be exactly 4 bytes");

//! Add order book request (4 bytes)
#pragma pack(push, 1)
struct AddOrderBookRequest
{
    uint32_t SymbolId;
};
#pragma pack(pop)
static_assert(sizeof(AddOrderBookRequest) == 4, "AddOrderBookRequest must be exactly 4 bytes");

//! Delete order book request (4 bytes)
#pragma pack(push, 1)
struct DeleteOrderBookRequest
{
    uint32_t SymbolId;
};
#pragma pack(pop)
static_assert(sizeof(DeleteOrderBookRequest) == 4, "DeleteOrderBookRequest must be exactly 4 bytes");

//! Get order book request (8 bytes)
#pragma pack(push, 1)
struct GetOrderBookRequest
{
    uint32_t SymbolId;
    int32_t Depth;
};
#pragma pack(pop)
static_assert(sizeof(GetOrderBookRequest) == 8, "GetOrderBookRequest must be exactly 8 bytes");

//! Reduce order request (16 bytes)
#pragma pack(push, 1)
struct ReduceOrderRequest
{
    uint64_t Id;
    uint64_t Quantity;
};
#pragma pack(pop)
static_assert(sizeof(ReduceOrderRequest) == 16, "ReduceOrderRequest must be exactly 16 bytes");

//! Modify order request (24 bytes)
#pragma pack(push, 1)
struct ModifyOrderRequest
{
    uint64_t Id;
    uint64_t NewPrice;
    uint64_t NewQuantity;
};
#pragma pack(pop)
static_assert(sizeof(ModifyOrderRequest) == 24, "ModifyOrderRequest must be exactly 24 bytes");

//! Mitigate order request (24 bytes)
#pragma pack(push, 1)
struct MitigateOrderRequest
{
    uint64_t Id;
    uint64_t NewPrice;
    uint64_t NewQuantity;
};
#pragma pack(pop)
static_assert(sizeof(MitigateOrderRequest) == 24, "MitigateOrderRequest must be exactly 24 bytes");

//! Replace order request (32 bytes)
#pragma pack(push, 1)
struct ReplaceOrderRequest
{
    uint64_t Id;
    uint64_t NewId;
    uint64_t NewPrice;
    uint64_t NewQuantity;
};
#pragma pack(pop)
static_assert(sizeof(ReplaceOrderRequest) == 32, "ReplaceOrderRequest must be exactly 32 bytes");

//! Delete order request (8 bytes)
#pragma pack(push, 1)
struct DeleteOrderRequest
{
    uint64_t Id;
};
#pragma pack(pop)
static_assert(sizeof(DeleteOrderRequest) == 8, "DeleteOrderRequest must be exactly 8 bytes");

//! Execute order request (24 bytes)
#pragma pack(push, 1)
struct ExecuteOrderRequest
{
    uint64_t Id;
    uint64_t Price;
    uint64_t Quantity;
};
#pragma pack(pop)
static_assert(sizeof(ExecuteOrderRequest) == 24, "ExecuteOrderRequest must be exactly 24 bytes");

//! Get order request (8 bytes)
#pragma pack(push, 1)
struct GetOrderRequest
{
    uint64_t Id;
};
#pragma pack(pop)
static_assert(sizeof(GetOrderRequest) == 8, "GetOrderRequest must be exactly 8 bytes");

//! Subscribe request (4 bytes)
#pragma pack(push, 1)
struct SubscribeRequest
{
    uint32_t SymbolId;
};
#pragma pack(pop)
static_assert(sizeof(SubscribeRequest) == 4, "SubscribeRequest must be exactly 4 bytes");

//! Auth request (120 bytes: apiKeyId(32) + timestamp(8) + nonce(16) + signature(32) + recoveryToken(32))
#pragma pack(push, 1)
struct AuthRequest
{
    char ApiKeyId[32];
    int64_t Timestamp;
    char Nonce[16];
    char Signature[32];
    char RecoveryToken[32];
};
#pragma pack(pop)
static_assert(sizeof(AuthRequest) == 120, "AuthRequest must be exactly 120 bytes");

//! Auth response (42 bytes: error(1) + sessionToken(32) + accountId(8) + role(1))
#pragma pack(push, 1)
struct AuthResponse
{
    uint8_t Error;
    char SessionToken[32];
    uint64_t AccountId;
    uint8_t Role;
};
#pragma pack(pop)
static_assert(sizeof(AuthResponse) == 42, "AuthResponse must be exactly 42 bytes");

//! Event acknowledgment
#pragma pack(push, 1)
struct EventAck
{
    uint64_t EventId;
    uint32_t EventType;
};
#pragma pack(pop)
static_assert(sizeof(EventAck) == 12, "EventAck must be exactly 12 bytes");

//! Reconcile request
#pragma pack(push, 1)
struct ReconcileRequest
{
    uint32_t SymbolId;
};
#pragma pack(pop)
static_assert(sizeof(ReconcileRequest) == 4, "ReconcileRequest must be exactly 4 bytes");

//! Reconcile response header
#pragma pack(push, 1)
struct ReconcileResponseHeader
{
    uint8_t Error;
    uint32_t SymbolId;
    uint64_t LastTradeId;
    uint64_t LastTradePrice;
    uint64_t LastTradeQuantity;
    uint32_t OrderCount;
    uint32_t LevelCount;
};
#pragma pack(pop)
static_assert(sizeof(ReconcileResponseHeader) == 37, "ReconcileResponseHeader must be exactly 37 bytes");

//! Order book summary
#pragma pack(push, 1)
struct OrderBookSummary
{
    uint32_t SymbolId;
    uint64_t BestBidPrice;
    uint64_t BestBidVolume;
    uint64_t BestAskPrice;
    uint64_t BestAskVolume;
    uint32_t TotalBidLevels;
    uint32_t TotalAskLevels;
    uint32_t TotalOrders;
};
#pragma pack(pop)
static_assert(sizeof(OrderBookSummary) == 48, "OrderBookSummary must be exactly 48 bytes");

#pragma pack(push, 1)
struct SnapshotResponse
{
    uint8_t Error;
    uint64_t TimestampNs;
    uint64_t WalSequence;
    uint32_t SymbolCount;
    uint32_t OrderCount;
};
#pragma pack(pop)
static_assert(sizeof(SnapshotResponse) == 25, "SnapshotResponse must be exactly 25 bytes");

    } // namespace Protocol
} // namespace CppTrader

#endif // CPPTRADER_PROTOCOL_MESSAGE_H