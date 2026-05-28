# CppTrader Java Market Data Service — Code Wiki

## 1. 项目概览

**项目名称**：`java-marketdata`
**坐标**：`com.cpptrader:java-marketdata:1.0.0`
**定位**：CppTrader 交易系统的独立行情数据服务，负责从 C++ 撮合引擎接收实时订单簿与订单推送，维护本地行情快照，并通过 REST API 和 WebSocket 向外部客户端提供行情查询与实时推送能力。

**技术栈**：
| 类别 | 技术 | 版本 |
|------|------|------|
| 运行时 | Java | 17 |
| 框架 | Spring Boot | 3.2.5 |
| 网络通信 | Netty | 4.1.107.Final |
| 序列化 | Jackson | Spring Boot 内置 |
| 简化代码 | Lombok | Spring Boot 内置 |
| 构建 | Maven | — |

---

## 2. 项目整体架构

```
┌─────────────────────────────────────────────────────────────────┐
│                    External Clients (前端/策略)                    │
│         REST API (/api/marketdata/*)  │  WebSocket (/ws/marketdata)  │
└──────────────────┬────────────────────┴──────────────┬──────────┘
                   │                                   │
         ┌─────────▼──────────┐            ┌──────────▼──────────┐
         │ MarketDataController│            │MarketDataWebSocket  │
         │   (REST 入口)       │            │    Handler          │
         └─────────┬──────────┘            └──────────┬──────────┘
                   │                                   │
                   │           ┌───────────────────────┤
                   ▼           ▼                       │
         ┌──────────────────────┐                      │
         │   MarketDataEngine   │◄─────────────────────┘ (broadcast)
         │   (核心行情引擎)      │
         └───────┬──────┬───────┘
                 │      │
    ┌────────────▼┐  ┌──▼──────────────────┐
    │OrderBook    │  │TradeRecord History   │
    │Manager      │  │(LinkedBlockingQueue) │
    │(TreeMap)    │  └─────────────────────┘
    └─────────────┘
                 │
                 ▼ (事件驱动)
         ┌──────────────────┐
         │ MarketDataClient  │
         │ (Netty TCP Client)│
         └────────┬─────────┘
                  │ 二进制自定义协议 (Little-Endian)
                  ▼
         ┌──────────────────┐
         │ C++ Protocol     │
         │ Server (撮合引擎) │
         │ host:port        │
         └──────────────────┘
```

**核心数据流**：

1. **下行推送**：C++ 撮合引擎 → 自定义二进制协议 → Netty TCP → `MarketDataClient` 解码 → `MarketDataEngine` 更新订单簿/记录成交 → WebSocket 广播
2. **上行查询**：外部客户端 → REST API → `MarketDataController` → `MarketDataEngine` 读取快照 → 返回 JSON

---

## 3. 包结构与模块职责

```
com.cpptrader.marketdata
├── MarketDataApplication.java          # Spring Boot 启动类
├── config/                             # 配置层
│   ├── MarketDataConfig.java           # 行情参数配置 (maxDepth, tradeHistorySize)
│   ├── MatchingEngineConfig.java       # 撮合引擎连接配置 (host, port, heartbeat)
│   └── WebSocketConfig.java            # WebSocket 端点注册
├── controller/                         # REST API 层
│   └── MarketDataController.java       # 行情查询 REST 控制器
├── engine/                             # 核心引擎层
│   ├── MarketDataEngine.java           # 行情引擎 (事件处理中枢)
│   ├── OrderBookManager.java           # 单个订单簿管理器
│   ├── QuoteSnapshot.java              # 行情快照数据结构
│   ├── LevelEntry.java                 # 盘口档位数据结构
│   └── TradeRecord.java                # 成交记录数据结构
├── protocol/                           # 协议层
│   ├── ProtocolConstants.java          # 协议常量定义 (Magic/MsgType/Flags)
│   ├── client/
│   │   └── MarketDataClient.java       # Netty TCP 客户端 (连接/心跳/编解码)
│   ├── events/
│   │   ├── OrderBookUpdateEvent.java   # 订单簿更新事件
│   │   ├── OrderUpdateEvent.java       # 订单更新事件
│   │   └── ProtocolConstants.java      # 事件层协议常量 (HEADER_SIZE)
│   └── requests/
│       ├── SubscribeOrderBookRequest.java  # 订阅订单簿请求
│       └── SubscribeOrdersRequest.java     # 订阅订单请求
└── websocket/                          # WebSocket 层
    └── MarketDataWebSocketHandler.java # WebSocket 行情推送处理器
```

