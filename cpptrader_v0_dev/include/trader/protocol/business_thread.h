#ifndef CPPTRADER_PROTOCOL_BUSINESS_THREAD_H
#define CPPTRADER_PROTOCOL_BUSINESS_THREAD_H

#include "spsc_queue.h"
#include "server.h"
#include "trader/protocol/request_handler.h"
#include "trader/matching/market_manager.h"
#include "trader/wal/wal.h"

#include <atomic>
#include <condition_variable>
#include <functional>
#include <memory>
#include <mutex>
#include <thread>

namespace CppTrader {
namespace Protocol {

class BusinessThread
{
public:
    BusinessThread(std::unique_ptr<INetworkBackend> backend,
                   Matching::MarketManager& market,
                   SPSCQueue<PendingFrame>& from_io,
                   SPSCQueue<OutboundFrame>& to_io);
    ~BusinessThread();

    BusinessThread(const BusinessThread&) = delete;
    BusinessThread(BusinessThread&&) = delete;
    BusinessThread& operator=(const BusinessThread&) = delete;
    BusinessThread& operator=(const BusinessThread&&) = delete;

    bool Start();
    void Stop();
    void Join();

    ProtocolServer& Server() noexcept { return *_server; }
    RequestHandler& Handler() noexcept { return *_handler; }

    void SetWALWriter(std::shared_ptr<WAL::WALWriter> wal_writer);
    void SetSnapshotManager(std::shared_ptr<Snapshot::SnapshotManager> snapshot_manager);
    void SetIONotify(std::function<void()> notify);
      void Notify();

private:
    void Run();

    std::unique_ptr<ProtocolServer> _server;
    Matching::MarketManager& _market;
    std::unique_ptr<RequestHandler> _handler;
    std::shared_ptr<WAL::WALWriter> _wal_writer;
    std::shared_ptr<Snapshot::SnapshotManager> _snapshot_manager;
    SPSCQueue<PendingFrame>& _from_io;
    SPSCQueue<OutboundFrame>& _to_io;
    std::thread _thread;
    std::atomic<bool> _running;
      std::function<void()> _io_notify;
      std::mutex _cv_mutex;
      std::condition_variable _cv;
};

} // namespace Protocol
} // namespace CppTrader

#endif // CPPTRADER_PROTOCOL_BUSINESS_THREAD_H
