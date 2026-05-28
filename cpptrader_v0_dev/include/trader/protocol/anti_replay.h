/*!
    \file anti_replay.h
    \brief Anti-replay attack checker for authentication
    \author CppTrader Team
    \date 28.05.2026
    \copyright MIT License
*/

#ifndef CPPTRADER_PROTOCOL_ANTI_REPLAY_H
#define CPPTRADER_PROTOCOL_ANTI_REPLAY_H

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
    AntiReplayChecker() = default;
    ~AntiReplayChecker() = default;

    AntiReplayChecker(const AntiReplayChecker&) = delete;
    AntiReplayChecker(AntiReplayChecker&&) = delete;
    AntiReplayChecker& operator=(const AntiReplayChecker&) = delete;
    AntiReplayChecker& operator=(AntiReplayChecker&&) = delete;

    bool CheckNonce(const uint8_t* nonce, size_t nonce_len, uint64_t timestamp_ms);

    bool CheckTimestamp(uint64_t timestamp_ms, int64_t tolerance_ms = 30000);

    void Cleanup();

private:
    static constexpr size_t MAX_NONCE_ENTRIES = 4096;

    static std::string NonceToKey(const uint8_t* nonce, size_t nonce_len);

    void CleanupLocked();

    std::unordered_map<std::string, std::chrono::steady_clock::time_point> recent_nonces_;
    std::mutex mutex_;
};

} // namespace Protocol
} // namespace CppTrader

#endif // CPPTRADER_PROTOCOL_ANTI_REPLAY_H
