# CppTrader C++ 与 Java 协议完整解释文档

## 一、协议总体架构

CppTrader 交易系统采用 **C++ 撮合引擎 + Java 管理后台** 的双进程架构，两者之间通过**自研二进制 TCP 协议**通信。

```
┌──────────────────────────────────────────┐
│         Java Admin (Spring Boot)         │
│  ┌────────────┐  ┌────────────────────┐  │
│  │  Trading   │  │ ProtocolClient     │  │
│  │  Controller│─>│ Service            │  │
│  └────────────┘  └────────┬───────────┘  │
│                   ┌───────┴────────┐      │
│                   │ NettyTcpBackend│      │
│                   │ (Netty 4.x)    │      │
│                   └───────┬────────┘      │
└───────────────────────────┼──────────────┘
                            │ TCP (自研二进制协议)
                            │ 默认端口: 50059
┌───────────────────────────┼──────────────┐
│                   ┌───────┴────────┐      │
│                   │  TcpBackend    │      │
│                   │  (asio)        │      │
│                   └───────┬────────┘      │
│  ┌────────────┐  ┌───────┴────────────┐  │
│  │  Market    │  │ ProtocolServer +   │  │
│  │  Manager   │<-│ RequestHandler     │  │
│  └────────────┘  └────────────────────┘  │
│           C++ Trading Core                │
└──────────────────────────────────────────┘
```

---

## 二、协议帧格式

### 2.1 整体帧结构

每条协议消息由 **8 字节定长 Header** + **变长 Body** 组成：

```
 偏移量    0        1        2        3        4        5        6        7
      ┌────────┬────────┬────────┬────────┬────────┬────────┬────────┬────────┐
      │ Magic  │ Magic  │Version │MsgType │ Flags  │Reserved│BodyLen │BodyLen │
      │  (LSB) │  (MSB) │        │        │        │        │ (LSB)  │ (MSB)  │
      └────────┴────────┴────────┴────────┴────────┴────────┴────────┴────────┘
      ├──────── Header (8 字节) ────────────────────────┤├──── Body (N 字节) ──→
```

### 2.2 Header 字段详解

| 字段 | C++ 类型 | Java 类型 | 大小 | 偏移 | 说明 |
|------|---------|----------|------|------|------|
| Magic | `uint16_t` | `short` | 2B | 0 | 魔数，固定值 `0x5452`（小端序字节为 `0x52, 0x54`，即 "TR"） |
| Version | `uint8_t` | `byte` | 1B | 2 | 协议版本，当前为 `1` |
| MsgType | `uint8_t` (enum) | `byte` | 1B | 3 | 消息类型，见下文枚举 |
| Flags | `uint8_t` | `byte` | 1B | 4 | 消息标志位 |
| Reserved | `uint8_t` | `byte` | 1B | 5 | 保留字段，填 `0` |
| BodyLen | `uint16_t` | `short` | 2B | 6 | Body 部分字节数（小端序） |

**关键约束：**
- 所有字段均为**小端序 (Little Endian)**
- C++ 端使用 `#pragma pack(push, 1)` 紧凑对齐，`sizeof(MsgHeader) == 8`
- Java 端使用 `ByteBuffer.order(ByteOrder.LITTLE_ENDIAN)`

### 2.3 Flags 标志位定义

| 标志 | 值 | C++ 定义 | Java 定义 | 说明 |
|------|-----|---------|----------|------|
| NONE | 0x00 | `Flags::NONE` | - | 无标志 |
| REQUEST | 0x01 | `Flags::REQUEST` | `FLAG_REQUEST` | 请求消息 |
| RESPONSE | 0x02 | `Flags::RESPONSE` | `FLAG_RESPONSE` | 响应消息 |
| PUSH | 0x04 | `Flags::PUSH` | `FLAG_PUSH` | 推送消息（服务端主动推送） |
| ERROR | 0x08 | `Flags::ERROR` | `FLAG_ERROR` | 错误消息 |
| HEARTBEAT | 0x10 | `Flags::HEARTBEAT` | `FLAG_HEARTBEAT` | 心跳消息 |

标志位可组合使用，例如 `REQUEST | ERROR = 0x09` 表示错误响应。

---

## 三、消息类型枚举

### 3.1 请求消息 (0x01 - 0x12)

| 枚举名 | 值 | C++ MsgType | Java 常量 | 说明 |
|--------|-----|------------|----------|------|
| ADD_SYMBOL_REQUEST | 0x01 | `MsgType::ADD_SYMBOL_REQUEST` | `ADD_SYMBOL_REQ` | 添加交易品种 |
| DELETE_SYMBOL_REQUEST | 0x02 | `MsgType::DELETE_SYMBOL_REQUEST` | `DELETE_SYMBOL_REQ` | 删除交易品种 |
| GET_SYMBOL_REQUEST | 0x03 | `MsgType::GET_SYMBOL_REQUEST` | `GET_SYMBOL_REQ` | 查询交易品种 |
| ADD_ORDER_BOOK_REQUEST | 0x04 | `MsgType::ADD_ORDER_BOOK_REQUEST` | `ADD_ORDER_BOOK_REQ` | 添加订单簿 |
| DELETE_ORDER_BOOK_REQUEST | 0x05 | `MsgType::DELETE_ORDER_BOOK_REQUEST` | `DELETE_ORDER_BOOK_REQ` | 删除订单簿 |
| GET_ORDER_BOOK_REQUEST | 0x06 | `MsgType::GET_ORDER_BOOK_REQUEST` | `GET_ORDER_BOOK_REQ` | 查询订单簿 |
| ADD_ORDER_REQUEST | 0x07 | `MsgType::ADD_ORDER_REQUEST` | `ADD_ORDER_REQ` | 添加订单 |
| REDUCE_ORDER_REQUEST | 0x08 | `MsgType::REDUCE_ORDER_REQUEST` | `REDUCE_ORDER_REQ` | 减少订单数量 |
| MODIFY_ORDER_REQUEST | 0x09 | `MsgType::MODIFY_ORDER_REQUEST` | `MODIFY_ORDER_REQ` | 修改订单价格/数量 |
| MITIGATE_ORDER_REQUEST | 0x0A | `MsgType::MITIGATE_ORDER_REQUEST` | `MITIGATE_ORDER_REQ` | 触发订单 |
| REPLACE_ORDER_REQUEST | 0x0B | `MsgType::REPLACE_ORDER_REQUEST` | `REPLACE_ORDER_REQ` | 替换订单 |
| DELETE_ORDER_REQUEST | 0x0C | `MsgType::DELETE_ORDER_REQUEST` | `DELETE_ORDER_REQ` | 删除订单 |
| EXECUTE_ORDER_REQUEST | 0x0D | `MsgType::EXECUTE_ORDER_REQUEST` | `EXECUTE_ORDER_REQ` | 执行订单 |
| GET_ORDER_REQUEST | 0x0E | `MsgType::GET_ORDER_REQUEST` | `GET_ORDER_REQ` | 查询订单 |
| ENABLE_MATCHING_REQUEST | 0x0F | `MsgType::ENABLE_MATCHING_REQUEST` | `ENABLE_MATCHING_REQ` | 启用撮合引擎 |
| DISABLE_MATCHING_REQUEST | 0x10 | `MsgType::DISABLE_MATCHING_REQUEST` | `DISABLE_MATCHING_REQ` | 禁用撮合引擎 |
| SUBSCRIBE_ORDER_BOOK_REQUEST | 0x11 | `MsgType::SUBSCRIBE_ORDER_BOOK_REQUEST` | `SUBSCRIBE_ORDER_BOOK_REQ` | 订阅订单簿更新 |
| SUBSCRIBE_ORDERS_REQUEST | 0x12 | `MsgType::SUBSCRIBE_ORDERS_REQUEST` | `SUBSCRIBE_ORDERS_REQ` | 订阅订单更新 |

