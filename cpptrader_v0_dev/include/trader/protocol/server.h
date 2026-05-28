/*!
    \file server.h
    \brief Protocol server main class
    \author CppTrader Team
    \date 18.05.2026
    \copyright MIT License
*/

#ifndef CPPTRADER_PROTOCOL_SERVER_H
#define CPPTRADER_PROTOCOL_SERVER_H

#include "network_backend.h"
#include "protocol.h"
#include "message.h"
#include "hmac.h"
#include "anti_replay.h"

#include "trader/matching/market_manager.h"

#include <algorithm>
#include <chrono>
#include <cstdint>
#include <cstddef>
#include <functional>
#include <memory>
#include <mutex>
#include <string>
#include <unordered_map>
#include <unordered_set>
#include <vector>

namespace CppTrader {
namespace Protocol {

//! Simple token-bucket rate limiter
class RateLimiter
{
public:
    explicit RateLimiter(uint32_t max_requests_per_second = 1000)
        : _max_rate(max_requests_per_second)
        , _tokens(max_requests_per_second)
        , _last_refill(std::chrono::steady_clock::now())
    {}

    bool Allow()
    {
        auto now = std::chrono::steady_clock::now();
        auto elapsed = std::chrono::duration_cast<std::chrono::milliseconds>(now - _last_refill).count();
        _tokens = std::min(_tokens + static_cast<uint32_t>(elapsed * _max_rate / 1000), _max_rate);
        _last_refill = now;
        if (_tokens > 0)
        {
            --_tokens;
            return true;
        }
        return false;
    }

private:
    uint32_t _max_rate;
    uint32_t _tokens;
    std::chrono::steady_clock::time_point _last_refill;
};

//! Protocol server main class
/*!
    The ProtocolServer manages the network backend, routes incoming requests
    to appropriate handlers based on msg_type, manages per-connection subscription
    state (symbol_id filtering), and broadcasts market events to subscribed clients.
*/
class ProtocolServer
{
public:
    //! Request handler function type
    using RequestHandler = std::function<void(uint16_t conn_id, const MsgHeader& header, const uint8_t* body, size_t body_len)>;

    //! Constructor
    /*!
        \param backend - Network backend instance (TCP or DPDK)
        \param market - Market manager reference
    */
    ProtocolServer(std::unique_ptr<INetworkBackend> backend, CppTrader::Matching::MarketManager& market);
    ~ProtocolServer();

    ProtocolServer(const ProtocolServer&) = delete;
    ProtocolServer(ProtocolServer&&) = delete;
    ProtocolServer& operator=(const ProtocolServer&) = delete;
    ProtocolServer& operator=(ProtocolServer&&) = delete;

    //! Initialize the server
    bool init();

    //! Run one iteration of the event loop
    void poll();

    //! Register a handler for a specific message type
    /*!
        \param msg_type - Message type to handle
        \param handler - Handler function
    */
    void RegisterHandler(MsgType msg_type, const RequestHandler& handler);

    //! Send a response to a specific connection
    /*!
        \param conn_id - Connection identifier
        \param header - Message header
        \param body - Message body data
        \param body_len - Body length
    */
    void SendResponse(uint16_t conn_id, const MsgHeader& header, const void* body, size_t body_len);

    //! Send a response to a specific connection (header-only)
    /*!
        \param conn_id - Connection identifier
        \param header - Message header
    */
    void SendResponse(uint16_t conn_id, const MsgHeader& header);

    //! Broadcast a message to all connected clients
    /*!
        \param header - Message header
        \param body - Message body data
        \param body_len - Body length
    */
    void Broadcast(const MsgHeader& header, const void* body, size_t body_len);

    //! Broadcast a message to clients subscribed to a specific symbol
    /*!
        \param symbol_id - Symbol identifier to filter subscribers
        \param header - Message header
        \param body - Message body data
        \param body_len - Body length
    */
    void BroadcastToSymbol(uint32_t symbol_id, const MsgHeader& header, const void* body, size_t body_len);

    //! Subscribe a connection to order book updates for a symbol
    /*!
        \param conn_id - Connection identifier
        \param symbol_id - Symbol identifier
    */
    void SubscribeOrderBook(uint16_t conn_id, uint32_t symbol_id);

