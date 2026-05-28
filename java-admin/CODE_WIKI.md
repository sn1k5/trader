# CppTrader Java Admin - Code Wiki

## 1. 项目概述

### 1.1 项目简介

CppTrader Java Admin 是一个基于 Spring Boot 的交易管理后端系统，用于管理与 C++ 交易引擎（CppTrader）的通信。该系统提供了交易品种管理、订单管理、订单簿查询、实时行情订阅等核心功能。

**项目基本信息：**
- 项目名称：CppTrader Java Admin
- 版本：1.0.0
- 技术栈：Spring Boot 3.2.5 + Java 17 + Netty 4.1.107
- 打包方式：JAR
- 描述：Java management backend for CppTrader

### 1.2 核心功能

- **交易品种管理**：添加、删除、查询交易品种
- **订单管理**：下单、撤单、改单、查询订单
- **订单簿管理**：添加、删除、查询订单簿
- **实时行情订阅**：订阅订单簿更新、订单状态更新
- **协议通信**：通过 TCP/DPDK 与 C++ 交易引擎通信
- **心跳机制**：自动检测连接状态，断线重连

---

## 2. 项目架构

### 2.1 整体架构图

```
┌─────────────────────────────────────────────────────────────┐
│                    Java Admin Backend                        │
├─────────────────────────────────────────────────────────────┤
│  ┌──────────────┐    ┌──────────────┐    ┌──────────────┐  │
│  │  Controller  │───▶│   Protocol   │───▶│   Network    │  │
│  │    Layer     │    │     Layer    │    │   Backend    │  │
│  └──────────────┘    └──────────────┘    └──────────────┘  │
│         │                   │                   │           │
│         │            ┌──────┴──────┐            │           │
│         │            │   Config    │            │           │
│         │            │    Layer    │            │           │
│         │            └─────────────┘            │           │
│         │                                      │           │
│  ┌──────┴──────────────────────────────────────┴──────┐    │
│  │                  Spring Boot Application             │    │
│  └─────────────────────────────────────────────────────┘    │
└─────────────────────────────────────────────────────────────┘
                              │
                    ┌─────────┴─────────┐
                    │   Network Backend │
                    ├───────────────────┤
                    │  ┌─────────────┐  │
                    │  │ Netty TCP   │  │  ← Default
                    │  └─────────────┘  │
                    │  ┌─────────────┐  │
                    │  │  DPDK JNI   │  │  ← High Performance
                    │  └─────────────┘  │
                    └───────────────────┘
                              │
                              ▼
                    ┌───────────────────┐
                    │  C++ Trading Core  │
                    └───────────────────┘
```

### 2.2 模块结构

```
src/main/java/com/cpptrader/admin/
├── AdminApplication.java              # Spring Boot 应用入口
├── config/
│   └── ProtocolConfig.java            # 协议配置类
├── controller/
│   └── TradingController.java         # 交易 REST 控制器
└── protocol/
    ├── ProtocolConstants.java         # 协议常量定义
    ├── ProtocolMessage.java           # 协议消息基类
    ├── FrameDecoder.java               # 帧解码器
    ├── client/                        # 客户端网络层
    │   ├── INetworkBackend.java       # 网络后端接口
    │   ├── ProtocolClientService.java # 协议客户端服务
    │   ├── NettyTcpBackend.java       # Netty TCP 实现
    │   ├── DpdkJniBackend.java        # DPDK JNI 实现
    │   ├── ProtocolEncoder.java       # Netty 编码器
    │   ├── ProtocolDecoder.java       # Netty 解码器
    │   └── ProtocolStreamSubscriber.java # 流订阅器
    ├── requests/                      # 请求消息
    │   ├── AddSymbolRequest.java      # 添加交易品种
    │   ├── DeleteSymbolRequest.java   # 删除交易品种
    │   ├── GetSymbolRequest.java      # 查询交易品种
    │   ├── AddOrderBookRequest.java   # 添加订单簿
    │   ├── DeleteOrderBookRequest.java # 删除订单簿
    │   ├── GetOrderBookRequest.java   # 查询订单簿
    │   ├── AddOrderRequest.java       # 添加订单
    │   ├── ReduceOrderRequest.java     # 减少订单
    │   ├── ModifyOrderRequest.java    # 修改订单
    │   ├── MitigateOrderRequest.java  # 缓解订单
    │   ├── ReplaceOrderRequest.java    # 替换订单
    │   ├── DeleteOrderRequest.java    # 删除订单
    │   ├── ExecuteOrderRequest.java   # 执行订单
    │   ├── GetOrderRequest.java       # 查询订单
    │   ├── EnableMatchingRequest.java  # 启用撮合
    │   ├── DisableMatchingRequest.java # 禁用撮合
    │   ├── SubscribeOrderBookRequest.java  # 订阅订单簿
    │   └── SubscribeOrdersRequest.java      # 订阅订单
    ├── responses/                     # 响应消息
    │   ├── SymbolResponse.java       # 品种响应
    │   ├── SimpleResponse.java        # 简单响应
    │   ├── OrderResponse.java         # 订单响应
    │   └── OrderBookResponse.java    # 订单簿响应
    ├── events/                       # 事件消息
    │   ├── OrderBookUpdateEvent.java  # 订单簿更新事件
    │   └── OrderUpdateEvent.java      # 订单更新事件
    ├── exception/                     # 异常处理
    │   ├── ProtocolException.java    # 协议异常
    │   └── ProtocolErrorHandler.java # 错误处理器
    ├── validation/                    # 验证工具
    │   └── ProtocolValidator.java    # 协议验证器
    └── factory/                      # 工厂类
        ├── ProtocolMessageFactory.java # 消息工厂
        └── CodecFactory.java          # 编解码工厂
```

