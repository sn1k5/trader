/*!
    \file request_handler.cpp
    \brief Protocol request handler implementation
    \author CppTrader Team
    \date 18.05.2026
    \copyright MIT License
*/

#include "trader/protocol/request_handler.h"
#include "trader/wal/wal.h"

#include <array>
#include <cstring>
#include <iostream>
#include <atomic>
#include <random>

namespace CppTrader
{
    namespace Protocol
    {

        RequestHandler::RequestHandler(ProtocolServer &server, CppTrader::Matching::MarketManager &market)
            : _server(server), _market(market), _trade_id_generator(0)
        {
        }

        RequestHandler::RequestHandler(ProtocolServer &server, CppTrader::Matching::MarketManager &market,
                                       std::shared_ptr<CppTrader::WAL::WALWriter> wal_writer)
            : _server(server), _market(market), _wal_writer(wal_writer), _trade_id_generator(0)
        {
        }

        RequestHandler::RequestHandler(ProtocolServer &server, CppTrader::Matching::MarketManager &market,
                                       std::shared_ptr<CppTrader::WAL::WALWriter> wal_writer,
                                       std::shared_ptr<CppTrader::Snapshot::SnapshotManager> snapshot_manager)
            : _server(server), _market(market), _wal_writer(wal_writer), _snapshot_manager(snapshot_manager), _trade_id_generator(0)
        {
        }

        void RequestHandler::RegisterHandlers()
        {
            _server.RegisterHandler(MsgType::ADD_SYMBOL_REQUEST,
                                    [this](uint16_t conn_id, const MsgHeader &header, const uint8_t *body, size_t body_len)
                                    { HandleAddSymbol(conn_id, header, body, body_len); });

            _server.RegisterHandler(MsgType::DELETE_SYMBOL_REQUEST,
                                    [this](uint16_t conn_id, const MsgHeader &header, const uint8_t *body, size_t body_len)
                                    { HandleDeleteSymbol(conn_id, header, body, body_len); });

            _server.RegisterHandler(MsgType::GET_SYMBOL_REQUEST,
                                    [this](uint16_t conn_id, const MsgHeader &header, const uint8_t *body, size_t body_len)
                                    { HandleGetSymbol(conn_id, header, body, body_len); });

            _server.RegisterHandler(MsgType::ADD_ORDER_BOOK_REQUEST,
                                    [this](uint16_t conn_id, const MsgHeader &header, const uint8_t *body, size_t body_len)
                                    { HandleAddOrderBook(conn_id, header, body, body_len); });

            _server.RegisterHandler(MsgType::DELETE_ORDER_BOOK_REQUEST,
                                    [this](uint16_t conn_id, const MsgHeader &header, const uint8_t *body, size_t body_len)
                                    { HandleDeleteOrderBook(conn_id, header, body, body_len); });

            _server.RegisterHandler(MsgType::GET_ORDER_BOOK_REQUEST,
                                    [this](uint16_t conn_id, const MsgHeader &header, const uint8_t *body, size_t body_len)
                                    { HandleGetOrderBook(conn_id, header, body, body_len); });

            _server.RegisterHandler(MsgType::ADD_ORDER_REQUEST,
                                    [this](uint16_t conn_id, const MsgHeader &header, const uint8_t *body, size_t body_len)
                                    { HandleAddOrder(conn_id, header, body, body_len); });

            _server.RegisterHandler(MsgType::REDUCE_ORDER_REQUEST,
                                    [this](uint16_t conn_id, const MsgHeader &header, const uint8_t *body, size_t body_len)
                                    { HandleReduceOrder(conn_id, header, body, body_len); });

            _server.RegisterHandler(MsgType::MODIFY_ORDER_REQUEST,
                                    [this](uint16_t conn_id, const MsgHeader &header, const uint8_t *body, size_t body_len)
                                    { HandleModifyOrder(conn_id, header, body, body_len); });

            _server.RegisterHandler(MsgType::MITIGATE_ORDER_REQUEST,
                                    [this](uint16_t conn_id, const MsgHeader &header, const uint8_t *body, size_t body_len)
                                    { HandleMitigateOrder(conn_id, header, body, body_len); });

            _server.RegisterHandler(MsgType::REPLACE_ORDER_REQUEST,
                                    [this](uint16_t conn_id, const MsgHeader &header, const uint8_t *body, size_t body_len)
                                    { HandleReplaceOrder(conn_id, header, body, body_len); });

            _server.RegisterHandler(MsgType::DELETE_ORDER_REQUEST,
                                    [this](uint16_t conn_id, const MsgHeader &header, const uint8_t *body, size_t body_len)
                                    { HandleDeleteOrder(conn_id, header, body, body_len); });

            _server.RegisterHandler(MsgType::EXECUTE_ORDER_REQUEST,
                                    [this](uint16_t conn_id, const MsgHeader &header, const uint8_t *body, size_t body_len)
                                    { HandleExecuteOrder(conn_id, header, body, body_len); });

            _server.RegisterHandler(MsgType::GET_ORDER_REQUEST,
                                    [this](uint16_t conn_id, const MsgHeader &header, const uint8_t *body, size_t body_len)
                                    { HandleGetOrder(conn_id, header, body, body_len); });

            _server.RegisterHandler(MsgType::ENABLE_MATCHING_REQUEST,
                                    [this](uint16_t conn_id, const MsgHeader &header, const uint8_t *body, size_t body_len)
                                    { HandleEnableMatching(conn_id, header, body, body_len); });

            _server.RegisterHandler(MsgType::DISABLE_MATCHING_REQUEST,
                                    [this](uint16_t conn_id, const MsgHeader &header, const uint8_t *body, size_t body_len)
                                    { HandleDisableMatching(conn_id, header, body, body_len); });

            _server.RegisterHandler(MsgType::SUBSCRIBE_ORDER_BOOK_REQUEST,
                                    [this](uint16_t conn_id, const MsgHeader &header, const uint8_t *body, size_t body_len)
                                    { HandleSubscribeOrderBook(conn_id, header, body, body_len); });

            _server.RegisterHandler(MsgType::SUBSCRIBE_ORDERS_REQUEST,
                                    [this](uint16_t conn_id, const MsgHeader &header, const uint8_t *body, size_t body_len)
                                    { HandleSubscribeOrders(conn_id, header, body, body_len); });

            _server.RegisterHandler(MsgType::HEARTBEAT_REQ,
                                    [this](uint16_t conn_id, const MsgHeader &header, const uint8_t *body, size_t body_len)
                                    { HandleHeartbeat(conn_id, header, body, body_len); });

            _server.RegisterHandler(MsgType::AUTH_REQUEST,
                                    [this](uint16_t conn_id, const MsgHeader &header, const uint8_t *body, size_t body_len)
                                    { HandleAuth(conn_id, header, body, body_len); });

            _server.RegisterHandler(MsgType::EVENT_ACK,
                                    [this](uint16_t conn_id, const MsgHeader &header, const uint8_t *body, size_t body_len)
                                    { HandleEventAck(conn_id, header, body, body_len); });

            _server.RegisterHandler(MsgType::RECONCILE_REQUEST,
                                    [this](uint16_t conn_id, const MsgHeader &header, const uint8_t *body, size_t body_len)
                                    { HandleReconcileRequest(conn_id, header, body, body_len); });

            _server.RegisterHandler(MsgType::SNAPSHOT_REQUEST,
                                    [this](uint16_t conn_id, const MsgHeader &header, const uint8_t *body, size_t body_len)
                                    { HandleSnapshot(conn_id, header, body, body_len); });
        }

