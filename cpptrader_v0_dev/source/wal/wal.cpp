#include "trader/wal/wal.h"

#include <cstring>
#include <ctime>
#include <sys/stat.h>
#include <sys/types.h>
#include <unistd.h>
#include <fcntl.h>
#include <cerrno>
#include <iostream>

namespace CppTrader {
namespace WAL {

WALWriter::WALWriter(const WALConfig& config)
    : _config(config), _currentLSN(0), _fd(-1), _currentFileSize(0),
      _running(false), _bufferPos(0)
{
    _buffer.resize(_config.BufferSize);
}

WALWriter::~WALWriter()
{
    Shutdown();
}

void WALWriter::Initialize()
{
    mkdir(_config.LogDirectory.c_str(), 0755);
    OpenNewFile();
    _running = true;
    _worker = std::thread(&WALWriter::WorkerThread, this);
}

void WALWriter::Shutdown()
{
    _running = false;
    _cv.notify_one();
    if (_worker.joinable())
    {
        _worker.join();
    }
    if (_fd >= 0)
    {
        Sync();
        ::close(_fd);
        _fd = -1;
    }
}

uint64_t WALWriter::WriteNewOrder(const Matching::Order& order)
{
    WALEntry entry{};
    entry.LSN = ++_currentLSN;
    entry.Timestamp = std::chrono::duration_cast<std::chrono::microseconds>(
        std::chrono::system_clock::now().time_since_epoch()).count();
    entry.Operation = OperationType::NEW_ORDER;

    // 将 Order 结构体序列化到 Data 字段
    size_t order_size = sizeof(Matching::Order);
    if (order_size <= sizeof(entry.Data))
    {
        std::memcpy(entry.Data, &order, order_size);
    }

    if (_config.SyncOnWrite)
    {
        std::lock_guard<std::mutex> lock(_mutex);
        WriteEntry(entry);
        Sync();
        return entry.LSN;
    }
    else
    {
        std::lock_guard<std::mutex> lock(_mutex);
        _queue.push(entry);
        _cv.notify_one();
        return entry.LSN;
    }
}

uint64_t WALWriter::WriteCancelOrder(uint64_t orderId, uint32_t symbolId)
{
    WALEntry entry{};
    entry.LSN = ++_currentLSN;
    entry.Timestamp = std::chrono::duration_cast<std::chrono::microseconds>(
        std::chrono::system_clock::now().time_since_epoch()).count();
    entry.Operation = OperationType::CANCEL_ORDER;

    CancelOrderData cancel_data{};
    cancel_data.OrderId = orderId;
    cancel_data.SymbolId = symbolId;
    std::memcpy(entry.Data, &cancel_data, sizeof(CancelOrderData));

    if (_config.SyncOnWrite)
    {
        std::lock_guard<std::mutex> lock(_mutex);
        WriteEntry(entry);
        Sync();
        return entry.LSN;
    }
    else
    {
        std::lock_guard<std::mutex> lock(_mutex);
        _queue.push(entry);
        _cv.notify_one();
        return entry.LSN;
    }
}

uint64_t WALWriter::WriteTrade(const TradeData& trade)
{
    WALEntry entry{};
    entry.LSN = ++_currentLSN;
    entry.Timestamp = std::chrono::duration_cast<std::chrono::microseconds>(
        std::chrono::system_clock::now().time_since_epoch()).count();
    entry.Operation = OperationType::TRADE;

    std::memcpy(entry.Data, &trade, sizeof(TradeData));

    if (_config.SyncOnWrite)
    {
        std::lock_guard<std::mutex> lock(_mutex);
        WriteEntry(entry);
        Sync();
        return entry.LSN;
    }
    else
    {
        std::lock_guard<std::mutex> lock(_mutex);
        _queue.push(entry);
        _cv.notify_one();
        return entry.LSN;
    }
}

void WALWriter::WriteEntry(const WALEntry& entry)
{
    if (_fd < 0)
        return;

    ssize_t written = ::write(_fd, &entry, sizeof(entry));
    if (written == sizeof(entry))
    {
        _currentFileSize += sizeof(entry);

        if (_currentFileSize >= _config.MaxFileSize)
        {
            RotateFile();
        }
    }
    else
    {
        std::cerr << "[WAL] Write error: " << strerror(errno) << std::endl;
    }
}

void WALWriter::FlushQueue()
{
    while (!_queue.empty())
    {
        const WALEntry& entry = _queue.front();
        WriteEntry(entry);
        _queue.pop();
    }
}

void WALWriter::WorkerThread()
{
    while (_running)
    {
        std::unique_lock<std::mutex> lock(_mutex);
        _cv.wait(lock, [this] { return !_queue.empty() || !_running; });

        FlushQueue();

        if (_config.SyncOnWrite)
        {
            Sync();
        }
    }

    // 确保退出前刷完所有剩余条目
    std::lock_guard<std::mutex> lock(_mutex);
    FlushQueue();
    Sync();
}

bool WALWriter::OpenNewFile()
{
    if (_fd >= 0)
    {
        Sync();
        ::close(_fd);
        _fd = -1;
    }

    std::time_t now = std::time(nullptr);
    char timestamp[20];
    std::strftime(timestamp, sizeof(timestamp), "%Y%m%d_%H%M%S", std::localtime(&now));

    _currentFileName = _config.LogDirectory + "/wal_" + timestamp + ".log";
    _fd = ::open(_currentFileName.c_str(), O_WRONLY | O_CREAT | O_TRUNC | O_APPEND, 0644);

    if (_fd < 0)
    {
        std::cerr << "[WAL] Failed to open file: " << _currentFileName
                  << " error: " << strerror(errno) << std::endl;
        return false;
    }

    _currentFileSize = 0;
    return true;
}

bool WALWriter::RotateFile()
{
    Sync();
    ::close(_fd);
    _fd = -1;

    std::time_t now = std::time(nullptr);
    char timestamp[20];
    std::strftime(timestamp, sizeof(timestamp), "%Y%m%d_%H%M%S", std::localtime(&now));

    _currentFileName = _config.LogDirectory + "/wal_" + timestamp + ".log";
    _fd = ::open(_currentFileName.c_str(), O_WRONLY | O_CREAT | O_TRUNC | O_APPEND, 0644);

    if (_fd < 0)
    {
        std::cerr << "[WAL] Failed to rotate file: " << _currentFileName
                  << " error: " << strerror(errno) << std::endl;
        return false;
    }

    _currentFileSize = 0;
    return true;
}

void WALWriter::Sync()
{
    if (_fd >= 0)
    {
        ::fsync(_fd);
    }
}

} // namespace WAL
} // namespace CppTrader