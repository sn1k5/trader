# Java-C++ 协议一致性修复 - 修改文档

## 文档信息

| 项目 | 内容 |
|------|------|
| **文档版本** | 1.0 |
| **创建日期** | 2026-05-20 |
| **适用版本** | CppTrader v0_debug |
| **修改目的** | 修复 Java 客户端与 C++ 服务端之间的协议不一致问题，确保跨语言通信正确 |

---

## 一、问题概述

在 Java 客户端与 C++ 服务端的二进制协议实现中，存在 **8 处严重不一致**，导致两端完全无法正常通信：

| 问题编号 | 问题描述 | 严重级别 |
|---------|---------|---------|
| P001 | OrderType 枚举值反转（MARKET=1, LIMIT=0） | 🔴 严重 |
| P002 | OrderProto 缺少 Padding1 字节（87 vs 88 字节） | 🔴 严重 |
| P003 | ErrorCode 定义完全不同（5个通用码 vs 11个具体码） | 🔴 严重 |
| P004 | SymbolResponse 多了 hasSymbol 标志字节 | 🔴 严重 |
| P005 | OrderResponse 多了 hasOrder 标志字节 | 🔴 严重 |
| P006 | OrderBookResponse 格式完全不同 | 🔴 严重 |
| P007 | OrderBookUpdateEvent 使用字符串而非数值枚举 | 🔴 严重 |
| P008 | OrderUpdateEvent 使用字符串而非数值枚举 | 🔴 严重 |

---

## 二、修改原则

**以 C++ 服务端为基准**，修改 Java 客户端代码以匹配 C++ 的二进制协议格式。理由：
1. C++ 是服务端，修改影响面更大
2. C++ 使用 `#pragma pack(1)` 固定内存布局，是二进制协议的"事实标准"
3. C++ 的枚举定义更完整

---

## 三、修改详情

### 3.1 ProtocolConstants.java

**文件路径**：`java-admin/src/main/java/com/cpptrader/admin/protocol/ProtocolConstants.java`

**修改内容**：

#### OrderType 枚举修正

| 修改前 | 修改后 | 说明 |
|--------|--------|------|
| LIMIT = 0 | MARKET = 0 | 与 C++ 对齐 |
| MARKET = 1 | LIMIT = 1 | 与 C++ 对齐 |
| STOP = 2 | STOP = 2 | 不变 |
| STOP_LIMIT = 3 | STOP_LIMIT = 3 | 不变 |
| (缺失) | TRAILING_STOP = 4 | 新增 |
| (缺失) | TRAILING_STOP_LIMIT = 5 | 新增 |

#### ErrorCode 枚举修正

| 修改前 | 修改后 | 说明 |
|--------|--------|------|
| OK = 0 | OK = 0 | 不变 |
| ERROR = 1 | SYMBOL_DUPLICATE = 1 | 与 C++ 对齐 |
| NOT_FOUND = 2 | SYMBOL_NOT_FOUND = 2 | 与 C++ 对齐 |
| ALREADY_EXISTS = 3 | ORDER_BOOK_DUPLICATE = 3 | 与 C++ 对齐 |
| INVALID_ARGUMENT = 4 | ORDER_BOOK_NOT_FOUND = 4 | 与 C++ 对齐 |
| (缺失) | ORDER_DUPLICATE = 5 | 新增 |
| (缺失) | ORDER_NOT_FOUND = 6 | 新增 |
| (缺失) | ORDER_ID_INVALID = 7 | 新增 |
| (缺失) | ORDER_TYPE_INVALID = 8 | 新增 |
| (缺失) | ORDER_PARAMETER_INVALID = 9 | 新增 |
| (缺失) | ORDER_QUANTITY_INVALID = 10 | 新增 |

#### 新增常量类

- **UpdateType**：ADD=1, UPDATE=2, DELETE=3
- **LevelType**：BID=0, ASK=1
- **ActionType**：ADD=1, UPDATE=2, DELETE=3, EXECUTE=4

**影响范围**：所有使用 OrderType 和 ErrorCode 的代码

---

### 3.2 ProtocolMessage.java

**文件路径**：`java-admin/src/main/java/com/cpptrader/admin/protocol/ProtocolMessage.java`

**修改内容**：在 OrderProto 编解码中添加 Padding1 字节

```java
// writeOrderProto - 在 TimeInForce 之后添加 Padding1
buf.put(o.timeInForce);
buf.put((byte) 0); // Padding1，与 C++ 对齐
buf.putLong(o.maxVisibleQuantity);

// readOrderProto - 在 TimeInForce 之后跳过 Padding1
o.timeInForce = buf.get();
buf.get(); // Padding1
o.maxVisibleQuantity = buf.getLong();
```

**修改原因**：C++ 的 OrderProto 结构体在 TimeInForce 和 MaxVisibleQuantity 之间有 1 字节 Padding，Java 需要对齐

**效果**：OrderProto 编码后为 **88 字节**（修改前为 87 字节）

---

### 3.3 SymbolResponse.java