        bool RequestHandler::CheckRole(uint16_t conn_id, Role minimum_role)
        {
            auto session = _server.GetSessionManager().FindByConnId(conn_id);
            if (!session)
                return false;

            Role role = session->role;

            switch (minimum_role)
            {
            case Role::ADMIN:
                return role == Role::ADMIN;
            case Role::TRADER:
                return role == Role::ADMIN || role == Role::TRADER || role == Role::QUANTBOT;
            case Role::VIEWER:
                return true;
            default:
                return false;
            }
        }

        void RequestHandler::onAddSymbol(const CppTrader::Matching::Symbol &symbol)
        {
            SymbolResponse response{static_cast<uint8_t>(ErrorCode::OK), ConvertSymbol(symbol)};
            MsgHeader header(MsgType::SYMBOL_RESPONSE, Flags::PUSH, sizeof(response));
            _server.Broadcast(header, &response, sizeof(response));
        }

        void RequestHandler::onDeleteSymbol(const CppTrader::Matching::Symbol &symbol)
        {
            SymbolResponse response{static_cast<uint8_t>(ErrorCode::OK), ConvertSymbol(symbol)};
            MsgHeader header(MsgType::SYMBOL_RESPONSE, Flags::PUSH, sizeof(response));
            _server.Broadcast(header, &response, sizeof(response));
        }

        void RequestHandler::onAddOrderBook(const CppTrader::Matching::OrderBook &order_book)
        {
            (void)order_book;
        }

        void RequestHandler::onUpdateOrderBook(const CppTrader::Matching::OrderBook &order_book, bool top)
        {
            (void)order_book;
            (void)top;
        }

        void RequestHandler::onDeleteOrderBook(const CppTrader::Matching::OrderBook &order_book)
        {
            (void)order_book;
        }

        void RequestHandler::onAddLevel(const CppTrader::Matching::OrderBook &order_book, const CppTrader::Matching::Level &level, bool top)
        {
            OrderBookUpdateEvent event = {
                order_book.symbol().Id,
                (uint8_t)(top ? 1 : 0),
                static_cast<uint8_t>(UpdateType::ADD),
                (uint8_t)(level.IsBid() ? 0 : 1),
                0,
                ConvertLevel(level)
            };

            MsgHeader header(MsgType::ORDER_BOOK_UPDATE_EVENT, Flags::PUSH, sizeof(event));
            _server.BroadcastToSymbol(order_book.symbol().Id, header, &event, sizeof(event));
        }

        void RequestHandler::onUpdateLevel(const CppTrader::Matching::OrderBook &order_book, const CppTrader::Matching::Level &level, bool top)
        {
            OrderBookUpdateEvent event = {
                order_book.symbol().Id,
                (uint8_t)(top ? 1 : 0),
                static_cast<uint8_t>(UpdateType::UPDATE),
                (uint8_t)(level.IsBid() ? 0 : 1),
                0,
                ConvertLevel(level)
            };

            MsgHeader header(MsgType::ORDER_BOOK_UPDATE_EVENT, Flags::PUSH, sizeof(event));
            _server.BroadcastToSymbol(order_book.symbol().Id, header, &event, sizeof(event));
        }

        void RequestHandler::onDeleteLevel(const CppTrader::Matching::OrderBook &order_book, const CppTrader::Matching::Level &level, bool top)
        {
            OrderBookUpdateEvent event = {
                order_book.symbol().Id,
                (uint8_t)(top ? 1 : 0),
                static_cast<uint8_t>(UpdateType::DELETE),
                (uint8_t)(level.IsBid() ? 0 : 1),
                0,
                ConvertLevel(level)
            };

            MsgHeader header(MsgType::ORDER_BOOK_UPDATE_EVENT, Flags::PUSH, sizeof(event));
            _server.BroadcastToSymbol(order_book.symbol().Id, header, &event, sizeof(event));
        }

        void RequestHandler::onAddOrder(const CppTrader::Matching::Order &order)
        {
            OrderUpdateEvent event = { 1, ConvertOrder(order), 0, 0 };
            MsgHeader header(MsgType::ORDER_UPDATE_EVENT, Flags::PUSH, sizeof(event));
            _server.BroadcastToSymbol(order.SymbolId, header, &event, sizeof(event));
        }

        void RequestHandler::onUpdateOrder(const CppTrader::Matching::Order &order)
        {
            OrderUpdateEvent event = { 2, ConvertOrder(order), 0, 0 };
            MsgHeader header(MsgType::ORDER_UPDATE_EVENT, Flags::PUSH, sizeof(event));
            _server.BroadcastToSymbol(order.SymbolId, header, &event, sizeof(event));
        }

        void RequestHandler::onDeleteOrder(const CppTrader::Matching::Order &order)
        {
            OrderUpdateEvent event = { 3, ConvertOrder(order), 0, 0 };
            MsgHeader header(MsgType::ORDER_UPDATE_EVENT, Flags::PUSH, sizeof(event));
            _server.BroadcastToSymbol(order.SymbolId, header, &event, sizeof(event));
        }

        void RequestHandler::onExecuteOrder(const CppTrader::Matching::Order &order, uint64_t price, uint64_t quantity)
        {

            if (_wal_writer)
            {
                CppTrader::WAL::TradeData trade{};
                trade.TradeId = ++_trade_id_generator;
                trade.BidOrderId = order.IsBuy() ? order.Id : 0;
                trade.AskOrderId = order.IsSell() ? order.Id : 0;
                trade.SymbolId = order.SymbolId;
                trade.Price = price;
                trade.Quantity = quantity;
                trade.Timestamp = std::chrono::duration_cast<std::chrono::microseconds>(
                    std::chrono::system_clock::now().time_since_epoch()).count();
                _wal_writer->WriteTrade(trade);
            }

            OrderUpdateEvent event = { 4, ConvertOrder(order), price, quantity };
            MsgHeader header(MsgType::ORDER_UPDATE_EVENT, Flags::PUSH, sizeof(event));
            _server.BroadcastToSymbol(order.SymbolId, header, &event, sizeof(event));
        }

