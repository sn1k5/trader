#ifndef CPPTRADER_PROTOCOL_SESSION_MANAGER_H
#define CPPTRADER_PROTOCOL_SESSION_MANAGER_H

#include <array>
#include <atomic>
#include <cstdint>
#include <chrono>
#include <map>
#include <memory>
#include <mutex>
#include <unordered_map>
#include <random>

namespace CppTrader {
namespace Protocol {

enum class Role : uint8_t
{
    ADMIN = 0,
    TRADER = 1,
    VIEWER = 2,
    QUANTBOT = 3
};

struct TokenKey
{
    std::array<uint8_t, 32> token{};
    size_t hash;

    TokenKey() noexcept : hash(0) {}
    explicit TokenKey(const std::array<uint8_t, 32>& t) noexcept : token(t), hash(ComputeHash(t)) {}

    static size_t ComputeHash(const std::array<uint8_t, 32>& key) noexcept
    {
        size_t result = 0xcbf29ce484222325ULL;
        for (auto b : key)
        {
            result ^= b;
            result *= 0x100000001b3ULL;
        }
        return result;
    }
};

struct TokenKeyHash
{
    size_t operator()(const TokenKey& key) const noexcept { return key.hash; }
};

struct TokenKeyEqual
{
    bool operator()(const TokenKey& a, const TokenKey& b) const noexcept
    {
        return a.hash == b.hash && a.token == b.token;
    }
};

class SessionManager
{
public:
    using ExpiryQueue = std::multimap<uint64_t, TokenKey>;

    struct Session
    {
        std::array<uint8_t, 32> token{};
        uint64_t account_id = 0;
        Role role = Role::TRADER;
        uint16_t conn_id = 0;
        uint64_t created_at_ms = 0;
        uint64_t last_active_ms = 0;
        bool revoked = false;
        ExpiryQueue::iterator expiry_it;
    };

    static constexpr size_t SHARD_COUNT = 64;

    using SessionMap = std::unordered_map<TokenKey, std::shared_ptr<Session>, TokenKeyHash, TokenKeyEqual>;

    struct Shard
    {
        SessionMap sessions;
        std::unordered_map<uint16_t, TokenKey> conn_sessions;
        ExpiryQueue expiry_queue;
        mutable std::mutex mutex;
    };

    SessionManager() = default;
    ~SessionManager() = default;

    SessionManager(const SessionManager&) = delete;
    SessionManager(SessionManager&&) = delete;
    SessionManager& operator=(const SessionManager&) = delete;
    SessionManager& operator=(SessionManager&&) = delete;

    std::shared_ptr<Session> Create(uint64_t account_id, Role role, uint16_t conn_id);

    std::shared_ptr<Session> Validate(const std::array<uint8_t, 32>& token);

    void Touch(const std::array<uint8_t, 32>& token);

    void Destroy(uint16_t conn_id);

    void Revoke(const std::array<uint8_t, 32>& token);

    struct RecoverResult
    {
        std::shared_ptr<Session> session;
        uint16_t old_conn_id = 0;
        bool had_old_conn = false;
    };

    RecoverResult Recover(const std::array<uint8_t, 32>& token, uint16_t new_conn_id);

    void CleanupExpired();

    size_t ActiveSessionCount() const;

    std::shared_ptr<Session> FindByConnId(uint16_t conn_id);

    std::shared_ptr<Session> ValidateAndTouchByConnId(uint16_t conn_id);

    static constexpr uint64_t SESSION_TIMEOUT_MS = 30 * 60 * 1000;
    static constexpr uint64_t SESSION_MAX_AGE_MS = 8 * 60 * 60 * 1000;

private:
    std::array<Shard, SHARD_COUNT> shards_;
    std::unordered_map<uint16_t, size_t> conn_shard_;
    mutable std::mutex conn_shard_mutex_;
    mutable std::atomic<size_t> active_count_{0};
    std::random_device rd_;
    std::uniform_int_distribution<uint8_t> dist_{0, 255};

    uint64_t NowMs() const;

    bool IsExpired(const Session& session) const;

    static uint64_t EffectiveExpiryMs(const Session& session);

    void UpdateSessionExpiry(Shard& shard, Session& session);

    std::array<uint8_t, 32> GenerateToken();

    size_t ShardIndex(const TokenKey& key) const;

    void RemoveSessionInternal(Shard& shard, SessionMap::iterator it);
};

} // namespace Protocol
} // namespace CppTrader

#endif // CPPTRADER_PROTOCOL_SESSION_MANAGER_H