---

## 4. 关键类与函数说明

### 4.1 `MarketDataApplication`

| 项目 | 说明 |
|------|------|
| 位置 | `com.cpptrader.marketdata.MarketDataApplication` |
| 职责 | Spring Boot 应用入口 |
| 关键方法 | `main(String[] args)` — 启动 Spring 上下文 |

---

### 4.2 `MarketDataEngine` — 核心行情引擎

| 项目 | 说明 |
|------|------|
| 位置 | `com.cpptrader.marketdata.engine.MarketDataEngine` |
| 注解 | `@Service` |
| 依赖 | `MarketDataConfig`, `MarketDataWebSocketHandler` |

**核心数据结构**：
- `ConcurrentHashMap<Integer, OrderBookManager> orderBooks` — symbolId → 订单簿管理器
- `ConcurrentHashMap<Integer, LinkedBlockingQueue<TradeRecord>> tradeHistory` — symbolId → 成交历史队列
- `long tradeIdCounter` — 成交 ID 自增计数器

**关键方法**：

| 方法 | 签名 | 说明 |
|------|------|------|
| `onOrderBookUpdate` | `void onOrderBookUpdate(OrderBookUpdateEvent)` | 处理订单簿更新推送：更新本地订单簿，通过 WebSocket 广播 |
| `onOrderUpdate` | `void onOrderUpdate(OrderUpdateEvent)` | 处理订单更新推送：如果是成交事件则记录成交并广播 |
| `getQuote` | `QuoteSnapshot getQuote(int symbolId, int depth)` | 获取指定深度的行情快照 |
| `getBestBid` | `LevelEntry getBestBid(int symbolId)` | 获取最优买价 |
| `getBestAsk` | `LevelEntry getBestAsk(int symbolId)` | 获取最优卖价 |
| `getSpread` | `long getSpread(int symbolId)` | 计算买卖价差 (ask - bid) |
| `getMidPrice` | `long getMidPrice(int symbolId)` | 计算中间价 (bid + ask) / 2 |
| `getTradeHistory` | `List<TradeRecord> getTradeHistory(int symbolId, int limit)` | 获取最近 N 条成交记录 |
| `getActiveSymbols` | `Set<Integer> getActiveSymbols()` | 获取当前有数据的所有 symbolId |

---

### 4.3 `OrderBookManager` — 订单簿管理器

| 项目 | 说明 |
|------|------|
| 位置 | `com.cpptrader.marketdata.engine.OrderBookManager` |
| 职责 | 管理单个 symbol 的订单簿（买盘/卖盘） |

**核心数据结构**：
- `TreeMap<Long, LevelEntry> bids` — 买盘，按价格降序排列（最高买价在前）
- `TreeMap<Long, LevelEntry> asks` — 卖盘，按价格升序排列（最低卖价在前）
- `long lastTradePrice / lastTradeQuantity / totalVolume / tradeCount` — 成交统计

**关键方法**：

| 方法 | 签名 | 说明 |
|------|------|------|
| `onLevelUpdate` | `synchronized void onLevelUpdate(int updateType, int levelType, LevelData)` | 处理盘口更新：updateType=1(ADD), 2(UPDATE), 3(DELETE)；levelType=0(BID), 1(ASK) |
| `onTrade` | `synchronized void onTrade(long price, long quantity, int side)` | 记录成交，更新统计 |
| `getSnapshot` | `synchronized QuoteSnapshot getSnapshot(int maxDepth)` | 生成指定深度的行情快照（深拷贝） |
| `getBestBid/Ask` | `synchronized LevelEntry getBestBid/Ask()` | 获取最优买/卖档位 |
| `getSpread` | `synchronized long getSpread()` | 买卖价差 |
| `getMidPrice` | `synchronized long getMidPrice()` | 中间价 |

