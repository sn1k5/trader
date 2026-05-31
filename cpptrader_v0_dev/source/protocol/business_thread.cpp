#include "trader/protocol/business_thread.h"

#include <algorithm>
#include <cstring>
#include <iostream>

namespace CppTrader {
namespace Protocol {

BusinessThread::BusinessThread(std::unique_ptr<INetworkBackend> backend,
                               Matching::MarketManager& market,
                               SPSCQueue<PendingFrame>& from_io,
                               SPSCQueue<OutboundFrame>& to_io)
    : _market(market)
    , _from_io(from_io)
    , _to_io(to_io)
    , _running(false)
{
    _server = std::make_unique<ProtocolServer>(std::move(backend), market);

    _server->SetOutputCallback(
        [this](uint16_t conn_id, const void* data, size_t len)
          {
              OutboundFrame frame;
              frame.conn_id = conn_id;
              frame.frame_size = static_cast<uint16_t>(std::min(len, sizeof(frame.frame_data)));
              std::memcpy(frame.frame_data.data(), data, frame.frame_size);
              while (!_to_io.TryEnqueue(frame))
            {
                std::this_thread::yield();
            }
            if (_io_notify)
            {
                _io_notify();
            }
        },
        [this](const void* data, size_t len)
          {
              OutboundFrame frame;
              frame.conn_id = 0;
              frame.frame_size = static_cast<uint16_t>(std::min(len, sizeof(frame.frame_data)));
              std::memcpy(frame.frame_data.data(), data, frame.frame_size);
              while (!_to_io.TryEnqueue(frame))
            {
                std::this_thread::yield();
            }
            if (_io_notify)
            {
                _io_notify();
            }
        }
    );
}

BusinessThread::~BusinessThread()
{
    Stop();
    Join();
}

bool BusinessThread::Start()
{
    if (!_server->init())
    {
        std::cerr << "BusinessThread: server init failed" << std::endl;
        return false;
    }

    _handler = std::make_unique<RequestHandler>(*_server, _market, _wal_writer, _snapshot_manager);
    _handler->RegisterHandlers();

    _running = true;
    _thread = std::thread(&BusinessThread::Run, this);
    std::cout << "BusinessThread started" << std::endl;
    return true;
}

void BusinessThread::Stop()
{
    _running = false;
}

void BusinessThread::Join()
{
    if (_thread.joinable())
    {
        _thread.join();
    }
}

void BusinessThread::SetWALWriter(std::shared_ptr<WAL::WALWriter> wal_writer)
{
    _wal_writer = std::move(wal_writer);
}

void BusinessThread::SetSnapshotManager(std::shared_ptr<Snapshot::SnapshotManager> snapshot_manager)
{
    _snapshot_manager = std::move(snapshot_manager);
}

void BusinessThread::SetIONotify(std::function<void()> notify)
{
    _io_notify = std::move(notify);
}

void BusinessThread::Notify()
{
    _cv.notify_one();
}

void BusinessThread::Run()
{
    while (_running)
    {
        PendingFrame frame;
        bool processed = false;

        while (_from_io.TryDequeue(frame))
        {
            processed = true;
            switch (frame.type)
            {
            case FrameType::MESSAGE:
                _server->ProcessMessage(frame.conn_id, frame.header, frame.body.data(), frame.body_size);
                break;
            case FrameType::CONNECT:
                _server->ProcessConnect(frame.conn_id);
                break;
            case FrameType::DISCONNECT:
                _server->ProcessDisconnect(frame.conn_id);
                break;
            }
        }

        if (!processed)
          {
              std::unique_lock<std::mutex> lk(_cv_mutex);
              _cv.wait_for(lk, std::chrono::microseconds(100));
          }
    }
}

} // namespace Protocol
} // namespace CppTrader
