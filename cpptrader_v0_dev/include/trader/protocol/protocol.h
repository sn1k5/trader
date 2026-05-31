/*!
    \file protocol.h
    \brief Protocol core definitions
    \author CppTrader Team
    \date 18.05.2026
    \copyright MIT License
*/

#ifndef CPPTRADER_PROTOCOL_PROTOCOL_H
#define CPPTRADER_PROTOCOL_PROTOCOL_H

#include <cstdint>

namespace CppTrader {
namespace Protocol {

//! Protocol magic number (little-endian: "TR" -> 0x5452)
static constexpr uint16_t PROTOCOL_MAGIC = 0x5452;

//! Protocol version
static constexpr uint8_t PROTOCOL_VERSION = 2;

//! Message flags
struct Flags
{
    static constexpr uint8_t NONE = 0x00;
    static constexpr uint8_t REQUEST = 0x01;
    static constexpr uint8_t RESPONSE = 0x02;
    static constexpr uint8_t PUSH = 0x04;
    static constexpr uint8_t ERROR = 0x08;
    static constexpr uint8_t HEARTBEAT = 0x10;
};

//! Message type enumeration
enum class MsgType : uint8_t
{
    // Request messages (0x01-0x3F)
    ADD_SYMBOL_REQUEST = 0x01,
    DELETE_SYMBOL_REQUEST = 0x02,
    GET_SYMBOL_REQUEST = 0x03,
    ADD_ORDER_BOOK_REQUEST = 0x04,
    DELETE_ORDER_BOOK_REQUEST = 0x05,
    GET_ORDER_BOOK_REQUEST = 0x06,
    ADD_ORDER_REQUEST = 0x07,
    REDUCE_ORDER_REQUEST = 0x08,
    MODIFY_ORDER_REQUEST = 0x09,
    MITIGATE_ORDER_REQUEST = 0x0A,
    REPLACE_ORDER_REQUEST = 0x0B,
    DELETE_ORDER_REQUEST = 0x0C,
    EXECUTE_ORDER_REQUEST = 0x0D,
    GET_ORDER_REQUEST = 0x0E,
    ENABLE_MATCHING_REQUEST = 0x0F,
    DISABLE_MATCHING_REQUEST = 0x10,
    SUBSCRIBE_ORDER_BOOK_REQUEST = 0x11,
    SUBSCRIBE_ORDERS_REQUEST = 0x12,
    SNAPSHOT_REQUEST = 0x13,

    // Response messages (0x41-0x7F)
    SYMBOL_RESPONSE = 0x41,
    ORDER_BOOK_RESPONSE = 0x42,
    ORDER_RESPONSE = 0x43,
    SIMPLE_RESPONSE = 0x44,
    SNAPSHOT_RESPONSE = 0x45,

    // Push messages (0x81-0xBF)
    ORDER_BOOK_UPDATE_EVENT = 0x81,
    ORDER_UPDATE_EVENT = 0x82,

    // Management messages (0xC0-0xEF)
    HEARTBEAT_REQ = 0xC0,
    HEARTBEAT_RESP = 0xC1,
    SHUTDOWN_NOTIFY = 0xCF,
    EVENT_ACK = 0xE0,
    RECONCILE_REQUEST = 0xE1,
    RECONCILE_RESPONSE = 0xE2,

    // Authentication messages (0xD0-0xDF)
    AUTH_REQUEST = 0xD0,
    AUTH_RESPONSE = 0xD1
};

//! Message header (16 bytes, little-endian)
#pragma pack(push, 1)
struct MsgHeader
{
    //! Magic number (2 bytes)
    uint16_t Magic;
    //! Protocol version (1 byte)
    uint8_t Version;
    //! Message type (1 byte)
    MsgType Type;
    //! Message flags (1 byte)
    uint8_t Flags;
    //! Reserved (1 byte)
    uint8_t Reserved;
    //! Body length in bytes (2 bytes)
    uint16_t Length;
    //! Sequence number (4 bytes)
    uint32_t Sequence;
    //! HMAC prefix (2 bytes)
    uint16_t HmacPrefix;
    //! Reserved2 (2 bytes)
    uint16_t Reserved2;

    MsgHeader() noexcept = default;
    MsgHeader(MsgType type, uint8_t flags, uint16_t length) noexcept
        : Magic(PROTOCOL_MAGIC)
        , Version(PROTOCOL_VERSION)
        , Type(type)
        , Flags(flags)
        , Reserved(0)
        , Length(length)
        , Sequence(0)
        , HmacPrefix(0)
        , Reserved2(0)
    {}

    //! Validate header
    bool IsValid() const noexcept { return Magic == PROTOCOL_MAGIC && Version == PROTOCOL_VERSION; }

    //! Check if magic number is valid
    bool HasValidMagic() const noexcept { return Magic == PROTOCOL_MAGIC; }

    //! Check if protocol version is valid
    bool HasValidVersion() const noexcept { return Version == PROTOCOL_VERSION; }
};
static_assert(sizeof(MsgHeader) == 16, "MsgHeader must be exactly 16 bytes");
#pragma pack(pop)

} // namespace Protocol
} // namespace CppTrader

#endif // CPPTRADER_PROTOCOL_PROTOCOL_H
