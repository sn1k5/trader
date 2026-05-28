# CppTrader 自研协议 API 文档

## 1. 协议概述

本文档描述了 CppTrader 交易系统使用的自研二进制协议的实现规范。该协议采用小端序（Little Endian）字节序，支持请求/响应模式和推送模式，具备完整的错误处理和验证机制。

### 1.1 协议特性

- **协议版本**: 1
- **魔数**: 0x5452 ("TR")
- **Header 大小**: 8 字节
- **字节序**: Little Endian
- **传输编码**: UTF-8

### 1.2 技术实现

- **网络通信**: 基于 Netty TCP 客户端
- **依赖**: 仅依赖 Netty 4.x，无 DPDK/JNDI 等复杂技术
- **验证机制**: 内置数据验证和错误处理
- **可扩展性**: 工厂模式支持消息类型扩展

---

## 2. 协议格式

### 2.1 消息结构

```
+----------------+----------------+----------------+----------------+
|   Magic (2B)   |   Version (1B) |  MsgType (1B)  |   Flags (1B)   |
+----------------+----------------+----------------+----------------+
|  Reserved (1B) |              BodyLen (2B)                           |
+----------------+----------------+----------------+----------------+
|                         Body (Variable)                              |
+----------------+----------------+----------------+----------------+
```

### 2.2 Header 字段说明

| 字段 | 类型 | 大小 | 说明 |
|------|------|------|------|
| Magic | short | 2B | 魔数，固定值 0x5452 |
| Version | byte | 1B | 协议版本，当前为 1 |
| MsgType | byte | 1B | 消息类型 |
| Flags | byte | 1B | 标志位 |
| Reserved | byte | 1B | 保留字段 |
| BodyLen | short | 2B | Body 长度 |

### 2.3 Flags 定义

| 标志 | 值 | 说明 |
|------|-----|------|
| FLAG_REQUEST | 0x01 | 请求消息 |
| FLAG_RESPONSE | 0x02 | 响应消息 |
| FLAG_PUSH | 0x04 | 推送消息 |
| FLAG_ERROR | 0x08 | 错误消息 |
| FLAG_HEARTBEAT | 0x10 | 心跳消息 |

---

## 3. 消息类型定义

### 3.1 请求消息类型 (0x01 - 0x12)

| 类型 | 值 | 说明 |
|------|-----|------|
| ADD_SYMBOL_REQ | 0x01 | 添加交易品种 |
| DELETE_SYMBOL_REQ | 0x02 | 删除交易品种 |
| GET_SYMBOL_REQ | 0x03 | 获取交易品种信息 |
| ADD_ORDER_BOOK_REQ | 0x04 | 添加订单簿 |
| DELETE_ORDER_BOOK_REQ | 0x05 | 删除订单簿 |
| GET_ORDER_BOOK_REQ | 0x06 | 获取订单簿信息 |
| ADD_ORDER_REQ | 0x07 | 添加订单 |
| REDUCE_ORDER_REQ | 0x08 | 减少订单数量 |
| MODIFY_ORDER_REQ | 0x09 | 修改订单 |
| MITIGATE_ORDER_REQ | 0x0A | 触发订单 |
| REPLACE_ORDER_REQ | 0x0B | 替换订单 |
| DELETE_ORDER_REQ | 0x0C | 删除订单 |
| EXECUTE_ORDER_REQ | 0x0D | 执行订单 |
| GET_ORDER_REQ | 0x0E | 获取订单信息 |
| ENABLE_MATCHING_REQ | 0x0F | 启用撮合 |
| DISABLE_MATCHING_REQ | 0x10 | 禁用撮合 |
| SUBSCRIBE_ORDER_BOOK_REQ | 0x11 | 订阅订单簿更新 |
| SUBSCRIBE_ORDERS_REQ | 0x12 | 订阅订单更新 |

### 3.2 响应消息类型 (0x41 - 0x44)

| 类型 | 值 | 说明 |
|------|-----|------|
| SYMBOL_RESP | 0x41 | 交易品种响应 |
| ORDER_BOOK_RESP | 0x42 | 订单簿响应 |
| ORDER_RESP | 0x43 | 订单响应 |
| SIMPLE_RESP | 0x44 | 简单响应 |