        void RequestHandler::HandleAddSymbol(uint16_t conn_id, const MsgHeader &header, const uint8_t *body, size_t body_len)
        {
            if (!CheckRole(conn_id, Role::ADMIN))
            {
                SymbolResponse response{static_cast<uint8_t>(ErrorCode::NOT_AUTHORIZED), SymbolProto{}};
                MsgHeader resp_header(MsgType::SYMBOL_RESPONSE, Flags::RESPONSE, sizeof(response));
                resp_header.Sequence = header.Sequence;
                _server.SendResponse(conn_id, resp_header, &response, sizeof(response));
                return;
            }

            if (body_len < sizeof(AddSymbolRequest))
            {
                SymbolResponse response{static_cast<uint8_t>(ErrorCode::ORDER_PARAMETER_INVALID), SymbolProto{}};
                MsgHeader resp_header(MsgType::SYMBOL_RESPONSE, Flags::RESPONSE, sizeof(response));
                resp_header.Sequence = header.Sequence;
                _server.SendResponse(conn_id, resp_header, &response, sizeof(response));
                return;
            }

            const auto *request = reinterpret_cast<const AddSymbolRequest *>(body);
            auto symbol = ConvertSymbolProto(request->Symbol);
            auto error = _market.AddSymbol(symbol);

            SymbolResponse response{static_cast<uint8_t>(ConvertMatchingError(error)), ConvertSymbol(symbol)};
            MsgHeader resp_header(MsgType::SYMBOL_RESPONSE, Flags::RESPONSE, sizeof(response));
            resp_header.Sequence = header.Sequence;
            _server.SendResponse(conn_id, resp_header, &response, sizeof(response));
        }

        void RequestHandler::HandleDeleteSymbol(uint16_t conn_id, const MsgHeader &header, const uint8_t *body, size_t body_len)
        {
            if (!CheckRole(conn_id, Role::ADMIN))
            {
                SimpleResponse response(ErrorCode::NOT_AUTHORIZED);
                MsgHeader resp_header(MsgType::SIMPLE_RESPONSE, Flags::RESPONSE, sizeof(response));
                resp_header.Sequence = header.Sequence;
                _server.SendResponse(conn_id, resp_header, &response, sizeof(response));
                return;
            }

            if (body_len < sizeof(DeleteSymbolRequest))
            {
                SimpleResponse response(ErrorCode::ORDER_PARAMETER_INVALID);
                MsgHeader resp_header(MsgType::SIMPLE_RESPONSE, Flags::RESPONSE, sizeof(response));
                resp_header.Sequence = header.Sequence;
                _server.SendResponse(conn_id, resp_header, &response, sizeof(response));
                return;
            }

            const auto *request = reinterpret_cast<const DeleteSymbolRequest *>(body);
            auto error = _market.DeleteSymbol(request->Id);

            SimpleResponse response(ConvertMatchingError(error));
            MsgHeader resp_header(MsgType::SIMPLE_RESPONSE, Flags::RESPONSE, sizeof(response));
            resp_header.Sequence = header.Sequence;
            _server.SendResponse(conn_id, resp_header, &response, sizeof(response));
        }

        void RequestHandler::HandleGetSymbol(uint16_t conn_id, const MsgHeader &header, const uint8_t *body, size_t body_len)
        {
            if (body_len < sizeof(GetSymbolRequest))
            {
                SymbolResponse response{static_cast<uint8_t>(ErrorCode::ORDER_PARAMETER_INVALID), SymbolProto{}};
                MsgHeader resp_header(MsgType::SYMBOL_RESPONSE, Flags::RESPONSE, sizeof(response));
                resp_header.Sequence = header.Sequence;
                _server.SendResponse(conn_id, resp_header, &response, sizeof(response));
                return;
            }

            const auto *request = reinterpret_cast<const GetSymbolRequest *>(body);
            const auto *symbol = _market.GetSymbol(request->Id);

            if (symbol)
            {
                SymbolResponse response{static_cast<uint8_t>(ErrorCode::OK), ConvertSymbol(*symbol)};
                MsgHeader resp_header(MsgType::SYMBOL_RESPONSE, Flags::RESPONSE, sizeof(response));
                resp_header.Sequence = header.Sequence;
                _server.SendResponse(conn_id, resp_header, &response, sizeof(response));
            }
            else
            {
                SymbolResponse response{static_cast<uint8_t>(ErrorCode::SYMBOL_NOT_FOUND), SymbolProto{}};
                MsgHeader resp_header(MsgType::SYMBOL_RESPONSE, Flags::RESPONSE, sizeof(response));
                resp_header.Sequence = header.Sequence;
                _server.SendResponse(conn_id, resp_header, &response, sizeof(response));
            }
        }

        void RequestHandler::HandleAddOrderBook(uint16_t conn_id, const MsgHeader &header, const uint8_t *body, size_t body_len)
        {
            if (!CheckRole(conn_id, Role::ADMIN))
            {
                SimpleResponse response(ErrorCode::NOT_AUTHORIZED);
                MsgHeader resp_header(MsgType::SIMPLE_RESPONSE, Flags::RESPONSE, sizeof(response));
                resp_header.Sequence = header.Sequence;
                _server.SendResponse(conn_id, resp_header, &response, sizeof(response));
                return;
            }

            if (body_len < sizeof(AddOrderBookRequest))
            {
                SimpleResponse response(ErrorCode::ORDER_PARAMETER_INVALID);
                MsgHeader resp_header(MsgType::SIMPLE_RESPONSE, Flags::RESPONSE, sizeof(response));
                resp_header.Sequence = header.Sequence;
                _server.SendResponse(conn_id, resp_header, &response, sizeof(response));
                return;
            }

            const auto *request = reinterpret_cast<const AddOrderBookRequest *>(body);
            const auto *symbol = _market.GetSymbol(request->SymbolId);
            if (!symbol)
            {
                SimpleResponse response(ErrorCode::SYMBOL_NOT_FOUND);
                MsgHeader resp_header(MsgType::SIMPLE_RESPONSE, Flags::RESPONSE, sizeof(response));
                resp_header.Sequence = header.Sequence;
                _server.SendResponse(conn_id, resp_header, &response, sizeof(response));
                return;
            }

            auto error = _market.AddOrderBook(*symbol);
            SimpleResponse response(ConvertMatchingError(error));
            MsgHeader resp_header(MsgType::SIMPLE_RESPONSE, Flags::RESPONSE, sizeof(response));
            resp_header.Sequence = header.Sequence;
            _server.SendResponse(conn_id, resp_header, &response, sizeof(response));
        }

        void RequestHandler::HandleDeleteOrderBook(uint16_t conn_id, const MsgHeader &header, const uint8_t *body, size_t body_len)
        {
            if (!CheckRole(conn_id, Role::ADMIN))
            {
                SimpleResponse response(ErrorCode::NOT_AUTHORIZED);
                MsgHeader resp_header(MsgType::SIMPLE_RESPONSE, Flags::RESPONSE, sizeof(response));
                resp_header.Sequence = header.Sequence;
                _server.SendResponse(conn_id, resp_header, &response, sizeof(response));
                return;
            }

            if (body_len < sizeof(DeleteOrderBookRequest))
            {
                SimpleResponse response(ErrorCode::ORDER_PARAMETER_INVALID);
                MsgHeader resp_header(MsgType::SIMPLE_RESPONSE, Flags::RESPONSE, sizeof(response));
                resp_header.Sequence = header.Sequence;
                _server.SendResponse(conn_id, resp_header, &response, sizeof(response));
                return;
            }

            const auto *request = reinterpret_cast<const DeleteOrderBookRequest *>(body);
            auto error = _market.DeleteOrderBook(request->SymbolId);
            SimpleResponse response(ConvertMatchingError(error));
            MsgHeader resp_header(MsgType::SIMPLE_RESPONSE, Flags::RESPONSE, sizeof(response));
            resp_header.Sequence = header.Sequence;
            _server.SendResponse(conn_id, resp_header, &response, sizeof(response));
        }