### 3.2 响应消息 (0x41 - 0x44)

| 枚举名 | 值 | C++ MsgType | Java 常量 | 说明 |
|--------|-----|------------|----------|------|
| SYMBOL_RESPONSE | 0x41 | `MsgType::SYMBOL_RESPONSE` | `SYMBOL_RESP` | 品种响应 |
| ORDER_BOOK_RESPONSE | 0x42 | `MsgType::ORDER_BOOK_RESPONSE` | `ORDER_BOOK_RESP` | 订单簿响应 |
| ORDER_RESPONSE | 0x43 | `MsgType::ORDER_RESPONSE` | `ORDER_RESP` | 订单响应 |
| SIMPLE_RESPONSE | 0x44 | `MsgType::SIMPLE_RESPONSE` | `SIMPLE_RESP` | 简单响应（仅含错误码） |

### 3.3 推送消息 (0x81 - 0x82)

| 枚举名 | 值 | C++ MsgType | Java 常量 | 说明 |
|--------|-----|------------|----------|------|
| ORDER_BOOK_UPDATE_EVENT | 0x81 | `MsgType::ORDER_BOOK_UPDATE_EVENT` | `ORDER_BOOK_UPDATE_EVT` | 订单簿更新推送 |
| ORDER_UPDATE_EVENT | 0x82 | `MsgType::ORDER_UPDATE_EVENT` | `ORDER_UPDATE_EVT` | 订单更新推送 |

### 3.4 控制消息 (0xC0 - 0xC1)

| 枚举名 | 值 | C++ MsgType | Java 常量 | 说明 |
|--------|-----|------------|----------|------|
| HEARTBEAT_REQ | 0xC0 | `MsgType::HEARTBEAT_REQ` | `HEARTBEAT_REQ` | 心跳请求 |
| HEARTBEAT_RESP | 0xC1 | `MsgType::HEARTBEAT_RESP` | `HEARTBEAT_RESP` | 心跳响应 |

---

## 四、协议数据结构详解

### 4.1 SymbolProto（12 字节）

```
 偏移   0        3  4                       11
      ┌─────────┬────────────────────────────┐
      │ Id (4B) │       Name (8B)            │
      │ uint32  │       char[8]              │
      └─────────┴────────────────────────────┘
```

| 字段 | C++ 类型 | Java 类型 | 大小 | 说明 |
|------|---------|----------|------|------|
| Id | `uint32_t` | `int` | 4B | 品种 ID |
| Name | `char[8]` | `String` (UTF-8) | 8B | 品种名称，不足 8 字节补 `\0` |

**C++ 定义：** `include/trader/protocol/message.h` -> `SymbolProto`
**Java 编解码：** `ProtocolMessage.writeSymbolProto()` / `readSymbolProto()`

### 4.2 LevelProto（32 字节）

```
 偏移   0        7  8       15 16       23 24       31
      ┌─────────┬──────────┬──────────┬──────────┐
      │Price(8B)│TotalVol  │VisibleVol│ Orders   │
      │uint64_t │uint64_t  │uint64_t  │uint64_t  │
      └─────────┴──────────┴──────────┴──────────┘
```

| 字段 | C++ 类型 | Java 类型 | 大小 | 说明 |
|------|---------|----------|------|------|
| Price | `uint64_t` | `long` | 8B | 价格 |
| TotalVolume | `uint64_t` | `long` | 8B | 总挂单量 |
| VisibleVolume | `uint64_t` | `long` | 8B | 可见挂单量（冰山单隐藏部分不计） |
| Orders | `uint64_t` | `long` | 8B | 该价位挂单数 |

### 4.3 OrderProto（88 字节）

```
 偏移    0         7  8    11 12  13 14  15
      ┌──────────┬───────┬────┬────┐
      │  Id (8B) │SymId  │Type│Side│
      │ uint64_t │uint32 │u8  │u8  │
      ├──────────┴───────┴────┴────┼──────────┐
      │  16        23│ 24       31 │ 32    39  │
      │  Price (8B) │ StopPrice    │ Quantity  │
      │  uint64_t   │ uint64_t     │ uint64_t  │
      ├──────────────┼──────────────┼───────────┤
      │  40       47 │ 48       55  │ 56    63  │
      │  ExecQty(8B) │ LeavesQty    │ TIF  │P1  │
      │  uint64_t    │ uint64_t     │ u8   │u8  │
      ├──────────────┴──────────────┴──────┴────┤
      │  64       71 │ 72       79 │ 80     87  │
      │  MaxVisQty   │ Slippage    │TrailDist   │
      │  uint64_t    │ uint64_t    │ int64_t    │
      ├──────────────┼─────────────┼────────────┤
      │  88       95                              │
      │  TrailStep                                │
      │  int64_t                                  │
      └───────────────────────────────────────────┘
```