### 3.3 推送消息类型 (0x81 - 0x82)

| 类型 | 值 | 说明 |
|------|-----|------|
| ORDER_BOOK_UPDATE_EVT | 0x81 | 订单簿更新事件 |
| ORDER_UPDATE_EVT | 0x82 | 订单更新事件 |

### 3.4 心跳消息 (0xC0 - 0xC1)

| 类型 | 值 | 说明 |
|------|-----|------|
| HEARTBEAT_REQ | 0xC0 | 心跳请求 |
| HEARTBEAT_RESP | 0xC1 | 心跳响应 |

---

## 4. 数据类型定义

### 4.1 SymbolProto (12 字节)

```
+----------------+----------------+----------------+----------------+
|     ID (4B)    |           Name (8B)                                 |
+----------------+----------------+----------------+----------------+
```

### 4.2 OrderProto (88 字节)

```
Offset  Field              Size  说明
0       ID                 8B    订单ID
8       SymbolID           4B    品种ID
12      Type               1B    订单类型
13      Side               1B    订单方向
14      Price              8B    价格
22      StopPrice          8B    止损价格
30      Quantity           8B    数量
38      ExecQty            8B    已成交数量
46      LeavesQty          8B    剩余数量
54      TimeInForce        1B    有效期类型
55      Padding1           1B    填充字段(固定0)
56      MaxVisibleQuantity 8B    最大可见数量
64      Slippage           8B    滑点
72      TrailingDistance   8B    追踪距离
80      TrailingStep       8B    追踪步长
```

字节布局:

```
+----------------+----------------+----------------+----------------+
|     ID (8B)    |  SymbolID (4B) |  Type (1B) |  Side (1B)       |
+----------------+----------------+----------------+----------------+
|     Price (8B) |  StopPrice (8B)|  Quantity (8B)                    |
+----------------+----------------+----------------+----------------+
| ExecQty (8B)   | LeavesQty (8B) |  TIF (1B) | Padding1 (1B)      |
+----------------+----------------+----------------+----------------+
| MaxVisQty (8B) |  Slippage (8B) |  TrailDist (8B)                  |
+----------------+----------------+----------------+----------------+
|  TrailStep (8B)                                                        |
+----------------+----------------+----------------+----------------+
```

### 4.3 LevelProto (32 字节)

```
+----------------+----------------+----------------+----------------+
|    Price (8B)  |  TotalVol (8B) |  VisibleVol (8B)                   |
+----------------+----------------+----------------+----------------+
|    Orders (8B)                                                        |
+----------------+----------------+----------------+----------------+
```

---

## 5. 错误码定义

| 错误码 | 值 | 说明 |
|--------|-----|------|
| OK | 0 | 成功 |
| SYMBOL_DUPLICATE | 1 | 交易品种重复 |
| SYMBOL_NOT_FOUND | 2 | 交易品种不存在 |
| ORDER_BOOK_DUPLICATE | 3 | 订单簿重复 |
| ORDER_BOOK_NOT_FOUND | 4 | 订单簿不存在 |
| ORDER_DUPLICATE | 5 | 订单重复 |
| ORDER_NOT_FOUND | 6 | 订单不存在 |
| ORDER_ID_INVALID | 7 | 订单ID无效 |
| ORDER_TYPE_INVALID | 8 | 订单类型无效 |
| ORDER_PARAMETER_INVALID | 9 | 订单参数无效 |
| ORDER_QUANTITY_INVALID | 10 | 订单数量无效 |

---

## 6. 订单类型定义

| 类型 | 值 | 说明 |
|------|-----|------|
| MARKET | 0 | 市价单 |
| LIMIT | 1 | 限价单 |
| STOP | 2 | 止损单 |
| STOP_LIMIT | 3 | 止损限价单 |
| TRAILING_STOP | 4 | 追踪止损单 |
| TRAILING_STOP_LIMIT | 5 | 追踪止损限价单 |

---

## 7. 订单方向定义

| 方向 | 值 | 说明 |
|------|-----|------|
| BUY | 0 | 买入 |
| SELL | 1 | 卖出 |

---

## 8. Time In Force 定义

