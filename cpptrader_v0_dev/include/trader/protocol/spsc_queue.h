#ifndef CPPTRADER_PROTOCOL_SPSC_QUEUE_H
#define CPPTRADER_PROTOCOL_SPSC_QUEUE_H

#include "protocol.h"

#include <array>
#include <atomic>
#include <cstddef>
#include <cstdint>

namespace CppTrader {
namespace Protocol {

enum class FrameType : uint8_t
{
    MESSAGE = 0,
    CONNECT = 1,
    DISCONNECT = 2
};

struct PendingFrame
{
    FrameType type = FrameType::MESSAGE;
    uint16_t conn_id;
    MsgHeader header;
    std::array<uint8_t, 1024> body;
    uint16_t body_size = 0;
};

struct OutboundFrame
{
    uint16_t conn_id;
    std::array<uint8_t, 1024> frame_data;
    uint16_t frame_size = 0;
};

template <typename T, size_t Capacity = 65536>
class SPSCQueue
{
    static_assert((Capacity & (Capacity - 1)) == 0, "Capacity must be a power of 2");
    static constexpr size_t kMask = Capacity - 1;

public:
    SPSCQueue()
          : head_(0)
          , tail_(0)
      {
      }

    SPSCQueue(const SPSCQueue&) = delete;
    SPSCQueue& operator=(const SPSCQueue&) = delete;

    bool TryEnqueue(const T& item)
    {
        const size_t head = head_.load(std::memory_order_relaxed);
        const size_t next = (head + 1) & kMask;
        if (next == tail_.load(std::memory_order_acquire))
        {
            overflow_count_.fetch_add(1, std::memory_order_relaxed);
            return false;
        }
        buffer_[head] = item;
        head_.store(next, std::memory_order_release);
        enqueue_count_.fetch_add(1, std::memory_order_relaxed);
        return true;
    }

    bool TryDequeue(T& item)
    {
        const size_t tail = tail_.load(std::memory_order_relaxed);
        if (tail == head_.load(std::memory_order_acquire))
            return false;
        item = std::move(buffer_[tail]);
        tail_.store((tail + 1) & kMask, std::memory_order_release);
        dequeue_count_.fetch_add(1, std::memory_order_relaxed);
        return true;
    }

    bool Empty() const
    {
        return head_.load(std::memory_order_acquire) == tail_.load(std::memory_order_acquire);
    }

    size_t Size() const
    {
        const size_t head = head_.load(std::memory_order_acquire);
        const size_t tail = tail_.load(std::memory_order_acquire);
        return (head - tail) & kMask;
    }

    struct Stats { uint64_t enqueue_count; uint64_t dequeue_count; uint64_t overflow_count; };

    Stats GetStats() const
    {
        return Stats{
            enqueue_count_.load(std::memory_order_relaxed),
            dequeue_count_.load(std::memory_order_relaxed),
            overflow_count_.load(std::memory_order_relaxed)
        };
    }

private:
    std::atomic<size_t> head_;
    std::atomic<size_t> tail_;
    std::array<T, Capacity> buffer_;
    std::atomic<uint64_t> enqueue_count_{0};
    std::atomic<uint64_t> dequeue_count_{0};
    std::atomic<uint64_t> overflow_count_{0};
};

} // namespace Protocol
} // namespace CppTrader

#endif // CPPTRADER_PROTOCOL_SPSC_QUEUE_H
