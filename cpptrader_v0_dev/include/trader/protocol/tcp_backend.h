/*!
    \file tcp_backend.h
    \brief TCP network backend based on asio
    \author CppTrader Team
    \date 18.05.2026
    \copyright MIT License
*/

#ifndef CPPTRADER_PROTOCOL_TCP_BACKEND_H
#define CPPTRADER_PROTOCOL_TCP_BACKEND_H

#include "network_backend.h"
#include "frame_decoder.h"

#include <asio.hpp>

#include <cstdint>
#include <cstddef>
#include <functional>
#include <memory>
#include <unordered_map>
#include <vector>

namespace CppTrader {
namespace Protocol {

//! Forward declaration
class ProtocolServer;

//! TCP connection context
struct TcpConnection
{
    //! Connection identifier
    uint16_t Id;
    //! Asio socket
    asio::ip::tcp::socket Socket;
    //! Receive buffer
    std::vector<uint8_t> Buffer;
    //! Frame decoder for this connection
    FrameDecoder Decoder;
    //! Decode failure counter
    uint32_t DecodeFailCount;

    TcpConnection(uint16_t id, asio::io_context& io_context)
        : Id(id)
        , Socket(io_context)
        , Buffer(4096)
        , DecodeFailCount(0)
    {}

    TcpConnection(const TcpConnection&) = delete;
    TcpConnection(TcpConnection&&) = delete;
    TcpConnection& operator=(const TcpConnection&) = delete;
    TcpConnection& operator=(TcpConnection&&) = delete;
};

//! TCP network backend implementation using asio
/*!
    This class implements INetworkBackend using asio for standard TCP networking.
    It supports multiple concurrent client connections, each with its own FrameDecoder
    to handle sticky/partial packet scenarios.
*/
class TcpBackend : public INetworkBackend
{
public:
    //! Message handler callback type
    using MessageHandler = std::function<void(uint16_t conn_id, const MsgHeader& header, const uint8_t* body, size_t body_len)>;
    //! Connection handler callback type
    using ConnectionHandler = std::function<void(uint16_t conn_id)>;

    //! Constructor
    /*!
        \param io_context - Asio io_context reference
        \param port - TCP listen port
    */
    TcpBackend(asio::io_context& io_context, uint16_t port);
    ~TcpBackend() override;

    TcpBackend(const TcpBackend&) = delete;
    TcpBackend(TcpBackend&&) = delete;
    TcpBackend& operator=(const TcpBackend&) = delete;
    TcpBackend& operator=(TcpBackend&&) = delete;

    //! Initialize the TCP server
    bool init() override;

    //! Poll for incoming data (processes asio events)
    void poll() override;

    //! Send data to a specific connection
    void send(uint16_t conn_id, const void* data, size_t len) override;

    //! Broadcast data to all connected clients
    void broadcast(const void* data, size_t len) override;

    //! Set message handler callback
    void SetMessageHandler(const MessageHandler& handler) { _message_handler = handler; }

    //! Set connection established handler
    void SetConnectHandler(const ConnectionHandler& handler) { _connect_handler = handler; }

    //! Set connection closed handler
    void SetDisconnectHandler(const ConnectionHandler& handler) { _disconnect_handler = handler; }

    //! Get number of active connections
    size_t ConnectionCount() const noexcept { return _connections.size(); }

private:
    asio::io_context& _io_context;
    asio::ip::tcp::acceptor _acceptor;
    uint16_t _port;
    uint16_t _next_conn_id;

    std::unordered_map<uint16_t, std::shared_ptr<TcpConnection>> _connections;
    MessageHandler _message_handler;
    ConnectionHandler _connect_handler;
    ConnectionHandler _disconnect_handler;

    void StartAccept();
    void HandleAccept(std::shared_ptr<TcpConnection> conn, const asio::error_code& ec);
    void StartRead(std::shared_ptr<TcpConnection> conn);
    void HandleRead(std::shared_ptr<TcpConnection> conn, const asio::error_code& ec, size_t bytes_transferred);
    void CloseConnection(uint16_t conn_id);
};

} // namespace Protocol
} // namespace CppTrader

#endif // CPPTRADER_PROTOCOL_TCP_BACKEND_H
