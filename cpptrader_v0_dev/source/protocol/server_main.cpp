/*!
    \file server_main.cpp
    \brief Protocol server entry point
    \author CppTrader Team
    \date 18.05.2026
    \copyright MIT License
*/

#include "trader/protocol/server.h"
#include "trader/protocol/tcp_backend.h"
#include "trader/protocol/request_handler.h"
#include "trader/matching/market_manager.h"
#include "trader/wal/wal.h"

#include <asio.hpp>

#include <cstdint>
#include <cstdlib>
#include <iostream>
#include <memory>
#include <string>
#include <thread>
#include <csignal>

using namespace CppTrader;
using namespace CppTrader::Protocol;

// 全局 WAL 写入器（用于信号处理中的优雅关闭）
static std::shared_ptr<WAL::WALWriter> g_wal_writer;
static volatile std::sig_atomic_t g_running = 1;

void SignalHandler(int signum)
{
    std::cout << "\nReceived signal " << signum << ", shutting down..." << std::endl;
    g_running = 0;
}

void PrintUsage(const char* program)
{
    std::cout << "Usage: " << program << " [options]" << std::endl;
    std::cout << "Options:" << std::endl;
    std::cout << "  --port <port>           TCP listen port (default: 8080)" << std::endl;
    std::cout << "  --wal-dir <dir>         WAL log directory (default: ./wal)" << std::endl;
    std::cout << "  --no-wal-sync           Disable fsync on WAL write (faster, less safe)" << std::endl;
    std::cout << "  --no-wal                Disable WAL entirely" << std::endl;
    std::cout << "  --auth                  Enable authentication requirement" << std::endl;
    std::cout << "  --api-key <id> <secret> Register an API key pair (can be repeated)" << std::endl;
    std::cout << "  --help                  Show this help message" << std::endl;
}

int main(int argc, char** argv)
{
    uint16_t port = 8080;
    std::string wal_dir = "./wal";
    bool wal_sync = true;
    bool wal_enabled = true;
    bool auth_enabled = false;

    // Parse command line arguments
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
        else if (arg == "--auth")
        {
            auth_enabled = true;
        }
        else if (arg == "--api-key" && i + 2 < argc)
        {
            i += 2;
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

    // Register signal handlers for graceful shutdown
    std::signal(SIGINT, SignalHandler);
    std::signal(SIGTERM, SignalHandler);

    // Create asio io_context
    asio::io_context io_context;

    // Create network backend (TCP only)
    std::unique_ptr<INetworkBackend> backend;
    std::cout << "Using TCP backend on port " << port << std::endl;
    backend = std::make_unique<TcpBackend>(io_context, port);

    // Create market manager
    Matching::MarketManager market;

    // Create and initialize WAL writer
    if (wal_enabled)
    {
        WAL::WALConfig wal_config;
        wal_config.LogDirectory = wal_dir;
        wal_config.SyncOnWrite = wal_sync;
        g_wal_writer = std::make_shared<WAL::WALWriter>(wal_config);
        g_wal_writer->Initialize();
        std::cout << "WAL enabled: dir=" << wal_dir
                  << " sync=" << (wal_sync ? "on" : "off") << std::endl;
    }
    else
    {
        std::cout << "WAL disabled" << std::endl;
    }

    // Create protocol server
    auto server = std::make_unique<ProtocolServer>(std::move(backend), market);

    // Register API keys from command line
    for (int i = 1; i < argc; ++i)
    {
        std::string arg(argv[i]);
        if (arg == "--api-key" && i + 2 < argc)
        {
            std::string key_id = argv[++i];
            std::string key_secret = argv[++i];
            server->RegisterApiKey(key_id, key_secret);
            std::cout << "Registered API key: " << key_id << std::endl;
        }
    }

    // Enable authentication if requested
    if (auth_enabled)
    {
        server->SetAuthEnabled(true);
        std::cout << "Authentication enabled" << std::endl;
    }

    // Create request handler with optional WAL support
    RequestHandler request_handler(*server, market, g_wal_writer);
    request_handler.RegisterHandlers();

    // Initialize server
    if (!server->init())
    {
        std::cerr << "Failed to initialize protocol server" << std::endl;
        if (g_wal_writer)
        {
            g_wal_writer->Shutdown();
        }
        return 1;
    }

    std::cout << "Protocol server started. Press Ctrl+C to stop." << std::endl;

    // Main event loop
    while (g_running)
    {
        server->poll();

        // Run asio handlers for timer-based operations
        io_context.poll();

        // Small yield to prevent busy-waiting
        std::this_thread::sleep_for(std::chrono::milliseconds(1));
    }

    // Graceful shutdown
    std::cout << "Shutting down..." << std::endl;
    if (g_wal_writer)
    {
        g_wal_writer->Shutdown();
        g_wal_writer.reset();
    }

    std::cout << "Server stopped." << std::endl;
    return 0;
}