**文件路径**：`java-admin/src/main/java/com/cpptrader/admin/protocol/responses/SymbolResponse.java`

**修改内容**：

| 修改项 | 修改前 | 修改后 |
|--------|--------|--------|
| 格式 | [Error(1B)] [hasSymbol(1B)] [SymbolProto(12B)] | [Error(1B)] [SymbolProto(12B)] |
| 大小 | 2 或 14 字节（可变） | 固定 13 字节 |
| hasSymbol | 参与编解码 | 不参与编解码，由 errorCode==OK 推导 |
| 构造函数 | SymbolResponse(byte, boolean, ...) | SymbolResponse(byte, int, String) |

**修改原因**：C++ 的 SymbolResponse 使用固定布局，不包含条件性标志字节

---

### 3.4 OrderResponse.java

**文件路径**：`java-admin/src/main/java/com/cpptrader/admin/protocol/responses/OrderResponse.java`

**修改内容**：

| 修改项 | 修改前 | 修改后 |
|--------|--------|--------|
| 格式 | [Error(1B)] [hasOrder(1B)] [OrderProto(87B)] | [Error(1B)] [OrderProto(88B)] |
| 大小 | 2 或 89 字节（可变） | 固定 89 字节 |
| hasOrder | 参与编解码 | 不参与编解码，由 errorCode==OK 推导 |
| 构造函数 | OrderResponse(byte, boolean) | OrderResponse(byte) |

**修改原因**：C++ 的 OrderResponse 使用固定布局，不包含条件性标志字节

---

### 3.5 OrderBookResponse.java

**文件路径**：`java-admin/src/main/java/com/cpptrader/admin/protocol/responses/OrderBookResponse.java`

**修改内容**：

| 修改项 | 修改前 | 修改后 |
|--------|--------|--------|
| 格式 | [Error(1B)] [SymbolId(4B)] [hasBestBid(1B)] [BestBid(32B)?] [hasBestAsk(1B)] [BestAsk(32B)?] [Bids] [Asks] | [SymbolId(4B)] [BestBid(32B)] [BestAsk(32B)] [BidCount(2B)] [Bids] [AskCount(2B)] [Asks] |
| errorCode | 存在 | 移除 |
| hasBestBid/hasBestAsk | 存在 | 移除 |
| BestBid/BestAsk | 条件性存在 | 始终存在（可能为零值） |
| 构造函数 | OrderBookResponse(byte) | OrderBookResponse(int) |

**修改原因**：C++ 的 OrderBookSnapshot 不包含 errorCode 和条件性标志字节

---

### 3.6 OrderBookUpdateEvent.java

**文件路径**：`java-admin/src/main/java/com/cpptrader/admin/protocol/events/OrderBookUpdateEvent.java`

**修改内容**：

| 修改项 | 修改前 | 修改后 |
|--------|--------|--------|
| updateType | String（如 "ADD"） | byte 数值枚举（1=ADD, 2=UPDATE, 3=DELETE） |
| levelType | 缺失 | 新增 byte（0=BID, 1=ASK） |
| 格式 | [SymbolId(4B)] [IsTop(1B)] [Level(32B)] [typeLen(1B)] [updateType(16B)] | [SymbolId(4B)] [IsTop(1B)] [Type(1B)] [LevelType(1B)] [Padding1(1B)] [Level(32B)] |
| 大小 | 约 54 字节 | 固定 40 字节 |

**修改原因**：C++ 使用数值枚举而非字符串，更高效且格式固定

---

### 3.7 OrderUpdateEvent.java

**文件路径**：`java-admin/src/main/java/com/cpptrader/admin/protocol/events/OrderUpdateEvent.java`

**修改内容**：

| 修改项 | 修改前 | 修改后 |
|--------|--------|--------|
| action | String（如 "ADD"） | byte 数值枚举（1=ADD, 2=UPDATE, 3=DELETE, 4=EXECUTE） |
| hasExecute | 存在 | 移除 |
| 格式 | [actionLen(1B)] [action(16B)] [OrderProto(87B)] [hasExecute(1B)] [ExecutePrice(8B)] [ExecuteQuantity(8B)] | [Action(1B)] [OrderProto(88B)] [ExecutePrice(8B)] [ExecuteQuantity(8B)] |
| 大小 | 约 121 字节 | 固定 105 字节 |

**修改原因**：C++ 使用数值枚举而非字符串，且不包含 hasExecute 标志

---

### 3.8 FrameDecoder.java

**文件路径**：`java-admin/src/main/java/com/cpptrader/admin/protocol/FrameDecoder.java`

**修改内容**：

| 修改项 | 修改前 | 修改后 |
|--------|--------|--------|
| maxFrameSize | 1MB (1024 * 1024) | 64MB (64 * 1024 * 1024) |

**修改原因**：与 C++ 端保持一致，支持大型订单簿快照传输

---

### 3.9 ProtocolMessageFactory.java

**文件路径**：`java-admin/src/main/java/com/cpptrader/admin/protocol/factory/ProtocolMessageFactory.java`