| 偏移 | 字段 | C++ 类型 | Java 类型 | 大小 | 说明 |
|------|------|---------|----------|------|------|
| 0 | Id | `uint64_t` | `long` | 8B | 订单 ID |
| 8 | SymbolId | `uint32_t` | `int` | 4B | 品种 ID |
| 12 | Type | `uint8_t` | `byte` | 1B | 订单类型（见枚举） |
| 13 | Side | `uint8_t` | `byte` | 1B | 买卖方向（见枚举） |
| 14 | Padding1 | `uint8_t` | - | 1B | 对齐填充（C++端） |
| 16 | Price | `uint64_t` | `long` | 8B | 价格 |
| 24 | StopPrice | `uint64_t` | `long` | 8B | 止损价 |
| 32 | Quantity | `uint64_t` | `long` | 8B | 委托数量 |
| 40 | ExecutedQuantity | `uint64_t` | `long` | 8B | 已成交数量 |
| 48 | LeavesQuantity | `uint64_t` | `long` | 8B | 剩余数量 |
| 56 | TimeInForce | `uint8_t` | `byte` | 1B | 有效期类型 |
| 57 | Padding1 | `uint8_t` | - | 1B | 对齐填充 |
| 64 | MaxVisibleQuantity | `uint64_t` | `long` | 8B | 最大可见数量（冰山单） |
| 72 | Slippage | `uint64_t` | `long` | 8B | 滑点 |
| 80 | TrailingDistance | `int64_t` | `long` | 8B | 追踪距离 |
| 88 | TrailingStep | `int64_t` | `long` | 8B | 追踪步长 |

> **注意**: C++ 端 `sizeof(OrderProto) == 88`，但 Java 端 `writeOrderProto()` 不写入 Padding1 字段（偏移14-15），实际写入 86 字节。这是两端实现的一个差异点，实际通信中以 C++ 端的 88 字节为准。

### 4.4 枚举类型

#### OrderSide（订单方向）

| 值 | C++ 枚举 | Java 常量 | 说明 |
|----|---------|----------|------|
| 0 | `OrderSide::BUY` | `OrderSide.BUY` | 买入 |
| 1 | `OrderSide::SELL` | `OrderSide.SELL` | 卖出 |

#### OrderType（订单类型）

| 值 | C++ 枚举 | Java 常量 | 说明 |
|----|---------|----------|------|
| 0 | `OrderType::MARKET` | `OrderType.LIMIT`* | 市价单 |
| 1 | `OrderType::LIMIT` | `OrderType.MARKET`* | 限价单 |
| 2 | `OrderType::STOP` | `OrderType.STOP` | 止损单 |
| 3 | `OrderType::STOP_LIMIT` | `OrderType.STOP_LIMIT` | 止损限价单 |
| 4 | `OrderType::TRAILING_STOP` | - | 追踪止损单 |
| 5 | `OrderType::TRAILING_STOP_LIMIT` | - | 追踪止损限价单 |

> **⚠️ 重要差异**: C++ 端 `MARKET=0, LIMIT=1`，Java 端 `LIMIT=0, MARKET=1`，两者值**恰好相反**！Java 端在编解码时需要注意这个映射关系。

#### OrderTimeInForce（有效期类型）

| 值 | C++ 枚举 | Java 常量 | 说明 |
|----|---------|----------|------|
| 0 | `OrderTimeInForce::GTC` | `TimeInForce.GTC` | Good Till Cancel |
| 1 | `OrderTimeInForce::IOC` | `TimeInForce.IOC` | Immediate Or Cancel |
| 2 | `OrderTimeInForce::FOK` | `TimeInForce.FOK` | Fill Or Kill |
| 3 | `OrderTimeInForce::AON` | `TimeInForce.AON` | All Or None |

#### ErrorCode（错误码）

| 值 | C++ 枚举 | Java 常量 | 说明 |
|----|---------|----------|------|
| 0 | `ErrorCode::OK` | `ErrorCode.OK` | 成功 |
| 1 | `ErrorCode::SYMBOL_DUPLICATE` | `ErrorCode.ERROR` | 品种重复 |
| 2 | `ErrorCode::SYMBOL_NOT_FOUND` | `ErrorCode.NOT_FOUND` | 品种不存在 |
| 3 | `ErrorCode::ORDER_BOOK_DUPLICATE` | `ErrorCode.ALREADY_EXISTS` | 订单簿重复 |
| 4 | `ErrorCode::ORDER_BOOK_NOT_FOUND` | - | 订单簿不存在 |
| 5 | `ErrorCode::ORDER_DUPLICATE` | - | 订单重复 |
| 6 | `ErrorCode::ORDER_NOT_FOUND` | - | 订单不存在 |
| 7 | `ErrorCode::ORDER_ID_INVALID` | - | 订单 ID 无效 |
| 8 | `ErrorCode::ORDER_TYPE_INVALID` | - | 订单类型无效 |
| 9 | `ErrorCode::ORDER_PARAMETER_INVALID` | - | 订单参数无效 |
| 10 | `ErrorCode::ORDER_QUANTITY_INVALID` | - | 订单数量无效 |

> **⚠️ 差异**: C++ 端有 11 个细分错误码，Java 端只定义了 5 个通用错误码。Java 端的 `ErrorCode` 与 C++ 端的值映射并非一一对应。

#### UpdateType（更新类型）

| 值 | C++ 枚举 | 说明 |
|----|---------|------|
| 0 | `UpdateType::NONE` | 无更新 |
| 1 | `UpdateType::ADD` | 新增 |
| 2 | `UpdateType::UPDATE` | 修改 |
| 3 | `UpdateType::DELETE` | 删除 |

---

## 五、请求/响应消息体详解

### 5.1 品种管理

#### AddSymbolRequest（Body: 12 字节）

```
Body = SymbolProto (12B)
```

| 字段 | 类型 | 说明 |
|------|------|------|
| Symbol.Id | uint32 | 品种 ID |
| Symbol.Name | char[8] | 品种名称 |

