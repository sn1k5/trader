#ifndef CPPTRADER_WAL_WAL_H
#define CPPTRADER_WAL_WAL_H

#include "trader/matching/order.h"

#include <atomic>
#include <cstdint>
#include <functional>
#include <memory>
#include <string>
#include <thread>
#include <mutex>
#include <condition_variable>
#include <queue>
#include <chrono>
#include <vector>

namespace CppTrader {

namespace Matching {
class MarketManager;
}

namespace WAL {

//! WAL 操作类型枚举
enum class OperationType : uint8_t
{
    NEW_ORDER = 1,
    CANCEL_ORDER = 2,
    TRADE = 3
};

//! WAL 日志条目（定长二进制格式，148 字节）
#pragma pack(push, 1)
struct WALEntry
{
    uint64_t LSN;
    uint64_t Timestamp;
    OperationType Operation;
    uint32_t CRC32;
    uint8_t Data[127];
};
#pragma pack(pop)
static_assert(sizeof(WALEntry) == 148, "WALEntry must be exactly 148 bytes");

//! 成交数据结构（56 字节）
struct TradeData
{
    uint64_t TradeId;
    uint64_t BidOrderId;
    uint64_t AskOrderId;
    uint32_t SymbolId;
    uint64_t Price;
    uint64_t Quantity;
    uint64_t Timestamp;
};
static_assert(sizeof(TradeData) == 56, "TradeData must be exactly 56 bytes");

//! 撤单数据结构（16 字节）
#pragma pack(push, 1)
struct CancelOrderData
{
    uint64_t OrderId;
    uint32_t SymbolId;
    uint8_t Reserved[4];
};
#pragma pack(pop)
static_assert(sizeof(CancelOrderData) == 16, "CancelOrderData must be exactly 16 bytes");

enum class SyncStrategy : uint8_t
{
    ON_WRITE,
    PERIODIC,
    BATCH
};

//! WAL 配置
struct WALConfig
{
    std::string LogDirectory = "./wal";     // 日志文件目录
    uint64_t MaxFileSize = 1024 * 1024 * 1024; // 单文件最大 1GB
    bool SyncOnWrite = false;
    SyncStrategy SyncMode = SyncStrategy::PERIODIC;
    size_t SyncBatchSize = 10;
    size_t BufferSize = 64 * 1024;

    size_t MaxFileCount = 0;                // 最大保留文件数 (0=不限)
    uint64_t MaxFileAgeSec = 0;             // 最大文件保留时间秒数 (0=不限)
    size_t MinReserveCount = 2;             // 最少保留文件数 (安全预留)
};

//! WAL 写入器
/*!
    负责将预写日志条目以定长二进制格式写入磁盘文件。
    支持同步写（fsync）和异步写（后台线程）两种模式。
    日志文件按大小滚动（默认 1GB）。
*/
class WALWriter
{
public:
    WALWriter(const WALConfig& config = WALConfig());
    ~WALWriter();

    WALWriter(const WALWriter&) = delete;
    WALWriter(WALWriter&&) = delete;
    WALWriter& operator=(const WALWriter&) = delete;
    WALWriter& operator=(WALWriter&&) = delete;

    //! 初始化 WAL 写入器（创建目录、打开文件、启动后台线程）
    void Initialize();
    //! 关闭 WAL 写入器（刷盘、关闭文件、停止后台线程）
    void Shutdown();

    //! 写入新订单日志（验证后、撮合前调用）
    uint64_t WriteNewOrder(const Matching::Order& order);
    //! 写入撤单日志（查找订单后、从订单簿移除前调用）
    uint64_t WriteCancelOrder(uint64_t orderId, uint32_t symbolId);
    //! 写入成交日志（更新订单状态后调用）
    uint64_t WriteTrade(const TradeData& trade);

    uint64_t CurrentLSN() const noexcept { return _currentLSN.load(); }
    static uint32_t CalculateCRC32(const WALEntry& entry);

    using WalRotateCallback = std::function<void()>;
    void SetRotateCallback(WalRotateCallback callback);

    struct CleanupResult
    {
        size_t files_deleted;
        size_t files_kept;
        size_t bytes_freed;
    };

    CleanupResult CleanupOldFiles(uint64_t safe_wal_sequence = 0);

private:
    void WorkerThread();
    bool OpenNewFile();
    bool RotateFile();
    void Sync();
    void WriteEntry(const WALEntry& entry);
    void FlushQueue();

    WALConfig _config;
    std::atomic<uint64_t> _currentLSN;
    int _fd;                    // POSIX 文件描述符
    std::string _currentFileName;
    uint64_t _currentFileSize;

    std::thread _worker;
    std::atomic<bool> _running;
    std::mutex _mutex;
    std::condition_variable _cv;
    std::queue<WALEntry> _queue;

    std::vector<uint8_t> _buffer;
    size_t _bufferPos;
    WalRotateCallback _rotate_callback;
    bool _rotated;

    void FlushBuffer();
    uint64_t _flushCount;
    size_t _entriesSinceSync;
    static constexpr uint64_t FSYNC_INTERVAL = 10;
    static constexpr auto FLUSH_TIMEOUT = std::chrono::microseconds(100);
};

struct WALReplayResult
{
    uint64_t entries_replayed;
    uint64_t entries_skipped;
    uint64_t last_lsn;
};

class WALReader
{
public:
    explicit WALReader(const std::string& log_directory);

    WALReplayResult Replay(CppTrader::Matching::MarketManager& market, uint64_t start_lsn = 0);

private:
    std::vector<std::string> FindWALFiles() const;

    std::string _logDirectory;
};

} // namespace WAL
} // namespace CppTrader

#endif // CPPTRADER_WAL_WAL_H