        void RequestHandler::HandleGetOrderBook(uint16_t conn_id, const MsgHeader &header, const uint8_t *body, size_t body_len)
        {
            if (body_len < sizeof(GetOrderBookRequest))
            {
                SimpleResponse response(ErrorCode::ORDER_PARAMETER_INVALID);
                MsgHeader resp_header(MsgType::SIMPLE_RESPONSE, Flags::RESPONSE, sizeof(response));
                resp_header.Sequence = header.Sequence;
                _server.SendResponse(conn_id, resp_header, &response, sizeof(response));
                return;
            }

            const auto *request = reinterpret_cast<const GetOrderBookRequest *>(body);
            const auto *order_book = _market.GetOrderBook(request->SymbolId);
            if (!order_book)
            {
                SimpleResponse response(ErrorCode::ORDER_BOOK_NOT_FOUND);
                MsgHeader resp_header(MsgType::SIMPLE_RESPONSE, Flags::RESPONSE, sizeof(response));
                resp_header.Sequence = header.Sequence;
                _server.SendResponse(conn_id, resp_header, &response, sizeof(response));
                return;
            }

            uint16_t bid_count = 0;
            uint16_t ask_count = 0;

            for (const auto &level : order_book->bids())
            {
                (void)level;
                ++bid_count;
            }
            for (const auto &level : order_book->asks())
            {
                (void)level;
                ++ask_count;
            }

            LevelProto best_bid;
            LevelProto best_ask;

            if (order_book->best_bid())
                best_bid = ConvertLevel(*order_book->best_bid());
            if (order_book->best_ask())
                best_ask = ConvertLevel(*order_book->best_ask());

            size_t snapshot_size = OrderBookSnapshotSize(bid_count, ask_count);
            std::vector<uint8_t> snapshot_data(snapshot_size);
            auto *snapshot = reinterpret_cast<OrderBookSnapshot *>(snapshot_data.data());
            snapshot->SymbolId = request->SymbolId;
            snapshot->BestBid = best_bid;
            snapshot->BestAsk = best_ask;
            snapshot->BidCount = bid_count;
            snapshot->AskCount = ask_count;

            uint16_t idx = 0;
            for (const auto &level : order_book->bids())
            {
                OrderBookSnapshotBids(snapshot)[idx++] = ConvertLevel(level);
            }
            idx = 0;
            for (const auto &level : order_book->asks())
            {
                OrderBookSnapshotAsks(snapshot)[idx++] = ConvertLevel(level);
            }

            MsgHeader resp_header(MsgType::ORDER_BOOK_RESPONSE, Flags::RESPONSE, static_cast<uint16_t>(snapshot_size));
            resp_header.Sequence = header.Sequence;
            _server.SendResponse(conn_id, resp_header, snapshot_data.data(), snapshot_size);
        }

        void RequestHandler::HandleAddOrder(uint16_t conn_id, const MsgHeader &header, const uint8_t *body, size_t body_len)
        {
            if (!CheckRole(conn_id, Role::TRADER))
            {
                OrderResponse response{static_cast<uint8_t>(ErrorCode::NOT_AUTHORIZED), OrderProto{}};
                MsgHeader resp_header(MsgType::ORDER_RESPONSE, Flags::RESPONSE, sizeof(response));
                resp_header.Sequence = header.Sequence;
                _server.SendResponse(conn_id, resp_header, &response, sizeof(response));
                return;
            }

            if (body_len < sizeof(AddOrderRequest))
            {
                OrderResponse response{static_cast<uint8_t>(ErrorCode::ORDER_PARAMETER_INVALID), OrderProto{}};
                MsgHeader resp_header(MsgType::ORDER_RESPONSE, Flags::RESPONSE, sizeof(response));
                resp_header.Sequence = header.Sequence;
                _server.SendResponse(conn_id, resp_header, &response, sizeof(response));
                return;
            }

            const auto *request = reinterpret_cast<const AddOrderRequest *>(body);
            auto order = ConvertOrderProto(request->Order);

            auto session = _server.GetSessionManager().FindByConnId(conn_id);
            if (session)
                order.AccountId = session->account_id;

            if (_wal_writer)
            {
                _wal_writer->WriteNewOrder(order);
            }

            auto error = _market.AddOrder(order);

            OrderResponse response{static_cast<uint8_t>(ConvertMatchingError(error)), ConvertOrder(order)};
            MsgHeader resp_header(MsgType::ORDER_RESPONSE, Flags::RESPONSE, sizeof(response));
            resp_header.Sequence = header.Sequence;
            _server.SendResponse(conn_id, resp_header, &response, sizeof(response));
        }

        void RequestHandler::HandleReduceOrder(uint16_t conn_id, const MsgHeader &header, const uint8_t *body, size_t body_len)
        {
            if (!CheckRole(conn_id, Role::TRADER))
            {
                OrderResponse response{static_cast<uint8_t>(ErrorCode::NOT_AUTHORIZED), OrderProto{}};
                MsgHeader resp_header(MsgType::ORDER_RESPONSE, Flags::RESPONSE, sizeof(response));
                resp_header.Sequence = header.Sequence;
                _server.SendResponse(conn_id, resp_header, &response, sizeof(response));
                return;
            }

            if (body_len < sizeof(ReduceOrderRequest))
            {
                OrderResponse response{static_cast<uint8_t>(ErrorCode::ORDER_PARAMETER_INVALID), OrderProto{}};
                MsgHeader resp_header(MsgType::ORDER_RESPONSE, Flags::RESPONSE, sizeof(response));
                resp_header.Sequence = header.Sequence;
                _server.SendResponse(conn_id, resp_header, &response, sizeof(response));
                return;
            }

            const auto *request = reinterpret_cast<const ReduceOrderRequest *>(body);

            const auto *order = _market.GetOrder(request->Id);
            if (order && _wal_writer)
            {
                _wal_writer->WriteCancelOrder(request->Id, order->SymbolId);
            }

            auto error = _market.ReduceOrder(request->Id, request->Quantity);

            order = _market.GetOrder(request->Id);
            OrderResponse response{static_cast<uint8_t>(ConvertMatchingError(error)), order ? ConvertOrder(*order) : OrderProto{}};
            MsgHeader resp_header(MsgType::ORDER_RESPONSE, Flags::RESPONSE, sizeof(response));
            resp_header.Sequence = header.Sequence;
            _server.SendResponse(conn_id, resp_header, &response, sizeof(response));
        }

