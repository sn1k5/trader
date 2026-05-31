/*!
    \file hmac.h
    \brief HMAC signature verification for protocol messages
    \author CppTrader Team
    \date 28.05.2026
    \copyright MIT License
*/

#ifndef CPPTRADER_PROTOCOL_HMAC_H
#define CPPTRADER_PROTOCOL_HMAC_H

#include "protocol.h"

#include <array>
#include <cstdint>
#include <cstddef>
#include <string>
#include <vector>

namespace CppTrader {
namespace Protocol {

class HmacVerifier
{
public:
    explicit HmacVerifier(const uint8_t* session_key, size_t key_len);
    explicit HmacVerifier(const std::vector<uint8_t>& session_key);

    HmacVerifier(const HmacVerifier&) = default;
    HmacVerifier(HmacVerifier&&) = default;
    HmacVerifier& operator=(const HmacVerifier&) = default;
    HmacVerifier& operator=(HmacVerifier&&) = default;

    uint16_t ComputePrefix(const MsgHeader& header, const uint8_t* body, size_t body_len);

    bool VerifyPrefix(const MsgHeader& header, const uint8_t* body, size_t body_len);

    std::array<uint8_t, 32> ComputeFull(const MsgHeader& header, const uint8_t* body, size_t body_len);

    static std::vector<uint8_t> BuildSignInput(const MsgHeader& header, const uint8_t* body, size_t body_len);

    static std::array<uint8_t, 32> HmacSHA256(const uint8_t* key, size_t key_len,
                                               const uint8_t* data, size_t data_len);

    static std::string BuildAuthSignMessage(uint64_t timestamp, const uint8_t* nonce, size_t nonce_len, const std::string& api_key_id);

    static bool VerifyAuthSignature(const uint8_t* api_key_secret, size_t secret_len,
                                    uint64_t timestamp, const uint8_t* nonce, size_t nonce_len,
                                    const std::string& api_key_id,
                                    const uint8_t* provided_signature, size_t sig_len);

private:
    std::vector<uint8_t> session_key_;
};

} // namespace Protocol
} // namespace CppTrader

#endif // CPPTRADER_PROTOCOL_HMAC_H
