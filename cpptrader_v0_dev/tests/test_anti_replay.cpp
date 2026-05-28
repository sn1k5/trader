#include "trader/protocol/anti_replay.h"

#include <catch_amalgamated.hpp>

using namespace CppTrader::Protocol;

TEST_CASE("AntiReplayChecker - First nonce passes", "[anti_replay]")
{
    AntiReplayChecker checker;
    uint8_t nonce[] = {0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08,
                       0x09, 0x0a, 0x0b, 0x0c, 0x0d, 0x0e, 0x0f, 0x10};
    uint64_t now_ms = static_cast<uint64_t>(
        std::chrono::duration_cast<std::chrono::milliseconds>(
            std::chrono::system_clock::now().time_since_epoch()).count());
    REQUIRE(checker.CheckNonce(nonce, sizeof(nonce), now_ms));
}

TEST_CASE("AntiReplayChecker - Duplicate nonce rejected", "[anti_replay]")
{
    AntiReplayChecker checker;
    uint8_t nonce[] = {0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08,
                       0x09, 0x0a, 0x0b, 0x0c, 0x0d, 0x0e, 0x0f, 0x10};
    uint64_t now_ms = static_cast<uint64_t>(
        std::chrono::duration_cast<std::chrono::milliseconds>(
            std::chrono::system_clock::now().time_since_epoch()).count());
    REQUIRE(checker.CheckNonce(nonce, sizeof(nonce), now_ms));
    REQUIRE_FALSE(checker.CheckNonce(nonce, sizeof(nonce), now_ms));
}

TEST_CASE("AntiReplayChecker - Expired timestamp rejected", "[anti_replay]")
{
    AntiReplayChecker checker;
    uint64_t expired_ms = 1000;
    REQUIRE_FALSE(checker.CheckTimestamp(expired_ms, 30000));
}

TEST_CASE("AntiReplayChecker - Valid timestamp passes", "[anti_replay]")
{
    AntiReplayChecker checker;
    uint64_t now_ms = static_cast<uint64_t>(
        std::chrono::duration_cast<std::chrono::milliseconds>(
            std::chrono::system_clock::now().time_since_epoch()).count());
    REQUIRE(checker.CheckTimestamp(now_ms, 30000));
}

TEST_CASE("AntiReplayChecker - Different nonces both pass", "[anti_replay]")
{
    AntiReplayChecker checker;
    uint8_t nonce1[] = {0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08,
                        0x09, 0x0a, 0x0b, 0x0c, 0x0d, 0x0e, 0x0f, 0x10};
    uint8_t nonce2[] = {0x11, 0x12, 0x13, 0x14, 0x15, 0x16, 0x17, 0x18,
                        0x19, 0x1a, 0x1b, 0x1c, 0x1d, 0x1e, 0x1f, 0x20};
    uint64_t now_ms = static_cast<uint64_t>(
        std::chrono::duration_cast<std::chrono::milliseconds>(
            std::chrono::system_clock::now().time_since_epoch()).count());
    REQUIRE(checker.CheckNonce(nonce1, sizeof(nonce1), now_ms));
    REQUIRE(checker.CheckNonce(nonce2, sizeof(nonce2), now_ms));
}

TEST_CASE("AntiReplayChecker - Timestamp tolerance boundary", "[anti_replay]")
{
    AntiReplayChecker checker;
    uint64_t now_ms = static_cast<uint64_t>(
        std::chrono::duration_cast<std::chrono::milliseconds>(
            std::chrono::system_clock::now().time_since_epoch()).count());

    REQUIRE(checker.CheckTimestamp(now_ms - 29000, 30000));
    REQUIRE(checker.CheckTimestamp(now_ms + 29000, 30000));
    REQUIRE_FALSE(checker.CheckTimestamp(now_ms - 31000, 30000));
    REQUIRE_FALSE(checker.CheckTimestamp(now_ms + 31000, 30000));
}
