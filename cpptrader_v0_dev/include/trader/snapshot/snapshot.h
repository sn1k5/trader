#ifndef CPPTRADER_SNAPSHOT_SNAPSHOT_H
#define CPPTRADER_SNAPSHOT_SNAPSHOT_H

#include "trader/matching/market_manager.h"
#include "trader/matching/symbol.h"
#include "trader/matching/order.h"
#include "trader/matching/order_book.h"

#include <cstdint>
#include <string>
#include <vector>
#include <mutex>
#include <functional>
#include <atomic>
#include <thread>
#include <condition_variable>

namespace CppTrader {
namespace Snapshot {

#pragma pack(push, 1)
struct SnapshotHeader
{
    uint32_t magic = 0x53534E50;
    uint32_t version = 1;
    uint64_t timestamp_ns;
    uint64_t wal_sequence;
    uint32_t symbol_count;
    uint32_t order_count;
};
#pragma pack(pop)
static_assert(sizeof(SnapshotHeader) == 32, "SnapshotHeader must be exactly 32 bytes");

#pragma pack(push, 1)
struct SnapshotSymbol
{
    uint32_t Id;
    char Name[8];
};
#pragma pack(pop)
static_assert(sizeof(SnapshotSymbol) == 12, "SnapshotSymbol must be exactly 12 bytes");

#pragma pack(push, 1)
struct SnapshotOrderBook
{
    uint32_t SymbolId;
    uint64_t LastBidPrice;
    uint64_t LastAskPrice;
    uint64_t MatchingBidPrice;
    uint64_t MatchingAskPrice;
    uint64_t TrailingBidPrice;
    uint64_t TrailingAskPrice;
};
#pragma pack(pop)
static_assert(sizeof(SnapshotOrderBook) == 52, "SnapshotOrderBook must be exactly 52 bytes");

struct SnapshotConfig
{
    std::string SnapshotDirectory = "./snapshots";
    uint64_t SnapshotIntervalSec = 300;
    bool SyncOnWrite = true;

    size_t MaxSnapshotCount = 0;            // 最大保留快照数 (0=不限)
    size_t MinReserveCount = 2;             // 最少保留快照数 (安全预留)
    uint64_t MaxSnapshotAgeSec = 0;         // 最大快照保留时间秒数 (0=不限)
};

class SnapshotManager
{
public:
    SnapshotManager(const SnapshotConfig& config = SnapshotConfig());
    ~SnapshotManager();

    SnapshotManager(const SnapshotManager&) = delete;
    SnapshotManager(SnapshotManager&&) = delete;
    SnapshotManager& operator=(const SnapshotManager&) = delete;
    SnapshotManager& operator=(SnapshotManager&&) = delete;

    void Initialize();
    void Shutdown();
    bool TakeSnapshot(const CppTrader::Matching::MarketManager& market, uint64_t wal_sequence);
    bool LoadSnapshot(CppTrader::Matching::MarketManager& market, uint64_t& wal_sequence);
    std::string GetLatestSnapshotPath() const;

    struct CleanupResult
    {
        size_t snapshots_deleted;
        size_t snapshots_kept;
        size_t bytes_freed;
    };

    CleanupResult CleanupOldSnapshots();

    using SnapshotCallback = std::function<uint64_t()>;
    void StartPeriodicSnapshot(const CppTrader::Matching::MarketManager& market, SnapshotCallback get_wal_sequence);
    void StopPeriodicSnapshot();

    void SetPauseMatchingCallback(std::function<void()> callback);
    void SetResumeMatchingCallback(std::function<void()> callback);

private:
    bool WriteSnapshotFile(const std::string& path, const CppTrader::Matching::MarketManager& market, uint64_t wal_sequence);
    bool ReadSnapshotFile(const std::string& path, CppTrader::Matching::MarketManager& market, uint64_t& wal_sequence);
    bool ValidateSnapshotCRC32(const std::string& path);
    std::vector<std::string> FindSnapshotFiles() const;
    uint32_t CalculateCRC32(const uint8_t* data, size_t length);

    SnapshotConfig _config;
    std::mutex _mutex;
    std::string _latestSnapshotPath;

    std::function<void()> _pause_matching_callback;
    std::function<void()> _resume_matching_callback;

    std::thread _periodic_thread;
    std::atomic<bool> _periodic_running;
    std::condition_variable _periodic_cv;
    const CppTrader::Matching::MarketManager* _periodic_market;
    SnapshotCallback _get_wal_sequence;
};

} // namespace Snapshot
} // namespace CppTrader

#endif // CPPTRADER_SNAPSHOT_SNAPSHOT_H
