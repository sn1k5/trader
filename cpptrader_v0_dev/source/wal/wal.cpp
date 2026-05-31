#include "trader/wal/wal.h"
#include "trader/matching/market_manager.h"

#include <cstddef>
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

namespace CppTrader {
namespace WAL {

WALWriter::WALWriter(const WALConfig& config)
    : _config(config), _currentLSN(0), _fd(-1), _currentFileSize(0),
      _running(false), _bufferPos(0), _rotated(false), _flushCount(0), _entriesSinceSync(0)
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
        {
            std::lock_guard<std::mutex> lock(_mutex);
            FlushBuffer();
        }
        Sync();
        ::close(_fd);
        _fd = -1;
    }
}

uint64_t WALWriter::WriteNewOrder(const Matching::Order& order)
{
    WALEntry entry{};
    entry.Timestamp = std::chrono::duration_cast<std::chrono::microseconds>(
        std::chrono::system_clock::now().time_since_epoch()).count();
    entry.Operation = OperationType::NEW_ORDER;

    size_t order_size = sizeof(Matching::Order);
    if (order_size <= sizeof(entry.Data))
    {
        std::memcpy(entry.Data, &order, order_size);
    }

    std::lock_guard<std::mutex> lock(_mutex);
    entry.LSN = ++_currentLSN;
    entry.CRC32 = CalculateCRC32(entry);
    std::memcpy(_buffer.data() + _bufferPos, &entry, sizeof(WALEntry));
    _bufferPos += sizeof(WALEntry);
    if (_bufferPos + sizeof(WALEntry) > _config.BufferSize)
    {
        FlushBuffer();
    }
    ++_entriesSinceSync;
    _cv.notify_one();
    return entry.LSN;
}

uint64_t WALWriter::WriteCancelOrder(uint64_t orderId, uint32_t symbolId)
{
    WALEntry entry{};
    entry.Timestamp = std::chrono::duration_cast<std::chrono::microseconds>(
        std::chrono::system_clock::now().time_since_epoch()).count();
    entry.Operation = OperationType::CANCEL_ORDER;

    CancelOrderData cancel_data{};
    cancel_data.OrderId = orderId;
    cancel_data.SymbolId = symbolId;
    std::memcpy(entry.Data, &cancel_data, sizeof(CancelOrderData));

    std::lock_guard<std::mutex> lock(_mutex);
    entry.LSN = ++_currentLSN;
    entry.CRC32 = CalculateCRC32(entry);
    std::memcpy(_buffer.data() + _bufferPos, &entry, sizeof(WALEntry));
    _bufferPos += sizeof(WALEntry);
    if (_bufferPos + sizeof(WALEntry) > _config.BufferSize)
    {
        FlushBuffer();
    }
    ++_entriesSinceSync;
    _cv.notify_one();
    return entry.LSN;
}

uint64_t WALWriter::WriteTrade(const TradeData& trade)
{
    WALEntry entry{};
    entry.Timestamp = std::chrono::duration_cast<std::chrono::microseconds>(
        std::chrono::system_clock::now().time_since_epoch()).count();
    entry.Operation = OperationType::TRADE;

    std::memcpy(entry.Data, &trade, sizeof(TradeData));

    std::lock_guard<std::mutex> lock(_mutex);
    entry.LSN = ++_currentLSN;
    entry.CRC32 = CalculateCRC32(entry);
    std::memcpy(_buffer.data() + _bufferPos, &entry, sizeof(WALEntry));
    _bufferPos += sizeof(WALEntry);
    if (_bufferPos + sizeof(WALEntry) > _config.BufferSize)
    {
        FlushBuffer();
    }
    ++_entriesSinceSync;
    _cv.notify_one();
    return entry.LSN;
}

void WALWriter::WriteEntry(const WALEntry& entry)
{
}

void WALWriter::FlushBuffer()
{
    if (_bufferPos > 0 && _fd >= 0)
    {
        ssize_t written = ::write(_fd, _buffer.data(), _bufferPos);
        if (written > 0)
        {
            _currentFileSize += written;
            if (_currentFileSize >= _config.MaxFileSize)
            {
                RotateFile();
            }
        }
        else
        {
            std::cerr << "[WAL] FlushBuffer write error: " << strerror(errno) << std::endl;
        }
        _bufferPos = 0;
    }
}

void WALWriter::FlushQueue()
{
}

void WALWriter::WorkerThread()
{
    while (_running)
    {
        WalRotateCallback cb;
        {
            std::unique_lock<std::mutex> lock(_mutex);
            _cv.wait_for(lock, FLUSH_TIMEOUT, [this] { return _bufferPos > 0 || !_running; });

            FlushBuffer();
            ++_flushCount;

            switch (_config.SyncMode)
            {
            case SyncStrategy::ON_WRITE:
                Sync();
                break;
            case SyncStrategy::PERIODIC:
                if (_flushCount % FSYNC_INTERVAL == 0)
                {
                    Sync();
                }
                break;
            case SyncStrategy::BATCH:
                if (_entriesSinceSync >= _config.SyncBatchSize)
                {
                    Sync();
                    _entriesSinceSync = 0;
                }
                break;
            }

            cb = _rotated ? _rotate_callback : nullptr;
            _rotated = false;
        }

        if (cb)
        {
            cb();
        }
    }

    std::lock_guard<std::mutex> lock(_mutex);
    FlushBuffer();
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

    _rotated = true;

    return true;
}