        void RequestHandler::HandleModifyOrder(uint16_t conn_id, const MsgHeader &header, const uint8_t *body, size_t body_len)
        {
            if (!CheckRole(conn_id, Role::TRADER))
            {
                OrderResponse response{static_cast<uint8_t>(ErrorCode::NOT_AUTHORIZED), OrderProto{}};
                MsgHeader resp_header(MsgType::ORDER_RESPONSE, Flags::RESPONSE, sizeof(response));
                resp_header.Sequence = header.Sequence;
                _server.SendResponse(conn_id, resp_header, &response, sizeof(response));
                return;
            }

            if (body_len < sizeof(ModifyOrderRequest))
            {
                OrderResponse response{static_cast<uint8_t>(ErrorCode::ORDER_PARAMETER_INVALID), OrderProto{}};
                MsgHeader resp_header(MsgType::ORDER_RESPONSE, Flags::RESPONSE, sizeof(response));
                resp_header.Sequence = header.Sequence;
                _server.SendResponse(conn_id, resp_header, &response, sizeof(response));
                return;
            }

            const auto *request = reinterpret_cast<const ModifyOrderRequest *>(body);
            auto error = _market.ModifyOrder(request->Id, request->NewPrice, request->NewQuantity);

            const auto *order = _market.GetOrder(request->Id);
            OrderResponse response{static_cast<uint8_t>(ConvertMatchingError(error)), order ? ConvertOrder(*order) : OrderProto{}};
            MsgHeader resp_header(MsgType::ORDER_RESPONSE, Flags::RESPONSE, sizeof(response));
            resp_header.Sequence = header.Sequence;
            _server.SendResponse(conn_id, resp_header, &response, sizeof(response));
        }

        void RequestHandler::HandleMitigateOrder(uint16_t conn_id, const MsgHeader &header, const uint8_t *body, size_t body_len)
        {
            if (!CheckRole(conn_id, Role::TRADER))
            {
                OrderResponse response{static_cast<uint8_t>(ErrorCode::NOT_AUTHORIZED), OrderProto{}};
                MsgHeader resp_header(MsgType::ORDER_RESPONSE, Flags::RESPONSE, sizeof(response));
                resp_header.Sequence = header.Sequence;
                _server.SendResponse(conn_id, resp_header, &response, sizeof(response));
                return;
            }

            if (body_len < sizeof(MitigateOrderRequest))
            {
                OrderResponse response{static_cast<uint8_t>(ErrorCode::ORDER_PARAMETER_INVALID), OrderProto{}};
                MsgHeader resp_header(MsgType::ORDER_RESPONSE, Flags::RESPONSE, sizeof(response));
                resp_header.Sequence = header.Sequence;
                _server.SendResponse(conn_id, resp_header, &response, sizeof(response));
                return;
            }

            const auto *request = reinterpret_cast<const MitigateOrderRequest *>(body);
            auto error = _market.MitigateOrder(request->Id, request->NewPrice, request->NewQuantity);

            const auto *order = _market.GetOrder(request->Id);
            OrderResponse response{static_cast<uint8_t>(ConvertMatchingError(error)), order ? ConvertOrder(*order) : OrderProto{}};
            MsgHeader resp_header(MsgType::ORDER_RESPONSE, Flags::RESPONSE, sizeof(response));
            resp_header.Sequence = header.Sequence;
            _server.SendResponse(conn_id, resp_header, &response, sizeof(response));
        }

        void RequestHandler::HandleReplaceOrder(uint16_t conn_id, const MsgHeader &header, const uint8_t *body, size_t body_len)
        {
            if (!CheckRole(conn_id, Role::TRADER))
            {
                OrderResponse response{static_cast<uint8_t>(ErrorCode::NOT_AUTHORIZED), OrderProto{}};
                MsgHeader resp_header(MsgType::ORDER_RESPONSE, Flags::RESPONSE, sizeof(response));
                resp_header.Sequence = header.Sequence;
                _server.SendResponse(conn_id, resp_header, &response, sizeof(response));
                return;
            }

            if (body_len < sizeof(ReplaceOrderRequest))
            {
                OrderResponse response{static_cast<uint8_t>(ErrorCode::ORDER_PARAMETER_INVALID), OrderProto{}};
                MsgHeader resp_header(MsgType::ORDER_RESPONSE, Flags::RESPONSE, sizeof(response));
                resp_header.Sequence = header.Sequence;
                _server.SendResponse(conn_id, resp_header, &response, sizeof(response));
                return;
            }

            const auto *request = reinterpret_cast<const ReplaceOrderRequest *>(body);

            const auto *old_order = _market.GetOrder(request->Id);
            if (old_order && _wal_writer)
            {
                _wal_writer->WriteCancelOrder(request->Id, old_order->SymbolId);
            }

            auto error = _market.ReplaceOrder(request->Id, request->NewId, request->NewPrice, request->NewQuantity);

            CppTrader::Matching::Order new_order_proto(
                request->NewId,
                old_order ? old_order->SymbolId : 0u,
                uint64_t(0),
                CppTrader::Matching::STPPolicy::CANCEL_NEW,
                CppTrader::Matching::OrderType::LIMIT,
                CppTrader::Matching::OrderSide::BUY,
                request->NewPrice,
                uint64_t(0),
                request->NewQuantity,
                CppTrader::Matching::OrderTimeInForce::GTC
            );
            if (_wal_writer && error == CppTrader::Matching::ErrorCode::OK)
            {
                _wal_writer->WriteNewOrder(new_order_proto);
            }

            const auto *order = _market.GetOrder(request->NewId);
            OrderResponse response{static_cast<uint8_t>(ConvertMatchingError(error)), order ? ConvertOrder(*order) : OrderProto{}};
            MsgHeader resp_header(MsgType::ORDER_RESPONSE, Flags::RESPONSE, sizeof(response));
            resp_header.Sequence = header.Sequence;
            _server.SendResponse(conn_id, resp_header, &response, sizeof(response));
        }

        void RequestHandler::HandleDeleteOrder(uint16_t conn_id, const MsgHeader &header, const uint8_t *body, size_t body_len)
        {
            if (!CheckRole(conn_id, Role::TRADER))
            {
                OrderResponse response{static_cast<uint8_t>(ErrorCode::NOT_AUTHORIZED), OrderProto{}};
                MsgHeader resp_header(MsgType::ORDER_RESPONSE, Flags::RESPONSE, sizeof(response));
                resp_header.Sequence = header.Sequence;
                _server.SendResponse(conn_id, resp_header, &response, sizeof(response));
                return;
            }

            if (body_len < sizeof(DeleteOrderRequest))
            {
                OrderResponse response{static_cast<uint8_t>(ErrorCode::ORDER_PARAMETER_INVALID), OrderProto{}};
                MsgHeader resp_header(MsgType::ORDER_RESPONSE, Flags::RESPONSE, sizeof(response));
                resp_header.Sequence = header.Sequence;
                _server.SendResponse(conn_id, resp_header, &response, sizeof(response));
                return;
            }

            const auto *request = reinterpret_cast<const DeleteOrderRequest *>(body);

            const auto *order = _market.GetOrder(request->Id);
            if (order && _wal_writer)
            {
                _wal_writer->WriteCancelOrder(request->Id, order->SymbolId);
            }

            auto error = _market.DeleteOrder(request->Id);

            OrderResponse response{static_cast<uint8_t>(ConvertMatchingError(error)), OrderProto{}};
            MsgHeader resp_header(MsgType::ORDER_RESPONSE, Flags::RESPONSE, sizeof(response));
            resp_header.Sequence = header.Sequence;
            _server.SendResponse(conn_id, resp_header, &response, sizeof(response));
        }