| TIF | 值 | 说明 |
|-----|-----|------|
| GTC | 0 | Good Till Cancel (取消前有效) |
| IOC | 1 | Immediate Or Cancel (立即成交或取消) |
| FOK | 2 | Fill Or Kill (全部成交或取消) |
| AON | 3 | All Or None (全部否则无效) |

---

## 9. 核心类 API

### 9.1 ProtocolMessage (抽象基类)

所有消息类型的基类。

**方法:**

```java
public abstract class ProtocolMessage {
    // 获取消息类型
    public byte getMsgType()

    // 获取标志位
    public byte getFlags()

    // 设置标志位
    public void setFlags(byte flags)

    // 编码消息到 ByteBuffer
    public abstract void encode(ByteBuffer buf)

    // 从 ByteBuffer 解码消息
    public abstract void decode(ByteBuffer buf)

    // 获取 Body 大小
    public int getBodySize()

    // 获取消息总大小
    public final int getTotalSize()

    // 序列化为字节数组
    public final byte[] toBytes()

    // 从字节数组反序列化
    public final void fromBytes(byte[] data)

    // 验证消息
    public ProtocolValidator.ValidationResult validate()

    // 获取调试字符串
    public String toDebugString()
}
```

### 9.2 ProtocolConstants

协议常量定义类。

```java
public final class ProtocolConstants {
    public static final short MAGIC = (short) 0x5452;
    public static final byte VERSION = 1;
    public static final int HEADER_SIZE = 8;

    // 消息类型常量
    public static final byte ADD_SYMBOL_REQ = 0x01;
    // ... 其他消息类型

    // 标志常量
    public static final byte FLAG_REQUEST = 0x01;
    public static final byte FLAG_RESPONSE = 0x02;
    // ...

    // 错误码内部类
    public static final class ErrorCode {
        public static final byte OK = 0;
        public static final byte SYMBOL_DUPLICATE = 1;
        public static final byte SYMBOL_NOT_FOUND = 2;
        public static final byte ORDER_BOOK_DUPLICATE = 3;
        public static final byte ORDER_BOOK_NOT_FOUND = 4;
        public static final byte ORDER_DUPLICATE = 5;
        public static final byte ORDER_NOT_FOUND = 6;
        public static final byte ORDER_ID_INVALID = 7;
        public static final byte ORDER_TYPE_INVALID = 8;
        public static final byte ORDER_PARAMETER_INVALID = 9;
        public static final byte ORDER_QUANTITY_INVALID = 10;
        // ...
        public static String name(byte code)
    }

    // 订单类型内部类
    public static final class OrderType {
        public static final byte MARKET = 0;
        public static final byte LIMIT = 1;
        public static final byte STOP = 2;
        public static final byte STOP_LIMIT = 3;
        public static final byte TRAILING_STOP = 4;
        public static final byte TRAILING_STOP_LIMIT = 5;
        // ...
        public static String name(byte type)
    }

    // 订单方向内部类
    public static final class OrderSide {
        public static final byte BUY = 0;
        public static final byte SELL = 1;
        // ...
    }

    // Time In Force 内部类
    public static final class TimeInForce {
        public static final byte GTC = 0;
        public static final byte IOC = 1;
        // ...
    }
}
```

### 9.3 FrameDecoder

帧解码器，用于从字节流中解析完整的消息帧。

```java
public class FrameDecoder {
    // 内部状态
    public enum State {
        HEAD,           // 等待解析 Header
        BODY,           // 等待解析 Body
        ERROR_RECOVERY  // 错误恢复状态
    }

    // 构造函数
    public FrameDecoder()
    public FrameDecoder(ProtocolErrorHandler errorHandler)

    // 喂养数据
    public void feed(byte[] data, int offset, int len)
    public void feed(byte[] data)

    // 检查是否有完整帧
    public boolean hasCompleteFrame()

    // 尝试解码一帧
    public byte[] tryDecode()

    // 解码所有可用帧
    public List<byte[]> decodeAll()

    // 获取待处理消息类型
    public byte getPendingMsgType()

    // 获取待处理标志位
    public byte getPendingFlags()

    // 获取待处理 Body 长度
    public int getPendingBodyLength()

    // 获取当前状态
    public State getState()

    // 重置解码器
    public void reset()

    // 配置最大帧大小
    public void setMaxFrameSize(int maxFrameSize)

    // 获取帧计数
    public long getFrameCount()

    // 获取错误计数
    public long getErrorCount()

    // 清除统计
    public void clearStats()

    // 获取统计信息
    public String getStats()
}
```

