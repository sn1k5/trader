#ifndef CPPTRADER_PROTOCOL_NULL_BACKEND_H
#define CPPTRADER_PROTOCOL_NULL_BACKEND_H

#include "network_backend.h"

namespace CppTrader {
namespace Protocol {

class NullBackend : public INetworkBackend
{
public:
    bool init() override { return true; }
    void poll() override {}
    void send(uint16_t, const void*, size_t) override {}
    void broadcast(const void*, size_t) override {}
    void SetMessageHandler(const MessageHandler&) override {}
    void SetConnectHandler(const ConnectHandler&) override {}
    void SetDisconnectHandler(const DisconnectHandler&) override {}
    void close(uint16_t) override {}
};

} // namespace Protocol
} // namespace CppTrader

#endif // CPPTRADER_PROTOCOL_NULL_BACKEND_H