        void RequestHandler::HandleExecuteOrder(uint16_t conn_id, const MsgHeader &header, const uint8_t *body, size_t body_len)
        {
            if (!CheckRole(conn_id, Role::ADMIN))
            {
                OrderResponse response{static_cast<uint8_t>(ErrorCode::NOT_AUTHORIZED), OrderProto{}};
                MsgHeader resp_header(MsgType::ORDER_RESPONSE, Flags::RESPONSE, sizeof(response));
                resp_header.Sequence = header.Sequence;
                _server.SendResponse(conn_id, resp_header, &response, sizeof(response));
                return;
            }

            if (body_len < sizeof(ExecuteOrderRequest))
            {
                OrderResponse response{static_cast<uint8_t>(ErrorCode::ORDER_PARAMETER_INVALID), OrderProto{}};
                MsgHeader resp_header(MsgType::ORDER_RESPONSE, Flags::RESPONSE, sizeof(response));
                resp_header.Sequence = header.Sequence;
                _server.SendResponse(conn_id, resp_header, &response, sizeof(response));
                return;
            }

            const auto *request = reinterpret_cast<const ExecuteOrderRequest *>(body);
            auto error = _market.ExecuteOrder(request->Id, request->Price, request->Quantity);

            const auto *order = _market.GetOrder(request->Id);
            OrderResponse response{static_cast<uint8_t>(ConvertMatchingError(error)), order ? ConvertOrder(*order) : OrderProto{}};
            MsgHeader resp_header(MsgType::ORDER_RESPONSE, Flags::RESPONSE, sizeof(response));
            resp_header.Sequence = header.Sequence;
            _server.SendResponse(conn_id, resp_header, &response, sizeof(response));
        }

        void RequestHandler::HandleGetOrder(uint16_t conn_id, const MsgHeader &header, const uint8_t *body, size_t body_len)
        {
            if (body_len < sizeof(GetOrderRequest))
            {
                OrderResponse response{static_cast<uint8_t>(ErrorCode::ORDER_PARAMETER_INVALID), OrderProto{}};
                MsgHeader resp_header(MsgType::ORDER_RESPONSE, Flags::RESPONSE, sizeof(response));
                resp_header.Sequence = header.Sequence;
                _server.SendResponse(conn_id, resp_header, &response, sizeof(response));
                return;
            }

            const auto *request = reinterpret_cast<const GetOrderRequest *>(body);
            const auto *order = _market.GetOrder(request->Id);

            if (order)
            {
                OrderResponse response{static_cast<uint8_t>(ErrorCode::OK), ConvertOrder(*order)};
                MsgHeader resp_header(MsgType::ORDER_RESPONSE, Flags::RESPONSE, sizeof(response));
                resp_header.Sequence = header.Sequence;
                _server.SendResponse(conn_id, resp_header, &response, sizeof(response));
            }
            else
            {
                OrderResponse response{static_cast<uint8_t>(ErrorCode::ORDER_NOT_FOUND), OrderProto{}};
                MsgHeader resp_header(MsgType::ORDER_RESPONSE, Flags::RESPONSE, sizeof(response));
                resp_header.Sequence = header.Sequence;
                _server.SendResponse(conn_id, resp_header, &response, sizeof(response));
            }
        }

        void RequestHandler::HandleEnableMatching(uint16_t conn_id, const MsgHeader &header, const uint8_t *body, size_t body_len)
        {
            if (!CheckRole(conn_id, Role::ADMIN))
            {
                SimpleResponse response(ErrorCode::NOT_AUTHORIZED);
                MsgHeader resp_header(MsgType::SIMPLE_RESPONSE, Flags::RESPONSE, sizeof(response));
                resp_header.Sequence = header.Sequence;
                _server.SendResponse(conn_id, resp_header, &response, sizeof(response));
                return;
            }

            (void)body;
            (void)body_len;


            _market.EnableMatching();

            SimpleResponse response(ErrorCode::OK);
            MsgHeader resp_header(MsgType::SIMPLE_RESPONSE, Flags::RESPONSE, sizeof(response));
            resp_header.Sequence = header.Sequence;
            _server.SendResponse(conn_id, resp_header, &response, sizeof(response));
        }

        void RequestHandler::HandleDisableMatching(uint16_t conn_id, const MsgHeader &header, const uint8_t *body, size_t body_len)
        {
            if (!CheckRole(conn_id, Role::ADMIN))
            {
                SimpleResponse response(ErrorCode::NOT_AUTHORIZED);
                MsgHeader resp_header(MsgType::SIMPLE_RESPONSE, Flags::RESPONSE, sizeof(response));
                resp_header.Sequence = header.Sequence;
                _server.SendResponse(conn_id, resp_header, &response, sizeof(response));
                return;
            }

            (void)body;
            (void)body_len;


            _market.DisableMatching();

            SimpleResponse response(ErrorCode::OK);
            MsgHeader resp_header(MsgType::SIMPLE_RESPONSE, Flags::RESPONSE, sizeof(response));
            resp_header.Sequence = header.Sequence;
            _server.SendResponse(conn_id, resp_header, &response, sizeof(response));
        }

        void RequestHandler::HandleSubscribeOrderBook(uint16_t conn_id, const MsgHeader &header, const uint8_t *body, size_t body_len)
        {
            if (body_len < sizeof(SubscribeRequest))
            {
                SimpleResponse response(ErrorCode::ORDER_PARAMETER_INVALID);
                MsgHeader resp_header(MsgType::SIMPLE_RESPONSE, Flags::RESPONSE, sizeof(response));
                resp_header.Sequence = header.Sequence;
                _server.SendResponse(conn_id, resp_header, &response, sizeof(response));
                return;
            }

            const auto *request = reinterpret_cast<const SubscribeRequest *>(body);
            _server.SubscribeOrderBook(conn_id, request->SymbolId);

            SimpleResponse response(ErrorCode::OK);
            MsgHeader resp_header(MsgType::SIMPLE_RESPONSE, Flags::RESPONSE, sizeof(response));
            resp_header.Sequence = header.Sequence;
            _server.SendResponse(conn_id, resp_header, &response, sizeof(response));
        }

        void RequestHandler::HandleSubscribeOrders(uint16_t conn_id, const MsgHeader &header, const uint8_t *body, size_t body_len)
        {
            if (body_len < sizeof(SubscribeRequest))
            {
                SimpleResponse response(ErrorCode::ORDER_PARAMETER_INVALID);
                MsgHeader resp_header(MsgType::SIMPLE_RESPONSE, Flags::RESPONSE, sizeof(response));
                resp_header.Sequence = header.Sequence;
                _server.SendResponse(conn_id, resp_header, &response, sizeof(response));
                return;
            }

            const auto *request = reinterpret_cast<const SubscribeRequest *>(body);
            _server.SubscribeOrders(conn_id, request->SymbolId);

            SimpleResponse response(ErrorCode::OK);
            MsgHeader resp_header(MsgType::SIMPLE_RESPONSE, Flags::RESPONSE, sizeof(response));
            resp_header.Sequence = header.Sequence;
            _server.SendResponse(conn_id, resp_header, &response, sizeof(response));
        }

