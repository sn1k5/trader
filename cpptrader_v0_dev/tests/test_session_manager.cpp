#include "trader/protocol/session_manager.h"

#include <catch_amalgamated.hpp>
#include <cstring>
#include <thread>
#include <chrono>

using namespace CppTrader::Protocol;

TEST_CASE("SessionManager - Create session returns valid session", "[session]")
{
    SessionManager mgr;
    auto session = mgr.Create(1001, Role::TRADER, 1);

    REQUIRE(session != nullptr);
    REQUIRE(session->account_id == 1001);
    REQUIRE(session->role == Role::TRADER);
    REQUIRE(session->conn_id == 1);
    REQUIRE(session->revoked == false);
    REQUIRE(session->created_at_ms > 0);
    REQUIRE(session->last_active_ms == session->created_at_ms);

    bool all_zero = true;
    for (auto b : session->token)
    {
        if (b != 0) { all_zero = false; break; }
    }
    REQUIRE_FALSE(all_zero);
}

TEST_CASE("SessionManager - Validate returns session for valid token", "[session]")
{
    SessionManager mgr;
    auto created = mgr.Create(1002, Role::ADMIN, 2);
    auto validated = mgr.Validate(created->token);

    REQUIRE(validated != nullptr);
    REQUIRE(validated->account_id == 1002);
    REQUIRE(validated->conn_id == 2);
}

TEST_CASE("SessionManager - Validate returns nullptr for unknown token", "[session]")
{
    SessionManager mgr;
    std::array<uint8_t, 32> fake_token{};
    fake_token[0] = 0xFF;

    auto result = mgr.Validate(fake_token);
    REQUIRE(result == nullptr);
}

TEST_CASE("SessionManager - Validate returns nullptr for zero token", "[session]")
{
    SessionManager mgr;
    std::array<uint8_t, 32> zero_token{};

    auto result = mgr.Validate(zero_token);
    REQUIRE(result == nullptr);
}

TEST_CASE("SessionManager - Destroy removes session by conn_id", "[session]")
{
    SessionManager mgr;
    auto session = mgr.Create(1003, Role::VIEWER, 3);
    auto token = session->token;

    mgr.Destroy(3);

    auto result = mgr.Validate(token);
    REQUIRE(result == nullptr);
    REQUIRE(mgr.ActiveSessionCount() == 0);
}

TEST_CASE("SessionManager - Destroy unknown conn_id is no-op", "[session]")
{
    SessionManager mgr;
    mgr.Create(1004, Role::TRADER, 4);

    mgr.Destroy(999);

    REQUIRE(mgr.ActiveSessionCount() == 1);
}

TEST_CASE("SessionManager - Revoke marks session as revoked", "[session]")
{
    SessionManager mgr;
    auto session = mgr.Create(1005, Role::QUANTBOT, 5);
    auto token = session->token;

    mgr.Revoke(token);

    auto result = mgr.Validate(token);
    REQUIRE(result == nullptr);
}

TEST_CASE("SessionManager - Touch updates last_active_ms", "[session]")
{
    SessionManager mgr;
    auto session = mgr.Create(1006, Role::TRADER, 6);
    auto token = session->token;
    uint64_t original_active = session->last_active_ms;

    std::this_thread::sleep_for(std::chrono::milliseconds(10));
    mgr.Touch(token);

    auto updated = mgr.Validate(token);
    REQUIRE(updated != nullptr);
    REQUIRE(updated->last_active_ms >= original_active);
}

TEST_CASE("SessionManager - FindByConnId returns session", "[session]")
{
    SessionManager mgr;
    mgr.Create(1007, Role::TRADER, 7);

    auto result = mgr.FindByConnId(7);
    REQUIRE(result != nullptr);
    REQUIRE(result->account_id == 1007);
}

TEST_CASE("SessionManager - FindByConnId returns nullptr for unknown conn", "[session]")
{
    SessionManager mgr;
    auto result = mgr.FindByConnId(999);
    REQUIRE(result == nullptr);
}

TEST_CASE("SessionManager - Multiple sessions independent", "[session]")
{
    SessionManager mgr;
    auto s1 = mgr.Create(2001, Role::ADMIN, 10);
    auto s2 = mgr.Create(2002, Role::TRADER, 11);

    REQUIRE(mgr.ActiveSessionCount() == 2);
    REQUIRE(s1->account_id == 2001);
    REQUIRE(s2->account_id == 2002);
    REQUIRE(s1->token != s2->token);

    mgr.Destroy(10);
    REQUIRE(mgr.ActiveSessionCount() == 1);
    REQUIRE(mgr.Validate(s1->token) == nullptr);
    REQUIRE(mgr.Validate(s2->token) != nullptr);
}

