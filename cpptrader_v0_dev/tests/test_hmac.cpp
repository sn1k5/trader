#include "trader/protocol/hmac.h"
#include "trader/protocol/protocol.h"

#include <catch_amalgamated.hpp>

using namespace CppTrader::Protocol;

TEST_CASE("HmacVerifier - ComputePrefix returns non-zero", "[hmac]")
{
    uint8_t key[] = {0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08,
                     0x09, 0x0a, 0x0b, 0x0c, 0x0d, 0x0e, 0x0f, 0x10};
    HmacVerifier verifier(key, sizeof(key));

    MsgHeader header;
    header.Magic = 0x5452;
    header.Version = 2;
    header.Type = MsgType::ADD_SYMBOL_REQUEST;
    header.Flags = Flags::REQUEST;
    header.Reserved = 0;
    header.Length = 12;
    header.Sequence = 1;
    header.HmacPrefix = 0;
    header.Reserved2 = 0;

    uint8_t body[] = {0x01, 0x00, 0x00, 0x00, 0x41, 0x42, 0x43, 0x44, 0x45, 0x46, 0x47, 0x00};

    uint16_t prefix = verifier.ComputePrefix(header, body, sizeof(body));
    REQUIRE(prefix != 0);
}

TEST_CASE("HmacVerifier - VerifyPrefix correct signature passes", "[hmac]")
{
    uint8_t key[] = {0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08,
                     0x09, 0x0a, 0x0b, 0x0c, 0x0d, 0x0e, 0x0f, 0x10};
    HmacVerifier verifier(key, sizeof(key));

    MsgHeader header;
    header.Magic = 0x5452;
    header.Version = 2;
    header.Type = MsgType::ADD_SYMBOL_REQUEST;
    header.Flags = Flags::REQUEST;
    header.Reserved = 0;
    header.Length = 12;
    header.Sequence = 1;
    header.HmacPrefix = 0;
    header.Reserved2 = 0;

    uint8_t body[] = {0x01, 0x00, 0x00, 0x00, 0x41, 0x42, 0x43, 0x44, 0x45, 0x46, 0x47, 0x00};

    header.HmacPrefix = verifier.ComputePrefix(header, body, sizeof(body));
    REQUIRE(verifier.VerifyPrefix(header, body, sizeof(body)));
}

TEST_CASE("HmacVerifier - VerifyPrefix tampered body fails", "[hmac]")
{
    uint8_t key[] = {0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08,
                     0x09, 0x0a, 0x0b, 0x0c, 0x0d, 0x0e, 0x0f, 0x10};
    HmacVerifier verifier(key, sizeof(key));

    MsgHeader header;
    header.Magic = 0x5452;
    header.Version = 2;
    header.Type = MsgType::ADD_SYMBOL_REQUEST;
    header.Flags = Flags::REQUEST;
    header.Reserved = 0;
    header.Length = 12;
    header.Sequence = 1;
    header.HmacPrefix = 0;
    header.Reserved2 = 0;

    uint8_t body[] = {0x01, 0x00, 0x00, 0x00, 0x41, 0x42, 0x43, 0x44, 0x45, 0x46, 0x47, 0x00};

    header.HmacPrefix = verifier.ComputePrefix(header, body, sizeof(body));

    uint8_t tampered_body[] = {0x02, 0x00, 0x00, 0x00, 0x41, 0x42, 0x43, 0x44, 0x45, 0x46, 0x47, 0x00};
    REQUIRE_FALSE(verifier.VerifyPrefix(header, tampered_body, sizeof(tampered_body)));
}

TEST_CASE("HmacVerifier - VerifyPrefix tampered sequence fails", "[hmac]")
{
    uint8_t key[] = {0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08,
                     0x09, 0x0a, 0x0b, 0x0c, 0x0d, 0x0e, 0x0f, 0x10};
    HmacVerifier verifier(key, sizeof(key));

    MsgHeader header;
    header.Magic = 0x5452;
    header.Version = 2;
    header.Type = MsgType::ADD_SYMBOL_REQUEST;
    header.Flags = Flags::REQUEST;
    header.Reserved = 0;
    header.Length = 12;
    header.Sequence = 1;
    header.HmacPrefix = 0;
    header.Reserved2 = 0;

    uint8_t body[] = {0x01, 0x00, 0x00, 0x00, 0x41, 0x42, 0x43, 0x44, 0x45, 0x46, 0x47, 0x00};

    header.HmacPrefix = verifier.ComputePrefix(header, body, sizeof(body));

    MsgHeader tampered_header = header;
    tampered_header.Sequence = 2;
    REQUIRE_FALSE(verifier.VerifyPrefix(tampered_header, body, sizeof(body)));
}

