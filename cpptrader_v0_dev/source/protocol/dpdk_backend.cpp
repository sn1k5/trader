/*!
    \file dpdk_backend.cpp
    \brief DPDK network backend implementation
    \author CppTrader Team
    \date 18.05.2026
    \copyright MIT License
*/

#include "trader/protocol/dpdk_backend.h"

#include <cstring>
#include <iostream>

namespace CppTrader {
namespace Protocol {

DpdkBackend::DpdkBackend(uint16_t port_id, uint16_t num_rx_queues, uint16_t num_tx_queues)
    : _port_id(port_id)
    , _num_rx_queues(num_rx_queues)
    , _num_tx_queues(num_tx_queues)
    , _next_conn_id(1)
    , _initialized(false)
{
}

DpdkBackend::~DpdkBackend()
{
    _connections.clear();
}

bool DpdkBackend::init()
{
#if defined(CPPTRADER_DPDK_ENABLED)
    // DPDK EAL initialization would go here:
    // - rte_eal_init(argc, argv)
    // - rte_eth_dev_configure(port_id, num_rx_queues, num_tx_queues, &port_conf)
    // - rte_eth_rx_queue_setup(...)
    // - rte_eth_tx_queue_setup(...)
    // - rte_eth_dev_start(port_id)
    // - rte_eth_promiscuous_enable(port_id)

    std::cout << "DPDK backend initialized on port " << _port_id << std::endl;
    _initialized = true;
    return true;
#else
    std::cerr << "DPDK backend not available: compiled without CPPTRADER_DPDK_ENABLED" << std::endl;
    return false;
#endif
}

void DpdkBackend::poll()
{
#if defined(CPPTRADER_DPDK_ENABLED)
    if (!_initialized)
        return;

    ProcessRxPackets();
#else
    // No-op when DPDK is not available
#endif
}

void DpdkBackend::send(uint16_t conn_id, const void* data, size_t len)
{
#if defined(CPPTRADER_DPDK_ENABLED)
    auto it = _connections.find(conn_id);
    if (it == _connections.end())
        return;

    auto& conn = it->second;
    if (!conn->Connected)
        return;

    // Zero-copy send: allocate mbuf from pool, copy data, enqueue to TX ring
    // In a full implementation, this would use rte_pktmbuf_alloc and rte_eth_tx_burst
    // For now, we simulate the send path
    SendTcpPacket(conn->RemoteIp, conn->RemotePort, 0x18, static_cast<const uint8_t*>(data), len); // PSH+ACK
#else
    (void)conn_id;
    (void)data;
    (void)len;
#endif
}

void DpdkBackend::broadcast(const void* data, size_t len)
{
#if defined(CPPTRADER_DPDK_ENABLED)
    std::vector<uint16_t> dead_connections;

    for (auto& [conn_id, conn] : _connections)
    {
        if (!conn->Connected)
            continue;

        // In full implementation: zero-copy broadcast using reference counting on mbufs
        SendTcpPacket(conn->RemoteIp, conn->RemotePort, 0x18, static_cast<const uint8_t*>(data), len);
    }

    for (auto conn_id : dead_connections)
    {
        CloseConnection(conn_id);
    }
#else
    (void)data;
    (void)len;
#endif
}

bool DpdkBackend::IsAvailable() noexcept
{
#if defined(CPPTRADER_DPDK_ENABLED)
    return true;
#else
    return false;
#endif
}

void DpdkBackend::ProcessRxPackets()
{
#if defined(CPPTRADER_DPDK_ENABLED)
    // In full implementation:
    // struct rte_mbuf* rx_pkts[BURST_SIZE];
    // uint16_t nb_rx = rte_eth_rx_burst(_port_id, 0, rx_pkts, BURST_SIZE);
    // for (uint16_t i = 0; i < nb_rx; ++i) {
    //     ProcessTcpPacket(rte_pktmbuf_mtod(rx_pkts[i], uint8_t*), rx_pkts[i]->data_len);
    //     rte_pktmbuf_free(rx_pkts[i]);
    // }
#endif
}

void DpdkBackend::ProcessTcpPacket(const uint8_t* data, size_t len)
{
#if defined(CPPTRADER_DPDK_ENABLED)
    if (len < 40) // Minimum Ethernet + IP + TCP header
        return;

    // Parse Ethernet header (14 bytes)
    // Parse IP header (20 bytes min)
    // Parse TCP header (20 bytes min)
    // Extract flags and payload

    // Simplified parsing for demonstration
    uint8_t ip_protocol = data[23];
    if (ip_protocol != 6) // TCP
        return;

    uint32_t remote_ip;
    std::memcpy(&remote_ip, data + 26, 4);

    uint16_t remote_port;
    std::memcpy(&remote_port, data + 34, 2);
    remote_port = (remote_port >> 8) | (remote_port << 8); // ntohs

    uint16_t local_port;
    std::memcpy(&local_port, data + 36, 2);
    local_port = (local_port >> 8) | (local_port << 8); // ntohs

    uint8_t tcp_flags = data[47];
    uint32_t seq_num;
    std::memcpy(&seq_num, data + 38, 4);

    size_t ip_header_len = (data[14] & 0x0F) * 4;
    size_t tcp_header_len = ((data[14 + ip_header_len + 12] >> 4) & 0x0F) * 4;
    size_t payload_offset = 14 + ip_header_len + tcp_header_len;
    size_t payload_len = len > payload_offset ? len - payload_offset : 0;

    if (tcp_flags & 0x02) // SYN
    {
        HandleSyn(remote_ip, remote_port, local_port, seq_num);
    }
    else if (tcp_flags & 0x01) // FIN
    {
        HandleFin(remote_ip, remote_port);
    }
    else if (payload_len > 0 && (tcp_flags & 0x10)) // ACK with data
    {
        HandleData(remote_ip, remote_port, data + payload_offset, payload_len);
    }
    else if (tcp_flags & 0x10) // ACK
    {
        HandleAck(remote_ip, remote_port, seq_num);
    }
#else
    (void)data;
    (void)len;
#endif
}

void DpdkBackend::HandleSyn(uint32_t remote_ip, uint16_t remote_port, uint16_t local_port, uint32_t seq_num)
{
#if defined(CPPTRADER_DPDK_ENABLED)
    auto conn = std::make_shared<DpdkConnection>(_next_conn_id++, remote_ip, remote_port, local_port);
    conn->SeqNum = seq_num + 1;
    conn->AckNum = seq_num + 1;
    conn->Connected = true;
    _connections[conn->Id] = conn;

    // Send SYN-ACK
    SendTcpPacket(remote_ip, remote_port, 0x12, nullptr, 0); // SYN+ACK

    if (_connect_handler)
    {
        _connect_handler(conn->Id);
    }
#else
    (void)remote_ip;
    (void)remote_port;
    (void)local_port;
    (void)seq_num;
#endif
}

void DpdkBackend::HandleAck(uint32_t remote_ip, uint16_t remote_port, uint32_t ack_num)
{
#if defined(CPPTRADER_DPDK_ENABLED)
    auto conn = FindConnection(remote_ip, remote_port, 0);
    if (!conn)
        return;

    conn->AckNum = ack_num;
#else
    (void)remote_ip;
    (void)remote_port;
    (void)ack_num;
#endif
}

void DpdkBackend::HandleData(uint32_t remote_ip, uint16_t remote_port, const uint8_t* data, size_t len)
{
#if defined(CPPTRADER_DPDK_ENABLED)
    auto conn = FindConnection(remote_ip, remote_port, 0);
    if (!conn || !conn->Connected)
        return;

    conn->Decoder.Feed(data, len);

    if (_message_handler)
    {
        while (true)
        {
            auto frame = conn->Decoder.TryDecode();
            if (!frame.has_value())
                break;

            _message_handler(conn->Id, frame->Header, frame->BodyBytes(), frame->Body.size());
        }
    }

    // Send ACK
    conn->SeqNum += static_cast<uint32_t>(len);
    SendTcpPacket(remote_ip, remote_port, 0x10, nullptr, 0);
#else
    (void)remote_ip;
    (void)remote_port;
    (void)data;
    (void)len;
#endif
}

void DpdkBackend::HandleFin(uint32_t remote_ip, uint16_t remote_port)
{
#if defined(CPPTRADER_DPDK_ENABLED)
    auto conn = FindConnection(remote_ip, remote_port, 0);
    if (!conn)
        return;

    // Send FIN-ACK
    SendTcpPacket(remote_ip, remote_port, 0x11, nullptr, 0); // FIN+ACK

    CloseConnection(conn->Id);
#else
    (void)remote_ip;
    (void)remote_port;
#endif
}

void DpdkBackend::SendTcpPacket(uint32_t remote_ip, uint16_t remote_port, uint16_t flags, const uint8_t* data, size_t len)
{
#if defined(CPPTRADER_DPDK_ENABLED)
    // In full implementation:
    // - Allocate mbuf from TX pool
    // - Build Ethernet + IP + TCP headers
    // - Copy payload (or use external buffer for zero-copy)
    // - Calculate checksums (offload if supported)
    // - Enqueue to TX ring via rte_eth_tx_burst

    (void)remote_ip;
    (void)remote_port;
    (void)flags;
    (void)data;
    (void)len;
#else
    (void)remote_ip;
    (void)remote_port;
    (void)flags;
    (void)data;
    (void)len;
#endif
}

void DpdkBackend::close(uint16_t conn_id)
{
    CloseConnection(conn_id);
}

void DpdkBackend::CloseConnection(uint16_t conn_id)
{
    auto it = _connections.find(conn_id);
    if (it == _connections.end())
        return;

    it->second->Connected = false;
    _connections.erase(it);

    if (_disconnect_handler)
    {
        _disconnect_handler(conn_id);
    }
}

std::shared_ptr<DpdkConnection> DpdkBackend::FindConnection(uint32_t remote_ip, uint16_t remote_port, uint16_t local_port)
{
    for (auto& [conn_id, conn] : _connections)
    {
        if (conn->RemoteIp == remote_ip && conn->RemotePort == remote_port)
        {
            if (local_port == 0 || conn->LocalPort == local_port)
            {
                return conn;
            }
        }
    }
    return nullptr;
}

} // namespace Protocol
} // namespace CppTrader
