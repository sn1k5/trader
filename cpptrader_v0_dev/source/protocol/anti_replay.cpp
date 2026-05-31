#include "trader/protocol/anti_replay.h"

#include <cstring>
#include <algorithm>
#include <sstream>
#include <iomanip>

namespace CppTrader {
namespace Protocol {

AntiReplayChecker::AntiReplayChecker(size_t max_entries_per_shard)
    : max_entries_per_shard_(max_entries_per_shard)
{
}

std::string AntiReplayChecker::NonceToKey(const uint8_t* nonce, size_t nonce_len)
{
    std::ostringstream oss;
    oss << std::hex << std::setfill('0');
    for (size_t i = 0; i < nonce_len; ++i)
        oss << std::setw(2) << static_cast<int>(nonce[i]);
    return oss.str();
}

size_t AntiReplayChecker::ShardIndex(const std::string& key) const
{
    size_t h = 0xcbf29ce484222325ULL;
    for (char c : key)
    {
        h ^= static_cast<size_t>(c);
        h *= 0x100000001b3ULL;
    }
    return h & (SHARD_COUNT - 1);
}

bool AntiReplayChecker::CheckNonce(const uint8_t* nonce, size_t nonce_len, uint64_t timestamp_ms)
{
    std::string key = NonceToKey(nonce, nonce_len);
    auto expiry = std::chrono::steady_clock::now() + std::chrono::seconds(60);

    size_t idx = ShardIndex(key);
    NonceShard& shard = shards_[idx];

    std::lock_guard<std::mutex> lock(shard.mutex);

    auto it = shard.nonces.find(key);
    if (it != shard.nonces.end())
    {
        return false;
    }

    if (shard.nonces.size() >= max_entries_per_shard_)
    {
        CleanupShard(shard);
        if (shard.nonces.size() >= max_entries_per_shard_)
            return false;
    }

    shard.nonces[std::move(key)] = expiry;
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
    for (size_t i = 0; i < SHARD_COUNT; ++i)
    {
        std::lock_guard<std::mutex> lock(shards_[i].mutex);
        CleanupShard(shards_[i]);
    }
}

void AntiReplayChecker::CleanupShard(NonceShard& shard)
{
    auto now = std::chrono::steady_clock::now();

    for (auto it = shard.nonces.begin(); it != shard.nonces.end(); )
    {
        if (it->second <= now)
        {
            it = shard.nonces.erase(it);
        }
        else
        {
            ++it;
        }
    }
}

} // namespace Protocol
} // namespace CppTrader