> **线程安全**：所有公共方法均使用 `synchronized` 保护，确保并发读写安全。

---

### 4.4 `MarketDataClient` — Netty 协议客户端

| 项目 | 说明 |
|------|------|
| 位置 | `com.cpptrader.marketdata.protocol.client.MarketDataClient` |
| 注解 | `@Service` |
| 依赖 | `MatchingEngineConfig`, `MarketDataEngine` |

**核心职责**：通过 Netty TCP 连接 C++ 撮合引擎，接收推送数据，发送订阅请求，维护心跳与自动重连。

**关键方法**：

| 方法 | 签名 | 说明 |
|------|------|------|
| `init` | `@PostConstruct void init()` | 应用启动后自动连接并启动心跳线程 |
| `shutdown` | `@PreDestroy void shutdown()` | 优雅关闭连接和线程组 |
| `subscribeSymbol` | `void subscribeSymbol(int symbolId)` | 订阅指定 symbol 的订单簿和订单推送 |
| `unsubscribeSymbol` | `void unsubscribeSymbol(int symbolId)` | 取消订阅 |
| `isConnected` | `boolean isConnected()` | 查询连接状态 |
| `getSubscribedSymbols` | `Set<Integer> getSubscribedSymbols()` | 获取已订阅的 symbol 集合 |

**内部类**：

| 类名 | 职责 |
|------|------|
| `MarketDataMessageDecoder` | Netty 入站处理器：解析二进制帧 → 解码推送事件 → 分发到 `MarketDataEngine` |
| `MarketDataMessageEncoder` | Netty 出站处理器：将 `byte[]` 包装为 `ByteBuf` 发送 |
| `ByteBufferWrapper` | 小端序字节读取工具（readShort/readByte/readInt/readLong） |

**连接管理机制**：
- **自动重连**：连接断开后 3 秒自动重试
- **心跳检测**：独立守护线程，按 `intervalSec` 发送心跳，超时 `timeoutSec` 未收到数据则断开重连
- **订阅恢复**：重连后自动重新发送所有已订阅 symbol 的订阅请求

---

### 4.5 `MarketDataController` — REST API 控制器

| 项目 | 说明 |
|------|------|
| 位置 | `com.cpptrader.marketdata.controller.MarketDataController` |
| 注解 | `@RestController`, `@RequestMapping("/api/marketdata")` |

**API 端点**：

| HTTP 方法 | 路径 | 说明 | 参数 |
|-----------|------|------|------|
| GET | `/api/marketdata/status` | 服务状态（连接状态、活跃 symbol、已订阅 symbol） | — |
| POST | `/api/marketdata/subscribe/{symbolId}` | 订阅指定 symbol | `symbolId` (路径) |
| DELETE | `/api/marketdata/subscribe/{symbolId}` | 取消订阅 | `symbolId` (路径) |
| GET | `/api/marketdata/quote/{symbolId}` | 获取行情快照（含盘口、成交统计） | `symbolId` (路径), `depth` (查询, 默认5) |
| GET | `/api/marketdata/depth/{symbolId}` | 获取盘口深度 | `symbolId` (路径), `depth` (查询, 默认10) |
| GET | `/api/marketdata/trades/{symbolId}` | 获取成交历史 | `symbolId` (路径), `limit` (查询, 默认50) |
| GET | `/api/marketdata/ticker/{symbolId}` | 获取最新行情摘要（最优买卖价、价差、中间价） | `symbolId` (路径) |
| GET | `/api/marketdata/symbols` | 获取所有活跃 symbol 列表 | — |

---

### 4.6 `MarketDataWebSocketHandler` — WebSocket 推送处理器