### 9.4 ProtocolValidator

协议验证工具类。

```java
public class ProtocolValidator {

    // 验证结果内部类
    public static class ValidationResult {
        public static ValidationResult success()
        public static ValidationResult failure(String error)

        public ValidationResult withError(String error)
        public ValidationResult withWarning(String warning)

        public boolean isValid()
        public List<String> getErrors()
        public List<String> getWarnings()
        public String getErrorMessage()
        public String getWarningMessage()
    }

    // 验证方法
    public static ValidationResult validateHeader(byte[] data)
    public static ValidationResult validateCompleteMessage(byte[] data)
    public static ValidationResult validateSymbolId(int symbolId)
    public static ValidationResult validateOrderId(long orderId)
    public static ValidationResult validatePrice(long price)
    public static ValidationResult validateQuantity(long quantity)
    public static ValidationResult validateSymbolName(String name)
    public static ValidationResult validateOrderType(byte orderType)
    public static ValidationResult validateOrderSide(byte orderSide)
    public static ValidationResult validateTimeInForce(byte tif)
    public static ValidationResult validateMessage(ProtocolMessage message)
    public static ValidationResult validateByteBuffer(ByteBuffer buf, int requiredBytes, String operation)

    // 验证并抛出异常
    public static void requireValid(ValidationResult result)
    public static void requireValidOrThrow(ValidationResult result, String customMessage)
}
```

### 9.5 ProtocolException

协议异常类。

```java
public class ProtocolException extends RuntimeException {
    public ProtocolException(byte errorCode, String message)
    public ProtocolException(byte errorCode, String message, Throwable cause)
    public ProtocolException(String message)
    public ProtocolException(String message, Throwable cause)

    public byte getErrorCode()
    public String getErrorType()

    // 工厂方法
    public static ProtocolException invalidMagic(short magic)
    public static ProtocolException unsupportedVersion(byte version)
    public static ProtocolException invalidMessageType(byte msgType)
    public static ProtocolException invalidBodySize(int expected, int actual)
    public static ProtocolException bufferUnderflow(String operation)
    public static ProtocolException bufferOverflow(String operation)
    public static ProtocolException encodingError(String details)
    public static ProtocolException decodingError(String details)
    public static ProtocolException notFound(String resource)
    public static ProtocolException alreadyExists(String resource)
}
```

### 9.6 ProtocolErrorHandler

错误处理器。

```java
public class ProtocolErrorHandler {
    public interface ErrorCallback {
        void onError(ProtocolException exception)
        void onWarning(String warning)
        void onDebug(String debug)
    }

    public ProtocolErrorHandler()
    public ProtocolErrorHandler(ErrorCallback callback)

    public void setCallback(ErrorCallback callback)
    public void setLogEnabled(boolean logEnabled)
    public void setThrowOnError(boolean throwOnError)

    public void handleException(ProtocolException exception)
    public void handleWarning(String warning)
    public void handleDebug(String debug)

    // 验证方法
    public ProtocolException validateMagic(short magic)
    public ProtocolException validateVersion(byte version)
    public ProtocolException validateMessageType(byte msgType)

    // 工厂方法
    public static ProtocolErrorHandler createDefault()
    public static ProtocolErrorHandler createStrict()
    public static ProtocolErrorHandler createSilent()
}
```

### 9.7 ProtocolMessageFactory

消息工厂类。

```java
public class ProtocolMessageFactory {
    public ProtocolMessageFactory()

    // 创建消息实例
    public ProtocolMessage createMessage(byte msgType)

    // 解析消息
    public ProtocolMessage parseMessage(byte[] data)

    // 类型判断
    public boolean isRequest(byte msgType)
    public boolean isResponse(byte msgType)
    public boolean isPush(byte msgType)
    public boolean isHeartbeat(byte msgType)

    // 获取消息类型名称
    public String getMessageTypeName(byte msgType)
    public Class<? extends ProtocolMessage> getMessageType(byte msgType)

    // 便捷工厂方法
    public AddSymbolRequest createAddSymbolRequest(int id, String name)
    public DeleteSymbolRequest createDeleteSymbolRequest(int id)
    public GetSymbolRequest createGetSymbolRequest(int id)
    public AddOrderRequest createAddOrderRequest(...)
    // ...

    // 单例获取
    public static synchronized ProtocolMessageFactory getInstance()
}
```