TEST_CASE("HmacVerifier - VerifyPrefix tampered HmacPrefix fails", "[hmac]")
{
    uint8_t key[] = {0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08,
                     0x09, 0x0a, 0x0b, 0x0c, 0x0d, 0x0e, 0x0f, 0x10};
    HmacVerifier verifier(key, sizeof(key));

    MsgHeader header;
    header.Magic = 0x5452;
    header.Version = 2;
    header.Type = MsgType::ADD_SYMBOL_REQUEST;
    header.Flags = Flags::REQUEST;
    header.Reserved = 0;
    header.Length = 12;
    header.Sequence = 1;
    header.HmacPrefix = 0;
    header.Reserved2 = 0;

    uint8_t body[] = {0x01, 0x00, 0x00, 0x00, 0x41, 0x42, 0x43, 0x44, 0x45, 0x46, 0x47, 0x00};

    header.HmacPrefix = verifier.ComputePrefix(header, body, sizeof(body));

    MsgHeader tampered_header = header;
    tampered_header.HmacPrefix = header.HmacPrefix ^ 0xFFFF;
    REQUIRE_FALSE(verifier.VerifyPrefix(tampered_header, body, sizeof(body)));
}

TEST_CASE("HmacVerifier - BuildSignInput byte order (little-endian)", "[hmac]")
{
    MsgHeader header;
    header.Magic = 0x5452;
    header.Version = 2;
    header.Type = static_cast<MsgType>(0x01);
    header.Flags = 0x02;
    header.Reserved = 0;
    header.Length = 4;
    header.Sequence = 0x12345678;
    header.HmacPrefix = 0;
    header.Reserved2 = 0;

    uint8_t body[] = {0xAA, 0xBB, 0xCC, 0xDD};

    auto input = HmacVerifier::BuildSignInput(header, body, sizeof(body));

    REQUIRE(input.size() == 12);

    REQUIRE(input[0] == 0x78);
    REQUIRE(input[1] == 0x56);
    REQUIRE(input[2] == 0x34);
    REQUIRE(input[3] == 0x12);

    REQUIRE(input[4] == 0x01);
    REQUIRE(input[5] == 0x02);

    REQUIRE(input[6] == 0x04);
    REQUIRE(input[7] == 0x00);

    REQUIRE(input[8] == 0xAA);
    REQUIRE(input[9] == 0xBB);
    REQUIRE(input[10] == 0xCC);
    REQUIRE(input[11] == 0xDD);
}

TEST_CASE("HmacVerifier - Empty body signature", "[hmac]")
{
    uint8_t key[] = {0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08,
                     0x09, 0x0a, 0x0b, 0x0c, 0x0d, 0x0e, 0x0f, 0x10};
    HmacVerifier verifier(key, sizeof(key));

    MsgHeader header;
    header.Magic = 0x5452;
    header.Version = 2;
    header.Type = MsgType::HEARTBEAT_REQ;
    header.Flags = Flags::HEARTBEAT;
    header.Reserved = 0;
    header.Length = 0;
    header.Sequence = 42;
    header.HmacPrefix = 0;
    header.Reserved2 = 0;

    uint16_t prefix = verifier.ComputePrefix(header, nullptr, 0);
    REQUIRE(prefix != 0);

    header.HmacPrefix = prefix;
    REQUIRE(verifier.VerifyPrefix(header, nullptr, 0));
}

TEST_CASE("HmacVerifier - Different keys produce different signatures", "[hmac]")
{
    uint8_t key1[] = {0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08,
                      0x09, 0x0a, 0x0b, 0x0c, 0x0d, 0x0e, 0x0f, 0x10};
    uint8_t key2[] = {0x11, 0x12, 0x13, 0x14, 0x15, 0x16, 0x17, 0x18,
                      0x19, 0x1a, 0x1b, 0x1c, 0x1d, 0x1e, 0x1f, 0x20};

    HmacVerifier verifier1(key1, sizeof(key1));
    HmacVerifier verifier2(key2, sizeof(key2));

    MsgHeader header;
    header.Magic = 0x5452;
    header.Version = 2;
    header.Type = MsgType::ADD_SYMBOL_REQUEST;
    header.Flags = Flags::REQUEST;
    header.Reserved = 0;
    header.Length = 4;
    header.Sequence = 1;
    header.HmacPrefix = 0;
    header.Reserved2 = 0;

    uint8_t body[] = {0x01, 0x00, 0x00, 0x00};

    uint16_t prefix1 = verifier1.ComputePrefix(header, body, sizeof(body));
    uint16_t prefix2 = verifier2.ComputePrefix(header, body, sizeof(body));
    REQUIRE(prefix1 != prefix2);
}

TEST_CASE("HmacVerifier - Cross-validation with known test vector", "[hmac]")
{
    uint8_t key[] = "test-session-key-1234";
    HmacVerifier verifier(key, sizeof(key) - 1);

    MsgHeader header;
    header.Magic = 0x5452;
    header.Version = 2;
    header.Type = static_cast<MsgType>(0x01);
    header.Flags = 0x01;
    header.Reserved = 0;
    header.Length = 0;
    header.Sequence = 1;
    header.HmacPrefix = 0;
    header.Reserved2 = 0;

    auto input = HmacVerifier::BuildSignInput(header, nullptr, 0);
    REQUIRE(input.size() == 8);

    auto full = HmacVerifier::HmacSHA256(key, sizeof(key) - 1, input.data(), input.size());
    REQUIRE(full.size() == 32);

    uint16_t prefix = verifier.ComputePrefix(header, nullptr, 0);
    uint16_t expected_prefix = static_cast<uint16_t>(full[0]) | (static_cast<uint16_t>(full[1]) << 8);
    REQUIRE(prefix == expected_prefix);
}
