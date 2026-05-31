#include "trader/snapshot/snapshot.h"

#include <cstring>
#include <ctime>
#include <sys/stat.h>
#include <sys/types.h>
#include <unistd.h>
#include <fcntl.h>
#include <dirent.h>
#include <cerrno>
#include <iostream>
#include <algorithm>
#include <chrono>

namespace CppTrader {
namespace Snapshot {

SnapshotManager::SnapshotManager(const SnapshotConfig& config)
    : _config(config), _periodic_running(false), _periodic_market(nullptr)
{
}

SnapshotManager::~SnapshotManager()
{
    Shutdown();
}

uint32_t SnapshotManager::CalculateCRC32(const uint8_t* data, size_t length)
{
    static uint32_t table[256];
    static bool initialized = false;

    if (!initialized)
    {
        for (uint32_t i = 0; i < 256; ++i)
        {
            uint32_t crc = i;
            for (int j = 0; j < 8; ++j)
            {
                if (crc & 1)
                    crc = (crc >> 1) ^ 0xEDB88320;
                else
                    crc >>= 1;
            }
            table[i] = crc;
        }
        initialized = true;
    }

    uint32_t crc = 0xFFFFFFFF;
    for (size_t i = 0; i < length; ++i)
    {
        crc = (crc >> 8) ^ table[(crc ^ data[i]) & 0xFF];
    }
    return crc ^ 0xFFFFFFFF;
}

void SnapshotManager::Initialize()
{
    mkdir(_config.SnapshotDirectory.c_str(), 0755);
}

void SnapshotManager::Shutdown()
{
    StopPeriodicSnapshot();
}

void SnapshotManager::SetPauseMatchingCallback(std::function<void()> callback)
{
    _pause_matching_callback = std::move(callback);
}

void SnapshotManager::SetResumeMatchingCallback(std::function<void()> callback)
{
    _resume_matching_callback = std::move(callback);
}

bool SnapshotManager::WriteSnapshotFile(const std::string& path, const CppTrader::Matching::MarketManager& market, uint64_t wal_sequence)
{
    int fd = ::open(path.c_str(), O_WRONLY | O_CREAT | O_TRUNC, 0644);
    if (fd < 0)
    {
        std::cerr << "[Snapshot] Failed to open file: " << path
                  << " error: " << strerror(errno) << std::endl;
        return false;
    }

    const auto& symbols = market.symbols();
    const auto& order_books = market.order_books();
    const auto& orders = market.orders();

    SnapshotHeader header{};
    header.magic = 0x53534E50;
    header.version = 1;
    header.timestamp_ns = std::chrono::duration_cast<std::chrono::nanoseconds>(
        std::chrono::system_clock::now().time_since_epoch()).count();
    header.wal_sequence = wal_sequence;
    header.symbol_count = static_cast<uint32_t>(symbols.size());
    header.order_count = static_cast<uint32_t>(orders.size());

    size_t order_size = sizeof(Matching::Order);
    size_t buffer_size = sizeof(SnapshotHeader)
                       + symbols.size() * sizeof(SnapshotSymbol)
                       + order_books.size() * sizeof(SnapshotOrderBook)
                       + orders.size() * order_size
                       + sizeof(uint32_t);

    std::vector<uint8_t> buffer(buffer_size);
    size_t offset = 0;

    std::memcpy(buffer.data() + offset, &header, sizeof(SnapshotHeader));
    offset += sizeof(SnapshotHeader);

    for (const auto* symbol : symbols)
    {
        SnapshotSymbol ss{};
        ss.Id = symbol->Id;
        std::memcpy(ss.Name, symbol->Name, sizeof(ss.Name));
        std::memcpy(buffer.data() + offset, &ss, sizeof(SnapshotSymbol));
        offset += sizeof(SnapshotSymbol);
    }

    for (const auto* ob : order_books)
    {
        SnapshotOrderBook sob{};
        sob.SymbolId = ob->symbol().Id;
        sob.LastBidPrice = 0;
        sob.LastAskPrice = 0;
        sob.MatchingBidPrice = 0;
        sob.MatchingAskPrice = 0;
        sob.TrailingBidPrice = 0;
        sob.TrailingAskPrice = 0;
        std::memcpy(buffer.data() + offset, &sob, sizeof(SnapshotOrderBook));
        offset += sizeof(SnapshotOrderBook);
    }

    for (auto it = orders.begin(); it != orders.end(); ++it)
    {
        const Matching::Order& order = *(it->second);
        std::memcpy(buffer.data() + offset, &order, order_size);
        offset += order_size;
    }

    uint32_t crc = CalculateCRC32(buffer.data(), offset);
    std::memcpy(buffer.data() + offset, &crc, sizeof(uint32_t));
    offset += sizeof(uint32_t);

    ssize_t written = ::write(fd, buffer.data(), offset);
    if (written != static_cast<ssize_t>(offset))
    {
        std::cerr << "[Snapshot] Write error: " << strerror(errno) << std::endl;
        ::close(fd);
        return false;
    }

    if (_config.SyncOnWrite)
    {
        ::fsync(fd);
    }

    ::close(fd);
    return true;
}

bool SnapshotManager::TakeSnapshot(const CppTrader::Matching::MarketManager& market, uint64_t wal_sequence)
{
    std::lock_guard<std::mutex> lock(_mutex);

    if (_pause_matching_callback)
        _pause_matching_callback();

    std::time_t now = std::time(nullptr);
    char timestamp[20];
    std::strftime(timestamp, sizeof(timestamp), "%Y%m%d_%H%M%S", std::localtime(&now));

    std::string path = _config.SnapshotDirectory + "/snapshot_" + timestamp + ".bin";

    bool result = WriteSnapshotFile(path, market, wal_sequence);

    if (_resume_matching_callback)
        _resume_matching_callback();

    if (result)
    {
        _latestSnapshotPath = path;
        return true;
    }

    return false;
}

bool SnapshotManager::ReadSnapshotFile(const std::string& path, CppTrader::Matching::MarketManager& market, uint64_t& wal_sequence)
{
    int fd = ::open(path.c_str(), O_RDONLY);
    if (fd < 0)
    {
        std::cerr << "[Snapshot] Failed to open file: " << path
                  << " error: " << strerror(errno) << std::endl;
        return false;
    }

    off_t file_size = lseek(fd, 0, SEEK_END);
    lseek(fd, 0, SEEK_SET);

    if (file_size < 36)
    {
        std::cerr << "[Snapshot] File too small: " << path << std::endl;
        ::close(fd);
        return false;
    }

    std::vector<uint8_t> buffer(file_size);
    ssize_t bytes_read = ::read(fd, buffer.data(), file_size);
    ::close(fd);

    if (bytes_read != file_size)
    {
        std::cerr << "[Snapshot] Read error: " << strerror(errno) << std::endl;
        return false;
    }

    uint32_t stored_crc;
    std::memcpy(&stored_crc, buffer.data() + file_size - sizeof(uint32_t), sizeof(uint32_t));

    uint32_t calculated_crc = CalculateCRC32(buffer.data(), file_size - sizeof(uint32_t));
    if (stored_crc != calculated_crc)
    {
        std::cerr << "[Snapshot] CRC mismatch in file: " << path << std::endl;
        return false;
    }

    SnapshotHeader header;
    std::memcpy(&header, buffer.data(), sizeof(SnapshotHeader));

    if (header.magic != 0x53534E50 || header.version != 1)
    {
        std::cerr << "[Snapshot] Invalid header in file: " << path << std::endl;
        return false;
    }

    size_t offset = sizeof(SnapshotHeader);

    for (uint32_t i = 0; i < header.symbol_count; ++i)
    {
        SnapshotSymbol ss;
        std::memcpy(&ss, buffer.data() + offset, sizeof(SnapshotSymbol));
        offset += sizeof(SnapshotSymbol);

        Matching::Symbol symbol;
        symbol.Id = ss.Id;
        std::memcpy(symbol.Name, ss.Name, sizeof(symbol.Name));
        market.AddSymbol(symbol);
    }

    size_t order_size = sizeof(Matching::Order);
    size_t data_size = file_size - sizeof(uint32_t);
    size_t order_book_section_size = data_size - sizeof(SnapshotHeader)
                                   - header.symbol_count * sizeof(SnapshotSymbol)
                                   - header.order_count * order_size;
    uint32_t order_book_count = static_cast<uint32_t>(order_book_section_size / sizeof(SnapshotOrderBook));

    for (uint32_t i = 0; i < order_book_count; ++i)
    {
        SnapshotOrderBook sob;
        std::memcpy(&sob, buffer.data() + offset, sizeof(SnapshotOrderBook));
        offset += sizeof(SnapshotOrderBook);

        const Matching::Symbol* sym = market.GetSymbol(sob.SymbolId);
        if (sym)
        {
            market.AddOrderBook(*sym);
        }
    }

    for (uint32_t i = 0; i < header.order_count; ++i)
    {
        Matching::Order order;
        std::memcpy(&order, buffer.data() + offset, order_size);
        offset += order_size;

        market.AddOrder(order);
    }

    wal_sequence = header.wal_sequence;
    return true;
}

bool SnapshotManager::ValidateSnapshotCRC32(const std::string& path)
{
    int fd = ::open(path.c_str(), O_RDONLY);
    if (fd < 0)
        return false;

    off_t file_size = lseek(fd, 0, SEEK_END);
    lseek(fd, 0, SEEK_SET);

    if (file_size < 36)
    {
        ::close(fd);
        return false;
    }

    std::vector<uint8_t> buffer(file_size);
    ssize_t bytes_read = ::read(fd, buffer.data(), file_size);
    ::close(fd);

    if (bytes_read != file_size)
        return false;

    uint32_t stored_crc;
    std::memcpy(&stored_crc, buffer.data() + file_size - sizeof(uint32_t), sizeof(uint32_t));

    uint32_t calculated_crc = CalculateCRC32(buffer.data(), file_size - sizeof(uint32_t));

    return stored_crc == calculated_crc;
}

std::vector<std::string> SnapshotManager::FindSnapshotFiles() const
{
    std::vector<std::string> files;

    DIR* dir = opendir(_config.SnapshotDirectory.c_str());
    if (!dir)
        return files;

    struct dirent* entry;
    while ((entry = readdir(dir)) != nullptr)
    {
        std::string name = entry->d_name;
        if (name.size() >= 13 && name.compare(0, 9, "snapshot_") == 0 && name.compare(name.size() - 4, 4, ".bin") == 0)
        {
            files.push_back(_config.SnapshotDirectory + "/" + name);
        }
    }

    closedir(dir);

    std::sort(files.begin(), files.end());
    return files;
}

std::string SnapshotManager::GetLatestSnapshotPath() const
{
    std::vector<std::string> files = FindSnapshotFiles();

    for (auto it = files.rbegin(); it != files.rend(); ++it)
    {
        if (const_cast<SnapshotManager*>(this)->ValidateSnapshotCRC32(*it))
        {
            return *it;
        }
    }

    return "";
}

bool SnapshotManager::LoadSnapshot(CppTrader::Matching::MarketManager& market, uint64_t& wal_sequence)
{
    std::string path = GetLatestSnapshotPath();
    if (path.empty())
        return false;

    std::lock_guard<std::mutex> lock(_mutex);
    return ReadSnapshotFile(path, market, wal_sequence);
}

SnapshotManager::CleanupResult SnapshotManager::CleanupOldSnapshots()
{
    CleanupResult result{};
    result.snapshots_deleted = 0;
    result.snapshots_kept = 0;
    result.bytes_freed = 0;

    std::vector<std::string> files = FindSnapshotFiles();

    if (files.empty())
        return result;

    std::vector<std::pair<std::string, bool>> file_validity;
    for (const auto& f : files)
    {
        file_validity.push_back({f, ValidateSnapshotCRC32(f)});
    }

    std::vector<std::pair<std::string, struct stat>> valid_files;
    for (const auto& [path, valid] : file_validity)
    {
        if (!valid)
            continue;

        struct stat st;
        if (stat(path.c_str(), &st) == 0)
        {
            valid_files.push_back({path, st});
        }
    }

    if (valid_files.empty())
        return result;

    size_t total_valid = valid_files.size();
    size_t min_keep = std::min(_config.MinReserveCount, total_valid);

    size_t can_delete_count = 0;
    if (_config.MaxSnapshotCount > 0 && total_valid > _config.MaxSnapshotCount)
    {
        can_delete_count = total_valid - _config.MaxSnapshotCount;
    }

    time_t now = std::time(nullptr);

    for (size_t i = 0; i + min_keep < total_valid; ++i)
    {
        bool should_delete = false;

        if (_config.MaxSnapshotCount > 0 && i < can_delete_count)
        {
            should_delete = true;
        }

        if (_config.MaxSnapshotAgeSec > 0)
        {
            if (static_cast<uint64_t>(now - valid_files[i].second.st_mtime) > _config.MaxSnapshotAgeSec)
            {
                should_delete = true;
            }
        }

        if (should_delete)
        {
            if (::unlink(valid_files[i].first.c_str()) == 0)
            {
                ++result.snapshots_deleted;
                result.bytes_freed += valid_files[i].second.st_size;
                std::cout << "[Snapshot] Deleted old snapshot: " << valid_files[i].first
                          << " (size=" << valid_files[i].second.st_size << ")" << std::endl;
            }
            else
            {
                std::cerr << "[Snapshot] Failed to delete: " << valid_files[i].first
                          << " error: " << strerror(errno) << std::endl;
            }
        }
    }

    result.snapshots_kept = total_valid - result.snapshots_deleted;
    return result;
}

void SnapshotManager::StartPeriodicSnapshot(const CppTrader::Matching::MarketManager& market, SnapshotCallback get_wal_sequence)
{
    StopPeriodicSnapshot();

    _periodic_market = &market;
    _get_wal_sequence = std::move(get_wal_sequence);
    _periodic_running = true;

    _periodic_thread = std::thread([this]() {
        std::cout << "[Snapshot] Periodic snapshot thread started (interval="
                  << _config.SnapshotIntervalSec << "s)" << std::endl;

        while (_periodic_running)
        {
            std::unique_lock<std::mutex> lock(_mutex);
            bool stopped = _periodic_cv.wait_for(
                lock,
                std::chrono::seconds(_config.SnapshotIntervalSec),
                [this] { return !_periodic_running; });

            if (stopped || !_periodic_running)
                break;

            lock.unlock();

            if (_periodic_market && _get_wal_sequence)
            {
                uint64_t wal_seq = _get_wal_sequence();
                bool ok = TakeSnapshot(*_periodic_market, wal_seq);
                if (ok)
                {
                    std::cout << "[Snapshot] Periodic snapshot taken (wal_sequence="
                              << wal_seq << ")" << std::endl;
                }
                else
                {
                    std::cerr << "[Snapshot] Periodic snapshot failed" << std::endl;
                }

                CleanupResult cleanup = CleanupOldSnapshots();
                if (cleanup.snapshots_deleted > 0)
                {
                    std::cout << "[Snapshot] Cleanup: deleted=" << cleanup.snapshots_deleted
                              << " kept=" << cleanup.snapshots_kept
                              << " freed=" << cleanup.bytes_freed << " bytes" << std::endl;
                }
            }
        }

        std::cout << "[Snapshot] Periodic snapshot thread stopped" << std::endl;
    });
}

void SnapshotManager::StopPeriodicSnapshot()
{
    _periodic_running = false;
    _periodic_cv.notify_all();

    if (_periodic_thread.joinable())
    {
        _periodic_thread.join();
    }

    _periodic_market = nullptr;
    _get_wal_sequence = nullptr;
}

} // namespace Snapshot
} // namespace CppTrader
