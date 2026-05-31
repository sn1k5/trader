/*!
    \file server.cpp
    \brief Protocol server implementation
    \author CppTrader Team
    \date 18.05.2026
    \copyright MIT License
*/

#include "trader/protocol/server.h"

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
    , _last_cleanup_ms(0)
{
}

ProtocolServer::~ProtocolServer()
{
}

void ProtocolServer::SetOutboundHandlers(OutboundSendHandler send_handler, OutboundBroadcastHandler broadcast_handler)
{
    _outbound_send = std::move(send_handler);
    _outbound_broadcast = std::move(broadcast_handler);
}

void ProtocolServer::SetOutputCallback(OutputCallback send_cb, BroadcastCallback broadcast_cb)
{
    _output_send = std::move(send_cb);
    _output_broadcast = std::move(broadcast_cb);
}

void ProtocolServer::ProcessMessage(uint16_t conn_id, const MsgHeader& header, const uint8_t* body, size_t body_len)
{
    OnMessage(conn_id, header, body, body_len);
}

void ProtocolServer::ProcessConnect(uint16_t conn_id)
{
    OnConnect(conn_id);
}

void ProtocolServer::ProcessDisconnect(uint16_t conn_id)
{
    OnDisconnect(conn_id);
}

bool ProtocolServer::init()
{
    if (_backend)
    {
        _backend->SetMessageHandler([this](uint16_t conn_id, const MsgHeader& header, const uint8_t* body, size_t body_len)
        {
            OnMessage(conn_id, header, body, body_len);
        });
        _backend->SetConnectHandler([this](uint16_t conn_id)
        {
            OnConnect(conn_id);
        });
        _backend->SetDisconnectHandler([this](uint16_t conn_id)
        {
            OnDisconnect(conn_id);
        });

        return _backend->init();
    }

    if (_output_send && _output_broadcast)
    {
        return true;
    }

    std::cerr << "ProtocolServer::init() failed: no backend and no output callbacks" << std::endl;
    return false;
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
    if (!_backend && !_output_send)
        return;

    MsgHeader out_header = header;
    {
        std::lock_guard<std::mutex> lock(_state_mutex);
        auto& ack_state = conn_ack_states_[conn_id];
        out_header.Sequence = static_cast<uint32_t>(++ack_state.last_sent_seq);
    }

    std::vector<uint8_t> frame(sizeof(MsgHeader) + body_len);
    std::memcpy(frame.data(), &out_header, sizeof(MsgHeader));
    if (body_len > 0 && body != nullptr)
    {
        std::memcpy(frame.data() + sizeof(MsgHeader), body, body_len);
    }

    if (_output_send)
        _output_send(conn_id, frame.data(), frame.size());
    else if (_backend)
        _backend->send(conn_id, frame.data(), frame.size());
}

void ProtocolServer::SendResponse(uint16_t conn_id, const MsgHeader& header)
{
    if (!_backend && !_output_send)
        return;

    MsgHeader out_header = header;
    {
        std::lock_guard<std::mutex> lock(_state_mutex);
        auto& ack_state = conn_ack_states_[conn_id];
        out_header.Sequence = static_cast<uint32_t>(++ack_state.last_sent_seq);
    }

    if (_output_send)
        _output_send(conn_id, &out_header, sizeof(MsgHeader));
    else if (_backend)
        _backend->send(conn_id, &out_header, sizeof(MsgHeader));
}

void ProtocolServer::Broadcast(const MsgHeader& header, const void* body, size_t body_len)
{
    if (!_backend && !_output_broadcast)
        return;


    std::vector<uint8_t> frame(sizeof(MsgHeader) + body_len);
    std::memcpy(frame.data(), &header, sizeof(MsgHeader));
    if (body_len > 0 && body != nullptr)
    {
        std::memcpy(frame.data() + sizeof(MsgHeader), body, body_len);
    }

    if (_output_broadcast)
        _output_broadcast(frame.data(), frame.size());
    else if (_backend)
        _backend->broadcast(frame.data(), frame.size());
}

void ProtocolServer::BroadcastToSymbol(uint32_t symbol_id, const MsgHeader& header, const void* body, size_t body_len)
{
    if (!_backend && !_output_send)
        return;


    std::vector<uint8_t> frame(sizeof(MsgHeader) + body_len);
    std::memcpy(frame.data(), &header, sizeof(MsgHeader));
    if (body_len > 0 && body != nullptr)
    {
        std::memcpy(frame.data() + sizeof(MsgHeader), body, body_len);
    }

    std::vector<uint16_t> target_conns;
    {
        std::lock_guard<std::mutex> lock(_state_mutex);
        for (const auto& [conn_id, symbols] : _order_book_subscriptions)
        {
            if (symbols.find(symbol_id) != symbols.end())
            {
                target_conns.push_back(conn_id);
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

                target_conns.push_back(conn_id);
            }
        }
    }

    for (uint16_t cid : target_conns)
    {
        {
            std::lock_guard<std::mutex> lock(_state_mutex);
            auto& ack_state = conn_ack_states_[cid];
            reinterpret_cast<MsgHeader*>(frame.data())->Sequence = static_cast<uint32_t>(++ack_state.last_sent_seq);
        }

        if (_output_send)
            _output_send(cid, frame.data(), frame.size());
        else if (_backend)
            _backend->send(cid, frame.data(), frame.size());
    }
}

