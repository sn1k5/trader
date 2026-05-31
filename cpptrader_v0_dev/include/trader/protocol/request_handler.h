#ifndef CPPTRADER_PROTOCOL_REQUEST_HANDLER_H
#define CPPTRADER_PROTOCOL_REQUEST_HANDLER_H

#include "server.h"
#include "protocol.h"
#include "message.h"
#include "session_manager.h"

#include "trader/matching/market_handler.h"
#include "trader/matching/market_manager.h"
#include "trader/matching/symbol.h"
#include "trader/matching/order.h"
#include "trader/matching/order_book.h"
#include "trader/matching/level.h"

#include "trader/wal/wal.h"
#include "trader/snapshot/snapshot.h"

#include <cstdint>
#include <cstddef>
#include <memory>

namespace CppTrader {
namespace Protocol {

class RequestHandler : public CppTrader::Matching::MarketHandler
{
public:
    RequestHandler(ProtocolServer& server, CppTrader::Matching::MarketManager& market);
    RequestHandler(ProtocolServer& server, CppTrader::Matching::MarketManager& market, 
                   std::shared_ptr<CppTrader::WAL::WALWriter> wal_writer);
    RequestHandler(ProtocolServer& server, CppTrader::Matching::MarketManager& market,
                   std::shared_ptr<CppTrader::WAL::WALWriter> wal_writer,
                   std::shared_ptr<CppTrader::Snapshot::SnapshotManager> snapshot_manager);
    ~RequestHandler() override = default;

    RequestHandler(const RequestHandler&) = delete;
    RequestHandler(RequestHandler&&) = delete;
    RequestHandler& operator=(const RequestHandler&) = delete;
    RequestHandler& operator=(RequestHandler&&) = delete;

    void RegisterHandlers();

protected:
    void onAddSymbol(const CppTrader::Matching::Symbol& symbol) override;
    void onDeleteSymbol(const CppTrader::Matching::Symbol& symbol) override;

    void onAddOrderBook(const CppTrader::Matching::OrderBook& order_book) override;
    void onUpdateOrderBook(const CppTrader::Matching::OrderBook& order_book, bool top) override;
    void onDeleteOrderBook(const CppTrader::Matching::OrderBook& order_book) override;

    void onAddLevel(const CppTrader::Matching::OrderBook& order_book, const CppTrader::Matching::Level& level, bool top) override;
    void onUpdateLevel(const CppTrader::Matching::OrderBook& order_book, const CppTrader::Matching::Level& level, bool top) override;
    void onDeleteLevel(const CppTrader::Matching::OrderBook& order_book, const CppTrader::Matching::Level& level, bool top) override;

    void onAddOrder(const CppTrader::Matching::Order& order) override;
    void onUpdateOrder(const CppTrader::Matching::Order& order) override;
    void onDeleteOrder(const CppTrader::Matching::Order& order) override;

    void onExecuteOrder(const CppTrader::Matching::Order& order, uint64_t price, uint64_t quantity) override;

public:
    void HandleAddSymbol(uint16_t conn_id, const MsgHeader& header, const uint8_t* body, size_t body_len);
    void HandleDeleteSymbol(uint16_t conn_id, const MsgHeader& header, const uint8_t* body, size_t body_len);
    void HandleGetSymbol(uint16_t conn_id, const MsgHeader& header, const uint8_t* body, size_t body_len);

    void HandleAddOrderBook(uint16_t conn_id, const MsgHeader& header, const uint8_t* body, size_t body_len);
    void HandleDeleteOrderBook(uint16_t conn_id, const MsgHeader& header, const uint8_t* body, size_t body_len);
    void HandleGetOrderBook(uint16_t conn_id, const MsgHeader& header, const uint8_t* body, size_t body_len);

    void HandleAddOrder(uint16_t conn_id, const MsgHeader& header, const uint8_t* body, size_t body_len);
    void HandleReduceOrder(uint16_t conn_id, const MsgHeader& header, const uint8_t* body, size_t body_len);
    void HandleModifyOrder(uint16_t conn_id, const MsgHeader& header, const uint8_t* body, size_t body_len);
    void HandleMitigateOrder(uint16_t conn_id, const MsgHeader& header, const uint8_t* body, size_t body_len);
    void HandleReplaceOrder(uint16_t conn_id, const MsgHeader& header, const uint8_t* body, size_t body_len);
    void HandleDeleteOrder(uint16_t conn_id, const MsgHeader& header, const uint8_t* body, size_t body_len);
    void HandleExecuteOrder(uint16_t conn_id, const MsgHeader& header, const uint8_t* body, size_t body_len);
    void HandleGetOrder(uint16_t conn_id, const MsgHeader& header, const uint8_t* body, size_t body_len);

    void HandleEnableMatching(uint16_t conn_id, const MsgHeader& header, const uint8_t* body, size_t body_len);
    void HandleDisableMatching(uint16_t conn_id, const MsgHeader& header, const uint8_t* body, size_t body_len);

    void HandleSubscribeOrderBook(uint16_t conn_id, const MsgHeader& header, const uint8_t* body, size_t body_len);
    void HandleSubscribeOrders(uint16_t conn_id, const MsgHeader& header, const uint8_t* body, size_t body_len);

    void HandleHeartbeat(uint16_t conn_id, const MsgHeader& header, const uint8_t* body, size_t body_len);
    void HandleAuth(uint16_t conn_id, const MsgHeader& header, const uint8_t* body, size_t body_len);
    void HandleEventAck(uint16_t conn_id, const MsgHeader& header, const uint8_t* body, size_t body_len);
    void HandleReconcileRequest(uint16_t conn_id, const MsgHeader& header, const uint8_t* body, size_t body_len);
    void HandleSnapshot(uint16_t conn_id, const MsgHeader& header, const uint8_t* body, size_t body_len);

private:
    ProtocolServer& _server;
    CppTrader::Matching::MarketManager& _market;
    std::shared_ptr<CppTrader::WAL::WALWriter> _wal_writer;
    std::shared_ptr<CppTrader::Snapshot::SnapshotManager> _snapshot_manager;
    std::atomic<uint64_t> _trade_id_generator;

    static OrderProto ConvertOrder(const CppTrader::Matching::Order& order);
    static SymbolProto ConvertSymbol(const CppTrader::Matching::Symbol& symbol);
    static LevelProto ConvertLevel(const CppTrader::Matching::Level& level);
    static CppTrader::Matching::Order ConvertOrderProto(const OrderProto& proto);
    static CppTrader::Matching::Symbol ConvertSymbolProto(const SymbolProto& proto);
    static CppTrader::Matching::OrderType ConvertOrderType(OrderType type);
    static CppTrader::Matching::OrderSide ConvertOrderSide(OrderSide side);
    static CppTrader::Matching::OrderTimeInForce ConvertOrderTimeInForce(OrderTimeInForce tif);
        static CppTrader::Matching::STPPolicy ConvertSTPPolicy(Protocol::STPPolicy policy);
        static ErrorCode ConvertMatchingError(CppTrader::Matching::ErrorCode error);

    bool CheckRole(uint16_t conn_id, Role minimum_role);
};

} // namespace Protocol
} // namespace CppTrader

#endif // CPPTRADER_PROTOCOL_REQUEST_HANDLER_H