---

## 3. 核心模块详解

### 3.1 应用入口层

#### AdminApplication.java

**职责：** Spring Boot 应用主类，负责启动应用程序。

**关键特性：**
- 启用 Spring Boot 自动配置
- 启用配置属性绑定
- 加载 ProtocolConfig 配置类

**核心代码：**

```java
@SpringBootApplication
@EnableConfigurationProperties(ProtocolConfig.class)
public class AdminApplication {
    public static void main(String[] args) {
        SpringApplication.run(AdminApplication.class, args);
    }
}
```

### 3.2 配置层

#### ProtocolConfig.java

**职责：** 管理协议通信配置，包括网络连接参数和心跳设置。

**配置项：**

| 配置项 | 默认值 | 说明 |
|-------|-------|------|
| `host` | localhost | 服务器地址 |
| `port` | 50051 | 服务器端口 |
| `backend` | netty | 网络后端类型（netty/dpdk） |
| `tcp.host` | localhost | TCP 连接地址 |
| `tcp.port` | 50051 | TCP 连接端口 |
| `heartbeat.intervalSec` | 5 | 心跳间隔（秒） |
| `heartbeat.timeoutSec` | 15 | 心跳超时（秒） |

**内部类：**
- `TcpConfig`：TCP 连接配置
- `DpdkConfig`：DPDK 连接配置
- `HeartbeatConfig`：心跳配置

### 3.3 控制器层

#### TradingController.java

**职责：** 提供 RESTful API 接口，处理 HTTP 请求并转发到协议客户端。

**API 接口一览：**

| 方法 | 路径 | 说明 | 参数 |
|-----|------|------|------|
| POST | `/api/symbols` | 添加交易品种 | id, name |
| DELETE | `/api/symbols/{id}` | 删除交易品种 | id |
| GET | `/api/symbols/{id}` | 查询交易品种 | id |
| POST | `/api/orderbooks` | 添加订单簿 | symbolId |
| DELETE | `/api/orderbooks/{symbolId}` | 删除订单簿 | symbolId |
| GET | `/api/orderbooks/{symbolId}` | 查询订单簿 | symbolId, depth |
| POST | `/api/orders` | 添加订单 | id, symbolId, side, type, price, quantity |
| DELETE | `/api/orders/{id}` | 删除订单 | id |
| GET | `/api/orders/{id}` | 查询订单 | id |
| POST | `/api/matching/enable` | 启用撮合 | - |
| POST | `/api/matching/disable` | 禁用撮合 | - |

**核心方法：**

```java
// 同步发送请求并接收响应
@PostMapping("/orders")
public Map<String, Object> addOrder(...) {
    AddOrderRequest req = new AddOrderRequest(...);
    byte[] respBytes = protocolClient.sendSync(req.toBytes());
    OrderResponse resp = new OrderResponse();
    if (respBytes != null) {
        resp.fromBytes(respBytes);
    }
    // 处理响应...
}
```

### 3.4 协议层

#### 3.4.1 ProtocolConstants.java

**职责：** 定义所有协议常量，包括消息类型、标志位、错误码等。

**消息类型常量：**

