#include "trader/protocol/io_thread.h"
#include "trader/protocol/business_thread.h"
#include "trader/protocol/server.h"
#include "trader/protocol/request_handler.h"
#include "trader/matching/market_manager.h"
#include "trader/wal/wal.h"
#include "trader/snapshot/snapshot.h"
#include "trader/timer/timer.h"

#include <asio.hpp>

#include <cstdint>
#include <cstdlib>
#include <iostream>
#include <memory>
#include <string>
#include <chrono>

using namespace CppTrader;
using namespace CppTrader::Protocol;

void PrintUsage(const char* program)
{
    std::cout << "Usage: " << program << " [options]" << std::endl;
    std::cout << "Options:" << std::endl;
    std::cout << "  --port <port>           TCP listen port (default: 8080)" << std::endl;
    std::cout << "  --wal-dir <dir>         WAL log directory (default: ./wal)" << std::endl;
    std::cout << "  --no-wal-sync           Disable fsync on WAL write (faster, less safe)" << std::endl;
    std::cout << "  --no-wal                Disable WAL entirely" << std::endl;
    std::cout << "  --wal-max-files <n>     Max WAL files to keep (0=unlimited, default: 0)" << std::endl;
    std::cout << "  --wal-max-age <s>       Max WAL file age in seconds (0=unlimited, default: 0)" << std::endl;
    std::cout << "  --wal-min-reserve <n>   Min WAL files to reserve (default: 2)" << std::endl;
    std::cout << "  --snapshot-dir <dir>    Snapshot directory (default: ./snapshots)" << std::endl;
    std::cout << "  --snapshot-interval <s> Snapshot interval in seconds (default: 300)" << std::endl;
    std::cout << "  --snapshot-max <n>      Max snapshots to keep (0=unlimited, default: 0)" << std::endl;
    std::cout << "  --snapshot-min-reserve <n> Min snapshots to reserve (default: 2)" << std::endl;
    std::cout << "  --snapshot-max-age <s>  Max snapshot age in seconds (0=unlimited, default: 0)" << std::endl;
    std::cout << "  --no-snapshot           Disable snapshot functionality" << std::endl;
    std::cout << "  --auth                  Enable authentication requirement" << std::endl;
    std::cout << "  --api-key <id> <secret> [account_id] [role]  Register an API key pair (can be repeated)" << std::endl;
    std::cout << "  --help                  Show this help message" << std::endl;
}