        void RequestHandler::HandleHeartbeat(uint16_t conn_id, const MsgHeader &header, const uint8_t *body, size_t body_len)
        {
            (void)body;
            (void)body_len;


            MsgHeader resp_header(MsgType::HEARTBEAT_RESP, Flags::HEARTBEAT, 0);
            resp_header.Sequence = header.Sequence;
            _server.SendResponse(conn_id, resp_header);
        }

        void RequestHandler::HandleAuth(uint16_t conn_id, const MsgHeader &header, const uint8_t *body, size_t body_len)
        {

            auto send_auth_error = [this, conn_id, &header](ErrorCode ec) {
                AuthResponse response{static_cast<uint8_t>(ec), {}, 0, 0};
                MsgHeader resp_header(MsgType::AUTH_RESPONSE, Flags::RESPONSE, sizeof(response));
                resp_header.Sequence = header.Sequence;
                _server.SendResponse(conn_id, resp_header, &response, sizeof(response));
            };

            if (body_len < sizeof(AuthRequest))
            {
                send_auth_error(ErrorCode::NOT_AUTHENTICATED);
                return;
            }

            const auto *request = reinterpret_cast<const AuthRequest *>(body);

            std::array<uint8_t, 32> recovery_token;
            std::memcpy(recovery_token.data(), request->RecoveryToken, 32);
            bool has_recovery = false;
            for (auto b : recovery_token)
            {
                if (b != 0)
                {
                    has_recovery = true;
                    break;
                }
            }

            if (has_recovery)
            {
                auto recover_result = _server.GetSessionManager().Recover(recovery_token, conn_id);
                if (recover_result.session)
                {
                    if (recover_result.had_old_conn)
                    {
                        _server.CleanupOldConnection(recover_result.old_conn_id);
                    }
                    _server.SetAuthenticated(conn_id, true);
                    _server.SetSessionKey(conn_id, recover_result.session->token.data(), recover_result.session->token.size());

                    AuthResponse response{0, {}, 0, 0};
                    std::memcpy(response.SessionToken, recover_result.session->token.data(), recover_result.session->token.size());
                    response.AccountId = recover_result.session->account_id;
                    response.Role = static_cast<uint8_t>(recover_result.session->role);
                    MsgHeader resp_header(MsgType::AUTH_RESPONSE, Flags::RESPONSE, sizeof(response));
                    resp_header.Sequence = header.Sequence;
                    _server.SendResponse(conn_id, resp_header, &response, sizeof(response));
                    return;
                }
            }

            std::string api_key_id(request->ApiKeyId, strnlen(request->ApiKeyId, sizeof(request->ApiKeyId)));
            if (api_key_id.empty())
            {
                send_auth_error(ErrorCode::NOT_AUTHENTICATED);
                return;
            }

            ApiKeyInfo api_key_info = _server.GetApiKeyInfo(api_key_id);
            if (api_key_info.secret.empty())
            {
                send_auth_error(ErrorCode::NOT_AUTHENTICATED);
                return;
            }

            uint64_t timestamp = static_cast<uint64_t>(request->Timestamp);
            if (!_server.GetAntiReplayChecker().CheckTimestamp(timestamp, 30000))
            {
                send_auth_error(ErrorCode::AUTH_EXPIRED);
                return;
            }

            if (!_server.GetAntiReplayChecker().CheckNonce(reinterpret_cast<const uint8_t *>(request->Nonce), 16, timestamp))
            {
                send_auth_error(ErrorCode::REPLAY_DETECTED);
                return;
            }

            if (!HmacVerifier::VerifyAuthSignature(
                    reinterpret_cast<const uint8_t *>(api_key_info.secret.data()), api_key_info.secret.size(),
                    timestamp,
                    reinterpret_cast<const uint8_t *>(request->Nonce), 16,
                    api_key_id,
                    reinterpret_cast<const uint8_t *>(request->Signature), 32))
            {
                send_auth_error(ErrorCode::INVALID_SIGNATURE);
                return;
            }

            auto session = _server.GetSessionManager().Create(api_key_info.account_id, api_key_info.role, conn_id);
            if (!session)
            {
                send_auth_error(ErrorCode::NOT_AUTHENTICATED);
                return;
            }

            _server.SetAuthenticated(conn_id, true);
            _server.SetSessionKey(conn_id, session->token.data(), session->token.size());

            AuthResponse response{0, {}, 0, 0};
            std::memcpy(response.SessionToken, session->token.data(), session->token.size());
            response.AccountId = api_key_info.account_id;
            response.Role = static_cast<uint8_t>(api_key_info.role);
            MsgHeader resp_header(MsgType::AUTH_RESPONSE, Flags::RESPONSE, sizeof(response));
            resp_header.Sequence = header.Sequence;
            _server.SendResponse(conn_id, resp_header, &response, sizeof(response));

        }

        void RequestHandler::HandleEventAck(uint16_t conn_id, const MsgHeader &header, const uint8_t *body, size_t body_len)
        {

            if (body_len < sizeof(EventAck))
            {
                SimpleResponse response(ErrorCode::ORDER_PARAMETER_INVALID);
                MsgHeader resp_header(MsgType::SIMPLE_RESPONSE, Flags::RESPONSE, sizeof(response));
                resp_header.Sequence = header.Sequence;
                _server.SendResponse(conn_id, resp_header, &response, sizeof(response));
                return;
            }

            const auto *request = reinterpret_cast<const EventAck *>(body);

            _server.RecordAck(conn_id, request->EventId);

            SimpleResponse response(ErrorCode::OK);
            MsgHeader resp_header(MsgType::SIMPLE_RESPONSE, Flags::RESPONSE, sizeof(response));
            resp_header.Sequence = header.Sequence;
            _server.SendResponse(conn_id, resp_header, &response, sizeof(response));
        }

        void RequestHandler::HandleReconcileRequest(uint16_t conn_id, const MsgHeader &header, const uint8_t *body, size_t body_len)
        {

            if (body_len < sizeof(ReconcileRequest))
            {
                ReconcileResponseHeader response{static_cast<uint8_t>(ErrorCode::ORDER_PARAMETER_INVALID), 0, 0, 0, 0, 0, 0};
                MsgHeader resp_header(MsgType::RECONCILE_RESPONSE, Flags::RESPONSE, sizeof(response));
                resp_header.Sequence = header.Sequence;
                _server.SendResponse(conn_id, resp_header, &response, sizeof(response));
                return;
            }

            const auto *request = reinterpret_cast<const ReconcileRequest *>(body);
            uint32_t symbol_id = request->SymbolId;

            const auto *order_book = _market.GetOrderBook(symbol_id);
            if (!order_book)
            {
                ReconcileResponseHeader response{static_cast<uint8_t>(ErrorCode::ORDER_BOOK_NOT_FOUND), symbol_id, 0, 0, 0, 0, 0};
                MsgHeader resp_header(MsgType::RECONCILE_RESPONSE, Flags::RESPONSE, sizeof(response));
                resp_header.Sequence = header.Sequence;
                _server.SendResponse(conn_id, resp_header, &response, sizeof(response));
                return;
            }

            uint32_t order_count = 0;
            uint32_t level_count = 0;
            for (const auto &level : order_book->bids())
            {
                (void)level;
                ++level_count;
                order_count += level.Orders;
            }
            for (const auto &level : order_book->asks())
            {
                (void)level;
                ++level_count;
                order_count += level.Orders;
            }

            ReconcileResponseHeader response{
                static_cast<uint8_t>(ErrorCode::OK),
                symbol_id,
                0, // LastTradeId
                0, // LastTradePrice
                0, // LastTradeQuantity
                order_count,
                level_count
            };
            MsgHeader resp_header(MsgType::RECONCILE_RESPONSE, Flags::RESPONSE, sizeof(response));
            resp_header.Sequence = header.Sequence;
            _server.SendResponse(conn_id, resp_header, &response, sizeof(response));
        }