| 常量 | 值 | 说明 |
|-----|-----|------|
| `ADD_SYMBOL_REQ` | 0x01 | 添加交易品种请求 |
| `DELETE_SYMBOL_REQ` | 0x02 | 删除交易品种请求 |
| `GET_SYMBOL_REQ` | 0x03 | 查询交易品种请求 |
| `ADD_ORDER_BOOK_REQ` | 0x04 | 添加订单簿请求 |
| `ADD_ORDER_REQ` | 0x07 | 添加订单请求 |
| `DELETE_ORDER_REQ` | 0x0C | 删除订单请求 |
| `GET_ORDER_REQ` | 0x0E | 查询订单请求 |
| `ENABLE_MATCHING_REQ` | 0x0F | 启用撮合请求 |
| `DISABLE_MATCHING_REQ` | 0x10 | 禁用撮合请求 |
| `SUBSCRIBE_ORDER_BOOK_REQ` | 0x11 | 订阅订单簿请求 |
| `SUBSCRIBE_ORDERS_REQ` | 0x12 | 订阅订单请求 |

**响应类型常量：**

| 常量 | 值 | 说明 |
|-----|-----|------|
| `SYMBOL_RESP` | 0x41 | 品种响应 |
| `ORDER_BOOK_RESP` | 0x42 | 订单簿响应 |
| `ORDER_RESP` | 0x43 | 订单响应 |
| `SIMPLE_RESP` | 0x44 | 简单响应 |

**事件类型常量：**

| 常量 | 值 | 说明 |
|-----|-----|------|
| `ORDER_BOOK_UPDATE_EVT` | 0x81 | 订单簿更新事件 |
| `ORDER_UPDATE_EVT` | 0x82 | 订单更新事件 |

**标志位常量：**

| 常量 | 值 | 说明 |
|-----|-----|------|
| `FLAG_REQUEST` | 0x01 | 请求标志 |
| `FLAG_RESPONSE` | 0x02 | 响应标志 |
| `FLAG_PUSH` | 0x04 | 推送标志 |
| `FLAG_ERROR` | 0x08 | 错误标志 |
| `FLAG_HEARTBEAT` | 0x10 | 心跳标志 |

**内部类：**

- `ErrorCode`：错误码（OK, ERROR, NOT_FOUND, ALREADY_EXISTS, INVALID_ARGUMENT）
- `OrderType`：订单类型（LIMIT, MARKET, STOP, STOP_LIMIT）
- `OrderSide`：订单方向（BUY, SELL）
- `TimeInForce`：有效期（GTC, IOC, FOK, AON）

#### 3.4.2 ProtocolMessage.java

**职责：** 所有协议消息的抽象基类，定义消息的编解码规范。

**核心属性：**
- `msgType`：消息类型
- `flags`：消息标志

**核心方法：**

| 方法 | 说明 |
|------|------|
| `encode(ByteBuffer buf)` | 将消息编码到 ByteBuffer |
| `decode(ByteBuffer buf)` | 从 ByteBuffer 解码消息 |
| `toBytes()` | 将消息序列化为字节数组 |
| `fromBytes(byte[] data)` | 从字节数组反序列化消息 |
| `validate()` | 验证消息合法性 |
| `getBodySize()` | 获取消息体大小 |
| `getTotalSize()` | 获取消息总大小 |

**内部类：**
- `SymbolHolder`：品种信息持有者（id, name）
- `OrderHolder`：订单信息持有者（id, symbolId, orderType, orderSide, price, quantity 等）
- `LevelHolder`：订单簿档位信息持有者（price, totalVolume, visibleVolume, orders）

**编码规范：**
- 字节序：Little Endian
- 字符串编码：UTF-8
- 协议头：8 字节（magic + version + msgType + flags + reserved + bodySize）

#### 3.4.3 FrameDecoder.java

**职责：** TCP 帧解码器，将字节流解析为完整的协议消息帧。

**状态机：**

```
         ┌─────────┐
         │  HEAD   │◀────────────────┐
         └────┬────┘                 │
              │ 接收到完整头           │ 完成解码
              ▼                      │
         ┌─────────┐                 │
    ┌───▶│  HEAD   │                 │
    │    │         │                 │
    │    └────┬────┘                 │
    │         │ 接收到完整头          │
    │         ▼                      │
    │    ┌─────────┐                 │
    └────│  BODY   │─────────────────┘
         └─────────┘
```

**核心方法：**

