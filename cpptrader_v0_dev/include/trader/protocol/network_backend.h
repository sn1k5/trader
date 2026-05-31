/*!
    \file network_backend.h
    \brief Network backend abstract interface
    \author CppTrader Team
    \date 18.05.2026
    \copyright MIT License
*/

#ifndef CPPTRADER_PROTOCOL_NETWORK_BACKEND_H
#define CPPTRADER_PROTOCOL_NETWORK_BACKEND_H

#include <cstdint>
#include <cstddef>
#include <functional>

namespace CppTrader {
namespace Protocol {

struct MsgHeader;

//! Network backend abstract interface
/*!
    This interface defines the contract for network backends used by ProtocolServer.
    Implementations include standard TCP (asio-based) and DPDK kernel-bypass.
*/
class INetworkBackend
{
public:
    INetworkBackend() = default;
    virtual ~INetworkBackend() = default;

    INetworkBackend(const INetworkBackend&) = delete;
    INetworkBackend(INetworkBackend&&) = delete;
    INetworkBackend& operator=(const INetworkBackend&) = delete;
    INetworkBackend& operator=(INetworkBackend&&) = delete;

    using MessageHandler = std::function<void(uint16_t conn_id, const MsgHeader& header, const uint8_t* body, size_t body_len)>;
    using ConnectHandler = std::function<void(uint16_t conn_id)>;
    using DisconnectHandler = std::function<void(uint16_t conn_id)>;

    virtual void SetMessageHandler(const MessageHandler& handler) = 0;
    virtual void SetConnectHandler(const ConnectHandler& handler) = 0;
    virtual void SetDisconnectHandler(const DisconnectHandler& handler) = 0;
    virtual void close(uint16_t conn_id) = 0;

    virtual bool init() = 0;

    //! Poll for incoming data
    /*!
        This method should be called in the main event loop to process
        incoming network data.
    */
    virtual void poll() = 0;

    //! Send data to a specific connection
    /*!
        \param conn_id - Connection identifier
        \param data - Pointer to data buffer
        \param len - Data length in bytes
    */
    virtual void send(uint16_t conn_id, const void* data, size_t len) = 0;

    //! Broadcast data to all connected clients
    /*!
        \param data - Pointer to data buffer
        \param len - Data length in bytes
    */
    virtual void broadcast(const void* data, size_t len) = 0;
};

} // namespace Protocol
} // namespace CppTrader

#endif // CPPTRADER_PROTOCOL_NETWORK_BACKEND_H