### 9.8 CodecFactory

编解码工厂类。

```java
public class CodecFactory {

    // 编码结果
    public static class CodecResult {
        public static CodecResult success(byte[] data)
        public static CodecResult failure(String error)
        public boolean isSuccess()
        public byte[] getData()
        public String getError()
    }

    // 编码方法
    public static byte[] encodeMessage(ProtocolMessage message)
    public static CodecResult tryEncode(ProtocolMessage message)

    // 解码方法
    public static ProtocolMessage decodeMessage(byte[] data)
    public static CodecResult tryDecode(byte[] data)

    // Netty 组件工厂
    public static ByteToMessageDecoder createNettyDecoder()
    public static MessageToByteEncoder<ProtocolMessage> createNettyEncoder()
    public static ChannelInitializer<SocketChannel> createChannelInitializer()

    // Bootstrap 创建
    public static Bootstrap createClientBootstrap(String host, int port,
                                                   Consumer<byte[]> messageCallback,
                                                   Consumer<Channel> channelCallback)

    // 工具方法
    public static byte[] encodeMessageWithHeader(byte msgType, byte flags, ByteBuffer body)
    public static byte[] buildHeartbeatRequest()
    public static byte[] buildHeartbeatResponse()

    // 消息检查方法
    public static boolean isHeartbeat(byte[] data)
    public static boolean isRequest(byte[] data)
    public static boolean isResponse(byte[] data)
    public static boolean isPush(byte[] data)

    // 消息信息获取
    public static String getMessageInfo(byte[] data)

    // 单例获取
    public static synchronized CodecFactory getInstance()
}
```

---

## 10. 请求消息 API

### 10.1 AddSymbolRequest

添加交易品种请求。

```java
public class AddSymbolRequest extends ProtocolMessage {
    public AddSymbolRequest()
    public AddSymbolRequest(int id, String name)

    public int getId()
    public void setId(int id)
    public String getName()
    public void setName(String name)
}
```

**示例:**

```java
AddSymbolRequest request = new AddSymbolRequest(1, "BTCUSD");
byte[] data = request.toBytes();
```

### 10.2 DeleteSymbolRequest

删除交易品种请求。

```java
public class DeleteSymbolRequest extends ProtocolMessage {
    public DeleteSymbolRequest(int id)

    public int getId()
    public void setId(int id)
}
```

### 10.3 GetSymbolRequest

获取交易品种信息请求。

```java
public class GetSymbolRequest extends ProtocolMessage {
    public GetSymbolRequest(int id)

    public int getId()
    public void setId(int id)
}
```

### 10.4 AddOrderRequest

添加订单请求。

```java
public class AddOrderRequest extends ProtocolMessage {
    public AddOrderRequest(long id, int symbolId, byte orderType, byte orderSide,
                          long price, long stopPrice, long quantity,
                          byte timeInForce, long maxVisibleQty,
                          long slippage, long trailingDistance, long trailingStep)

    // Getter 和 Setter 方法
    public long getId()
    public void setId(long id)
    public int getSymbolId()
    public void setSymbolId(int symbolId)
    // ... 其他字段
}
```

### 10.5 DeleteOrderRequest

删除订单请求。

```java
public class DeleteOrderRequest extends ProtocolMessage {
    public DeleteOrderRequest(long orderId)

    public long getOrderId()
    public void setOrderId(long orderId)
}
```

### 10.6 GetOrderRequest

获取订单信息请求。

```java
public class GetOrderRequest extends ProtocolMessage {
    public GetOrderRequest(long orderId)

    public long getOrderId()
    public void setOrderId(long orderId)
}
```

---

## 11. 响应消息 API

### 11.1 SymbolResponse

交易品种响应。固定 13 字节 (ErrorCode 1B + SymbolId 4B + SymbolName 8B)。