void WALWriter::SetRotateCallback(WalRotateCallback callback)
{
    _rotate_callback = std::move(callback);
}

WALWriter::CleanupResult WALWriter::CleanupOldFiles(uint64_t safe_wal_sequence)
{
    CleanupResult result{};
    result.files_deleted = 0;
    result.files_kept = 0;
    result.bytes_freed = 0;

    DIR* dir = opendir(_config.LogDirectory.c_str());
    if (!dir)
        return result;

    struct FileInfo
    {
        std::string path;
        std::string name;
        time_t mtime;
        off_t size;
        uint64_t max_lsn;
    };

    std::vector<FileInfo> file_infos;

    struct dirent* entry;
    while ((entry = readdir(dir)) != nullptr)
    {
        std::string name = entry->d_name;
        if (name.size() < 8 || name.compare(0, 4, "wal_") != 0 || name.compare(name.size() - 4, 4, ".log") != 0)
            continue;

        std::string path = _config.LogDirectory + "/" + name;

        struct stat st;
        if (stat(path.c_str(), &st) != 0)
            continue;

        uint64_t file_max_lsn = 0;
        int fd = ::open(path.c_str(), O_RDONLY);
        if (fd >= 0)
        {
            off_t file_size = lseek(fd, 0, SEEK_END);
            lseek(fd, 0, SEEK_SET);

            if (file_size >= static_cast<off_t>(sizeof(WALEntry)))
            {
                WALEntry first_entry;
                if (::read(fd, &first_entry, sizeof(WALEntry)) == sizeof(WALEntry))
                {
                    file_max_lsn = first_entry.LSN;
                }

                size_t entry_count = file_size / sizeof(WALEntry);
                if (entry_count > 1)
                {
                    lseek(fd, (entry_count - 1) * sizeof(WALEntry), SEEK_SET);
                    WALEntry last_entry;
                    if (::read(fd, &last_entry, sizeof(WALEntry)) == sizeof(WALEntry))
                    {
                        file_max_lsn = last_entry.LSN;
                    }
                }
            }
            ::close(fd);
        }

        file_infos.push_back({path, name, st.st_mtime, st.st_size, file_max_lsn});
    }

    closedir(dir);

    std::sort(file_infos.begin(), file_infos.end(),
        [](const FileInfo& a, const FileInfo& b) { return a.name < b.name; });

    if (file_infos.empty())
        return result;

    time_t now = std::time(nullptr);

    std::string current_file_name;
    {
        std::lock_guard<std::mutex> lock(_mutex);
        current_file_name = _currentFileName;
    }

    size_t deletable_count = file_infos.size();

    for (size_t i = 0; i < file_infos.size(); ++i)
    {
        if (file_infos[i].path == current_file_name)
        {
            deletable_count = i;
            break;
        }
    }

    size_t min_keep = std::min(_config.MinReserveCount, deletable_count);
    size_t safe_end = deletable_count;

    if (safe_wal_sequence > 0)
    {
        for (size_t i = 0; i < deletable_count; ++i)
        {
            if (file_infos[i].max_lsn >= safe_wal_sequence)
            {
                safe_end = i;
                break;
            }
        }
    }

    if (safe_end <= min_keep)
    {
        result.files_kept = file_infos.size();
        return result;
    }

    size_t delete_end = safe_end - min_keep;

    if (_config.MaxFileCount > 0 && delete_end > _config.MaxFileCount)
    {
        delete_end = _config.MaxFileCount;
    }

    for (size_t i = 0; i < delete_end; ++i)
    {
        bool should_delete = false;

        if (_config.MaxFileAgeSec > 0)
        {
            if (static_cast<uint64_t>(now - file_infos[i].mtime) > _config.MaxFileAgeSec)
            {
                should_delete = true;
            }
        }

        if (safe_wal_sequence > 0 && file_infos[i].max_lsn < safe_wal_sequence)
        {
            should_delete = true;
        }

        if (_config.MaxFileCount > 0 && i < deletable_count - _config.MaxFileCount)
        {
            should_delete = true;
        }

        if (should_delete)
        {
            if (::unlink(file_infos[i].path.c_str()) == 0)
            {
                ++result.files_deleted;
                result.bytes_freed += file_infos[i].size;
                std::cout << "[WAL] Deleted old file: " << file_infos[i].name
                          << " (size=" << file_infos[i].size << ")" << std::endl;
            }
            else
            {
                std::cerr << "[WAL] Failed to delete: " << file_infos[i].path
                          << " error: " << strerror(errno) << std::endl;
            }
        }
    }

    result.files_kept = file_infos.size() - result.files_deleted;
    return result;
}

void WALWriter::Sync()
{
    if (_fd >= 0)
    {
        ::fsync(_fd);
    }
}