void ProtocolServer::SubscribeOrderBook(uint16_t conn_id, uint32_t symbol_id)
{
    std::lock_guard<std::mutex> lock(_state_mutex);
    _order_book_subscriptions[conn_id].insert(symbol_id);
}

void ProtocolServer::SubscribeOrders(uint16_t conn_id, uint32_t symbol_id)
{
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
    conn_ack_states_.erase(conn_id);
    _session_manager.Destroy(conn_id);
}

void ProtocolServer::OnMessage(uint16_t conn_id, const MsgHeader& header, const uint8_t* body, size_t body_len)
{

    // Periodic session cleanup (every 60 seconds)
    {
        uint64_t now_ms = static_cast<uint64_t>(
            std::chrono::duration_cast<std::chrono::milliseconds>(
                std::chrono::system_clock::now().time_since_epoch()).count());
        if (now_ms - _last_cleanup_ms > 60000)
        {
            _session_manager.CleanupExpired();
            _last_cleanup_ms = now_ms;
        }
    }

    // Rate limiting check
    if (!_rate_limiter.Allow())
    {
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
                SimpleResponse response(ErrorCode::NOT_AUTHENTICATED);
                MsgHeader resp_header(MsgType::SIMPLE_RESPONSE, Flags::RESPONSE | Flags::ERROR, sizeof(response));
                SendResponse(conn_id, resp_header, &response, sizeof(response));
                return;
            }

            auto auth_result = ValidateAndTouchSession(conn_id);
            if (!auth_result.session)
            {
                SetAuthenticated(conn_id, false);
                RemoveSessionKey(conn_id);
                SimpleResponse response(ErrorCode::AUTH_EXPIRED);
                MsgHeader resp_header(MsgType::SIMPLE_RESPONSE, Flags::RESPONSE | Flags::ERROR, sizeof(response));
                SendResponse(conn_id, resp_header, &response, sizeof(response));
                return;
            }

            if (auth_result.verifier && !auth_result.verifier->VerifyPrefix(header, body, body_len))
            {
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
    std::lock_guard<std::mutex> lock(_state_mutex);
    _authenticated_connections[conn_id] = false;
    conn_ack_states_.try_emplace(conn_id);
}

void ProtocolServer::OnDisconnect(uint16_t conn_id)
{
    RemoveConnection(conn_id);
}

void ProtocolServer::SetAuthenticated(uint16_t conn_id, bool authenticated)
{
    std::lock_guard<std::mutex> lock(_state_mutex);
    _authenticated_connections[conn_id] = authenticated;
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

void ProtocolServer::RegisterApiKey(const std::string& api_key_id, const std::string& api_key_secret,
                                     uint64_t account_id, uint8_t role)
{
    std::lock_guard<std::mutex> lock(_state_mutex);
    ApiKeyInfo info;
    info.secret = api_key_secret;
    info.account_id = account_id;
    info.role = static_cast<Role>(role);
    _api_keys[api_key_id] = info;
}

ApiKeyInfo ProtocolServer::GetApiKeyInfo(const std::string& api_key_id) const
{
    std::lock_guard<std::mutex> lock(_state_mutex);
    auto it = _api_keys.find(api_key_id);
    if (it != _api_keys.end())
        return it->second;
    return ApiKeyInfo{};
}

std::string ProtocolServer::GetApiKeySecret(const std::string& api_key_id) const
{
    std::lock_guard<std::mutex> lock(_state_mutex);
    auto it = _api_keys.find(api_key_id);
    if (it != _api_keys.end())
        return it->second.secret;
    return "";
}

bool ProtocolServer::ValidateSession(uint16_t conn_id)
{
    auto session = _session_manager.FindByConnId(conn_id);
    return session != nullptr;
}

void ProtocolServer::TouchSession(uint16_t conn_id)
{
    auto session = _session_manager.FindByConnId(conn_id);
    if (session)
    {
        std::array<uint8_t, 32> token = session->token;
        _session_manager.Touch(token);
    }
}

ProtocolServer::AuthResult ProtocolServer::ValidateAndTouchSession(uint16_t conn_id)
{
    AuthResult result;
    result.session = _session_manager.ValidateAndTouchByConnId(conn_id);
    if (result.session)
    {
        std::lock_guard<std::mutex> lock(_state_mutex);
        result.verifier = GetHmacVerifierLocked(conn_id);
    }
    return result;
}

void ProtocolServer::CleanupOldConnection(uint16_t old_conn_id)
{
    std::lock_guard<std::mutex> lock(_state_mutex);
    _order_book_subscriptions.erase(old_conn_id);
    _order_subscriptions.erase(old_conn_id);
    _hmac_verifiers.erase(old_conn_id);
    _authenticated_connections.erase(old_conn_id);
}

void ProtocolServer::RecordAck(uint16_t conn_id, uint64_t ack_seq)
{
    std::lock_guard<std::mutex> lock(_state_mutex);
    auto it = conn_ack_states_.find(conn_id);
    if (it != conn_ack_states_.end())
    {
        uint64_t current = it->second.last_acked_seq.load(std::memory_order_relaxed);
        if (ack_seq > current)
            it->second.last_acked_seq.store(ack_seq, std::memory_order_relaxed);
    }
}

} // namespace Protocol
} // namespace CppTrader