```java
public class SymbolResponse extends ProtocolMessage {
    public SymbolResponse()
    public SymbolResponse(byte errorCode, int symbolId, String symbolName)

    public byte getErrorCode()
    public void setErrorCode(byte errorCode)
    public int getSymbolId()
    public void setSymbolId(int symbolId)
    public String getSymbolName()
    public void setSymbolName(String symbolName)
}
```

### 11.2 SimpleResponse

简单响应。

```java
public class SimpleResponse extends ProtocolMessage {
    public SimpleResponse()
    public SimpleResponse(byte errorCode)

    public byte getErrorCode()
    public void setErrorCode(byte errorCode)
}
```

### 11.3 OrderResponse

订单响应。固定 89 字节 (ErrorCode 1B + OrderProto 88B)。

```java
public class OrderResponse extends ProtocolMessage {
    public OrderResponse()
    public OrderResponse(byte errorCode)

    public byte getErrorCode()
    public void setErrorCode(byte errorCode)
    public OrderHolder getOrder()
    public void setOrder(OrderHolder order)
}
```

### 11.4 OrderBookResponse

订单簿响应。格式为 SymbolId(4B) + BestBid(32B) + BestAsk(32B) + Bids + Asks。

```java
public class OrderBookResponse extends ProtocolMessage {
    public OrderBookResponse()

    // Getter 和 Setter 方法
    public int getSymbolId()
    public void setSymbolId(int symbolId)
    public LevelHolder getBestBid()
    public LevelHolder getBestAsk()
    public List<LevelHolder> getBids()
    public List<LevelHolder> getAsks()
}
```

---

## 12. 事件消息 API

### 12.1 OrderBookUpdateEvent

订单簿更新事件。固定 40 字节。

updateType 数值枚举: 1=ADD, 2=UPDATE, 3=DELETE
levelType 数值枚举: 0=BID, 1=ASK

```java
public class OrderBookUpdateEvent extends ProtocolMessage {
    public OrderBookUpdateEvent()
    public OrderBookUpdateEvent(int symbolId, boolean isTop, LevelHolder level,
                                byte updateType, byte levelType)

    public int getSymbolId()
    public void setSymbolId(int symbolId)
    public boolean isTop()
    public void setTop(boolean top)
    public LevelHolder getLevel()
    public void setLevel(LevelHolder level)
    public byte getUpdateType()
    public void setUpdateType(byte updateType)
    public byte getLevelType()
    public void setLevelType(byte levelType)
}
```

### 12.2 OrderUpdateEvent

订单更新事件。固定 105 字节。

action 数值枚举: 1=ADD, 2=UPDATE, 3=DELETE, 4=EXECUTE

```java
public class OrderUpdateEvent extends ProtocolMessage {
    public OrderUpdateEvent()
    public OrderUpdateEvent(byte action, OrderHolder order,
                            long executePrice, long executeQuantity)

    public byte getAction()
    public void setAction(byte action)
    public OrderHolder getOrder()
    public void setOrder(OrderHolder order)
    public long getExecutePrice()
    public void setExecutePrice(long executePrice)
    public long getExecuteQuantity()
    public void setExecuteQuantity(long executeQuantity)
}
```

---

## 13. 使用示例

### 13.1 创建并发送请求

```java
// 创建请求消息
AddSymbolRequest request = new AddSymbolRequest(1, "BTCUSD");

// 序列化为字节数组
byte[] data = request.toBytes();

// 发送数据（通过 ProtocolClientService）
byte[] responseData = protocolClient.sendSync(data);

// 解析响应
SymbolResponse response = new SymbolResponse();
response.fromBytes(responseData);

// 处理响应
if (response.getErrorCode() == ProtocolConstants.ErrorCode.OK) {
    System.out.println("Symbol: " + response.getSymbolName());
}
```

### 13.2 使用 FrameDecoder 解析数据流

```java
FrameDecoder decoder = new FrameDecoder();

// 模拟接收数据
decoder.feed(receivedData);

// 解码所有完整帧
List<byte[]> frames = decoder.decodeAll();

for (byte[] frame : frames) {
    // 解析消息类型
    ByteBuffer buf = ByteBuffer.wrap(frame);
    buf.order(ByteOrder.LITTLE_ENDIAN);
    buf.getShort(); // magic
    buf.get(); // version
    byte msgType = buf.get();
    // ...
}
```

### 13.3 使用工厂类创建消息

