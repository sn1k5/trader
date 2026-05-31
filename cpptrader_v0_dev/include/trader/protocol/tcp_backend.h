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
#include <deque>
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
    std::deque<std::vector<uint8_t>> WriteQueue;
    bool Writing = false;
    bool Closed = false;

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

    void SetMessageHandler(const MessageHandler& handler) override { _message_handler = handler; }
    void SetConnectHandler(const ConnectHandler& handler) override { _connect_handler = handler; }
    void SetDisconnectHandler(const DisconnectHandler& handler) override { _disconnect_handler = handler; }
    void close(uint16_t conn_id) override;

    //! Get number of active connections
    size_t ConnectionCount() const noexcept { return _connections.size(); }

private:
    asio::io_context& _io_context;
    asio::ip::tcp::acceptor _acceptor;
    uint16_t _port;
    uint16_t _next_conn_id;

    std::unordered_map<uint16_t, std::shared_ptr<TcpConnection>> _connections;
    MessageHandler _message_handler;
    ConnectHandler _connect_handler;
    DisconnectHandler _disconnect_handler;

    void StartAccept();
    void HandleAccept(std::shared_ptr<TcpConnection> conn, const asio::error_code& ec);
    void StartRead(std::shared_ptr<TcpConnection> conn);
    void HandleRead(std::shared_ptr<TcpConnection> conn, const asio::error_code& ec, size_t bytes_transferred);
    void CloseConnection(uint16_t conn_id);
    void DoWrite(std::shared_ptr<TcpConnection> conn, std::vector<uint8_t> data);

    static constexpr size_t MAX_WRITE_QUEUE_SIZE = 64;
};

} // namespace Protocol
} // namespace CppTrader

#endif // CPPTRADER_PROTOCOL_TCP_BACKEND_H