**C++ 结构体**: `AddSymbolRequest` (包含 `SymbolProto Symbol`)
**Java 类**: `AddSymbolRequest` (msgType=0x01, flags=0x01)

#### DeleteSymbolRequest（Body: 4 字节）

| 字段 | 类型 | 说明 |
|------|------|------|
| Id | uint32 | 品种 ID |

#### GetSymbolRequest（Body: 4 字节）

| 字段 | 类型 | 说明 |
|------|------|------|
| Id | uint32 | 品种 ID |

#### SymbolResponse（Body: 13 字节）

| 字段 | 类型 | 说明 |
|------|------|------|
| Error | uint8 | 错误码 |
| Symbol | SymbolProto (12B) | 品种信息 |

### 5.2 订单簿管理

#### AddOrderBookRequest（Body: 4 字节）

| 字段 | 类型 | 说明 |
|------|------|------|
| SymbolId | uint32 | 品种 ID |

#### DeleteOrderBookRequest（Body: 4 字节）

| 字段 | 类型 | 说明 |
|------|------|------|
| SymbolId | uint32 | 品种 ID |

#### GetOrderBookRequest（Body: 8 字节）

| 字段 | 类型 | 说明 |
|------|------|------|
| SymbolId | uint32 | 品种 ID |
| Depth | int32 | 深度（保留参数） |

#### OrderBookResponse（Body: 变长）

C++ 端 `OrderBookSnapshot` 结构（72 字节基础 + 变长 Level 数组）：

```
 偏移   0        3  4      35 36      67 68  69 70  71
      ┌─────────┬─────────┬─────────┬────┬────┐
      │SymbolId │BestBid  │BestAsk  │BidC│AskC│
      │ uint32  │LevelProto│LevelProto│u16 │u16 │
      └─────────┴─────────┴─────────┴────┴────┘
      │←── 4B ──→│←── 32B ─→│←── 32B ─→│2B │2B │
      ├──────────────────────────────────────────┤
      │  72+                              变长   │
      │  Bids[0]..Bids[BidCount-1]               │
      │  Asks[0]..Asks[AskCount-1]               │
      └──────────────────────────────────────────┘
```

| 字段 | 类型 | 大小 | 说明 |
|------|------|------|------|
| SymbolId | uint32 | 4B | 品种 ID |
| BestBid | LevelProto | 32B | 最优买价 |
| BestAsk | LevelProto | 32B | 最优卖价 |
| BidCount | uint16 | 2B | 买盘层级数 |
| AskCount | uint16 | 2B | 卖盘层级数 |
| Bids[] | LevelProto[] | BidCount × 32B | 买盘各层级 |
| Asks[] | LevelProto[] | AskCount × 32B | 卖盘各层级 |

**总大小** = 72 + BidCount × 32 + AskCount × 32

> **Java 端差异**: Java 端 `OrderBookResponse.decode()` 使用 `hasBestBid`/`hasBestAsk` 布尔标志（各 1 字节）替代 C++ 端始终写入的 BestBid/BestAsk，编解码格式与 C++ 端不完全一致。

### 5.3 订单操作

#### AddOrderRequest（Body: 88 字节）

```
Body = OrderProto (88B)
```

#### ReduceOrderRequest（Body: 16 字节）

| 字段 | 类型 | 大小 | 说明 |
|------|------|------|------|
| Id | uint64 | 8B | 订单 ID |
| Quantity | uint64 | 8B | 减少数量 |

#### ModifyOrderRequest（Body: 24 字节）

| 字段 | 类型 | 大小 | 说明 |
|------|------|------|------|
| Id | uint64 | 8B | 订单 ID |
| NewPrice | uint64 | 8B | 新价格 |
| NewQuantity | uint64 | 8B | 新数量 |

#### MitigateOrderRequest（Body: 24 字节）

| 字段 | 类型 | 大小 | 说明 |
|------|------|------|------|
| Id | uint64 | 8B | 订单 ID |
| NewPrice | uint64 | 8B | 新价格 |
| NewQuantity | uint64 | 8B | 新数量 |

#### ReplaceOrderRequest（Body: 32 字节）

| 字段 | 类型 | 大小 | 说明 |
|------|------|------|------|
| Id | uint64 | 8B | 原订单 ID |
| NewId | uint64 | 8B | 新订单 ID |
| NewPrice | uint64 | 8B | 新价格 |
| NewQuantity | uint64 | 8B | 新数量 |

#### DeleteOrderRequest（Body: 8 字节）

| 字段 | 类型 | 大小 | 说明 |
|------|------|------|------|
| Id | uint64 | 8B | 订单 ID |

#### ExecuteOrderRequest（Body: 24 字节）

| 字段 | 类型 | 大小 | 说明 |
|------|------|------|------|
| Id | uint64 | 8B | 订单 ID |
| Price | uint64 | 8B | 成交价格 |
| Quantity | uint64 | 8B | 成交数量 |

#### GetOrderRequest（Body: 8 字节）

| 字段 | 类型 | 大小 | 说明 |
|------|------|------|------|
| Id | uint64 | 8B | 订单 ID |

#### OrderResponse（Body: 89 字节）

| 字段 | 类型 | 大小 | 说明 |
|------|------|------|------|
| Error | uint8 | 1B | 错误码 |
| Order | OrderProto | 88B | 订单信息 |

### 5.4 撮合控制

#### EnableMatchingRequest / DisableMatchingRequest（Body: 0 字节）

无 Body，仅 Header。

### 5.5 订阅

#### SubscribeRequest（Body: 4 字节）

用于 `SUBSCRIBE_ORDER_BOOK_REQUEST` 和 `SUBSCRIBE_ORDERS_REQUEST`。

| 字段 | 类型 | 大小 | 说明 |
|------|------|------|------|
| SymbolId | uint32 | 4B | 要订阅的品种 ID |

### 5.6 简单响应

#### SimpleResponse（Body: 1 字节）

| 字段 | 类型 | 大小 | 说明 |
|------|------|------|------|
| Error | uint8 | 1B | 错误码 |

### 5.7 心跳

#### HEARTBEAT_REQ / HEARTBEAT_RESP（Body: 0 字节）

仅 8 字节 Header，无 Body。

---

## 六、推送消息体详解

### 6.1 OrderBookUpdateEvent（Body: 40 字节）

