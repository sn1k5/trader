/*!
    \file tcp_backend.cpp
    \brief TCP network backend implementation
    \author CppTrader Team
    \date 18.05.2026
    \copyright MIT License
*/

#include "trader/protocol/tcp_backend.h"

#include <cstring>
#include <iomanip>
#include <iostream>

namespace CppTrader {
namespace Protocol {

TcpBackend::TcpBackend(asio::io_context& io_context, uint16_t port)
    : _io_context(io_context)
    , _acceptor(io_context)
    , _port(port)
    , _next_conn_id(1)
{
}

TcpBackend::~TcpBackend()
{
    _acceptor.close();
    _connections.clear();
}

bool TcpBackend::init()
{
    try
    {
        asio::ip::tcp::endpoint endpoint(asio::ip::tcp::v4(), _port);
        _acceptor.open(endpoint.protocol());
        _acceptor.set_option(asio::ip::tcp::acceptor::reuse_address(true));
        _acceptor.bind(endpoint);
        _acceptor.listen(asio::socket_base::max_listen_connections);

        StartAccept();
        return true;
    }
    catch (const std::exception& e)
    {
        std::cerr << "TcpBackend::init() failed: " << e.what() << std::endl;
        return false;
    }
}

void TcpBackend::poll()
{
    _io_context.poll();
}

void TcpBackend::send(uint16_t conn_id, const void* data, size_t len)
{
    auto it = _connections.find(conn_id);
    if (it == _connections.end())
    {
        std::cout << "[DEBUG] TcpBackend::send conn=" << conn_id << " FAILED: connection not found" << std::endl;
        return;
    }

    auto& conn = it->second;
    asio::error_code ec;
    asio::write(conn->Socket, asio::buffer(data, len), ec);
    if (ec)
    {
        std::cout << "[DEBUG] TcpBackend::send conn=" << conn_id << " ERROR: " << ec.message() << std::endl;
        CloseConnection(conn_id);
    }
}

void TcpBackend::broadcast(const void* data, size_t len)
{
    std::vector<uint16_t> dead_connections;

    for (auto& [conn_id, conn] : _connections)
    {
        asio::error_code ec;
        asio::write(conn->Socket, asio::buffer(data, len), ec);
        if (ec)
        {
            dead_connections.push_back(conn_id);
        }
    }

    for (auto conn_id : dead_connections)
    {
        CloseConnection(conn_id);
    }
}

void TcpBackend::StartAccept()
{
    auto conn = std::make_shared<TcpConnection>(_next_conn_id++, _io_context);
    _acceptor.async_accept(conn->Socket,
        [this, conn](const asio::error_code& ec)
        {
            HandleAccept(conn, ec);
        });
}

void TcpBackend::HandleAccept(std::shared_ptr<TcpConnection> conn, const asio::error_code& ec)
{
    if (ec)
    {
        if (ec != asio::error::operation_aborted)
        {
            std::cerr << "TcpBackend accept error: " << ec.message() << std::endl;
        }
        return;
    }

    _connections[conn->Id] = conn;

    std::cout << "[DEBUG] TcpBackend::HandleAccept conn=" << conn->Id
              << " total_connections=" << _connections.size() << std::endl;

    if (_connect_handler)
    {
        _connect_handler(conn->Id);
    }

    StartRead(conn);
    StartAccept();
}

void TcpBackend::StartRead(std::shared_ptr<TcpConnection> conn)
{
    conn->Socket.async_read_some(asio::buffer(conn->Buffer),
        [this, conn](const asio::error_code& ec, size_t bytes_transferred)
        {
            HandleRead(conn, ec, bytes_transferred);
        });
}

void TcpBackend::HandleRead(std::shared_ptr<TcpConnection> conn, const asio::error_code& ec, size_t bytes_transferred)
{
    if (ec)
    {
        std::cout << "[DEBUG] TcpBackend::HandleRead conn=" << conn->Id
                  << " ERROR: ec=" << ec.value() << " (" << ec.category().name() << ")"
                  << " msg=" << ec.message() << std::endl;
        CloseConnection(conn->Id);
        return;
    }

    std::cout << "[DEBUG] TcpBackend::HandleRead conn=" << conn->Id << " bytes=" << bytes_transferred;

    // Hex dump of raw bytes (up to 32 bytes)
    {
        size_t dump_len = std::min(bytes_transferred, size_t(32));
        std::cout << " hex=[";
        for (size_t i = 0; i < dump_len; ++i)
        {
            if (i > 0) std::cout << ' ';
            std::cout << std::hex << std::setw(2) << std::setfill('0')
                      << static_cast<int>(conn->Buffer[i]);
        }
        if (bytes_transferred > 32) std::cout << " ...";
        std::cout << "]" << std::dec << std::endl;
    }

    conn->Decoder.Feed(conn->Buffer.data(), bytes_transferred);

    if (_message_handler)
    {
        bool frame_decoded = false;
        while (true)
        {
            auto frame = conn->Decoder.TryDecode();
            if (!frame.has_value())
                break;

            frame_decoded = true;
            std::cout << "[DEBUG] TcpBackend::HandleRead conn=" << conn->Id
                      << " decoded frame: type=0x" << std::hex << std::setw(2) << std::setfill('0')
                      << static_cast<int>(frame->Header.Type)
                      << " flags=0x" << std::setw(2) << std::setfill('0')
                      << static_cast<int>(frame->Header.Flags)
                      << std::dec << " body_len=" << frame->Body.size() << std::endl;

            _message_handler(conn->Id, frame->Header, frame->BodyBytes(), frame->Body.size());
        }

        if (!frame_decoded && bytes_transferred > 0)
        {
            ++conn->DecodeFailCount;
        }
    }

    StartRead(conn);
}

void TcpBackend::CloseConnection(uint16_t conn_id)
{
    auto it = _connections.find(conn_id);
    if (it == _connections.end())
        return;

    std::cout << "[DEBUG] TcpBackend::CloseConnection conn=" << conn_id << " decode_fails=" << it->second->DecodeFailCount << std::endl;

    asio::error_code ec;
    it->second->Socket.close(ec);
    _connections.erase(it);

    if (_disconnect_handler)
    {
        _disconnect_handler(conn_id);
    }
}

} // namespace Protocol
} // namespace CppTrader
