/*!
    \file hmac.cpp
    \brief HMAC signature verification implementation
    \author CppTrader Team
    \date 28.05.2026
    \copyright MIT License
*/

#include "trader/protocol/hmac.h"

#include <openssl/hmac.h>
#include <openssl/evp.h>

#include <cstring>
#include <algorithm>

namespace CppTrader {
namespace Protocol {

HmacVerifier::HmacVerifier(const uint8_t* session_key, size_t key_len)
    : session_key_(session_key, session_key + key_len)
{
}

HmacVerifier::HmacVerifier(const std::vector<uint8_t>& session_key)
    : session_key_(session_key)
{
}

std::vector<uint8_t> HmacVerifier::BuildSignInput(const MsgHeader& header, const uint8_t* body, size_t body_len)
{
    std::vector<uint8_t> input;
    input.reserve(8 + body_len);

    uint32_t seq = header.Sequence;
    input.push_back(static_cast<uint8_t>(seq & 0xFF));
    input.push_back(static_cast<uint8_t>((seq >> 8) & 0xFF));
    input.push_back(static_cast<uint8_t>((seq >> 16) & 0xFF));
    input.push_back(static_cast<uint8_t>((seq >> 24) & 0xFF));

    input.push_back(static_cast<uint8_t>(header.Type));

    input.push_back(header.Flags);

    uint16_t len = header.Length;
    input.push_back(static_cast<uint8_t>(len & 0xFF));
    input.push_back(static_cast<uint8_t>((len >> 8) & 0xFF));

    if (body != nullptr && body_len > 0)
    {
        input.insert(input.end(), body, body + body_len);
    }

    return input;
}

std::array<uint8_t, 32> HmacVerifier::HmacSHA256(const uint8_t* key, size_t key_len,
                                                   const uint8_t* data, size_t data_len)
{
    std::array<uint8_t, 32> result{};
    unsigned int md_len = result.size();

#if OPENSSL_VERSION_NUMBER >= 0x10100000L
    HMAC_CTX* ctx = HMAC_CTX_new();
    if (ctx == nullptr)
        return result;

    if (HMAC_Init_ex(ctx, key, static_cast<int>(key_len), EVP_sha256(), nullptr) != 1 ||
        HMAC_Update(ctx, data, data_len) != 1 ||
        HMAC_Final(ctx, result.data(), &md_len) != 1)
    {
        HMAC_CTX_free(ctx);
        result.fill(0);
        return result;
    }

    HMAC_CTX_free(ctx);
#else
    if (HMAC(EVP_sha256(), key, static_cast<int>(key_len), data, data_len, result.data(), &md_len) == nullptr)
    {
        result.fill(0);
        return result;
    }
#endif

    return result;
}

std::array<uint8_t, 32> HmacVerifier::ComputeFull(const MsgHeader& header, const uint8_t* body, size_t body_len)
{
    auto input = BuildSignInput(header, body, body_len);
    return HmacSHA256(session_key_.data(), session_key_.size(), input.data(), input.size());
}

uint16_t HmacVerifier::ComputePrefix(const MsgHeader& header, const uint8_t* body, size_t body_len)
{
    auto full = ComputeFull(header, body, body_len);

    uint16_t prefix = static_cast<uint16_t>(full[0]) |
                      (static_cast<uint16_t>(full[1]) << 8);
    return prefix;
}

bool HmacVerifier::VerifyPrefix(const MsgHeader& header, const uint8_t* body, size_t body_len)
{
    uint16_t expected = ComputePrefix(header, body, body_len);
    return (expected ^ header.HmacPrefix) == 0;
}

} // namespace Protocol
} // namespace CppTrader
