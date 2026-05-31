#ifndef CPPTRADER_PROTOCOL_IO_THREAD_H
#define CPPTRADER_PROTOCOL_IO_THREAD_H

#include "spsc_queue.h"
#include "network_backend.h"
#include "tcp_backend.h"

#include <asio.hpp>

#include <atomic>
#include <functional>
#include <memory>
#include <thread>

namespace CppTrader {
namespace Protocol {

class IOThread
{
public:
    IOThread(uint16_t port, SPSCQueue<PendingFrame>& to_business, SPSCQueue<OutboundFrame>& from_business);
    ~IOThread();

    IOThread(const IOThread&) = delete;
    IOThread(IOThread&&) = delete;
    IOThread& operator=(const IOThread&) = delete;
    IOThread& operator=(const IOThread&&) = delete;

    bool Start();
    void Stop();
    void Join();
    void NotifyIO();
    INetworkBackend* Backend() const noexcept { return _backend.get(); }
    asio::io_context& GetIOContext() noexcept { return _io_context; }

private:
    void Run();
    void DrainOutboundQueue();

    asio::io_context _io_context;
    std::unique_ptr<TcpBackend> _backend;
    SPSCQueue<PendingFrame>& _to_business;
    SPSCQueue<OutboundFrame>& _from_business;
    std::thread _thread;
    std::atomic<bool> _running;
    uint16_t _port;
};

} // namespace Protocol
} // namespace CppTrader

#endif // CPPTRADER_PROTOCOL_IO_THREAD_H