| 项目 | 说明 |
|------|------|
| 位置 | `com.cpptrader.marketdata.websocket.MarketDataWebSocketHandler` |
| 注解 | `@Component` |
| 端点 | `/ws/marketdata` |

**核心数据结构**：
- `ConcurrentHashMap<String, WebSocketSession> sessions` — sessionId → WebSocket 会话
- `ConcurrentHashMap<String, Set<Integer>> sessionSubscriptions` — sessionId → 已订阅 symbolId 集合

**关键方法**：

| 方法 | 说明 |
|------|------|
| `afterConnectionEstablished` | 新连接建立，注册会话 |
| `afterConnectionClosed` | 连接关闭，清理会话 |
| `handleTextMessage` | 处理客户端消息：`{"action":"subscribe","symbolId":1}` 或 `"unsubscribe"` |
| `broadcastToSymbol(int symbolId, Map data)` | 向订阅了指定 symbol 的所有 WebSocket 客户端推送 JSON 消息 |
| `broadcastAll(Map data)` | 向所有连接的客户端广播消息 |

**推送消息类型**：
- `orderBookUpdate` — 订单簿更新（含 updateType/levelType/price/volume 等）
- `trade` — 成交推送（含 tradeId/price/quantity/side 等）

---

### 4.7 数据模型类

#### `LevelEntry` — 盘口档位

| 字段 | 类型 | 说明 |
|------|------|------|
| `price` | `long` | 价格 |
| `totalVolume` | `long` | 总量 |
| `visibleVolume` | `long` | 可见量（冰山订单） |
| `orders` | `long` | 该价位订单数 |

#### `QuoteSnapshot` — 行情快照

| 字段 | 类型 | 说明 |
|------|------|------|
| `symbolId` | `int` | 标的 ID |
| `bestBid` | `LevelEntry` | 最优买价 |
| `bestAsk` | `LevelEntry` | 最优卖价 |
| `bids` | `List<LevelEntry>` | 买盘列表 |
| `asks` | `List<LevelEntry>` | 卖盘列表 |
| `lastTradePrice` | `long` | 最新成交价 |
| `lastTradeQuantity` | `long` | 最新成交量 |
| `totalVolume` | `long` | 总成交量 |
| `tradeCount` | `long` | 成交笔数 |
| `timestamp` | `long` | 快照时间戳 |

#### `TradeRecord` — 成交记录

| 字段 | 类型 | 说明 |
|------|------|------|
| `tradeId` | `long` | 成交 ID |
| `symbolId` | `int` | 标的 ID |
| `price` | `long` | 成交价格 |
| `quantity` | `long` | 成交数量 |
| `side` | `int` | 方向 (0=BUY, 1=SELL) |
| `timestamp` | `long` | 成交时间戳 |

---

### 4.8 协议层类

#### `ProtocolConstants` — 协议常量

| 常量 | 值 | 说明 |
|------|------|------|
| `MAGIC` | `0x5452` | 协议魔数 ("TR" 小端序) |
| `VERSION` | `1` | 协议版本 |
| `HEADER_SIZE` | `8` | 消息头大小 (字节) |
| `SUBSCRIBE_ORDER_BOOK_REQ` | `0x11` | 订阅订单簿请求 |
| `SUBSCRIBE_ORDERS_REQ` | `0x12` | 订阅订单请求 |
| `ORDER_BOOK_UPDATE_EVT` | `0x81` | 订单簿更新推送 |
| `ORDER_UPDATE_EVT` | `0x82` | 订单更新推送 |
| `HEARTBEAT_REQ` | `0xC0` | 心跳请求 |
| `HEARTBEAT_RESP` | `0xC1` | 心跳响应 |
| `FLAG_REQUEST` | `0x01` | 请求标志 |
| `FLAG_RESPONSE` | `0x02` | 响应标志 |
| `FLAG_PUSH` | `0x04` | 推送标志 |
| `FLAG_ERROR` | `0x08` | 错误标志 |
| `FLAG_HEARTBEAT` | `0x10` | 心跳标志 |