        void RequestHandler::HandleSnapshot(uint16_t conn_id, const MsgHeader &header, const uint8_t *body, size_t body_len)
        {
            if (!CheckRole(conn_id, Role::ADMIN))
            {
                SnapshotResponse response{static_cast<uint8_t>(ErrorCode::NOT_AUTHORIZED), 0, 0, 0, 0};
                MsgHeader resp_header(MsgType::SNAPSHOT_RESPONSE, Flags::RESPONSE, sizeof(response));
                resp_header.Sequence = header.Sequence;
                _server.SendResponse(conn_id, resp_header, &response, sizeof(response));
                return;
            }

            (void)body;
            (void)body_len;

            if (!_snapshot_manager)
            {
                SnapshotResponse response{1, 0, 0, 0, 0};
                MsgHeader resp_header(MsgType::SNAPSHOT_RESPONSE, Flags::RESPONSE, sizeof(response));
                resp_header.Sequence = header.Sequence;
                _server.SendResponse(conn_id, resp_header, &response, sizeof(response));
                return;
            }

            _snapshot_manager->SetPauseMatchingCallback([this]() { _market.DisableMatching(); });
            _snapshot_manager->SetResumeMatchingCallback([this]() { _market.EnableMatching(); });

            uint64_t wal_lsn = _wal_writer ? _wal_writer->CurrentLSN() : 0;
            bool success = _snapshot_manager->TakeSnapshot(_market, wal_lsn);

            if (success)
            {
                uint64_t timestamp_ns = std::chrono::duration_cast<std::chrono::nanoseconds>(
                    std::chrono::system_clock::now().time_since_epoch()).count();
                SnapshotResponse response{0, timestamp_ns, wal_lsn,
                    static_cast<uint32_t>(_market.symbols().size()),
                    static_cast<uint32_t>(_market.orders().size())};
                MsgHeader resp_header(MsgType::SNAPSHOT_RESPONSE, Flags::RESPONSE, sizeof(response));
                resp_header.Sequence = header.Sequence;
                _server.SendResponse(conn_id, resp_header, &response, sizeof(response));
            }
            else
            {
                SnapshotResponse response{1, 0, 0, 0, 0};
                MsgHeader resp_header(MsgType::SNAPSHOT_RESPONSE, Flags::RESPONSE, sizeof(response));
                resp_header.Sequence = header.Sequence;
                _server.SendResponse(conn_id, resp_header, &response, sizeof(response));
            }
        }

        OrderProto RequestHandler::ConvertOrder(const CppTrader::Matching::Order &order)
        {
            OrderProto proto;
            proto.Id = order.Id;
            proto.SymbolId = order.SymbolId;
            proto.Type = static_cast<uint8_t>(order.Type);
            proto.Side = static_cast<uint8_t>(order.Side);
            proto.Price = order.Price;
            proto.StopPrice = order.StopPrice;
            proto.Quantity = order.Quantity;
            proto.ExecutedQuantity = order.ExecutedQuantity;
            proto.LeavesQuantity = order.LeavesQuantity;
            proto.TimeInForce = static_cast<uint8_t>(order.TimeInForce);
            proto.MaxVisibleQuantity = order.MaxVisibleQuantity;
            proto.Slippage = order.Slippage;
            proto.TrailingDistance = order.TrailingDistance;
            proto.TrailingStep = order.TrailingStep;
            proto.AccountId = order.AccountId;
            proto.StpPolicy = static_cast<uint8_t>(order.StpPolicy);
            return proto;
        }

        SymbolProto RequestHandler::ConvertSymbol(const CppTrader::Matching::Symbol &symbol)
        {
            SymbolProto proto;
            proto.Id = symbol.Id;
            for (size_t i = 0; i < 8; ++i)
                proto.Name[i] = symbol.Name[i];
            return proto;
        }

        LevelProto RequestHandler::ConvertLevel(const CppTrader::Matching::Level &level)
        {
            return LevelProto{level.Price, level.TotalVolume, level.VisibleVolume, level.Orders};
        }

        CppTrader::Matching::Order RequestHandler::ConvertOrderProto(const OrderProto &proto)
        {
            return CppTrader::Matching::Order(
                proto.Id,
                proto.SymbolId,
                proto.AccountId,
                ConvertSTPPolicy(static_cast<Protocol::STPPolicy>(proto.StpPolicy)),
                ConvertOrderType(static_cast<OrderType>(proto.Type)),
                ConvertOrderSide(static_cast<OrderSide>(proto.Side)),
                proto.Price,
                proto.StopPrice,
                proto.Quantity,
                ConvertOrderTimeInForce(static_cast<OrderTimeInForce>(proto.TimeInForce)),
                proto.MaxVisibleQuantity,
                proto.Slippage,
                proto.TrailingDistance,
                proto.TrailingStep);
        }

        CppTrader::Matching::Symbol RequestHandler::ConvertSymbolProto(const SymbolProto &proto)
        {
            return CppTrader::Matching::Symbol(proto.Id, proto.Name);
        }

        CppTrader::Matching::OrderType RequestHandler::ConvertOrderType(OrderType type)
        {
            return static_cast<CppTrader::Matching::OrderType>(static_cast<uint8_t>(type));
        }

        CppTrader::Matching::OrderSide RequestHandler::ConvertOrderSide(OrderSide side)
        {
            return static_cast<CppTrader::Matching::OrderSide>(static_cast<uint8_t>(side));
        }

        CppTrader::Matching::OrderTimeInForce RequestHandler::ConvertOrderTimeInForce(OrderTimeInForce tif)
        {
            return static_cast<CppTrader::Matching::OrderTimeInForce>(static_cast<uint8_t>(tif));
        }

        CppTrader::Matching::STPPolicy RequestHandler::ConvertSTPPolicy(Protocol::STPPolicy policy)
        {
            return static_cast<CppTrader::Matching::STPPolicy>(static_cast<uint8_t>(policy));
        }

        ErrorCode RequestHandler::ConvertMatchingError(CppTrader::Matching::ErrorCode error)
        {
            return static_cast<ErrorCode>(static_cast<uint8_t>(error));
        }

    } // namespace Protocol
} // namespace CppTrader