**修改内容**：

- `createSymbolResponse` 方法移除了 `hasSymbol` 参数
- 适配 SymbolResponse 新的构造函数签名

---

### 3.10 ProtocolValidator.java

**文件路径**：`java-admin/src/main/java/com/cpptrader/admin/protocol/validation/ProtocolValidator.java`

**修改内容**：

- `validateOrderType` 方法添加了 TRAILING_STOP 和 TRAILING_STOP_LIMIT 的验证
- 验证范围扩展为 0-5

---

### 3.11 TradingController.java

**文件路径**：`java-admin/src/main/java/com/cpptrader/admin/controller/TradingController.java`

**修改内容**：

| 修改项 | 修改前 | 修改后 |
|--------|--------|--------|
| OrderBookResponse 处理 | 使用 getErrorCode()/isHasBestBid()/isHasBestAsk() | 直接访问字段，通过 totalVolume > 0 判断有效性 |
| SymbolResponse hasSymbol | isHasSymbol() | getErrorCode() == OK |
| OrderResponse hasOrder | isHasOrder() | getErrorCode() == OK |
| OrderType switch | 4 种类型 | 6 种类型（添加 TRAILING_STOP/TRAILING_STOP_LIMIT） |

---

### 3.12 protocol-api.md

**文件路径**：`java-admin/protocol-api.md`

**修改内容**：

1. **第5节 错误码定义**：更新为 11 个具体码
2. **第6节 订单类型定义**：修正顺序，添加新类型
3. **第4.2节 OrderProto**：更新为 88 字节，添加 Padding1 说明
4. **第11节 响应消息API**：更新为固定格式，移除条件性标志
5. **第12节 事件消息API**：更新为数值枚举

---

## 四、修改影响评估

### 4.1 兼容性影响

| 修改项 | 兼容性影响 | 风险等级 |
|--------|-----------|---------|
| OrderType 枚举 | 破坏现有行为（MARKET/LIMIT 互换） | 🔴 高 |
| OrderProto Padding | 破坏现有编解码 | 🔴 高 |
| ErrorCode 定义 | 破坏现有错误处理 | 🔴 高 |
| Response 消息格式 | 破坏现有响应解析 | 🔴 高 |
| Event 消息格式 | 破坏现有推送解析 | 🔴 高 |
| FrameDecoder 大小 | 仅增大限制，兼容 | 🟢 低 |

### 4.2 迁移建议

由于当前 Java 客户端与 C++ 服务端**实际上无法正常通信**，建议：
1. **一次性全面部署**：所有修改同时上线
2. **测试验证**：先在测试环境验证端到端通信
3. **监控告警**：上线后密切监控连接和消息处理

---

## 五、验证检查清单

- [x] ProtocolConstants.OrderType 与 C++ 一致（MARKET=0, LIMIT=1, STOP=2, STOP_LIMIT=3, TRAILING_STOP=4, TRAILING_STOP_LIMIT=5）
- [x] ProtocolConstants.ErrorCode 与 C++ 一致（11 个具体码）
- [x] ProtocolMessage.OrderProto 为 88 字节（含 Padding1）
- [x] SymbolResponse 固定 13 字节，无 hasSymbol 标志
- [x] OrderResponse 固定 89 字节，无 hasOrder 标志
- [x] OrderBookResponse 无 errorCode/hasBestBid/hasBestAsk
- [x] OrderBookUpdateEvent 固定 40 字节，使用数值枚举
- [x] OrderUpdateEvent 固定 105 字节，使用数值枚举
- [x] FrameDecoder.maxFrameSize = 64MB
- [x] 辅助代码（Factory/Validator/Controller）已适配
- [x] 文档已更新

---

## 六、代码变更清单

| 文件 | 修改类型 | 变更数量 |
|------|---------|---------|
| ProtocolConstants.java | 修改/新增 | 3 处内部类修改，3 处内部类新增 |
| ProtocolMessage.java | 修改 | 2 处方法修改 |
| SymbolResponse.java | 修改 | 构造函数、encode、decode、getBodySize |
| OrderResponse.java | 修改 | 构造函数、encode、decode、getBodySize |
| OrderBookResponse.java | 修改 | 构造函数、encode、decode、getBodySize，移除字段 |
| OrderBookUpdateEvent.java | 修改 | 字段类型变更、新增字段、encode、decode、getBodySize |
| OrderUpdateEvent.java | 修改 | 字段类型变更、移除字段、encode、decode、getBodySize |
| FrameDecoder.java | 修改 | 1 处常量修改 |
| ProtocolMessageFactory.java | 修改 | 1 处方法签名修改 |
| ProtocolValidator.java | 修改 | 1 处验证逻辑修改 |
| TradingController.java | 修改 | 多处适配修改 |
| protocol-api.md | 修改 | 5 节内容更新 |

---

## 七、版本历史

| 版本 | 日期 | 修改人 | 说明 |
|------|------|--------|------|
| 1.0 | 2026-05-20 | Protocol Team | 初始版本，包含所有 12 个文件的修改说明 |