uint32_t WALWriter::CalculateCRC32(const WALEntry& entry)
{
    struct CRC32Table
    {
        uint32_t data[256];
        CRC32Table()
        {
            for (uint32_t i = 0; i < 256; ++i)
            {
                uint32_t crc = i;
                for (int j = 0; j < 8; ++j)
                {
                    crc = (crc & 1) ? ((crc >> 1) ^ 0xEDB88320u) : (crc >> 1);
                }
                data[i] = crc;
            }
        }
    };

    static CRC32Table table;

    uint32_t crc = 0xFFFFFFFF;
    const uint8_t* bytes = reinterpret_cast<const uint8_t*>(&entry);

    size_t crc_offset = offsetof(WALEntry, CRC32);
    for (size_t i = 0; i < crc_offset; ++i)
    {
        crc = table.data[(crc ^ bytes[i]) & 0xFF] ^ (crc >> 8);
    }
    for (size_t i = crc_offset + sizeof(uint32_t); i < sizeof(WALEntry); ++i)
    {
        crc = table.data[(crc ^ bytes[i]) & 0xFF] ^ (crc >> 8);
    }

    return crc ^ 0xFFFFFFFF;
}

WALReader::WALReader(const std::string& log_directory)
    : _logDirectory(log_directory)
{
}

std::vector<std::string> WALReader::FindWALFiles() const
{
    std::vector<std::string> files;

    DIR* dir = opendir(_logDirectory.c_str());
    if (!dir)
        return files;

    struct dirent* entry;
    while ((entry = readdir(dir)) != nullptr)
    {
        std::string name = entry->d_name;
        if (name.size() >= 8 && name.compare(0, 4, "wal_") == 0 && name.compare(name.size() - 4, 4, ".log") == 0)
        {
            files.push_back(_logDirectory + "/" + name);
        }
    }

    closedir(dir);

    std::sort(files.begin(), files.end());
    return files;
}

WALReplayResult WALReader::Replay(CppTrader::Matching::MarketManager& market, uint64_t start_lsn)
{
    WALReplayResult result{};
    result.entries_replayed = 0;
    result.entries_skipped = 0;
    result.last_lsn = start_lsn;

    std::vector<std::string> files = FindWALFiles();

    bool was_matching = market.IsMatchingEnabled();
    market.DisableMatching();

    for (const auto& filepath : files)
    {
        int fd = ::open(filepath.c_str(), O_RDONLY);
        if (fd < 0)
        {
            std::cerr << "[WALReader] Failed to open: " << filepath
                      << " error: " << strerror(errno) << std::endl;
            continue;
        }

        off_t file_size = lseek(fd, 0, SEEK_END);
        lseek(fd, 0, SEEK_SET);

        if (file_size < static_cast<off_t>(sizeof(WALEntry)))
        {
            ::close(fd);
            continue;
        }

        size_t entry_count = file_size / sizeof(WALEntry);
        size_t bytes_to_read = entry_count * sizeof(WALEntry);
        std::vector<uint8_t> buffer(bytes_to_read);

        ssize_t bytes_read = ::read(fd, buffer.data(), bytes_to_read);
        ::close(fd);

        if (bytes_read != static_cast<ssize_t>(bytes_to_read))
        {
            std::cerr << "[WALReader] Read error: " << filepath << std::endl;
            continue;
        }

        for (size_t i = 0; i < entry_count; ++i)
        {
            WALEntry entry;
            std::memcpy(&entry, buffer.data() + i * sizeof(WALEntry), sizeof(WALEntry));

            uint32_t stored_crc = entry.CRC32;
            entry.CRC32 = 0;
            uint32_t computed_crc = WALWriter::CalculateCRC32(entry);
            entry.CRC32 = stored_crc;

            if (stored_crc != computed_crc)
            {
                std::cerr << "[WALReader] CRC32 mismatch at LSN " << entry.LSN
                          << " stored=0x" << std::hex << stored_crc
                          << " computed=0x" << computed_crc << std::dec << std::endl;
                ++result.entries_skipped;
                continue;
            }

            if (entry.LSN <= start_lsn)
            {
                ++result.entries_skipped;
                continue;
            }

            switch (entry.Operation)
            {
            case OperationType::NEW_ORDER:
            {
                Matching::Order order;
                std::memcpy(&order, entry.Data, sizeof(Matching::Order));
                market.AddOrder(order);
                ++result.entries_replayed;
                break;
            }
            case OperationType::CANCEL_ORDER:
            {
                CancelOrderData cancel_data;
                std::memcpy(&cancel_data, entry.Data, sizeof(CancelOrderData));
                market.DeleteOrder(cancel_data.OrderId);
                ++result.entries_replayed;
                break;
            }
            case OperationType::TRADE:
                ++result.entries_skipped;
                break;
            default:
                ++result.entries_skipped;
                break;
            }

            if (entry.LSN > result.last_lsn)
            {
                result.last_lsn = entry.LSN;
            }
        }
    }

    if (was_matching)
    {
        market.EnableMatching();
    }

    return result;
}

} // namespace WAL
} // namespace CppTrader