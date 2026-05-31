/*!
    \file tcp_backend.cpp
    \brief TCP network backend implementation
    \author CppTrader Team
    \date 18.05.2026
    \copyright MIT License
*/

#include "trader/protocol/tcp_backend.h"

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
        return;
    }

    auto& conn = it->second;
    std::vector<uint8_t> frame(static_cast<const uint8_t*>(data), static_cast<const uint8_t*>(data) + len);

    if (conn->Writing)
    {
        conn->WriteQueue.push_back(std::move(frame));
        if (conn->WriteQueue.size() > MAX_WRITE_QUEUE_SIZE)
        {
            CloseConnection(conn_id);
        }
        return;
    }

    conn->Writing = true;
    DoWrite(conn, std::move(frame));
}

void TcpBackend::broadcast(const void* data, size_t len)
{
    for (auto& [conn_id, conn] : _connections)
    {
        std::vector<uint8_t> frame(static_cast<const uint8_t*>(data), static_cast<const uint8_t*>(data) + len);

        if (conn->Writing)
        {
            conn->WriteQueue.push_back(std::move(frame));
            if (conn->WriteQueue.size() > MAX_WRITE_QUEUE_SIZE)
            {
                CloseConnection(conn_id);
            }
            continue;
        }

        conn->Writing = true;
        DoWrite(conn, std::move(frame));
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

    asio::error_code ec_nodelay;
    conn->Socket.set_option(asio::ip::tcp::no_delay(true), ec_nodelay);

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
        if (ec == asio::error::eof)
        {
            conn->Closed = true;
            if (!conn->Writing)
            {
                CloseConnection(conn->Id);
            }
            return;
        }

        CloseConnection(conn->Id);
        return;
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

            _message_handler(conn->Id, frame->Header, frame->BodyBytes(), frame->Body.size());
        }

        if (!frame_decoded && bytes_transferred > 0)
        {
            ++conn->DecodeFailCount;
        }
    }

    StartRead(conn);
}

void TcpBackend::close(uint16_t conn_id)
{
    CloseConnection(conn_id);
}

void TcpBackend::DoWrite(std::shared_ptr<TcpConnection> conn, std::vector<uint8_t> data)
{
    auto data_ptr = std::make_shared<std::vector<uint8_t>>(std::move(data));
    asio::async_write(conn->Socket, asio::buffer(*data_ptr),
        [this, conn, data_ptr](const asio::error_code& ec, size_t bytes_transferred)
        {
            conn->Writing = false;
            if (conn->Closed)
            {
                CloseConnection(conn->Id);
                return;
            }
            if (ec)
            {
                CloseConnection(conn->Id);
                return;
            }
            if (!conn->WriteQueue.empty())
            {
                auto next = std::move(conn->WriteQueue.front());
                conn->WriteQueue.pop_front();
                conn->Writing = true;
                DoWrite(conn, std::move(next));
            }
        });
}

void TcpBackend::CloseConnection(uint16_t conn_id)
{
    auto it = _connections.find(conn_id);
    if (it == _connections.end())
        return;

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