#### `OrderBookUpdateEvent` — 订单簿更新事件

**二进制解码格式**（Header 8B + Body 40B）：

| 偏移 | 长度 | 字段 |
|------|------|------|
| 0 | 2 | Magic |
| 2 | 1 | Version |
| 3 | 1 | MsgType |
| 4 | 1 | Flags |
| 5 | 1 | Reserved |
| 6 | 2 | Body Length |
| 8 | 4 | symbolId |
| 12 | 1 | isTop (0/1) |
| 13 | 1 | updateType (1=ADD, 2=UPDATE, 3=DELETE) |
| 14 | 1 | levelType (0=BID, 1=ASK) |
| 15 | 1 | padding |
| 16 | 8 | price |
| 24 | 8 | totalVolume |
| 32 | 8 | visibleVolume |
| 40 | 8 | orders |

#### `OrderUpdateEvent` — 订单更新事件

**二进制解码格式**（Header 8B + Body 105B）：

| 偏移 | 长度 | 字段 |
|------|------|------|
| 0-7 | 8 | Header (同上) |
| 8 | 1 | action (1=ADD, 2=UPDATE, 3=DELETE, 4=EXECUTE) |
| 9 | 8 | order.id |
| 17 | 4 | order.symbolId |
| 21 | 1 | order.orderType |
| 22 | 1 | order.orderSide |
| 23 | 8 | order.price |
| 31 | 8 | order.stopPrice |
| 39 | 8 | order.quantity |
| 47 | 8 | order.executedQuantity |
| 55 | 8 | order.leavesQuantity |
| 63 | 1 | order.timeInForce |
| 64 | 1 | padding |
| 65 | 8 | order.maxVisibleQuantity |
| 73 | 8 | order.slippage |
| 81 | 8 | order.trailingDistance |
| 89 | 8 | order.trailingStep |
| 97 | 8 | executePrice |
| 105 | 8 | executeQuantity |

#### `SubscribeOrderBookRequest` / `SubscribeOrdersRequest`

**二进制编码格式**（Header 8B + Body 4B）：

| 偏移 | 长度 | 字段 |
|------|------|------|
| 0 | 2 | Magic (0x5452) |
| 2 | 1 | Version (1) |
| 3 | 1 | MsgType (0x11 / 0x12) |
| 4 | 1 | Flags (0x01 REQUEST) |
| 5 | 1 | Reserved (0) |
| 6 | 2 | Body Length (4) |
| 8 | 4 | symbolId |

---

### 4.9 配置类

#### `MarketDataConfig`

| 属性 | 默认值 | 配置前缀 | 说明 |
|------|--------|----------|------|
| `maxDepth` | 20 | `market-data.max-depth` | 最大盘口深度 |
| `tradeHistorySize` | 1000 | `market-data.trade-history-size` | 每个 symbol 的成交历史队列容量 |

#### `MatchingEngineConfig`

| 属性 | 默认值 | 配置前缀 | 说明 |
|------|--------|----------|------|
| `host` | `127.0.0.1` | `matching-engine.host` | 撮合引擎地址 |
| `port` | `50059` | `matching-engine.port` | 撮合引擎端口 |
| `backend` | `netty` | `matching-engine.backend` | 网络后端类型 |
| `heartbeat.intervalSec` | 5 | `matching-engine.heartbeat.interval-sec` | 心跳发送间隔 |
| `heartbeat.timeoutSec` | 15 | `matching-engine.heartbeat.timeout-sec` | 心跳超时时间 |

#### `WebSocketConfig`

注册 WebSocket 端点 `/ws/marketdata`，允许所有来源 (`*`) 跨域连接。

---

## 5. 依赖关系

### 5.1 Maven 依赖