C++ 端结构：

```
 偏移   0        3  4   5  6   7  8      39
      ┌─────────┬───┬───┬───┬──────────┐
      │SymbolId │IsT│Typ│LT │Pad│ Level │
      │ uint32  │u8 │u8 │u8 │u8 │32B    │
      └─────────┴───┴───┴───┴───┴──────┘
```

| 字段 | 类型 | 大小 | 说明 |
|------|------|------|------|
| SymbolId | uint32 | 4B | 品种 ID |
| IsTop | uint8 | 1B | 是否为最优价（1=是, 0=否） |
| Type | uint8 | 1B | 更新类型（UpdateType: 1=ADD, 2=UPDATE, 3=DELETE） |
| LevelType | uint8 | 1B | 层级方向（0=Bid, 1=Ask） |
| Padding1 | uint8 | 1B | 对齐填充 |
| Level | LevelProto | 32B | 盘口层级数据 |

**触发时机**: 撮合引擎 `onAddLevel`/`onUpdateLevel`/`onDeleteLevel` 回调时推送。

> **Java 端差异**: Java 端 `OrderBookUpdateEvent` 使用 String 类型的 `updateType`（如 "ADD"/"UPDATE"/"DELETE"），编解码格式与 C++ 端的数值类型不同。

### 6.2 OrderUpdateEvent（Body: 105 字节）

C++ 端结构：

```
 偏移   0     1     89    97     105
      ┌─────┬──────────┬──────────┐
      │Action│  Order   │ExecPrice │
      │ u8   │ 88B      │+ExecQty  │
      │      │OrderProto│ 16B      │
      └─────┴──────────┴──────────┘
```

| 字段 | 类型 | 大小 | 说明 |
|------|------|------|------|
| Action | uint8 | 1B | 操作类型（1=ADD, 2=UPDATE, 3=DELETE, 4=EXECUTE） |
| Order | OrderProto | 88B | 订单数据 |
| ExecutePrice | uint64 | 8B | 成交价格（仅 Action=4 时有效） |
| ExecuteQuantity | uint64 | 8B | 成交数量（仅 Action=4 时有效） |

**触发时机**:
- `onAddOrder` -> Action=1
- `onUpdateOrder` -> Action=2
- `onDeleteOrder` -> Action=3
- `onExecuteOrder` -> Action=4（含成交价量）

> **Java 端差异**: Java 端 `OrderUpdateEvent` 使用 String 类型的 `action`（如 "ADD"/"UPDATE"/"DELETE"/"EXECUTE"），以及 `hasExecute` 布尔标志，编解码格式与 C++ 端不同。

---

## 七、请求/响应对应关系

| 请求 MsgType | 请求 Body | 响应 MsgType | 响应 Body | 说明 |
|-------------|----------|-------------|----------|------|
| 0x01 ADD_SYMBOL | AddSymbolRequest (12B) | 0x41 SYMBOL_RESP | SymbolResponse (13B) | 添加品种 |
| 0x02 DELETE_SYMBOL | DeleteSymbolRequest (4B) | 0x41 SYMBOL_RESP | SymbolResponse (13B) | 删除品种 |
| 0x03 GET_SYMBOL | GetSymbolRequest (4B) | 0x41 SYMBOL_RESP | SymbolResponse (13B) | 查询品种 |
| 0x04 ADD_ORDER_BOOK | AddOrderBookRequest (4B) | 0x44 SIMPLE_RESP | SimpleResponse (1B) | 添加订单簿 |
| 0x05 DELETE_ORDER_BOOK | DeleteOrderBookRequest (4B) | 0x44 SIMPLE_RESP | SimpleResponse (1B) | 删除订单簿 |
| 0x06 GET_ORDER_BOOK | GetOrderBookRequest (8B) | 0x42 ORDER_BOOK_RESP | OrderBookSnapshot (变长) | 查询订单簿 |
| 0x07 ADD_ORDER | AddOrderRequest (88B) | 0x43 ORDER_RESP | OrderResponse (89B) | 添加订单 |
| 0x08 REDUCE_ORDER | ReduceOrderRequest (16B) | 0x43 ORDER_RESP | OrderResponse (89B) | 减少订单 |
| 0x09 MODIFY_ORDER | ModifyOrderRequest (24B) | 0x43 ORDER_RESP | OrderResponse (89B) | 修改订单 |
| 0x0A MITIGATE_ORDER | MitigateOrderRequest (24B) | 0x43 ORDER_RESP | OrderResponse (89B) | 触发订单 |
| 0x0B REPLACE_ORDER | ReplaceOrderRequest (32B) | 0x43 ORDER_RESP | OrderResponse (89B) | 替换订单 |
| 0x0C DELETE_ORDER | DeleteOrderRequest (8B) | 0x43 ORDER_RESP | OrderResponse (89B) | 删除订单 |
| 0x0D EXECUTE_ORDER | ExecuteOrderRequest (24B) | 0x43 ORDER_RESP | OrderResponse (89B) | 执行订单 |
| 0x0E GET_ORDER | GetOrderRequest (8B) | 0x43 ORDER_RESP | OrderResponse (89B) | 查询订单 |
| 0x0F ENABLE_MATCHING | (空) | 0x44 SIMPLE_RESP | SimpleResponse (1B) | 启用撮合 |
| 0x10 DISABLE_MATCHING | (空) | 0x44 SIMPLE_RESP | SimpleResponse (1B) | 禁用撮合 |
| 0x11 SUBSCRIBE_OB | SubscribeRequest (4B) | 0x44 SIMPLE_RESP | SimpleResponse (1B) | 订阅订单簿 |
| 0x12 SUBSCRIBE_ORDERS | SubscribeRequest (4B) | 0x44 SIMPLE_RESP | SimpleResponse (1B) | 订阅订单 |
| 0xC0 HEARTBEAT_REQ | (空) | 0xC1 HEARTBEAT_RESP | (空) | 心跳 |

---

## 八、帧解码与粘包/半包处理

TCP 是流式协议，存在粘包（多个消息合并在一次 read 中）和半包（一个消息被拆分到多次 read 中）问题。两端均使用**状态机 FrameDecoder** 解决。

### 8.1 解码状态机