| 方法 | 说明 |
|------|------|
| `feed(byte[] data)` | 接收字节数据 |
| `hasCompleteFrame()` | 检查是否有完整帧 |
| `tryDecode()` | 尝试解码一帧 |
| `decodeAll()` | 解码所有可用帧 |
| `reset()` | 重置解码器状态 |

**统计信息：**
- `frameCount`：已解码帧数
- `errorCount`：解码错误数

#### 3.4.4 消息验证器

##### ProtocolValidator.java

**职责：** 提供全面的消息验证功能，确保数据合法性。

**验证方法：**

| 方法 | 说明 |
|------|------|
| `validateHeader(byte[])` | 验证协议头 |
| `validateCompleteMessage(byte[])` | 验证完整消息 |
| `validateSymbolId(int)` | 验证品种 ID |
| `validateOrderId(long)` | 验证订单 ID |
| `validatePrice(long)` | 验证价格 |
| `validateQuantity(long)` | 验证数量 |
| `validateSymbolName(String)` | 验证品种名称 |
| `validateOrderType(byte)` | 验证订单类型 |
| `validateOrderSide(byte)` | 验证订单方向 |
| `validateTimeInForce(byte)` | 验证有效期 |

**ValidationResult：**
- `isValid()`：是否通过验证
- `getErrors()`：错误列表
- `getWarnings()`：警告列表
- `getErrorMessage()`：错误信息汇总

#### 3.4.5 异常处理

##### ProtocolException.java

**职责：** 协议相关异常定义和处理。

**工厂方法：**

| 方法 | 说明 |
|------|------|
| `invalidMagic(short)` | 无效的魔数 |
| `unsupportedVersion(byte)` | 不支持的版本 |
| `invalidMessageType(byte)` | 无效的消息类型 |
| `invalidBodySize(int, int)` | 无效的消息体大小 |
| `bufferUnderflow(String)` | 缓冲区下溢 |
| `bufferOverflow(String)` | 缓冲区上溢 |
| `encodingError(String)` | 编码错误 |
| `decodingError(String)` | 解码错误 |
| `notFound(String)` | 资源未找到 |
| `alreadyExists(String)` | 资源已存在 |

##### ProtocolErrorHandler.java

**职责：** 统一错误处理和日志记录。

**配置选项：**
- `setCallback(ErrorCallback)`：设置回调
- `setLogEnabled(boolean)`：启用/禁用日志
- `setThrowOnError(boolean)`：错误时是否抛出异常

**工厂方法：**
- `createDefault()`：创建默认处理器
- `createStrict()`：创建严格模式处理器
- `createSilent()`：创建静默模式处理器

### 3.5 网络层

#### 3.5.1 INetworkBackend.java

**职责：** 网络后端抽象接口，定义底层通信规范。

```java
public interface INetworkBackend {
    boolean init();              // 初始化连接
    void send(byte[] data);     // 发送数据
    byte[] recv();              // 接收数据
    void close();               // 关闭连接
}
```

#### 3.5.2 ProtocolClientService.java

**职责：** 协议客户端核心服务，管理网络连接和消息收发。

**核心功能：**

1. **连接管理**
   - 自动连接/重连
   - 支持 Netty TCP 和 DPDK 两种后端
   - 连接状态监控

2. **消息处理**
   - 同步/异步消息发送
   - 请求-响应匹配
   - 心跳机制

3. **流订阅**
   - 订单簿更新订阅
   - 订单状态订阅

**核心方法：**

| 方法 | 说明 |
|------|------|
| `sendSync(byte[])` | 同步发送请求（默认超时 10 秒） |
| `sendSync(byte[], long, TimeUnit)` | 同步发送请求（自定义超时） |
| `sendAsync(byte[])` | 异步发送请求 |
| `subscribeOrderBook(int, Consumer)` | 订阅订单簿更新 |
| `subscribeOrders(int, Consumer)` | 订阅订单更新 |

**内部线程：**
- `recvThread`：数据接收线程
- `heartbeatThread`：心跳发送线程
- `reconnectThread`：重连线程

**请求映射：**
- 使用 `AtomicInteger` 生成唯一请求 ID
- 使用 `ConcurrentHashMap` 存储待处理请求的 Future
- 响应到达时根据请求 ID 匹配并完成 Future

#### 3.5.3 NettyTcpBackend.java

**职责：** 基于 Netty 的 TCP 网络实现。

**特性：**
- 使用 NioEventLoopGroup
- TCP keepalive 启用
- TCP_NODELAY 启用（低延迟）
- 自动重连支持

