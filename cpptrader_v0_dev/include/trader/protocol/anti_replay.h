/*!
    \file anti_replay.h
    \brief Anti-replay attack checker for authentication
    \author CppTrader Team
    \date 28.05.2026
    \copyright MIT License
*/

#ifndef CPPTRADER_PROTOCOL_ANTI_REPLAY_H
#define CPPTRADER_PROTOCOL_ANTI_REPLAY_H

#include <array>
#include <cstdint>
#include <cstddef>
#include <chrono>
#include <mutex>
#include <string>
#include <unordered_map>
#include <unordered_set>

namespace CppTrader {
namespace Protocol {

class AntiReplayChecker
{
public:
    static constexpr size_t SHARD_COUNT = 16;
    static constexpr size_t MAX_NONCE_ENTRIES_PER_SHARD = 256;

    explicit AntiReplayChecker(size_t max_entries_per_shard = MAX_NONCE_ENTRIES_PER_SHARD);

    ~AntiReplayChecker() = default;

    AntiReplayChecker(const AntiReplayChecker&) = delete;
    AntiReplayChecker(AntiReplayChecker&&) = delete;
    AntiReplayChecker& operator=(const AntiReplayChecker&) = delete;
    AntiReplayChecker& operator=(AntiReplayChecker&&) = delete;

    bool CheckNonce(const uint8_t* nonce, size_t nonce_len, uint64_t timestamp_ms);

    bool CheckTimestamp(uint64_t timestamp_ms, int64_t tolerance_ms = 30000);

    void Cleanup();

private:
    struct NonceShard
    {
        std::unordered_map<std::string, std::chrono::steady_clock::time_point> nonces;
        mutable std::mutex mutex;
    };

    size_t max_entries_per_shard_;
    std::array<NonceShard, SHARD_COUNT> shards_;

    static std::string NonceToKey(const uint8_t* nonce, size_t nonce_len);

    size_t ShardIndex(const std::string& key) const;

    void CleanupShard(NonceShard& shard);
};

} // namespace Protocol
} // namespace CppTrader

#endif // CPPTRADER_PROTOCOL_ANTI_REPLAY_H