```
          ┌──────────┐
          │   HEAD   │ ← 等待 8 字节 Header
          └────┬─────┘
               │ 收到完整 Header
               │ 校验 Magic + Version
               ▼
          ┌──────────┐
          │   BODY   │ ← 等待 BodyLen 字节 Body
          └────┬─────┘
               │ 收到完整 Body
               │ 输出一帧
               ▼
          返回 HEAD 状态
```

### 8.2 C++ 端 FrameDecoder

**源文件**: `include/trader/protocol/frame_decoder.h` + `source/protocol/frame_decoder.cpp`

```cpp
class FrameDecoder {
    // 状态
    enum class DecodeState { HEAD, BODY };

    // 核心方法
    void Feed(const uint8_t* data, size_t size);     // 喂入原始字节
    std::optional<Frame> TryDecode();                  // 尝试解码一帧
    size_t ProcessFrames(const FrameHandler& handler); // 处理所有完整帧

    // 错误恢复
    void SyncToMagic();  // 搜索 0x52 0x54 重新同步
};
```

**工作流程**:
1. `Feed()` 将字节追加到内部 `_buffer`
2. `TryDecode()` 根据状态机解析:
   - HEAD 状态: 等待 8 字节，`memcpy` 到 `_current_header`，校验 Magic/Version
   - BODY 状态: 等待 `Length` 字节，提取 body，返回 `Frame`
3. 校验失败时调用 `SyncToMagic()` 丢弃字节直到找到魔数

### 8.3 Java 端 FrameDecoder

**源文件**: `protocol/FrameDecoder.java`

```java
public class FrameDecoder {
    enum State { HEAD, BODY }

    void feed(byte[] data, int offset, int len);  // 喂入字节
    boolean hasCompleteFrame();                     // 是否有完整帧
    byte[] tryDecode();                             // 解码一帧
    List<byte[]> decodeAll();                       // 解码所有帧
}
```

**差异**: Java 端不实现 `SyncToMagic()` 错误恢复，而是在 Header 校验失败时直接重置状态。

### 8.4 Netty 集成

Java 端通过 Netty Pipeline 集成帧解码：

```
Pipeline: ProtocolEncoder → ProtocolDecoder → ClientHandler
```

- `ProtocolEncoder` (`MessageToByteEncoder<ProtocolMessage>`): 调用 `msg.toBytes()` 写出
- `ProtocolDecoder` (`ByteToMessageDecoder`): 内部使用 `FrameDecoder`，输出 `byte[]`
- `ClientHandler` (`SimpleChannelInboundHandler<byte[]>`): 回调或入队

---

## 九、C++ 端协议实现详解

### 9.1 整体架构

```
server_main.cpp
    │
    ├── asio::io_context
    ├── TcpBackend(io_context, port=50059)
    ├── MarketManager
    ├── ProtocolServer(backend, market)
    │   ├── INetworkBackend* _backend
    │   ├── handlers[msg_type] → RequestHandler
    │   ├── _order_book_subscriptions[conn_id] → {symbol_id}
    │   └── _order_subscriptions[conn_id] → {symbol_id}
    └── RequestHandler(server, market) : MarketHandler
        ├── RegisterHandlers()  // 注册 19 种消息处理器
        ├── HandleXxx()         // 各请求处理函数
        └── onXxx()             // MarketHandler 回调 → 推送
```

### 9.2 网络后端抽象

```cpp
class INetworkBackend {
    virtual bool init() = 0;
    virtual void poll() = 0;
    virtual void send(uint16_t conn_id, const void* data, size_t len) = 0;
    virtual void broadcast(const void* data, size_t len) = 0;
};
```

- **TcpBackend**: 基于 asio，异步接收 + 同步发送
- **DpdkBackend**: 条件编译 `CPPTRADER_DPDK_ENABLED`，内核旁路

### 9.3 消息发送流程

```cpp
// 1. 构造 Header
MsgHeader header(MsgType::SYMBOL_RESPONSE, Flags::RESPONSE, sizeof(response));

// 2. 拼接完整帧
vector<uint8_t> frame(sizeof(MsgHeader) + body_len);
memcpy(frame.data(), &header, sizeof(MsgHeader));
memcpy(frame.data() + sizeof(MsgHeader), body, body_len);

// 3. 发送
backend->send(conn_id, frame.data(), frame.size());    // 单播
backend->broadcast(frame.data(), frame.size());          // 广播
```

### 9.4 消息接收流程

```cpp
// TcpBackend::HandleRead()
conn->Decoder.Feed(buffer.data(), bytes_transferred);
while (auto frame = conn->Decoder.TryDecode()) {
    message_handler(conn->Id, frame->Header, frame->BodyBytes(), frame->Body.size());
}

// ProtocolServer::OnMessage()
auto it = _handlers.find(header.Type);
it->second(conn_id, header, body, body_len);

// RequestHandler::HandleXxx()
const auto* request = reinterpret_cast<const AddSymbolRequest*>(body);
auto error = _market.AddSymbol(ConvertSymbolProto(request->Symbol));
// 构造响应并发送
```

### 9.5 推送机制

`RequestHandler` 继承 `Matching::MarketHandler`，当撮合引擎产生事件时自动回调：

```cpp
void RequestHandler::onAddLevel(const OrderBook& ob, const Level& level, bool top) {
    OrderBookUpdateEvent event = { ob.symbol().Id, top?1:0, UpdateType::ADD, level.IsBid()?0:1, 0, ConvertLevel(level) };
    MsgHeader header(MsgType::ORDER_BOOK_UPDATE_EVENT, Flags::PUSH, sizeof(event));
    _server.BroadcastToSymbol(ob.symbol().Id, header, &event, sizeof(event));
}
```

**BroadcastToSymbol** 只发送给订阅了该 symbol_id 的连接：

```cpp
for (auto& [conn_id, symbols] : _order_book_subscriptions) {
    if (symbols.find(symbol_id) != symbols.end())
        _backend->send(conn_id, frame.data(), frame.size());
}
```

### 9.6 内部模型与协议结构体转换

```cpp
// Matching::Order → OrderProto
OrderProto ConvertOrder(const Matching::Order& order);

// OrderProto → Matching::Order
Matching::Order ConvertOrderProto(const OrderProto& proto);

// Matching::Symbol → SymbolProto
SymbolProto ConvertSymbol(const Matching::Symbol& symbol);

// Matching::Level → LevelProto
LevelProto ConvertLevel(const Matching::Level& level);
```

