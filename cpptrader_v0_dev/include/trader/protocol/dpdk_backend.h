/*!
    \file dpdk_backend.h
    \brief DPDK kernel-bypass network backend
    \author CppTrader Team
    \date 18.05.2026
    \copyright MIT License
*/

#ifndef CPPTRADER_PROTOCOL_DPDK_BACKEND_H
#define CPPTRADER_PROTOCOL_DPDK_BACKEND_H

#include "network_backend.h"
#include "frame_decoder.h"

#include <cstdint>
#include <cstddef>
#include <functional>
#include <memory>
#include <unordered_map>
#include <vector>

namespace CppTrader {
namespace Protocol {

//! DPDK connection context (userspace TCP)
struct DpdkConnection
{
    //! Connection identifier
    uint16_t Id;
    //! Remote IP (network byte order)
    uint32_t RemoteIp;
    //! Remote port (host byte order)
    uint16_t RemotePort;
    //! Local port (host byte order)
    uint16_t LocalPort;
    //! Sequence number
    uint32_t SeqNum;
    //! Acknowledgment number
    uint32_t AckNum;
    //! Connection established
    bool Connected;
    //! Frame decoder for this connection
    FrameDecoder Decoder;

    DpdkConnection(uint16_t id, uint32_t remote_ip, uint16_t remote_port, uint16_t local_port)
        : Id(id)
        , RemoteIp(remote_ip)
        , RemotePort(remote_port)
        , LocalPort(local_port)
        , SeqNum(0)
        , AckNum(0)
        , Connected(false)
    {}

    DpdkConnection(const DpdkConnection&) = delete;
    DpdkConnection(DpdkConnection&&) = delete;
    DpdkConnection& operator=(const DpdkConnection&) = delete;
    DpdkConnection& operator=(DpdkConnection&&) = delete;
};

//! DPDK network backend implementation
/*!
    This class implements INetworkBackend using DPDK for kernel-bypass networking.
    It provides:
    - DPDK EAL initialization
    - PMD poll-mode packet reception
    - Simplified userspace TCP stack (connection management, reliable transport)
    - Zero-copy send/receive where possible

    Note: This is a simplified implementation. Full DPDK integration requires
    linking with DPDK libraries and proper environment setup (hugepages, NIC bindings).
*/
class DpdkBackend : public INetworkBackend
{
public:
    DpdkBackend(uint16_t port_id = 0, uint16_t num_rx_queues = 1, uint16_t num_tx_queues = 1);
    ~DpdkBackend() override;

    DpdkBackend(const DpdkBackend&) = delete;
    DpdkBackend(DpdkBackend&&) = delete;
    DpdkBackend& operator=(const DpdkBackend&) = delete;
    DpdkBackend& operator=(DpdkBackend&&) = delete;

    //! Initialize DPDK EAL and PMD
    bool init() override;

    //! Poll for incoming packets and process TCP state machine
    void poll() override;

    //! Send data to a specific connection (zero-copy where possible)
    void send(uint16_t conn_id, const void* data, size_t len) override;

    //! Broadcast data to all connected clients
    void broadcast(const void* data, size_t len) override;

    void SetMessageHandler(const MessageHandler& handler) override { _message_handler = handler; }
    void SetConnectHandler(const ConnectHandler& handler) override { _connect_handler = handler; }
    void SetDisconnectHandler(const DisconnectHandler& handler) override { _disconnect_handler = handler; }
    void close(uint16_t conn_id) override;

    //! Get number of active connections
    size_t ConnectionCount() const noexcept { return _connections.size(); }

    //! Check if DPDK is available/compiled in
    static bool IsAvailable() noexcept;

private:
    uint16_t _port_id;
    uint16_t _num_rx_queues;
    uint16_t _num_tx_queues;
    uint16_t _next_conn_id;
    bool _initialized;

    std::unordered_map<uint16_t, std::shared_ptr<DpdkConnection>> _connections;
    MessageHandler _message_handler;
    ConnectHandler _connect_handler;
    DisconnectHandler _disconnect_handler;

    // Simplified TCP state machine
    void ProcessRxPackets();
    void ProcessTcpPacket(const uint8_t* data, size_t len);
    void HandleSyn(uint32_t remote_ip, uint16_t remote_port, uint16_t local_port, uint32_t seq_num);
    void HandleAck(uint32_t remote_ip, uint16_t remote_port, uint32_t ack_num);
    void HandleData(uint32_t remote_ip, uint16_t remote_port, const uint8_t* data, size_t len);
    void HandleFin(uint32_t remote_ip, uint16_t remote_port);
    void SendTcpPacket(uint32_t remote_ip, uint16_t remote_port, uint16_t flags, const uint8_t* data, size_t len);
    void CloseConnection(uint16_t conn_id);

    // Connection lookup by 4-tuple
    std::shared_ptr<DpdkConnection> FindConnection(uint32_t remote_ip, uint16_t remote_port, uint16_t local_port);
};

} // namespace Protocol
} // namespace CppTrader

#endif // CPPTRADER_PROTOCOL_DPDK_BACKEND_H