**Netty Pipeline：**
```
┌────────────────┐
│ ProtocolEncoder │  ← 编码 ProtocolMessage
├────────────────┤
│ ProtocolDecoder │  ← 解码为字节数组
├────────────────┤
│  ClientHandler  │  ← 业务处理
└────────────────┘
```

#### 3.5.4 DpdkJniBackend.java

**职责：** 基于 DPDK 的高性能网络实现，使用 JNI 调用原生库。

**特性：**
- 绕过内核协议栈
- 超低延迟
- 高吞吐量
- 需要加载原生库 `cpptrader_dpdk_jni`

**原生方法：**
```java
private native boolean dpdkInit(String localIp, int localPort, String remoteIp, int remotePort);
private native void dpdkSend(byte[] data);
private native byte[] dpdkRecv();
private native void dpdkClose();
```

#### 3.5.5 ProtocolStreamSubscriber.java

**职责：** 管理实时数据流订阅，包括订单簿和订单状态更新。

**功能：**
- 订阅/取消订阅
- 订阅持久化（重连后自动恢复）
- 回调处理

### 3.6 消息工厂层

#### ProtocolMessageFactory.java

**职责：** 创建和管理协议消息实例。

**核心方法：**

| 方法 | 说明 |
|------|------|
| `createMessage(byte msgType)` | 根据类型创建消息 |
| `parseMessage(byte[] data)` | 解析字节数组为消息 |
| `isRequest(byte)` | 判断是否为请求 |
| `isResponse(byte)` | 判断是否为响应 |
| `isPush(byte)` | 判断是否为推送 |
| `isHeartbeat(byte)` | 判断是否心跳 |

#### CodecFactory.java

**职责：** 提供便捷的编解码功能封装。

**核心方法：**

| 方法 | 说明 |
|------|------|
| `encodeMessage(ProtocolMessage)` | 编码消息 |
| `tryEncode(ProtocolMessage)` | 尝试编码（带错误处理） |
| `decodeMessage(byte[])` | 解码消息 |
| `tryDecode(byte[])` | 尝试解码（带错误处理） |
| `createNettyDecoder()` | 创建 Netty 解码器 |
| `createNettyEncoder()` | 创建 Netty 编码器 |
| `createClientBootstrap(...)` | 创建 Netty Bootstrap |

### 3.7 消息类型详解

#### 3.7.1 请求消息

##### AddSymbolRequest

```java
public class AddSymbolRequest extends ProtocolMessage {
    public AddSymbolRequest(int id, String name)
    // 消息体：品种 ID (4字节) + 品种名称 (8字节 UTF-8)
}
```

##### AddOrderRequest

```java
public class AddOrderRequest extends ProtocolMessage {
    // 订单结构（80 字节）：
    // - id: long (8字节)
    // - symbolId: int (4字节)
    // - orderType: byte (1字节)
    // - orderSide: byte (1字节)
    // - price: long (8字节)
    // - stopPrice: long (8字节)
    // - quantity: long (8字节)
    // - executedQuantity: long (8字节)
    // - leavesQuantity: long (8字节)
    // - timeInForce: byte (1字节)
    // - maxVisibleQuantity: long (8字节)
    // - slippage: long (8字节)
    // - trailingDistance: long (8字节)
    // - trailingStep: long (8字节)
}
```

#### 3.7.2 响应消息

##### SymbolResponse

```java
public class SymbolResponse extends ProtocolMessage {
    private byte errorCode;
    private boolean hasSymbol;
    private int symbolId;
    private String symbolName;
}
```

##### OrderResponse

```java
public class OrderResponse extends ProtocolMessage {
    private byte errorCode;
    private boolean hasOrder;
    private OrderHolder order;  // 订单信息
}
```

##### OrderBookResponse

```java
public class OrderBookResponse extends ProtocolMessage {
    private byte errorCode;
    private int symbolId;
    private boolean hasBestBid;
    private LevelHolder bestBid;  // 最佳买价
    private boolean hasBestAsk;
    private LevelHolder bestAsk;  // 最佳卖价
    private List<LevelHolder> bids;  // 买方深度
    private List<LevelHolder> asks;  // 卖方深度
}
```

##### SimpleResponse

```java
public class SimpleResponse extends ProtocolMessage {
    private byte errorCode;  // 仅包含错误码
}
```

#### 3.7.3 事件消息

##### OrderBookUpdateEvent

```java
public class OrderBookUpdateEvent extends ProtocolMessage {
    private int symbolId;
    private boolean isTop;       // 是否顶部更新
    private LevelHolder level;   // 更新的档位
    private String updateType;    // 更新类型
}
```