---

## 十、Java 端协议实现详解

### 10.1 整体架构

```
ProtocolClientService (Spring Service)
    │
    ├── INetworkBackend backend
    │   ├── NettyTcpBackend (生产)
    │   └── DpdkJniBackend (JNI)
    │
    ├── ProtocolStreamSubscriber (订阅管理)
    │
    ├── CompletableFuture<byte[]> currentRequestFuture (请求/响应匹配)
    │
    └── 心跳线程 + 接收线程
```

### 10.2 消息编解码

所有消息继承 `ProtocolMessage` 抽象类：

```java
public abstract class ProtocolMessage {
    byte msgType;
    byte flags;

    abstract void encode(ByteBuffer buf);    // 编码 Body
    abstract void decode(ByteBuffer buf);    // 解码 Body
    abstract int getBodySize();              // Body 字节数

    byte[] toBytes() {                       // 完整序列化
        ByteBuffer buf = ByteBuffer.allocate(getTotalSize());
        buf.order(ByteOrder.LITTLE_ENDIAN);
        writeHeader(buf);                    // 写 8 字节 Header
        encode(buf);                         // 写 Body
        return buf.array();
    }

    void fromBytes(byte[] data) {            // 完整反序列化
        ByteBuffer buf = ByteBuffer.wrap(data);
        buf.order(ByteOrder.LITTLE_ENDIAN);
        readHeader(buf);                     // 读 Header + 校验
        decode(buf);                         // 读 Body
    }
}
```

### 10.3 请求/响应匹配

协议 Header 中**没有请求 ID 字段**，Java 端使用 `CompletableFuture` 进行简单的请求/响应配对：

```java
// 发送请求
CompletableFuture<byte[]> future = new CompletableFuture<>();
synchronized (requestLock) {
    currentRequestFuture = future;  // 同一时刻只能有一个未完成请求
}
backend.send(data);

// 接收响应
if ((flags & FLAG_RESPONSE) != 0) {
    synchronized (requestLock) {
        currentRequestFuture.complete(data);  // 完成当前请求
    }
}
```

> **限制**: 同一时刻只能有一个未完成的请求。新请求会覆盖旧请求的 Future。

### 10.4 心跳保活

```java
// 定时发送心跳
void heartbeatLoop() {
    while (running) {
        Thread.sleep(intervalMs);
        if (now - lastRecvTime > timeoutMs) {
            disconnect();          // 超时断连
            scheduleReconnect();   // 指数退避重连
        }
        if (now - lastSendTime >= intervalMs) {
            sendHeartbeat();       // 发送 HEARTBEAT_REQ
        }
    }
}
```

### 10.5 断线重连

```java
void scheduleReconnect() {
    long delay = 1000;    // 初始 1 秒
    long maxDelay = 5000; // 最大 5 秒
    while (!connected && running) {
        Thread.sleep(delay);
        connect();
        delay = Math.min(delay * 2, maxDelay);  // 指数退避
    }
}
```

### 10.6 推送消息处理

```java
void onMessageReceived(byte[] data) {
    // 解析 Header
    byte msgType = ...;
    byte flags = ...;

    if ((flags & FLAG_PUSH) != 0) {
        handlePushMessage(msgType, data);
    }
}

void handlePushMessage(byte msgType, byte[] data) {
    if (msgType == ORDER_BOOK_UPDATE_EVT) {
        OrderBookUpdateEvent event = new OrderBookUpdateEvent();
        event.fromBytes(data);
        streamSubscriber.onOrderBookUpdate(event);
    } else if (msgType == ORDER_UPDATE_EVT) {
        OrderUpdateEvent event = new OrderUpdateEvent();
        event.fromBytes(data);
        streamSubscriber.onOrdersUpdate(event);
    }
}
```

### 10.7 订阅管理

```java
// ProtocolStreamSubscriber
CopyOnWriteArraySet<Integer> orderBookSubscriptions;
CopyOnWriteArraySet<Integer> ordersSubscriptions;

void subscribeOrderBook(int symbolId) {
    orderBookSubscriptions.add(symbolId);
    service.sendSubscribeOrderBook(symbolId);
}

void restoreSubscriptions() {
    // 重连后自动恢复所有订阅
    for (int symbolId : orderBookSubscriptions) {
        service.sendSubscribeOrderBook(symbolId);
    }
}
```

---

## 十一、完整交互流程示例

### 11.1 添加品种 + 添加订单簿 + 下单

```
Java                                          C++
  │                                             │
  │  ── ADD_SYMBOL_REQ (0x01) ──────────────→   │
  │    Header: Magic=0x5452, Ver=1, Type=0x01   │
  │    Flags=0x01(REQUEST), BodyLen=12           │
  │    Body: {Id=1, Name="BTCUSD"}              │
  │                                             │ MarketManager.AddSymbol()
  │  ←── SYMBOL_RESP (0x41) ────────────────    │
  │    Flags=0x02(RESPONSE), BodyLen=13          │
  │    Body: {Error=0, Symbol={1,"BTCUSD"}}     │
  │                                             │
  │  ── ADD_ORDER_BOOK_REQ (0x04) ──────────→   │
  │    Body: {SymbolId=1}                       │
  │                                             │ MarketManager.AddOrderBook()
  │  ←── SIMPLE_RESP (0x44) ────────────────    │
  │    Body: {Error=0}                          │
  │                                             │
  │  ── ADD_ORDER_REQ (0x07) ───────────────→   │
  │    Body: OrderProto{Id=100, SymbolId=1,     │
  │      Type=LIMIT, Side=BUY, Price=50000,     │
  │      Quantity=10, TIF=GTC, ...}             │
  │                                             │ MarketManager.AddOrder()
  │                                             │ → onAddLevel() 回调
  │  ←── ORDER_RESP (0x43) ────────────────     │
  │    Flags=0x02(RESPONSE), BodyLen=89          │
  │    Body: {Error=0, Order=...}               │
  │                                             │
  │  ←── ORDER_BOOK_UPDATE_EVT (0x81) ──────    │  (推送，Flags=0x04)
  │    Body: {SymbolId=1, IsTop=1,              │
  │      Type=ADD, LevelType=0(Bid),            │
  │      Level={Price=50000, TotalVol=10, ...}} │
  │                                             │
  │  ←── ORDER_UPDATE_EVT (0x82) ──────────     │  (推送，Flags=0x04)
  │    Body: {Action=1(ADD), Order=...,         │
  │      ExecPrice=0, ExecQty=0}                │
```

