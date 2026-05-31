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
#include "session_manager.h"

#include "trader/matching/market_manager.h"

#include <algorithm>
#include <atomic>
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

struct ConnectionAckState
{
    std::atomic<uint64_t> last_sent_seq{0};
    std::atomic<uint64_t> last_acked_seq{0};
};

struct ApiKeyInfo
{
    std::string secret;
    uint64_t account_id = 0;
    Role role = Role::TRADER;
};

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
    friend class BusinessThread;

public:
    using RequestHandler = std::function<void(uint16_t conn_id, const MsgHeader& header, const uint8_t* body, size_t body_len)>;
    using OutboundSendHandler = std::function<void(uint16_t conn_id, const void* data, size_t len)>;
    using OutboundBroadcastHandler = std::function<void(const void* data, size_t len)>;
    using OutputCallback = std::function<void(uint16_t conn_id, const void* data, size_t len)>;
    using BroadcastCallback = std::function<void(const void* data, size_t len)>;

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

    bool init();

    void SetOutboundHandlers(OutboundSendHandler send_handler, OutboundBroadcastHandler broadcast_handler);

    void SetOutputCallback(OutputCallback send_cb, BroadcastCallback broadcast_cb);
    void ProcessMessage(uint16_t conn_id, const MsgHeader& header, const uint8_t* body, size_t body_len);
    void ProcessConnect(uint16_t conn_id);
    void ProcessDisconnect(uint16_t conn_id);

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

    void RecordAck(uint16_t conn_id, uint64_t ack_seq);

    //! Set session key for a connection (after successful authentication)
    void SetSessionKey(uint16_t conn_id, const uint8_t* key, size_t key_len);

    //! Get HMAC verifier for a connection (returns nullptr if not authenticated)
    HmacVerifier* GetHmacVerifier(uint16_t conn_id);

    //! Remove session key for a connection
    void RemoveSessionKey(uint16_t conn_id);

    //! Register an API key (api_key_id -> api_key_secret mapping)
    void RegisterApiKey(const std::string& api_key_id, const std::string& api_key_secret,
                        uint64_t account_id = 0, uint8_t role = 1);

    //! Look up API key info by ID (returns empty secret if not found)
    ApiKeyInfo GetApiKeyInfo(const std::string& api_key_id) const;

    //! Look up API key secret by ID (returns empty string if not found)
    std::string GetApiKeySecret(const std::string& api_key_id) const;

    //! Get the anti-replay checker
    AntiReplayChecker& GetAntiReplayChecker() { return _anti_replay; }

    //! Get the session manager
    SessionManager& GetSessionManager() { return _session_manager; }

    //! Get the network backend
    INetworkBackend* Backend() const noexcept { return _backend.get(); }

    //! Get the market manager
    CppTrader::Matching::MarketManager& Market() const noexcept { return _market; }

    //! Validate session for a connection, returns true if session is valid
    bool ValidateSession(uint16_t conn_id);

    //! Touch session for a connection (update last_active)
    void TouchSession(uint16_t conn_id);

    struct AuthResult
    {
        std::shared_ptr<SessionManager::Session> session;
        HmacVerifier* verifier = nullptr;
    };

    //! Validate session + touch + get HMAC verifier in one locked operation
    AuthResult ValidateAndTouchSession(uint16_t conn_id);

    //! Cleanup old connection on session recovery
    void CleanupOldConnection(uint16_t old_conn_id);

private:
    std::unique_ptr<INetworkBackend> _backend;
    CppTrader::Matching::MarketManager& _market;

    OutboundSendHandler _outbound_send;
    OutboundBroadcastHandler _outbound_broadcast;

    OutputCallback _output_send;
    BroadcastCallback _output_broadcast;

    // Message type -> handler mapping
    std::unordered_map<MsgType, RequestHandler> _handlers;

    // Subscription state: conn_id -> set of symbol_ids
    std::unordered_map<uint16_t, std::unordered_set<uint32_t>> _order_book_subscriptions;
    std::unordered_map<uint16_t, std::unordered_set<uint32_t>> _order_subscriptions;

    // Authentication state: conn_id -> authenticated flag
    std::unordered_map<uint16_t, bool> _authenticated_connections;

    std::unordered_map<uint16_t, ConnectionAckState> conn_ack_states_;

    // Whether authentication is required (default: false for backward compatibility)
    bool _auth_enabled;

    // Rate limiter
    RateLimiter _rate_limiter;

    // Audit log callback (optional)
    std::function<void(uint16_t conn_id, const char* action, const char* detail)> _audit_logger;

    // HMAC session state: conn_id -> HmacVerifier
    std::unordered_map<uint16_t, std::unique_ptr<HmacVerifier>> _hmac_verifiers;

    // API key store: api_key_id -> ApiKeyInfo
    std::unordered_map<std::string, ApiKeyInfo> _api_keys;

    // Anti-replay checker
    AntiReplayChecker _anti_replay;

    // Session manager
    SessionManager _session_manager;

    // Mutex for protecting shared state
    mutable std::mutex _state_mutex;

    // Last cleanup timestamp for periodic session cleanup
    uint64_t _last_cleanup_ms;

    // Internal message dispatcher
    void OnMessage(uint16_t conn_id, const MsgHeader& header, const uint8_t* body, size_t body_len);
    void OnConnect(uint16_t conn_id);
    void OnDisconnect(uint16_t conn_id);

    HmacVerifier* GetHmacVerifierLocked(uint16_t conn_id);
};

} // namespace Protocol
} // namespace CppTrader

#endif // CPPTRADER_PROTOCOL_SERVER_H
