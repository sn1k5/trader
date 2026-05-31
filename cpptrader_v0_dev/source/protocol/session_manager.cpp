#include "trader/protocol/session_manager.h"

#include <cstring>
#include <algorithm>
#include <vector>

namespace CppTrader {
namespace Protocol {

uint64_t SessionManager::NowMs() const
{
    return static_cast<uint64_t>(
        std::chrono::duration_cast<std::chrono::milliseconds>(
            std::chrono::system_clock::now().time_since_epoch()).count());
}

bool SessionManager::IsExpired(const Session& session) const
{
    if (session.revoked)
        return true;

    uint64_t now = NowMs();

    if (now - session.last_active_ms > SESSION_TIMEOUT_MS)
        return true;

    if (now - session.created_at_ms > SESSION_MAX_AGE_MS)
        return true;

    return false;
}

uint64_t SessionManager::EffectiveExpiryMs(const Session& session)
{
    uint64_t timeout_expiry = session.last_active_ms + SESSION_TIMEOUT_MS;
    uint64_t max_age_expiry = session.created_at_ms + SESSION_MAX_AGE_MS;
    return std::min(timeout_expiry, max_age_expiry);
}

void SessionManager::UpdateSessionExpiry(Shard& shard, Session& session)
{
    shard.expiry_queue.erase(session.expiry_it);
    uint64_t new_deadline = EffectiveExpiryMs(session);
    TokenKey token_key(session.token);
    session.expiry_it = shard.expiry_queue.emplace(new_deadline, token_key);
}

std::array<uint8_t, 32> SessionManager::GenerateToken()
{
    std::array<uint8_t, 32> token;
    for (auto& b : token)
        b = dist_(rd_);
    return token;
}

size_t SessionManager::ShardIndex(const TokenKey& key) const
{
    return key.hash & (SHARD_COUNT - 1);
}

std::shared_ptr<SessionManager::Session> SessionManager::Create(uint64_t account_id, Role role, uint16_t conn_id)
{
    auto raw_token = GenerateToken();
    TokenKey token_key(raw_token);
    size_t idx = ShardIndex(token_key);
    Shard& shard = shards_[idx];

    size_t old_shard_idx = SHARD_COUNT;
    {
        std::lock_guard<std::mutex> cs_lock(conn_shard_mutex_);
        auto cs_it = conn_shard_.find(conn_id);
        if (cs_it != conn_shard_.end())
        {
            old_shard_idx = cs_it->second;
            conn_shard_.erase(cs_it);
        }
        conn_shard_[conn_id] = idx;
    }

    if (old_shard_idx < SHARD_COUNT && old_shard_idx != idx)
    {
        Shard& old_shard = shards_[old_shard_idx];
        std::lock_guard<std::mutex> old_lock(old_shard.mutex);
        auto old_conn_it = old_shard.conn_sessions.find(conn_id);
        if (old_conn_it != old_shard.conn_sessions.end())
        {
            auto old_sit = old_shard.sessions.find(old_conn_it->second);
            if (old_sit != old_shard.sessions.end())
            {
                old_shard.expiry_queue.erase(old_sit->second->expiry_it);
                old_shard.sessions.erase(old_sit);
                --active_count_;
            }
            old_shard.conn_sessions.erase(old_conn_it);
        }
    }

    std::lock_guard<std::mutex> lock(shard.mutex);

    auto conn_it = shard.conn_sessions.find(conn_id);
    if (conn_it != shard.conn_sessions.end())
    {
        auto sit = shard.sessions.find(conn_it->second);
        if (sit != shard.sessions.end())
        {
            shard.expiry_queue.erase(sit->second->expiry_it);
            shard.sessions.erase(sit);
            --active_count_;
        }
        shard.conn_sessions.erase(conn_it);
    }

    uint64_t now = NowMs();

    auto session = std::make_shared<Session>();
    session->token = raw_token;
    session->account_id = account_id;
    session->role = role;
    session->conn_id = conn_id;
    session->created_at_ms = now;
    session->last_active_ms = now;
    session->revoked = false;

    uint64_t deadline = EffectiveExpiryMs(*session);
    auto expiry_it = shard.expiry_queue.emplace(deadline, token_key);
    session->expiry_it = expiry_it;

    auto result = shard.sessions.emplace(token_key, session);
    shard.conn_sessions[conn_id] = token_key;

    ++active_count_;

    return result.first->second;
}

std::shared_ptr<SessionManager::Session> SessionManager::Validate(const std::array<uint8_t, 32>& token)
{
    TokenKey token_key(token);
    size_t idx = ShardIndex(token_key);
    Shard& shard = shards_[idx];

    std::lock_guard<std::mutex> lock(shard.mutex);

    auto it = shard.sessions.find(token_key);
    if (it == shard.sessions.end())
        return nullptr;

    if (IsExpired(*(it->second)))
    {
        RemoveSessionInternal(shard, it);
        return nullptr;
    }

    return it->second;
}

void SessionManager::Touch(const std::array<uint8_t, 32>& token)
{
    TokenKey token_key(token);
    size_t idx = ShardIndex(token_key);
    Shard& shard = shards_[idx];

    std::lock_guard<std::mutex> lock(shard.mutex);

    auto it = shard.sessions.find(token_key);
    if (it != shard.sessions.end())
    {
        if (IsExpired(*(it->second)))
        {
            RemoveSessionInternal(shard, it);
            return;
        }
        it->second->last_active_ms = NowMs();
        UpdateSessionExpiry(shard, *(it->second));
    }
}

void SessionManager::Destroy(uint16_t conn_id)
{
    size_t idx;
    {
        std::lock_guard<std::mutex> cs_lock(conn_shard_mutex_);
        auto cs_it = conn_shard_.find(conn_id);
        if (cs_it == conn_shard_.end())
            return;
        idx = cs_it->second;
        conn_shard_.erase(cs_it);
    }

    Shard& shard = shards_[idx];
    std::lock_guard<std::mutex> lock(shard.mutex);

    auto conn_it = shard.conn_sessions.find(conn_id);
    if (conn_it != shard.conn_sessions.end())
    {
        auto session_it = shard.sessions.find(conn_it->second);
        if (session_it != shard.sessions.end())
        {
            shard.expiry_queue.erase(session_it->second->expiry_it);
            shard.sessions.erase(session_it);
            --active_count_;
        }
        shard.conn_sessions.erase(conn_it);
    }
}

void SessionManager::Revoke(const std::array<uint8_t, 32>& token)
{
    TokenKey token_key(token);
    size_t idx = ShardIndex(token_key);
    Shard& shard = shards_[idx];

    std::lock_guard<std::mutex> lock(shard.mutex);

    auto it = shard.sessions.find(token_key);
    if (it != shard.sessions.end())
    {
        it->second->revoked = true;
    }
}

SessionManager::RecoverResult SessionManager::Recover(const std::array<uint8_t, 32>& token, uint16_t new_conn_id)
{
    TokenKey token_key(token);
    size_t token_idx = ShardIndex(token_key);

    size_t new_conn_shard_idx;
    {
        std::lock_guard<std::mutex> cs_lock(conn_shard_mutex_);
        auto cs_it = conn_shard_.find(new_conn_id);
        new_conn_shard_idx = (cs_it != conn_shard_.end()) ? cs_it->second : token_idx;
    }

    RecoverResult result;

    if (token_idx == new_conn_shard_idx)
    {
        Shard& shard = shards_[token_idx];
        std::lock_guard<std::mutex> lock(shard.mutex);

        auto it = shard.sessions.find(token_key);
        if (it == shard.sessions.end() || IsExpired(*(it->second)))
            return result;

        Session& session = *(it->second);

        uint16_t old_conn_id = session.conn_id;
        result.had_old_conn = (old_conn_id != new_conn_id);
        result.old_conn_id = old_conn_id;

        if (result.had_old_conn)
        {
            shard.conn_sessions.erase(old_conn_id);
        }

        shard.conn_sessions.erase(new_conn_id);

        session.conn_id = new_conn_id;
        session.last_active_ms = NowMs();
        UpdateSessionExpiry(shard, session);
        shard.conn_sessions[new_conn_id] = token_key;

        {
            std::lock_guard<std::mutex> cs_lock(conn_shard_mutex_);
            if (result.had_old_conn)
                conn_shard_.erase(old_conn_id);
            conn_shard_[new_conn_id] = token_idx;
        }

        result.session = it->second;
        return result;
    }

    size_t lo = std::min(token_idx, new_conn_shard_idx);
    size_t hi = std::max(token_idx, new_conn_shard_idx);
    std::lock(shards_[lo].mutex, shards_[hi].mutex);
    std::lock_guard<std::mutex> lock_lo(shards_[lo].mutex, std::adopt_lock);
    std::lock_guard<std::mutex> lock_hi(shards_[hi].mutex, std::adopt_lock);

    Shard& token_shard = shards_[token_idx];
    Shard& conn_shard = shards_[new_conn_shard_idx];

    auto it = token_shard.sessions.find(token_key);
    if (it == token_shard.sessions.end() || IsExpired(*(it->second)))
        return result;

    Session& session = *(it->second);

    uint16_t old_conn_id = session.conn_id;
    result.had_old_conn = (old_conn_id != new_conn_id);
    result.old_conn_id = old_conn_id;

    if (result.had_old_conn && old_conn_id != 0)
    {
        token_shard.conn_sessions.erase(old_conn_id);
    }

    auto new_conn_it = conn_shard.conn_sessions.find(new_conn_id);
    if (new_conn_it != conn_shard.conn_sessions.end())
    {
        auto victim_it = conn_shard.sessions.find(new_conn_it->second);
        if (victim_it != conn_shard.sessions.end())
        {
            victim_it->second->conn_id = 0;
        }
        conn_shard.conn_sessions.erase(new_conn_it);
    }

    session.conn_id = new_conn_id;
    session.last_active_ms = NowMs();
    UpdateSessionExpiry(token_shard, session);

    if (&token_shard != &conn_shard)
    {
        token_shard.conn_sessions.erase(new_conn_id);
        conn_shard.conn_sessions[new_conn_id] = token_key;
    }
    else
    {
        conn_shard.conn_sessions[new_conn_id] = token_key;
    }

    {
        std::lock_guard<std::mutex> cs_lock(conn_shard_mutex_);
        if (result.had_old_conn)
            conn_shard_.erase(old_conn_id);
        conn_shard_.erase(new_conn_id);
        conn_shard_[new_conn_id] = token_idx;
    }

    result.session = it->second;
    return result;
}

void SessionManager::CleanupExpired()
{
    uint64_t now = NowMs();
    std::vector<uint16_t> expired_conns;

    for (size_t i = 0; i < SHARD_COUNT; ++i)
    {
        Shard& shard = shards_[i];
        if (!shard.mutex.try_lock())
            continue;
        std::lock_guard<std::mutex> lock(shard.mutex, std::adopt_lock);

        while (!shard.expiry_queue.empty() && shard.expiry_queue.begin()->first <= now)
        {
            TokenKey expired_key = shard.expiry_queue.begin()->second;
            auto it = shard.sessions.find(expired_key);
            if (it != shard.sessions.end())
            {
                uint16_t cid = it->second->conn_id;
                if (cid != 0)
                {
                    shard.conn_sessions.erase(cid);
                    expired_conns.push_back(cid);
                }
                shard.sessions.erase(it);
                --active_count_;
            }
            shard.expiry_queue.erase(shard.expiry_queue.begin());
        }
    }

    if (!expired_conns.empty())
    {
        std::lock_guard<std::mutex> cs_lock(conn_shard_mutex_);
        for (uint16_t cid : expired_conns)
            conn_shard_.erase(cid);
    }
}

size_t SessionManager::ActiveSessionCount() const
{
    return active_count_.load(std::memory_order_relaxed);
}

std::shared_ptr<SessionManager::Session> SessionManager::FindByConnId(uint16_t conn_id)
{
    if (conn_id == 0)
        return nullptr;

    size_t idx;
    {
        std::lock_guard<std::mutex> cs_lock(conn_shard_mutex_);
        auto cs_it = conn_shard_.find(conn_id);
        if (cs_it == conn_shard_.end())
            return nullptr;
        idx = cs_it->second;
    }

    Shard& shard = shards_[idx];
    std::lock_guard<std::mutex> lock(shard.mutex);

    auto conn_it = shard.conn_sessions.find(conn_id);
    if (conn_it == shard.conn_sessions.end())
        return nullptr;

    auto session_it = shard.sessions.find(conn_it->second);
    if (session_it == shard.sessions.end())
        return nullptr;

    if (IsExpired(*(session_it->second)))
    {
        RemoveSessionInternal(shard, session_it);
        return nullptr;
    }

    return session_it->second;
}

std::shared_ptr<SessionManager::Session> SessionManager::ValidateAndTouchByConnId(uint16_t conn_id)
{
    if (conn_id == 0)
        return nullptr;

    size_t idx;
    {
        std::lock_guard<std::mutex> cs_lock(conn_shard_mutex_);
        auto cs_it = conn_shard_.find(conn_id);
        if (cs_it == conn_shard_.end())
            return nullptr;
        idx = cs_it->second;
    }

    Shard& shard = shards_[idx];
    std::lock_guard<std::mutex> lock(shard.mutex);

    auto conn_it = shard.conn_sessions.find(conn_id);
    if (conn_it == shard.conn_sessions.end())
        return nullptr;

    auto session_it = shard.sessions.find(conn_it->second);
    if (session_it == shard.sessions.end())
        return nullptr;

    if (IsExpired(*(session_it->second)))
    {
        RemoveSessionInternal(shard, session_it);
        return nullptr;
    }

    session_it->second->last_active_ms = NowMs();
    UpdateSessionExpiry(shard, *(session_it->second));
    return session_it->second;
}

void SessionManager::RemoveSessionInternal(Shard& shard, SessionMap::iterator it)
{
    uint16_t cid = it->second->conn_id;
    if (cid != 0)
    {
        shard.conn_sessions.erase(cid);
        std::lock_guard<std::mutex> cs_lock(conn_shard_mutex_);
        conn_shard_.erase(cid);
    }
    shard.expiry_queue.erase(it->second->expiry_it);
    shard.sessions.erase(it);
    --active_count_;
}

} // namespace Protocol
} // namespace CppTrader