##### OrderUpdateEvent

```java
public class OrderUpdateEvent extends ProtocolMessage {
    private String action;           // 操作类型
    private OrderHolder order;        // 订单信息
    private boolean hasExecute;       // 是否有成交
    private long executePrice;        // 成交价格
    private long executeQuantity;      // 成交数量
}
```

---

## 4. 数据流与交互

### 4.1 HTTP 请求处理流程

```
┌─────────┐    ┌──────────────┐    ┌────────────────┐    ┌────────────┐
│  HTTP   │───▶│ TradingCtrl  │───▶│ProtocolClient  │───▶│NetworkBackend│
│ Request │    │              │    │   Service      │    │            │
└─────────┘    └──────────────┘    └────────────────┘    └────────────┘
                                                              │
                                                              ▼
                                                           ┌────────┐
                                                           │ C++    │
                                                           │ Core   │
                                                           └────────┘
                                                              │
                                                              ▼
                                                           ┌────────────┐
┌─────────┐    ┌──────────────┐    ┌────────────────┐    │NetworkBackend│
│  HTTP   │◀───│ TradingCtrl  │◀───│ProtocolClient  │◀───│            │
│ Response│    │              │    │   Service      │    └────────────┘
└─────────┘    └──────────────┘    └────────────────┘
```

### 4.2 实时数据推送流程

```
┌────────────┐    ┌────────────────────────┐    ┌────────────────────┐
│  C++ Core  │───▶│  NetworkBackend.recv()  │───▶│ProtocolClientService│
│            │    │                        │    │  onMessageReceived() │
└────────────┘    └────────────────────────┘    └─────────┬──────────┘
                                                           │
                                                           ▼
                                               ┌───────────────────────┐
                                               │ProtocolStreamSubscriber│
                                               │   handlePushMessage() │
                                               └───────────┬───────────┘
                                                           │
                                   ┌───────────────────────┼───────────────────────┐
                                   ▼                       ▼                       ▼
                            ┌────────────┐          ┌────────────┐          ┌────────────┐
                            │OrderBook   │          │  Order     │          │  Other     │
                            │UpdateEvent │          │UpdateEvent │          │  Events    │
                            └────────────┘          └────────────┘          └────────────┘
                                   │                       │                       │
                                   ▼                       ▼                       ▼
                            ┌────────────┐          ┌────────────┐          ┌────────────┐
                            │ Consumer   │          │ Consumer   │          │ Consumer   │
                            │Callback    │          │Callback    │          │Callback    │
                            └────────────┘          └────────────┘          └────────────┘
```

### 4.3 心跳与重连机制

```
┌─────────────┐         ┌─────────────────┐         ┌─────────────┐
│   Client    │         │  Heartbeat      │         │   Server    │
│             │         │  Thread         │         │             │
└──────┬──────┘         └────────┬────────┘         └──────┬──────┘
       │                         │                        │
       │    定时发送心跳           │                        │
       │─────────────────────────▶│                        │
       │                         │    转发心跳             │
       │                         │────────────────────────▶│
       │                         │                        │
       │                         │    接收响应             │
       │                         │◀────────────────────────│
       │                         │                        │
       │                         │ 更新 lastRecvTime      │
       │                         │                        │
       │    超时检测              │                        │
       │◀─────────────────────────│                        │
       │                         │                        │
       │    触发重连              │                        │
       │─────────────────────────│                        │
       │                         │                        │
       │    重新连接              │                        │
       │───────────────────────────────────────────────────│
       │                         │                        │
```

---

## 5. 依赖关系

### 5.1 项目依赖（pom.xml）

```xml
<!-- Spring Boot 框架 -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-validation</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>

<!-- Netty 网络框架 -->
<dependency>
    <groupId>io.netty</groupId>
    <artifactId>netty-all</artifactId>
    <version>4.1.107.Final</version>
</dependency>

<!-- Lombok 工具 -->
<dependency>
    <groupId>org.projectlombok</groupId>
    <artifactId>lombok</artifactId>
    <optional>true</optional>
</dependency>

<!-- 测试框架 -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-test</artifactId>
    <scope>test</scope>
</dependency>
```

### 5.2 依赖关系图

