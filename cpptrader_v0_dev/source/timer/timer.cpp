#include "trader/timer/timer.h"

#include <iostream>

namespace CppTrader {
namespace Timer {

TimerScheduler::TimerScheduler()
    : _running(false), _nextId(1)
{
}

TimerScheduler::~TimerScheduler()
{
    Stop();
}

void TimerScheduler::Start()
{
    bool expected = false;
    if (!_running.compare_exchange_strong(expected, true))
        return;

    _work_guard = std::make_unique<asio::executor_work_guard<asio::io_context::executor_type>>(
        asio::make_work_guard(_io_context));

    _thread = std::thread([this]() {
        _io_context.run();
    });
}

void TimerScheduler::Stop()
{
    bool expected = true;
    if (!_running.compare_exchange_strong(expected, false))
        return;

    CancelAll();

    _work_guard.reset();
    _io_context.stop();

    if (_thread.joinable())
    {
        _thread.join();
    }
}

TimerId TimerScheduler::ScheduleOnce(const std::string& name, std::chrono::steady_clock::duration delay, TimerCallback callback)
{
    std::lock_guard<std::mutex> lock(_mutex);

    auto it = _nameToId.find(name);
    if (it != _nameToId.end())
    {
        CancelInternal(it->second);
    }

    TimerId id = _nextId.fetch_add(1);

    auto entry = std::make_unique<TimerEntry>();
    entry->id = id;
    entry->name = name;
    entry->callback = std::move(callback);
    entry->interval = delay;
    entry->repeating = false;
    entry->timer = std::make_unique<asio::steady_timer>(_io_context, delay);

    entry->timer->async_wait([this, id](const asio::error_code& ec) {
        OnTimerExpired(id, ec);
    });

    _nameToId[name] = id;
    _timers[id] = std::move(entry);

    return id;
}

TimerId TimerScheduler::ScheduleRepeating(const std::string& name, std::chrono::steady_clock::duration interval, TimerCallback callback)
{
    std::lock_guard<std::mutex> lock(_mutex);

    auto it = _nameToId.find(name);
    if (it != _nameToId.end())
    {
        CancelInternal(it->second);
    }

    TimerId id = _nextId.fetch_add(1);

    auto entry = std::make_unique<TimerEntry>();
    entry->id = id;
    entry->name = name;
    entry->callback = std::move(callback);
    entry->interval = interval;
    entry->repeating = true;
    entry->timer = std::make_unique<asio::steady_timer>(_io_context, interval);

    entry->timer->async_wait([this, id](const asio::error_code& ec) {
        OnTimerExpired(id, ec);
    });

    _nameToId[name] = id;
    _timers[id] = std::move(entry);

    return id;
}

bool TimerScheduler::Cancel(TimerId id)
{
    std::lock_guard<std::mutex> lock(_mutex);
    return CancelInternal(id);
}

bool TimerScheduler::Cancel(const std::string& name)
{
    std::lock_guard<std::mutex> lock(_mutex);

    auto it = _nameToId.find(name);
    if (it == _nameToId.end())
        return false;

    bool result = CancelInternal(it->second);
    _nameToId.erase(it);
    return result;
}

void TimerScheduler::CancelAll()
{
    std::lock_guard<std::mutex> lock(_mutex);

    for (auto& [id, entry] : _timers)
    {
        if (entry && entry->timer)
        {
            entry->timer->cancel();
        }
    }

    _timers.clear();
    _nameToId.clear();
}

bool TimerScheduler::CancelInternal(TimerId id)
{
    auto it = _timers.find(id);
    if (it == _timers.end())
        return false;

    if (it->second && it->second->timer)
    {
        it->second->timer->cancel();
    }

    _nameToId.erase(it->second->name);
    _timers.erase(it);
    return true;
}

void TimerScheduler::OnTimerExpired(TimerId id, const asio::error_code& ec)
{
    if (ec == asio::error::operation_aborted)
        return;

    TimerCallback cb;
    bool is_repeating = false;
    std::chrono::steady_clock::duration interval;

    {
        std::lock_guard<std::mutex> lock(_mutex);

        auto it = _timers.find(id);
        if (it == _timers.end() || !it->second)
            return;

        is_repeating = it->second->repeating;
        interval = it->second->interval;
        cb = it->second->callback;

        if (!is_repeating)
        {
            _nameToId.erase(it->second->name);
            _timers.erase(it);
        }
    }

    if (cb)
    {
        try
        {
            cb();
        }
        catch (const std::exception& e)
        {
            std::cerr << "[Timer] Callback exception: " << e.what() << std::endl;
        }
    }

    if (is_repeating)
    {
        std::lock_guard<std::mutex> lock(_mutex);

        auto it = _timers.find(id);
        if (it == _timers.end() || !it->second)
            return;

        it->second->timer->expires_after(interval);
        it->second->timer->async_wait([this, id](const asio::error_code& ec2) {
            OnTimerExpired(id, ec2);
        });
    }
}

} // namespace Timer
} // namespace CppTrader
