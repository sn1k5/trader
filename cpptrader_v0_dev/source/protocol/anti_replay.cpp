/*!
    \file anti_replay.cpp
    \brief Anti-replay attack checker implementation
    \author CppTrader Team
    \date 28.05.2026
    \copyright MIT License
*/

#include "trader/protocol/anti_replay.h"

#include <cstring>
#include <algorithm>
#include <sstream>
#include <iomanip>

namespace CppTrader {
namespace Protocol {

std::string AntiReplayChecker::NonceToKey(const uint8_t* nonce, size_t nonce_len)
{
    std::ostringstream oss;
    oss << std::hex << std::setfill('0');
    for (size_t i = 0; i < nonce_len; ++i)
        oss << std::setw(2) << static_cast<int>(nonce[i]);
    return oss.str();
}

bool AntiReplayChecker::CheckNonce(const uint8_t* nonce, size_t nonce_len, uint64_t timestamp_ms)
{
    std::string key = NonceToKey(nonce, nonce_len);
    auto expiry = std::chrono::steady_clock::now() + std::chrono::seconds(60);

    std::lock_guard<std::mutex> lock(mutex_);

    auto it = recent_nonces_.find(key);
    if (it != recent_nonces_.end())
    {
        return false;
    }

    if (recent_nonces_.size() >= MAX_NONCE_ENTRIES)
    {
        CleanupLocked();
        if (recent_nonces_.size() >= MAX_NONCE_ENTRIES)
            return false;
    }

    recent_nonces_[std::move(key)] = expiry;
    return true;
}

bool AntiReplayChecker::CheckTimestamp(uint64_t timestamp_ms, int64_t tolerance_ms)
{
    auto now = std::chrono::system_clock::now();
    auto now_ms = std::chrono::duration_cast<std::chrono::milliseconds>(
        now.time_since_epoch()).count();

    int64_t diff = static_cast<int64_t>(timestamp_ms) - now_ms;
    if (diff < 0) diff = -diff;

    return diff <= tolerance_ms;
}

void AntiReplayChecker::Cleanup()
{
    std::lock_guard<std::mutex> lock(mutex_);
    CleanupLocked();
}

void AntiReplayChecker::CleanupLocked()
{
    auto now = std::chrono::steady_clock::now();

    for (auto it = recent_nonces_.begin(); it != recent_nonces_.end(); )
    {
        if (it->second <= now)
        {
            it = recent_nonces_.erase(it);
        }
        else
        {
            ++it;
        }
    }
}

} // namespace Protocol
} // namespace CppTrader