```
┌─────────────────────────────────────────────────────────────┐
│                    Java Admin Backend                        │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  ┌──────────────┐                                           │
│  │Spring Boot   │                                           │
│  │Starter Web   │                                           │
│  └──────┬───────┘                                           │
│         │                                                   │
│         ├─── Servlet API                                    │
│         ├─── Spring MVC                                      │
│         └─── Jackson (JSON)                                  │
│                                                              │
│  ┌──────────────┐                                           │
│  │Netty 4.1     │                                           │
│  └──────┬───────┘                                           │
│         │                                                   │
│         ├─── Buffer (ByteBuf)                              │
│         ├─── Channel                                         │
│         ├─── Pipeline                                        │
│         └─── EventLoop                                      │
│                                                              │
│  ┌──────────────┐                                           │
│  │Lombok        │                                           │
│  └──────────────┘                                           │
│         │                                                   │
│         ├─── @Data                                          │
│         ├─── @Slf4j                                         │
│         └─── @RequiredArgsConstructor                       │
│                                                              │
│  ┌──────────────┐                                           │
│  │DPDK JNI     │  ← 可选 (native library)                    │
│  └──────────────┘                                           │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

### 5.3 技术版本要求

| 组件 | 版本要求 | 说明 |
|-----|---------|------|
| Java | 17+ | 必须支持 |
| Spring Boot | 3.2.5 | 需兼容 |
| Netty | 4.1.107.Final | 低延迟网络 |
| Lombok | (由 Spring Boot 管理) | 注解处理 |

---

## 6. 配置说明

### 6.1 application.yml 完整配置

```yaml
server:
  port: 8080

protocol:
  host: localhost
  port: 50051
  backend: netty  # 可选: netty, dpdk

  tcp:
    host: localhost
    port: 50051

  dpdk:
    localIp: 0.0.0.0
    localPort: 0
    remoteIp: 127.0.0.1
    remotePort: 50051

  heartbeat:
    intervalSec: 5    # 心跳间隔
    timeoutSec: 15    # 心跳超时

logging:
  level:
    com.cpptrader.admin.protocol: DEBUG
    io.netty: WARN
```

### 6.2 配置类映射

| YML 配置项 | Java 配置类 | 类型 |
|----------|-----------|------|
| `protocol.host` | `ProtocolConfig.host` | String |
| `protocol.port` | `ProtocolConfig.port` | int |
| `protocol.backend` | `ProtocolConfig.backend` | String |
| `protocol.tcp.*` | `ProtocolConfig.tcp` | TcpConfig |
| `protocol.dpdk.*` | `ProtocolConfig.dpdk` | DpdkConfig |
| `protocol.heartbeat.*` | `ProtocolConfig.heartbeat` | HeartbeatConfig |

---

## 7. 运行方式

### 7.1 环境要求

- **JDK**: 17 或更高版本
- **Maven**: 3.6 或更高版本
- **C++ Trading Core**: 必须已启动并监听配置端口

### 7.2 编译项目

```bash
# 进入项目目录
cd java-admin

# 编译项目
mvn clean compile

# 运行测试
mvn test

# 打包项目
mvn clean package

# 跳过测试打包
mvn clean package -DskipTests
```

### 7.3 运行应用

```bash
# 使用 Maven 运行
mvn spring-boot:run

# 运行打包后的 JAR
java -jar target/java-admin-1.0.0.jar

# 指定配置文件运行
java -jar target/java-admin-1.0.0.jar --spring.config.location=./config/application.yml
```

### 7.4 Docker 部署（可选）

```dockerfile
FROM eclipse-temurin:17-jre-alpine
COPY target/java-admin-1.0.0.jar /app/app.jar
WORKDIR /app
ENTRYPOINT ["java", "-jar", "app.jar"]
```

### 7.5 API 测试示例

```bash
# 添加交易品种
curl -X POST "http://localhost:8080/api/symbols?id=1&name=BTCUSD"

# 查询交易品种
curl -X GET "http://localhost:8080/api/symbols/1"

# 添加订单
curl -X POST "http://localhost:8080/api/orders?id=1001&symbolId=1&side=BUY&price=50000&quantity=100"

# 查询订单
curl -X GET "http://localhost:8080/api/orders/1001"

# 撤单
curl -X DELETE "http://localhost:8080/api/orders/1001"

# 启用撮合
curl -X POST "http://localhost:8080/api/matching/enable"
```

---

## 8. 测试

### 8.1 测试类列表

| 测试类 | 覆盖范围 |
|-------|---------|
| `FrameDecoderTest` | 帧解码器功能测试 |
| `ProtocolMessageTest` | 消息编解码测试 |
| `ProtocolValidatorTest` | 消息验证测试 |
| `RequestResponseTest` | 请求响应交互测试 |

### 8.2 运行测试

```bash
# 运行所有测试
mvn test