    //! Subscribe a connection to order updates for a symbol
    /*!
        \param conn_id - Connection identifier
        \param symbol_id - Symbol identifier
    */
    void SubscribeOrders(uint16_t conn_id, uint32_t symbol_id);

    //! Unsubscribe a connection from order book updates
    /*!
        \param conn_id - Connection identifier
        \param symbol_id - Symbol identifier
    */
    void UnsubscribeOrderBook(uint16_t conn_id, uint32_t symbol_id);

    //! Unsubscribe a connection from order updates
    /*!
        \param conn_id - Connection identifier
        \param symbol_id - Symbol identifier
    */
    void UnsubscribeOrders(uint16_t conn_id, uint32_t symbol_id);

    //! Mark a connection as authenticated
    void SetAuthenticated(uint16_t conn_id, bool authenticated = true);

    //! Check if a connection is authenticated
    bool IsAuthenticated(uint16_t conn_id) const;

    //! Set audit logger callback
    void SetAuditLogger(std::function<void(uint16_t conn_id, const char* action, const char* detail)> logger) { _audit_logger = logger; }

    //! Enable or disable authentication requirement (default: disabled for backward compatibility)
    void SetAuthEnabled(bool enabled) { _auth_enabled = enabled; }

    //! Check if authentication is enabled
    bool IsAuthEnabled() const noexcept { return _auth_enabled; }

    //! Remove all subscriptions for a connection (called on disconnect)
    /*!
        \param conn_id - Connection identifier
    */
    void RemoveConnection(uint16_t conn_id);

    //! Set session key for a connection (after successful authentication)
    void SetSessionKey(uint16_t conn_id, const uint8_t* key, size_t key_len);

    //! Get HMAC verifier for a connection (returns nullptr if not authenticated)
    HmacVerifier* GetHmacVerifier(uint16_t conn_id);

    //! Remove session key for a connection
    void RemoveSessionKey(uint16_t conn_id);

    //! Register an API key (api_key_id -> api_key_secret mapping)
    void RegisterApiKey(const std::string& api_key_id, const std::string& api_key_secret);

    //! Look up API key secret by ID (returns empty string if not found)
    std::string GetApiKeySecret(const std::string& api_key_id) const;

    //! Get the anti-replay checker
    AntiReplayChecker& GetAntiReplayChecker() { return _anti_replay; }

    //! Get the network backend
    INetworkBackend* Backend() const noexcept { return _backend.get(); }

    //! Get the market manager
    CppTrader::Matching::MarketManager& Market() const noexcept { return _market; }

private:
    std::unique_ptr<INetworkBackend> _backend;
    CppTrader::Matching::MarketManager& _market;

    // Message type -> handler mapping
    std::unordered_map<MsgType, RequestHandler> _handlers;

    // Subscription state: conn_id -> set of symbol_ids
    std::unordered_map<uint16_t, std::unordered_set<uint32_t>> _order_book_subscriptions;
    std::unordered_map<uint16_t, std::unordered_set<uint32_t>> _order_subscriptions;

    // Authentication state: conn_id -> authenticated flag
    std::unordered_map<uint16_t, bool> _authenticated_connections;

    // Whether authentication is required (default: false for backward compatibility)
    bool _auth_enabled;

    // Rate limiter
    RateLimiter _rate_limiter;

    // Audit log callback (optional)
    std::function<void(uint16_t conn_id, const char* action, const char* detail)> _audit_logger;

    // HMAC session state: conn_id -> HmacVerifier
    std::unordered_map<uint16_t, std::unique_ptr<HmacVerifier>> _hmac_verifiers;

    // API key store: api_key_id -> api_key_secret
    std::unordered_map<std::string, std::string> _api_keys;

    // Anti-replay checker
    AntiReplayChecker _anti_replay;

    // Mutex for protecting shared state
    mutable std::mutex _state_mutex;

    // Internal message dispatcher
    void OnMessage(uint16_t conn_id, const MsgHeader& header, const uint8_t* body, size_t body_len);
    void OnConnect(uint16_t conn_id);
    void OnDisconnect(uint16_t conn_id);

    HmacVerifier* GetHmacVerifierLocked(uint16_t conn_id);
};

} // namespace Protocol
} // namespace CppTrader

#endif // CPPTRADER_PROTOCOL_SERVER_H