TEST_CASE("SessionManager - Re-create on same conn_id replaces session", "[session]")
{
    SessionManager mgr;
    auto s1 = mgr.Create(3001, Role::TRADER, 20);
    auto old_token = s1->token;

    auto s2 = mgr.Create(3002, Role::ADMIN, 20);
    auto new_token = s2->token;

    REQUIRE(mgr.ActiveSessionCount() == 1);
    REQUIRE(mgr.Validate(old_token) == nullptr);
    REQUIRE(mgr.Validate(new_token) != nullptr);
    REQUIRE(s2->account_id == 3002);
}

TEST_CASE("SessionManager - Recover valid session binds new conn_id", "[session]")
{
    SessionManager mgr;
    auto original = mgr.Create(4001, Role::TRADER, 30);
    auto token = original->token;

    auto result = mgr.Recover(token, 31);

    REQUIRE(result.session != nullptr);
    REQUIRE(result.session->conn_id == 31);
    REQUIRE(result.session->account_id == 4001);
    REQUIRE(result.had_old_conn == true);
    REQUIRE(result.old_conn_id == 30);

    REQUIRE(mgr.FindByConnId(30) == nullptr);
    REQUIRE(mgr.FindByConnId(31) != nullptr);
}

TEST_CASE("SessionManager - Recover with same conn_id", "[session]")
{
    SessionManager mgr;
    auto original = mgr.Create(4002, Role::TRADER, 32);
    auto token = original->token;

    auto result = mgr.Recover(token, 32);

    REQUIRE(result.session != nullptr);
    REQUIRE(result.had_old_conn == false);
}

TEST_CASE("SessionManager - Recover invalid token returns nullptr", "[session]")
{
    SessionManager mgr;
    std::array<uint8_t, 32> fake_token{};
    fake_token[0] = 0xAA;

    auto result = mgr.Recover(fake_token, 40);
    REQUIRE(result.session == nullptr);
}

TEST_CASE("SessionManager - CleanupExpired removes expired sessions", "[session]")
{
    SessionManager mgr;
    auto session = mgr.Create(5001, Role::TRADER, 50);
    auto token = session->token;

    session->last_active_ms = session->last_active_ms - SessionManager::SESSION_TIMEOUT_MS - 1000;

    mgr.CleanupExpired();

    REQUIRE(mgr.Validate(token) == nullptr);
    REQUIRE(mgr.ActiveSessionCount() == 0);
}

TEST_CASE("SessionManager - CleanupExpired keeps active sessions", "[session]")
{
    SessionManager mgr;
    mgr.Create(5002, Role::TRADER, 51);

    mgr.CleanupExpired();

    REQUIRE(mgr.ActiveSessionCount() == 1);
}

TEST_CASE("SessionManager - Session max age expiry", "[session]")
{
    SessionManager mgr;
    auto session = mgr.Create(5003, Role::TRADER, 52);
    auto token = session->token;

    session->created_at_ms = session->created_at_ms - SessionManager::SESSION_MAX_AGE_MS - 1000;
    session->last_active_ms = session->created_at_ms + 1000;

    auto result = mgr.Validate(token);
    REQUIRE(result == nullptr);
}

TEST_CASE("SessionManager - TokenKey hash is consistent and cached", "[session]")
{
    std::array<uint8_t, 32> key1{};
    key1[0] = 0x01;
    std::array<uint8_t, 32> key2{};
    key2[0] = 0x02;

    TokenKey tk1(key1);
    TokenKey tk2(key2);

    REQUIRE(tk1.hash != tk2.hash);
    REQUIRE(tk1.hash == TokenKey::ComputeHash(key1));
    REQUIRE(tk2.hash == TokenKey::ComputeHash(key2));

    TokenKey tk1_again(key1);
    REQUIRE(tk1.hash == tk1_again.hash);

    TokenKeyHash hasher;
    REQUIRE(hasher(tk1) == tk1.hash);
    REQUIRE(hasher(tk1) == hasher(tk1_again));

    TokenKeyEqual eq;
    REQUIRE(eq(tk1, tk1_again));
    REQUIRE_FALSE(eq(tk1, tk2));
}

TEST_CASE("SessionManager - Role enum values", "[session]")
{
    REQUIRE(static_cast<uint8_t>(Role::ADMIN) == 0);
    REQUIRE(static_cast<uint8_t>(Role::TRADER) == 1);
    REQUIRE(static_cast<uint8_t>(Role::VIEWER) == 2);
    REQUIRE(static_cast<uint8_t>(Role::QUANTBOT) == 3);
}