| 依赖 | 用途 |
|------|------|
| `spring-boot-starter-web` | REST API (内嵌 Tomcat) |
| `spring-boot-starter-websocket` | WebSocket 支持 |
| `spring-boot-starter-actuator` | 健康检查与监控端点 |
| `netty-all 4.1.107.Final` | 与 C++ 撮合引擎的 TCP 通信 |
| `jackson-databind` | JSON 序列化/反序列化 |
| `lombok` | 编译期代码生成 (getter/setter/log 等) |
| `spring-boot-starter-test` | 测试框架 (JUnit 5) |

### 5.2 内部模块依赖图

```
MarketDataApplication
    └── Spring Boot Auto-Configuration
        ├── MarketDataConfig ←── application.yml (market-data.*)
        ├── MatchingEngineConfig ←── application.yml (matching-engine.*)
        ├── WebSocketConfig
        │       └── MarketDataWebSocketHandler
        ├── MarketDataController
        │       ├── MarketDataEngine
        │       │       ├── MarketDataConfig
        │       │       └── MarketDataWebSocketHandler
        │       └── MarketDataClient
        │               ├── MatchingEngineConfig
        │               └── MarketDataEngine
        └── MarketDataClient (见上)
```

### 5.3 外部系统依赖

| 外部系统 | 协议 | 说明 |
|----------|------|------|
| C++ Protocol Server | 自定义二进制协议 (TCP) | CppTrader 撮合引擎，提供订单簿和订单推送 |

---

## 6. 自定义二进制协议规范

本服务与 C++ 撮合引擎之间使用自定义二进制协议通信，所有多字节字段均为**小端序 (Little-Endian)**。

### 6.1 消息头 (8 字节)

```
+--------+--------+--------+--------+--------+--------+--------+--------+
| Magic  | Magic  |Version |MsgType | Flags  |Reserved| Length | Length |
| (LSB)  | (MSB)  |        |        |        |        | (LSB)  | (MSB)  |
+--------+--------+--------+--------+--------+--------+--------+--------+
  0x52     0x54     0x01    0xXX     0xXX     0x00     body length
  ("TR" 小端)     (V1)
```

### 6.2 消息类型分类

| 范围 | 类别 | 示例 |
|------|------|------|
| `0x01-0x3F` | 请求 | SUBSCRIBE_ORDER_BOOK_REQ (0x11), SUBSCRIBE_ORDERS_REQ (0x12) |
| `0x41-0x7F` | 响应 | SYMBOL_RESP (0x41), ORDER_BOOK_RESP (0x42), ORDER_RESP (0x43), SIMPLE_RESP (0x44) |
| `0x81-0xBF` | 推送 | ORDER_BOOK_UPDATE_EVT (0x81), ORDER_UPDATE_EVT (0x82) |
| `0xC0-0xFF` | 控制 | HEARTBEAT_REQ (0xC0), HEARTBEAT_RESP (0xC1) |

### 6.3 标志位 (Flags)

| 位 | 名称 | 说明 |
|----|------|------|
| 0 | REQUEST | 请求消息 |
| 1 | RESPONSE | 响应消息 |
| 2 | PUSH | 服务端推送 |
| 3 | ERROR | 错误响应 |
| 4 | HEARTBEAT | 心跳消息 |

### 6.4 Netty 帧解码配置

```java
LengthFieldBasedFrameDecoder(
    ByteOrder.LITTLE_ENDIAN,
    65535 + 8,  // maxFrameLength
    6,          // lengthFieldOffset (Length 字段在 header 中的偏移)
    2,          // lengthFieldSize (Length 字段长度)
    -8,         // lengthAdjustment (总帧长 = length + 8 字节 header)
    0,          // initialBytesToStrip
    true        // failFast
)
```

---

## 7. 项目运行方式

### 7.1 前置条件

1. **JDK 17+** 已安装
2. **Maven 3.6+** 已安装
3. **C++ 撮合引擎** 已启动并监听指定端口（默认 `50059`）

### 7.2 配置

编辑 `src/main/resources/application.yml`：