```java
ProtocolMessageFactory factory = ProtocolMessageFactory.getInstance();

// 创建请求
AddSymbolRequest request = factory.createAddSymbolRequest(1, "BTCUSD");

// 解析消息
ProtocolMessage message = factory.parseMessage(data);

// 判断消息类型
if (factory.isRequest(msgType)) {
    System.out.println("Request: " + factory.getMessageTypeName(msgType));
} else if (factory.isResponse(msgType)) {
    System.out.println("Response: " + factory.getMessageTypeName(msgType));
} else if (factory.isPush(msgType)) {
    System.out.println("Push: " + factory.getMessageTypeName(msgType));
}
```

### 13.4 使用 CodecFactory

```java
CodecFactory codec = CodecFactory.getInstance();

// 编码消息
CodecFactory.CodecResult encodeResult = codec.tryEncode(request);
if (encodeResult.isSuccess()) {
    byte[] data = encodeResult.getData();
    // 发送数据
}

// 解码消息
CodecFactory.CodecResult decodeResult = codec.tryDecode(data);
if (decodeResult.isSuccess()) {
    ProtocolMessage message = codec.decodeMessage(data);
    // 处理消息
}

// 检查消息类型
if (codec.isHeartbeat(data)) {
    // 处理心跳
} else if (codec.isRequest(data)) {
    // 处理请求
}
```

### 13.5 自定义错误处理

```java
ProtocolErrorHandler errorHandler = new ProtocolErrorHandler(new ProtocolErrorHandler.ErrorCallback() {
    @Override
    public void onError(ProtocolException exception) {
        System.err.println("Protocol error: " + exception.getMessage());
    }

    @Override
    public void onWarning(String warning) {
        System.out.println("Warning: " + warning);
    }

    @Override
    public void onDebug(String debug) {
        System.out.println("Debug: " + debug);
    }
});

errorHandler.setThrowOnError(true);

// 使用错误处理器创建 FrameDecoder
FrameDecoder decoder = new FrameDecoder(errorHandler);
```

---

## 14. 文件结构

```
src/main/java/com/cpptrader/admin/
├── protocol/
│   ├── ProtocolConstants.java       # 协议常量定义
│   ├── ProtocolMessage.java        # 消息基类
│   ├── FrameDecoder.java           # 帧解码器
│   ├── exception/
│   │   ├── ProtocolException.java  # 协议异常
│   │   └── ProtocolErrorHandler.java # 错误处理器
│   ├── validation/
│   │   └── ProtocolValidator.java  # 验证工具
│   ├── factory/
│   │   ├── ProtocolMessageFactory.java # 消息工厂
│   │   └── CodecFactory.java       # 编解码工厂
│   ├── requests/                   # 请求消息类
│   │   ├── AddSymbolRequest.java
│   │   ├── DeleteSymbolRequest.java
│   │   ├── GetSymbolRequest.java
│   │   ├── AddOrderRequest.java
│   │   └── ...
│   ├── responses/                  # 响应消息类
│   │   ├── SymbolResponse.java
│   │   ├── SimpleResponse.java
│   │   ├── OrderResponse.java
│   │   └── ...
│   ├── events/                     # 事件消息类
│   │   ├── OrderBookUpdateEvent.java
│   │   └── OrderUpdateEvent.java
│   └── client/                     # 客户端组件
│       ├── ProtocolClientService.java
│       ├── ProtocolEncoder.java
│       ├── ProtocolDecoder.java
│       ├── NettyTcpBackend.java
│       └── ...
```

---

## 15. 版本历史

| 版本 | 日期 | 说明 |
|------|------|------|
| 1.0.0 | 2026-05-19 | 初始版本，实现核心协议功能 |

---

## 16. 注意事项

1. **字节序**: 所有多字节整数使用 Little Endian 字节序
2. **字符串编码**: 使用 UTF-8 编码
3. **线程安全**: 协议消息类本身非线程安全，需要外部同步
4. **缓冲区**: 编码时自动分配缓冲区，解码时需要外部提供足够空间
5. **错误处理**: 建议使用 `ProtocolErrorHandler` 进行统一错误处理
6. **验证**: 消息编解码过程中会自动进行数据验证

---

## 17. 联系方式

如有问题，请联系项目维护者。