int main(int argc, char** argv)
{
    uint16_t port = 8080;
    std::string wal_dir = "./wal";
    bool wal_sync = false;
    bool wal_enabled = true;
    size_t wal_max_files = 0;
    uint64_t wal_max_age = 0;
    size_t wal_min_reserve = 2;
    bool auth_enabled = false;
    std::string snapshot_dir = "./snapshots";
    uint64_t snapshot_interval = 300;
    size_t snapshot_max = 0;
    size_t snapshot_min_reserve = 2;
    uint64_t snapshot_max_age = 0;
    bool snapshot_enabled = true;

    for (int i = 1; i < argc; ++i)
    {
        std::string arg(argv[i]);

        if (arg == "--port" && i + 1 < argc)
        {
            port = static_cast<uint16_t>(std::atoi(argv[++i]));
        }
        else if (arg == "--wal-dir" && i + 1 < argc)
        {
            wal_dir = argv[++i];
        }
        else if (arg == "--no-wal-sync")
        {
            wal_sync = false;
        }
        else if (arg == "--no-wal")
        {
            wal_enabled = false;
        }
        else if (arg == "--wal-max-files" && i + 1 < argc)
        {
            wal_max_files = static_cast<size_t>(std::atol(argv[++i]));
        }
        else if (arg == "--wal-max-age" && i + 1 < argc)
        {
            wal_max_age = static_cast<uint64_t>(std::atol(argv[++i]));
        }
        else if (arg == "--wal-min-reserve" && i + 1 < argc)
        {
            wal_min_reserve = static_cast<size_t>(std::atol(argv[++i]));
        }
        else if (arg == "--snapshot-dir" && i + 1 < argc)
        {
            snapshot_dir = argv[++i];
        }
        else if (arg == "--snapshot-interval" && i + 1 < argc)
        {
            snapshot_interval = static_cast<uint64_t>(std::atol(argv[++i]));
        }
        else if (arg == "--snapshot-max" && i + 1 < argc)
        {
            snapshot_max = static_cast<size_t>(std::atol(argv[++i]));
        }
        else if (arg == "--snapshot-min-reserve" && i + 1 < argc)
        {
            snapshot_min_reserve = static_cast<size_t>(std::atol(argv[++i]));
        }
        else if (arg == "--snapshot-max-age" && i + 1 < argc)
        {
            snapshot_max_age = static_cast<uint64_t>(std::atol(argv[++i]));
        }
        else if (arg == "--no-snapshot")
        {
            snapshot_enabled = false;
        }
        else if (arg == "--auth")
        {
            auth_enabled = true;
        }
        else if (arg == "--api-key" && i + 2 < argc)
        {
            i += 2;
            if (i + 1 < argc && std::string(argv[i + 1])[0] != '-')
                ++i;
            if (i + 1 < argc && std::string(argv[i + 1])[0] != '-')
                ++i;
        }
        else if (arg == "--help" || arg == "-h")
        {
            PrintUsage(argv[0]);
            return 0;
        }
        else
        {
            std::cerr << "Unknown option: " << arg << std::endl;
            PrintUsage(argv[0]);
            return 1;
        }
    }

    Matching::MarketManager market;

    std::shared_ptr<WAL::WALWriter> wal_writer;
    std::shared_ptr<Snapshot::SnapshotManager> snapshot_manager;

    if (snapshot_enabled)
    {
        Snapshot::SnapshotConfig snapshot_config;
        snapshot_config.SnapshotDirectory = snapshot_dir;
        snapshot_config.SnapshotIntervalSec = snapshot_interval;
        snapshot_config.MaxSnapshotCount = snapshot_max;
        snapshot_config.MinReserveCount = snapshot_min_reserve;
        snapshot_config.MaxSnapshotAgeSec = snapshot_max_age;
        snapshot_manager = std::make_shared<Snapshot::SnapshotManager>(snapshot_config);
        snapshot_manager->Initialize();
        std::cout << "Snapshot enabled: dir=" << snapshot_dir
                  << " interval=" << snapshot_interval << "s"
                  << " max=" << snapshot_max
                  << " min_reserve=" << snapshot_min_reserve
                  << " max_age=" << snapshot_max_age << "s" << std::endl;

        uint64_t wal_sequence = 0;
        if (snapshot_manager->LoadSnapshot(market, wal_sequence))
        {
            std::cout << "Loaded snapshot: wal_sequence=" << wal_sequence
                      << " symbols=" << market.symbols().size()
                      << " orders=" << market.orders().size() << std::endl;

            if (wal_enabled)
            {
                WAL::WALReader wal_reader(wal_dir);
                auto replay_result = wal_reader.Replay(market, wal_sequence);
                std::cout << "WAL replay: entries_replayed=" << replay_result.entries_replayed
                          << " entries_skipped=" << replay_result.entries_skipped
                          << " last_lsn=" << replay_result.last_lsn << std::endl;
            }
        }
        else
        {
            std::cout << "No valid snapshot found, starting fresh" << std::endl;

            if (wal_enabled)
            {
                WAL::WALReader wal_reader(wal_dir);
                auto replay_result = wal_reader.Replay(market, 0);
                std::cout << "WAL full replay: entries_replayed=" << replay_result.entries_replayed
                          << " entries_skipped=" << replay_result.entries_skipped
                          << " last_lsn=" << replay_result.last_lsn << std::endl;
            }
        }
    }
    else
    {
        std::cout << "Snapshot disabled" << std::endl;

        if (wal_enabled)
        {
            WAL::WALReader wal_reader(wal_dir);
            auto replay_result = wal_reader.Replay(market, 0);
            if (replay_result.entries_replayed > 0)
            {
                std::cout << "WAL full replay: entries_replayed=" << replay_result.entries_replayed
                          << " entries_skipped=" << replay_result.entries_skipped
                          << " last_lsn=" << replay_result.last_lsn << std::endl;
            }
        }
    }

    if (wal_enabled)
    {
        WAL::WALConfig wal_config;
        wal_config.LogDirectory = wal_dir;
        wal_config.SyncOnWrite = wal_sync;
        wal_config.MaxFileCount = wal_max_files;
        wal_config.MaxFileAgeSec = wal_max_age;
        wal_config.MinReserveCount = wal_min_reserve;
        wal_writer = std::make_shared<WAL::WALWriter>(wal_config);
        wal_writer->Initialize();
        std::cout << "WAL enabled: dir=" << wal_dir
                  << " sync=" << (wal_sync ? "on" : "off")
                  << " max_files=" << wal_max_files
                  << " max_age=" << wal_max_age << "s"
                  << " min_reserve=" << wal_min_reserve << std::endl;

        if (snapshot_manager)
        {
            wal_writer->SetRotateCallback([&market, &wal_writer, &snapshot_manager]() {
                if (snapshot_manager && wal_writer)
                {
                    uint64_t lsn = wal_writer->CurrentLSN();
                    snapshot_manager->TakeSnapshot(market, lsn);
                    std::cout << "[Snapshot] Triggered by WAL rotation (wal_sequence=" << lsn << ")" << std::endl;

                    auto cleanup = snapshot_manager->CleanupOldSnapshots();
                    if (cleanup.snapshots_deleted > 0)
                    {
                        std::cout << "[Snapshot] Cleanup: deleted=" << cleanup.snapshots_deleted
                                  << " kept=" << cleanup.snapshots_kept
                                  << " freed=" << cleanup.bytes_freed << " bytes" << std::endl;
                    }

                    auto wal_cleanup = wal_writer->CleanupOldFiles(lsn);
                    if (wal_cleanup.files_deleted > 0)
                    {
                        std::cout << "[WAL] Cleanup: deleted=" << wal_cleanup.files_deleted
                                  << " kept=" << wal_cleanup.files_kept
                                  << " freed=" << wal_cleanup.bytes_freed << " bytes" << std::endl;
                    }
                }
            });
        }
    }
    else
    {
        std::cout << "WAL disabled" << std::endl;
    }

    if (snapshot_enabled && snapshot_manager && wal_writer)
    {
        snapshot_manager->StartPeriodicSnapshot(market, [&wal_writer]() -> uint64_t {
            return wal_writer->CurrentLSN();
        });
    }

    SPSCQueue<PendingFrame> to_business;
    SPSCQueue<OutboundFrame> to_io;

    auto io_thread = std::make_unique<IOThread>(port, to_business, to_io);

    std::unique_ptr<INetworkBackend> null_backend;
    auto business_thread = std::make_unique<BusinessThread>(std::move(null_backend), market, to_business, to_io);
    business_thread->SetWALWriter(wal_writer);
    business_thread->SetSnapshotManager(snapshot_manager);
    business_thread->SetIONotify(std::bind(&IOThread::NotifyIO, io_thread.get()));

    if (auth_enabled)
    {
        business_thread->Server().SetAuthEnabled(true);
        std::cout << "Authentication enabled" << std::endl;
    }

    for (int i = 1; i < argc; ++i)
    {
        std::string arg(argv[i]);
        if (arg == "--api-key" && i + 2 < argc)
        {
            std::string key_id = argv[++i];
            std::string key_secret = argv[++i];
            uint64_t account_id = 0;
            uint8_t role = 1;
            if (i + 1 < argc && std::string(argv[i + 1])[0] != '-')
            {
                account_id = static_cast<uint64_t>(std::strtoull(argv[++i], nullptr, 10));
            }
            if (i + 1 < argc && std::string(argv[i + 1])[0] != '-')
            {
                role = static_cast<uint8_t>(std::atoi(argv[++i]));
            }
            business_thread->Server().RegisterApiKey(key_id, key_secret, account_id, role);
            std::cout << "Registered API key: " << key_id
                      << " account_id=" << account_id
                      << " role=" << static_cast<int>(role) << std::endl;
        }
    }

    if (!io_thread->Start())
    {
        std::cerr << "Failed to start IO thread" << std::endl;
        if (snapshot_manager) snapshot_manager->Shutdown();
        if (wal_writer) wal_writer->Shutdown();
        return 1;
    }

    if (!business_thread->Start())
    {
        std::cerr << "Failed to start business thread" << std::endl;
        io_thread->Stop();
        io_thread->Join();
        if (snapshot_manager) snapshot_manager->Shutdown();
        if (wal_writer) wal_writer->Shutdown();
        return 1;
    }

    std::cout << "Protocol server started (IO+Business dual-thread). Press Ctrl+C to stop." << std::endl;

    asio::io_context signal_io;
    asio::signal_set signals(signal_io, SIGINT, SIGTERM);
    signals.async_wait([&](const asio::error_code&, int signum) {
        std::cout << "\nReceived signal " << signum << ", shutting down..." << std::endl;

        io_thread->Stop();
        io_thread->Join();

        business_thread->Stop();
        business_thread->Join();

        if (snapshot_manager)
        {
            snapshot_manager->StopPeriodicSnapshot();

            if (wal_writer)
            {
                uint64_t lsn = wal_writer->CurrentLSN();
                snapshot_manager->TakeSnapshot(market, lsn);
                std::cout << "[Snapshot] Final snapshot taken (wal_sequence=" << lsn << ")" << std::endl;

                auto cleanup = snapshot_manager->CleanupOldSnapshots();
                if (cleanup.snapshots_deleted > 0)
                {
                    std::cout << "[Snapshot] Final cleanup: deleted=" << cleanup.snapshots_deleted
                              << " kept=" << cleanup.snapshots_kept
                              << " freed=" << cleanup.bytes_freed << " bytes" << std::endl;
                }
            }

            snapshot_manager->Shutdown();
            snapshot_manager.reset();
        }

        if (wal_writer)
        {
            if (snapshot_manager)
            {
                auto wal_cleanup = wal_writer->CleanupOldFiles(wal_writer->CurrentLSN());
                if (wal_cleanup.files_deleted > 0)
                {
                    std::cout << "[WAL] Final cleanup: deleted=" << wal_cleanup.files_deleted
                              << " kept=" << wal_cleanup.files_kept
                              << " freed=" << wal_cleanup.bytes_freed << " bytes" << std::endl;
                }
            }

            wal_writer->Shutdown();
            wal_writer.reset();
        }

        signal_io.stop();
    });
    signal_io.run();

    std::cout << "Server stopped." << std::endl;
    return 0;
}
