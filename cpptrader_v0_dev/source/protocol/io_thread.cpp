#include "trader/protocol/io_thread.h"

#include <algorithm>
#include <cstring>
#include <iostream>

namespace CppTrader {
namespace Protocol {

IOThread::IOThread(uint16_t port, SPSCQueue<PendingFrame>& to_business, SPSCQueue<OutboundFrame>& from_business)
    : _to_business(to_business)
    , _from_business(from_business)
    , _running(false)
    , _port(port)
{
}

IOThread::~IOThread()
{
    Stop();
    Join();
}

bool IOThread::Start()
{
    _backend = std::make_unique<TcpBackend>(_io_context, _port);

    _backend->SetMessageHandler([this](uint16_t conn_id, const MsgHeader& header, const uint8_t* body, size_t body_len)
    {
        PendingFrame frame;
        frame.type = FrameType::MESSAGE;
        frame.conn_id = conn_id;
        frame.header = header;
        frame.body_size = static_cast<uint16_t>(std::min(body_len, sizeof(frame.body)));
          std::memcpy(frame.body.data(), body, frame.body_size);
        while (!_to_business.TryEnqueue(frame))
        {
            std::this_thread::yield();
        }
    });

    _backend->SetConnectHandler([this](uint16_t conn_id)
    {
        PendingFrame frame;
        frame.type = FrameType::CONNECT;
        frame.conn_id = conn_id;
        while (!_to_business.TryEnqueue(frame))
        {
            std::this_thread::yield();
        }
    });

    _backend->SetDisconnectHandler([this](uint16_t conn_id)
    {
        PendingFrame frame;
        frame.type = FrameType::DISCONNECT;
        frame.conn_id = conn_id;
        while (!_to_business.TryEnqueue(frame))
        {
            std::this_thread::yield();
        }
    });

    if (!_backend->init())
    {
        std::cerr << "IOThread: backend init failed" << std::endl;
        return false;
    }

    _running = true;
    _thread = std::thread(&IOThread::Run, this);
    std::cout << "IOThread started on port " << _port << std::endl;
    return true;
}

void IOThread::Stop()
{
    _running = false;
    _io_context.stop();
}

void IOThread::Join()
{
    if (_thread.joinable())
    {
        _thread.join();
    }
}

void IOThread::Run()
{
    while (_running)
    {
        _io_context.run_one();
        DrainOutboundQueue();
    }

    DrainOutboundQueue();
}

void IOThread::NotifyIO()
{
    asio::post(_io_context, [](){});
}

void IOThread::DrainOutboundQueue()
{
    OutboundFrame frame;
    while (_from_business.TryDequeue(frame))
    {
        if (_backend)
        {
            if (frame.conn_id == 0)
            {
                _backend->broadcast(frame.frame_data.data(), frame.frame_size);
            }
            else
            {
                _backend->send(frame.conn_id, frame.frame_data.data(), frame.frame_size);
            }
        }
    }
}

} // namespace Protocol
} // namespace CppTrader