# 运行特定测试类
mvn test -Dtest=FrameDecoderTest

# 生成测试报告
mvn test surefire-report:report
```

---

## 9. 关键设计模式

### 9.1 工厂模式

- `ProtocolMessageFactory`：创建协议消息实例
- `CodecFactory`：提供编解码便捷方法

### 9.2 策略模式

- `INetworkBackend`：支持多种网络后端实现
  - `NettyTcpBackend`：TCP 连接
  - `DpdkJniBackend`：DPDK 高性能连接

### 9.3 观察者模式

- `ProtocolStreamSubscriber`：订阅者管理
- 回调机制处理实时事件

### 9.4 模板方法模式

- `ProtocolMessage`：定义消息编解码骨架
- 子类实现具体的 encode/decode

### 9.5 单例模式

- `ProtocolMessageFactory.getInstance()`
- `CodecFactory.getInstance()`

---

## 10. 性能优化建议

### 10.1 网络层优化

1. **使用 DPDK 后端**：在高性能场景下使用 DPDK，绕过内核协议栈
2. **Netty 参数调优**：
   - 启用 TCP_NODELAY 降低延迟
   - 合理配置 ByteBuf 池化
3. **心跳间隔调整**：根据网络质量调整心跳参数

### 10.2 应用层优化

1. **连接池**：如果需要多个连接，考虑实现连接池
2. **异步处理**：对于非关键请求，使用异步发送
3. **批量订阅**：避免频繁的订阅/取消订阅操作

### 10.3 监控指标

建议监控以下指标：
- 连接状态和重连次数
- 消息发送/接收延迟
- 帧解码错误率
- 心跳超时次数

---

## 11. 故障排查

### 11.1 常见问题

#### 连接失败

```
症状：ProtocolClientService failed to connect
解决：
1. 检查 C++ Trading Core 是否启动
2. 验证 host 和 port 配置
3. 检查防火墙设置
```

#### 心跳超时

```
症状：Heartbeat timeout, closing connection
解决：
1. 增加 heartbeat.timeoutSec 配置
2. 检查网络质量
3. 增加 C++ Core 的心跳响应延迟
```

#### 消息解码错误

```
症状：Invalid magic: 0xXXXX, expected: 0x5452
解决：
1. 确认两端协议版本一致
2. 检查字节序是否正确（应为 Little Endian）
3. 验证消息格式
```

### 11.2 日志级别调整

```yaml
logging:
  level:
    # 协议层详细日志
    com.cpptrader.admin.protocol: DEBUG
    # Netty 网络层日志
    io.netty: WARN
    # Spring 框架日志
    org.springframework: INFO
```

---

## 12. 版本历史

| 版本 | 日期 | 说明 |
|-----|------|------|
| 1.0.0 | 2026-05-19 | 初始版本，实现核心协议通信功能 |

---

## 13. 联系方式

如有技术问题或建议，请联系项目维护团队。

---

## 附录 A：协议头格式

```
┌─────────────────────────────────────────────────────────────┐
│                    协议头（8 字节）                            │
├────────┬────────┬────────┬────────┬────────┬────────────────┤
│ MAGIC  │VERSION │MSGTYPE │ FLAGS  │RESERVED│ BODY_SIZE      │
│ (2B)   │ (1B)   │ (1B)   │ (1B)   │ (1B)   │ (2B)           │
└────────┴────────┴────────┴────────┴────────┴────────────────┘

字段说明：
- MAGIC: 0x5452 (小端序)
- VERSION: 1
- MSGTYPE: 消息类型
- FLAGS: 标志位组合
- RESERVED: 保留字段
- BODY_SIZE: 消息体长度
```

---

## 附录 B：字节序说明

所有多字节整数使用 **Little Endian**（小端序）编码：
- 最小有效字节在前
- 例：`0x12345678` 存储为 `78 56 34 12`

---

## 附录 C：错误码对照表

| 错误码 | 名称 | 说明 |
|-------|-----|------|
| 0 | OK | 操作成功 |
| 1 | ERROR | 通用错误 |
| 2 | NOT_FOUND | 资源未找到 |
| 3 | ALREADY_EXISTS | 资源已存在 |
| 4 | INVALID_ARGUMENT | 参数无效 |

---

*文档生成时间：2026-05-19*
*项目版本：1.0.0*
