#ifndef CPPTRADER_TIMER_TIMER_H
#define CPPTRADER_TIMER_TIMER_H

#include <asio.hpp>

#include <atomic>
#include <chrono>
#include <functional>
#include <memory>
#include <mutex>
#include <string>
#include <unordered_map>
#include <vector>

namespace CppTrader {
namespace Timer {

using TimerCallback = std::function<void()>;
using TimerId = uint64_t;

class TimerScheduler
{
public:
    TimerScheduler();
    ~TimerScheduler();

    TimerScheduler(const TimerScheduler&) = delete;
    TimerScheduler(TimerScheduler&&) = delete;
    TimerScheduler& operator=(const TimerScheduler&) = delete;
    TimerScheduler& operator=(TimerScheduler&&) = delete;

    void Start();
    void Stop();

    TimerId ScheduleOnce(const std::string& name, std::chrono::steady_clock::duration delay, TimerCallback callback);
    TimerId ScheduleRepeating(const std::string& name, std::chrono::steady_clock::duration interval, TimerCallback callback);

    bool Cancel(TimerId id);
    bool Cancel(const std::string& name);
    void CancelAll();

    bool IsRunning() const noexcept { return _running.load(); }

private:
    struct TimerEntry
    {
        TimerId id;
        std::string name;
        std::unique_ptr<asio::steady_timer> timer;
        TimerCallback callback;
        std::chrono::steady_clock::duration interval;
        bool repeating;
    };

    void OnTimerExpired(TimerId id, const asio::error_code& ec);
    void RescheduleRepeating(TimerId id);
    bool CancelInternal(TimerId id);

    asio::io_context _io_context;
    std::unique_ptr<asio::executor_work_guard<asio::io_context::executor_type>> _work_guard;
    std::thread _thread;
    std::atomic<bool> _running;
    std::atomic<TimerId> _nextId;

    std::mutex _mutex;
    std::unordered_map<TimerId, std::unique_ptr<TimerEntry>> _timers;
    std::unordered_map<std::string, TimerId> _nameToId;
};

} // namespace Timer
} // namespace CppTrader

#endif // CPPTRADER_TIMER_TIMER_H
