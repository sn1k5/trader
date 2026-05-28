/*!
    \file server.cpp
    \brief Protocol server implementation
    \author CppTrader Team
    \date 18.05.2026
    \copyright MIT License
*/

#include "trader/protocol/server.h"
#include "trader/protocol/tcp_backend.h"

#include <cstdio>
#include <cstring>
#include <iostream>
#include <random>

namespace CppTrader {
namespace Protocol {

ProtocolServer::ProtocolServer(std::unique_ptr<INetworkBackend> backend, CppTrader::Matching::MarketManager& market)
    : _backend(std::move(backend))
    , _market(market)
    , _auth_enabled(false)
{
}

ProtocolServer::~ProtocolServer()
{
}

bool ProtocolServer::init()
{
    if (!_backend)
    {
        std::cerr << "ProtocolServer::init() failed: no backend" << std::endl;
        return false;
    }

    // Set up backend callbacks
    if (auto* tcp = dynamic_cast<TcpBackend*>(_backend.get()))
    {
        tcp->SetMessageHandler([this](uint16_t conn_id, const MsgHeader& header, const uint8_t* body, size_t body_len)
        {
            OnMessage(conn_id, header, body, body_len);
        });
        tcp->SetConnectHandler([this](uint16_t conn_id)
        {
            OnConnect(conn_id);
        });
        tcp->SetDisconnectHandler([this](uint16_t conn_id)
        {
            OnDisconnect(conn_id);
        });
    }

    return _backend->init();
}

void ProtocolServer::poll()
{
    if (_backend)
    {
        _backend->poll();
    }
}

void ProtocolServer::RegisterHandler(MsgType msg_type, const RequestHandler& handler)
{
    _handlers[msg_type] = handler;
}

void ProtocolServer::SendResponse(uint16_t conn_id, const MsgHeader& header, const void* body, size_t body_len)
{
    if (!_backend)
        return;

    std::cout << "[DEBUG] SendResponse conn=" << conn_id << " type=0x" << std::hex << static_cast<int>(header.Type)
              << std::dec << " flags=0x" << std::hex << static_cast<int>(header.Flags)
              << std::dec << " body_len=" << body_len << std::endl;

    std::vector<uint8_t> frame(sizeof(MsgHeader) + body_len);
    std::memcpy(frame.data(), &header, sizeof(MsgHeader));
    if (body_len > 0 && body != nullptr)
    {
        std::memcpy(frame.data() + sizeof(MsgHeader), body, body_len);
    }

    _backend->send(conn_id, frame.data(), frame.size());
}

void ProtocolServer::SendResponse(uint16_t conn_id, const MsgHeader& header)
{
    if (!_backend)
        return;

    std::cout << "[DEBUG] SendResponse(header-only) conn=" << conn_id << " type=0x" << std::hex << static_cast<int>(header.Type)
              << std::dec << " flags=0x" << std::hex << static_cast<int>(header.Flags) << std::dec << std::endl;

    _backend->send(conn_id, &header, sizeof(MsgHeader));
}

void ProtocolServer::Broadcast(const MsgHeader& header, const void* body, size_t body_len)
{
    if (!_backend)
        return;

    std::cout << "[DEBUG] Broadcast type=0x" << std::hex << static_cast<int>(header.Type)
              << std::dec << " body_len=" << body_len << std::endl;

    std::vector<uint8_t> frame(sizeof(MsgHeader) + body_len);
    std::memcpy(frame.data(), &header, sizeof(MsgHeader));
    if (body_len > 0 && body != nullptr)
    {
        std::memcpy(frame.data() + sizeof(MsgHeader), body, body_len);
    }

    _backend->broadcast(frame.data(), frame.size());
}

void ProtocolServer::BroadcastToSymbol(uint32_t symbol_id, const MsgHeader& header, const void* body, size_t body_len)
{
    if (!_backend)
        return;

    std::cout << "[DEBUG] BroadcastToSymbol symbol_id=" << symbol_id << " type=0x" << std::hex << static_cast<int>(header.Type)
              << std::dec << " body_len=" << body_len << std::endl;

    std::vector<uint8_t> frame(sizeof(MsgHeader) + body_len);
    std::memcpy(frame.data(), &header, sizeof(MsgHeader));
    if (body_len > 0 && body != nullptr)
    {
        std::memcpy(frame.data() + sizeof(MsgHeader), body, body_len);
    }

    std::lock_guard<std::mutex> lock(_state_mutex);
    for (const auto& [conn_id, symbols] : _order_book_subscriptions)
    {
        if (symbols.find(symbol_id) != symbols.end())
        {
            _backend->send(conn_id, frame.data(), frame.size());
        }
    }

    for (const auto& [conn_id, symbols] : _order_subscriptions)
    {
        if (symbols.find(symbol_id) != symbols.end())
        {
            if (_order_book_subscriptions.count(conn_id) &&
                _order_book_subscriptions.at(conn_id).count(symbol_id))
            {
                continue;
            }

            _backend->send(conn_id, frame.data(), frame.size());
        }
    }
}

void ProtocolServer::SubscribeOrderBook(uint16_t conn_id, uint32_t symbol_id)
{
    std::cout << "[DEBUG] SubscribeOrderBook conn=" << conn_id << " symbol_id=" << symbol_id << std::endl;
    std::lock_guard<std::mutex> lock(_state_mutex);
    _order_book_subscriptions[conn_id].insert(symbol_id);
}

void ProtocolServer::SubscribeOrders(uint16_t conn_id, uint32_t symbol_id)
{
    std::cout << "[DEBUG] SubscribeOrders conn=" << conn_id << " symbol_id=" << symbol_id << std::endl;
    std::lock_guard<std::mutex> lock(_state_mutex);
    _order_subscriptions[conn_id].insert(symbol_id);
}

void ProtocolServer::UnsubscribeOrderBook(uint16_t conn_id, uint32_t symbol_id)
{
    std::lock_guard<std::mutex> lock(_state_mutex);
    auto it = _order_book_subscriptions.find(conn_id);
    if (it != _order_book_subscriptions.end())
    {
        it->second.erase(symbol_id);
        if (it->second.empty())
        {
            _order_book_subscriptions.erase(it);
        }
    }
}

void ProtocolServer::UnsubscribeOrders(uint16_t conn_id, uint32_t symbol_id)
{
    std::lock_guard<std::mutex> lock(_state_mutex);
    auto it = _order_subscriptions.find(conn_id);
    if (it != _order_subscriptions.end())
    {
        it->second.erase(symbol_id);
        if (it->second.empty())
        {
            _order_subscriptions.erase(it);
        }
    }
}

void ProtocolServer::RemoveConnection(uint16_t conn_id)
{
    std::lock_guard<std::mutex> lock(_state_mutex);
    _order_book_subscriptions.erase(conn_id);
    _order_subscriptions.erase(conn_id);
    _hmac_verifiers.erase(conn_id);
    _authenticated_connections.erase(conn_id);
}

void ProtocolServer::OnMessage(uint16_t conn_id, const MsgHeader& header, const uint8_t* body, size_t body_len)
{
    std::cout << "[DEBUG] OnMessage conn=" << conn_id << " type=0x" << std::hex << static_cast<int>(header.Type)
              << std::dec << " flags=0x" << std::hex << static_cast<int>(header.Flags)
              << std::dec << " body_len=" << body_len << std::endl;

    // Rate limiting check
    if (!_rate_limiter.Allow())
    {
        std::cout << "[DEBUG] OnMessage conn=" << conn_id << " RATE LIMITED" << std::endl;
        SimpleResponse response(ErrorCode::RATE_LIMITED);
        MsgHeader resp_header(MsgType::SIMPLE_RESPONSE, Flags::RESPONSE, sizeof(response));
        SendResponse(conn_id, resp_header, &response, sizeof(response));
        return;
    }

    // Authentication check: only AUTH/HEARTBEAT/RECONCILE allowed for unauthenticated connections
    // Skip check when authentication is not enabled (backward compatibility)
    if (_auth_enabled)
    {
        bool is_authenticated = IsAuthenticated(conn_id);
        if (!is_authenticated)
        {
            MsgType type = header.Type;
            if (type != MsgType::AUTH_REQUEST && type != MsgType::HEARTBEAT_REQ && type != MsgType::HEARTBEAT_RESP &&
                type != MsgType::RECONCILE_REQUEST && type != MsgType::RECONCILE_RESPONSE)
            {
                std::cout << "[DEBUG] OnMessage conn=" << conn_id << " NOT AUTHENTICATED" << std::endl;
                SimpleResponse response(ErrorCode::NOT_AUTHENTICATED);
                MsgHeader resp_header(MsgType::SIMPLE_RESPONSE, Flags::RESPONSE, sizeof(response));
                SendResponse(conn_id, resp_header, &response, sizeof(response));
                return;
            }
        }
        else
        {
            if (header.Type == MsgType::AUTH_REQUEST)
            {
                std::cout << "[DEBUG] OnMessage conn=" << conn_id << " RE-AUTH NOT ALLOWED" << std::endl;
                SimpleResponse response(ErrorCode::NOT_AUTHENTICATED);
                MsgHeader resp_header(MsgType::SIMPLE_RESPONSE, Flags::RESPONSE | Flags::ERROR, sizeof(response));
                SendResponse(conn_id, resp_header, &response, sizeof(response));
                return;
            }

            auto* verifier = GetHmacVerifier(conn_id);
            if (verifier && !verifier->VerifyPrefix(header, body, body_len))
            {
                std::cout << "[DEBUG] OnMessage conn=" << conn_id << " INVALID HMAC PREFIX" << std::endl;
                SimpleResponse response(ErrorCode::INVALID_SIGNATURE);
                MsgHeader resp_header(MsgType::SIMPLE_RESPONSE, Flags::RESPONSE | Flags::ERROR, sizeof(response));
                SendResponse(conn_id, resp_header, &response, sizeof(response));
                return;
            }
        }
    }

    // Audit logging
    if (_audit_logger)
    {
        char detail[64];
        std::snprintf(detail, sizeof(detail), "type=0x%02x body_len=%zu", static_cast<int>(header.Type), body_len);
        _audit_logger(conn_id, "message", detail);
    }

    auto it = _handlers.find(header.Type);
    if (it != _handlers.end())
    {
        it->second(conn_id, header, body, body_len);
    }
    else
    {
        std::cerr << "ProtocolServer: unhandled message type " << static_cast<int>(header.Type) << std::endl;
    }
}

void ProtocolServer::OnConnect(uint16_t conn_id)
{
    std::cout << "[DEBUG] OnConnect conn=" << conn_id << std::endl;
    std::lock_guard<std::mutex> lock(_state_mutex);
    _authenticated_connections[conn_id] = false;
}

void ProtocolServer::OnDisconnect(uint16_t conn_id)
{
    std::cout << "[DEBUG] OnDisconnect conn=" << conn_id << std::endl;
    RemoveConnection(conn_id);
}

void ProtocolServer::SetAuthenticated(uint16_t conn_id, bool authenticated)
{
    std::lock_guard<std::mutex> lock(_state_mutex);
    _authenticated_connections[conn_id] = authenticated;
    std::cout << "[DEBUG] SetAuthenticated conn=" << conn_id << " authenticated=" << authenticated << std::endl;
}

bool ProtocolServer::IsAuthenticated(uint16_t conn_id) const
{
    std::lock_guard<std::mutex> lock(_state_mutex);
    auto it = _authenticated_connections.find(conn_id);
    return it != _authenticated_connections.end() && it->second;
}

void ProtocolServer::SetSessionKey(uint16_t conn_id, const uint8_t* key, size_t key_len)
{
    std::lock_guard<std::mutex> lock(_state_mutex);
    _hmac_verifiers[conn_id] = std::make_unique<HmacVerifier>(key, key_len);
    std::cout << "[DEBUG] SetSessionKey conn=" << conn_id << " key_len=" << key_len << std::endl;
}

HmacVerifier* ProtocolServer::GetHmacVerifier(uint16_t conn_id)
{
    std::lock_guard<std::mutex> lock(_state_mutex);
    return GetHmacVerifierLocked(conn_id);
}

HmacVerifier* ProtocolServer::GetHmacVerifierLocked(uint16_t conn_id)
{
    auto it = _hmac_verifiers.find(conn_id);
    if (it != _hmac_verifiers.end())
        return it->second.get();
    return nullptr;
}

void ProtocolServer::RemoveSessionKey(uint16_t conn_id)
{
    std::lock_guard<std::mutex> lock(_state_mutex);
    _hmac_verifiers.erase(conn_id);
}

void ProtocolServer::RegisterApiKey(const std::string& api_key_id, const std::string& api_key_secret)
{
    std::lock_guard<std::mutex> lock(_state_mutex);
    _api_keys[api_key_id] = api_key_secret;
    std::cout << "[DEBUG] RegisterApiKey id=" << api_key_id << std::endl;
}

std::string ProtocolServer::GetApiKeySecret(const std::string& api_key_id) const
{
    std::lock_guard<std::mutex> lock(_state_mutex);
    auto it = _api_keys.find(api_key_id);
    if (it != _api_keys.end())
        return it->second;
    return "";
}

} // namespace Protocol
} // namespace CppTrader