### 11.2 心跳交互

```
Java                                          C++
  │                                             │
  │  ── HEARTBEAT_REQ (0xC0) ──────────────→    │
  │    Flags=0x10, BodyLen=0                     │
  │                                             │
  │  ←── HEARTBEAT_RESP (0xC1) ────────────    │
  │    Flags=0x10, BodyLen=0                     │
```

### 11.3 订阅 + 接收推送

```
Java                                          C++
  │                                             │
  │  ── SUBSCRIBE_ORDER_BOOK_REQ (0x11) ──→     │
  │    Body: {SymbolId=1}                       │
  │                                             │
  │  ←── SIMPLE_RESP (0x44) ────────────────    │
  │    Body: {Error=0}                          │
  │                                             │
  │   ... (其他客户端操作触发盘口变化) ...         │
  │                                             │
  │  ←── ORDER_BOOK_UPDATE_EVT (0x81) ──────    │  (仅推送给订阅者)
  │    Body: {SymbolId=1, IsTop=1,              │
  │      Type=UPDATE, LevelType=0, Level=...}   │
```

---

## 十二、C++ 与 Java 端文件对应关系

### 12.1 协议定义

| 功能 | C++ 文件 | Java 文件 |
|------|---------|----------|
| 协议常量 | `include/trader/protocol/protocol.h` | `protocol/ProtocolConstants.java` |
| 消息体定义 | `include/trader/protocol/message.h` | `protocol/ProtocolMessage.java` + `requests/` + `responses/` + `events/` |

### 12.2 帧解码

| 功能 | C++ 文件 | Java 文件 |
|------|---------|----------|
| 帧解码器 | `include/trader/protocol/frame_decoder.h` + `source/protocol/frame_decoder.cpp` | `protocol/FrameDecoder.java` |
| Netty 集成 | - | `protocol/client/ProtocolDecoder.java` + `ProtocolEncoder.java` |

### 12.3 网络通信

| 功能 | C++ 文件 | Java 文件 |
|------|---------|----------|
| 网络后端接口 | `include/trader/protocol/network_backend.h` | `protocol/client/INetworkBackend.java` |
| TCP 后端 | `include/trader/protocol/tcp_backend.h` + `source/protocol/tcp_backend.cpp` | `protocol/client/NettyTcpBackend.java` |
| DPDK 后端 | `include/trader/protocol/dpdk_backend.h` + `source/protocol/dpdk_backend.cpp` | `protocol/client/DpdkJniBackend.java` |

### 12.4 消息处理

| 功能 | C++ 文件 | Java 文件 |
|------|---------|----------|
| 协议服务器 | `include/trader/protocol/server.h` + `source/protocol/server.cpp` | - |
| 请求处理器 | `include/trader/protocol/request_handler.h` + `source/protocol/request_handler.cpp` | `protocol/client/ProtocolClientService.java` |
| 服务端入口 | `source/protocol/server_main.cpp` | - |
| 订阅管理 | (内嵌于 ProtocolServer) | `protocol/client/ProtocolStreamSubscriber.java` |

### 12.5 辅助工具

| 功能 | C++ | Java 文件 |
|------|-----|----------|
| 验证 | - | `protocol/validation/ProtocolValidator.java` |
| 异常 | - | `protocol/exception/ProtocolException.java` |
| 错误处理 | - | `protocol/exception/ProtocolErrorHandler.java` |
| 消息工厂 | - | `protocol/factory/ProtocolMessageFactory.java` |
| 编解码工厂 | - | `protocol/factory/CodecFactory.java` |

---

## 十三、已知差异与注意事项

### 13.1 OrderProto 编解码差异

C++ 端 `OrderProto` 有 `Padding1` 字段（偏移 14-15 和 56-57），Java 端 `writeOrderProto()` 不写入 Padding，导致两端 Body 字节数可能不一致。

### 13.2 OrderType 枚举值反转

C++ 端 `MARKET=0, LIMIT=1`，Java 端 `LIMIT=0, MARKET=1`。直接传递数值会导致类型混淆。

### 13.3 ErrorCode 映射不完整

C++ 端有 11 个细分错误码（SYMBOL_DUPLICATE, ORDER_NOT_FOUND 等），Java 端只定义了 5 个通用错误码（OK, ERROR, NOT_FOUND, ALREADY_EXISTS, INVALID_ARGUMENT）。

### 13.4 推送消息编解码格式差异

C++ 端推送消息使用紧凑的二进制数值（如 `uint8_t Action`），Java 端使用字符串（如 `String action = "ADD"`）和布尔标志（如 `hasExecute`）。两端无法直接互解对方的推送消息体。

### 13.5 OrderBookResponse 编解码差异

C++ 端始终写入 BestBid/BestAsk（32 字节 × 2），Java 端使用 `hasBestBid`/`hasBestAsk` 布尔标志条件写入。

### 13.6 请求/响应无 ID 匹配

协议 Header 中没有请求 ID 字段，Java 端使用单 Future 模式，同一时刻只能有一个未完成请求，不支持并发请求。

---

## 十四、配置参数

### 14.1 C++ 服务端

| 参数 | 默认值 | 命令行 | 说明 |
|------|--------|--------|------|
| port | 50059 | `--port` | TCP 监听端口 |

### 14.2 Java 客户端

通过 `ProtocolConfig` (Spring 配置) 管理：

| 配置项 | 说明 |
|--------|------|
| `backend` | 后端类型 ("tcp" 或 "dpdk") |
| `tcp.host` | C++ 服务端地址 |
| `tcp.port` | C++ 服务端端口 |
| `heartbeat.intervalSec` | 心跳发送间隔（秒） |
| `heartbeat.timeoutSec` | 心跳超时时间（秒） |
| `dpdk.*` | DPDK 配置（本地/远端 IP 和端口） |