```yaml
server:
  port: 8081                          # 本服务 HTTP 端口

matching-engine:
  host: 127.0.0.1                     # C++ 撮合引擎地址
  port: 50059                         # C++ 撮合引擎端口
  backend: netty                      # 网络后端 (当前仅支持 netty)
  heartbeat:
    intervalSec: 5                    # 心跳间隔 (秒)
    timeoutSec: 15                    # 心跳超时 (秒)

market-data:
  maxDepth: 20                        # 最大盘口深度
  tradeHistorySize: 1000              # 成交历史队列大小

logging:
  level:
    com.cpptrader.marketdata: INFO
    io.netty: WARN
```

### 7.3 构建与运行

```bash
# 构建
cd java-marketdata
mvn clean package -DskipTests

# 运行
java -jar target/java-marketdata-1.0.0.jar

# 或使用 Maven 直接运行
mvn spring-boot:run
```

### 7.4 验证服务

```bash
# 检查服务状态
curl http://localhost:8081/api/marketdata/status

# 订阅 symbol
curl -X POST http://localhost:8081/api/marketdata/subscribe/1

# 查询行情
curl http://localhost:8081/api/marketdata/quote/1?depth=5

# 查询盘口深度
curl http://localhost:8081/api/marketdata/depth/1?depth=10

# 查询成交
curl http://localhost:8081/api/marketdata/trades/1?limit=20

# 查询 Ticker
curl http://localhost:8081/api/marketdata/ticker/1

# 查看活跃 symbol
curl http://localhost:8081/api/marketdata/symbols

# 健康检查 (Actuator)
curl http://localhost:8081/actuator/health
```

### 7.5 WebSocket 连接

```
ws://localhost:8081/ws/marketdata
```

**客户端操作消息格式**：

```json
// 订阅
{"action": "subscribe", "symbolId": 1}

// 取消订阅
{"action": "unsubscribe", "symbolId": 1}
```

**服务端推送消息格式**：

```json
// 订单簿更新
{
  "type": "orderBookUpdate",
  "symbolId": 1,
  "updateType": "ADD",
  "levelType": "BID",
  "isTop": true,
  "price": 10000,
  "totalVolume": 500,
  "visibleVolume": 500,
  "orders": 3
}

// 成交推送
{
  "type": "trade",
  "symbolId": 1,
  "tradeId": 42,
  "price": 10050,
  "quantity": 100,
  "side": "BUY",
  "timestamp": 1716182400000
}
```

---

## 8. 设计特点与注意事项

### 8.1 设计特点

1. **事件驱动架构**：通过 `MarketDataEngine` 作为事件中枢，将协议层推送与 WebSocket 广播解耦
2. **深拷贝隔离**：`LevelEntry.copy()`、`QuoteSnapshot.deepCopy()`、`TradeRecord.copy()` 确保读取快照时不影响内部数据
3. **并发安全**：`ConcurrentHashMap` 管理订单簿集合，`synchronized` 保护单个订单簿的读写，`CopyOnWriteArraySet` 管理订阅集合
4. **自动重连与订阅恢复**：`MarketDataClient` 在连接断开后自动重连，并重新发送所有订阅请求
5. **心跳保活**：独立守护线程定期发送心跳，超时自动断开重连
6. **成交历史有界队列**：`LinkedBlockingQueue` 配合 `tradeHistorySize` 限制内存使用，旧数据自动淘汰

### 8.2 注意事项

1. **OrderBookManager 的 synchronized 粒度**：当前对整个 OrderBookManager 实例加锁，高并发场景下可能成为瓶颈，可考虑使用读写锁优化
2. **tradeIdCounter 非原子操作**：`++tradeIdCounter` 在 `onOrderUpdate` 中未使用同步，多线程下可能产生重复 ID
3. **WebSocket 无认证**：当前 `setAllowedOrigins("*")` 且无鉴权机制，生产环境需加强安全控制
4. **心跳线程模型**：使用独立 `Thread` 而非 Netty 的 `EventLoop` 定时任务，存在线程安全风险
5. **broadcastToSymbol 遍历效率**：每次广播遍历所有 session 的订阅集合，大量连接时可优化为 symbol → sessions 的反向索引
