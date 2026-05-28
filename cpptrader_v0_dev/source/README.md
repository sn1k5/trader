# source 目录说明

## 目录作用

`source/` 目录存放 CppTrader 项目的**源代码实现文件**（`.cpp` 文件）。与 `include/` 目录的头文件/内联实现相对应，这里存放非内联的、需要编译为二进制代码的源文件实现。

---

## 目录结构

```
source/
└── trader/
    ├── matching/              # 撮合引擎实现
    │   ├── market_manager.cpp # 市场管理器实现
    │   ├── order.cpp          # 订单验证实现
    │   └── order_book.cpp     # 订单簿实现
    └── providers/             # 数据提供方实现
        └── nasdaq/            # 纳斯达克数据
            └── itch_handler.cpp # ITCH 协议处理器实现
```

---

## 文件详解

### 1. matching/market_manager.cpp

**功能**：MarketManager 类的非内联成员函数实现。

**包含的核心逻辑**：

| 函数 | 说明 |
|------|------|
| `~MarketManager()` | 析构函数，释放所有订单、订单簿、品种的内存池资源 |
| `AddSymbol()` | 添加交易品种到 Symbol 数组，触发 `onAddSymbol` 回调 |
| `DeleteSymbol()` | 从数组中删除品种，触发 `onDeleteSymbol` 回调 |
| `AddOrderBook()` | 为指定品种创建订单簿，触发 `onAddOrderBook` 回调 |
| `DeleteOrderBook()` | 删除订单簿，触发 `onDeleteOrderBook` 回调 |
| `AddOrder()` | 添加订单的入口，根据订单类型分发到不同处理逻辑 |
| `AddMarketOrder()` | 市价单处理：直接触发撮合，不进入订单簿 |
| `AddLimitOrder()` | 限价单处理：插入订单簿，触发自动撮合 |
| `AddStopOrder()` | 止损单处理：检查触发条件，可能转为市价单 |
| `AddStopLimitOrder()` | 止损限价单处理：检查触发条件，可能转为限价单 |
| `ReduceOrder()` | 减少订单数量，更新订单簿中的价格层级 |
| `ModifyOrder()` | 修改订单价格和数量，重新插入订单簿 |
| `MitigateOrder()` | 订单缓解（In-Flight Mitigation），防止超额成交 |
| `ReplaceOrder()` | 替换订单（删除旧订单+创建新订单） |
| `DeleteOrder()` | 从订单簿和订单容器中删除订单 |
| `ExecuteOrder()` | 执行订单（按订单价格或指定价格） |
| `Match()` | 撮合主循环，遍历所有订单簿进行撮合 |
| `MatchMarket()` | 市价单撮合：计算可接受价格范围后撮合 |
| `MatchLimit()` | 限价单撮合：在价格限制内撮合 |
| `MatchOrder()` | 通用撮合逻辑，处理 IOC/FOK/AON 特殊订单 |
| `ActivateStopOrders()` | 激活触发的止损订单 |
| `CalculateMatchingChain()` | 计算 AON/FOK 订单的匹配链 |
| `ExecuteMatchingChain()` | 执行匹配链中的所有订单 |
| `RecalculateTrailingStopPrice()` | 重新计算跟踪止损价格 |
| `UpdateLevel()` | 更新价格层级，触发 MarketHandler 回调 |

**关键设计模式**：
- **内存池管理**：使用 `PoolAllocator` 管理 Symbol、OrderBook、OrderNode、LevelNode 的内存
- **事件驱动**：所有状态变更通过 `MarketHandler` 接口通知外部
- **递归控制**：`recursive` 参数防止撮合过程中的递归调用导致重复撮合

---

### 2. matching/order.cpp

**功能**：Order 结构体的验证逻辑实现。

**核心函数**：

```cpp
ErrorCode Order::Validate() const noexcept
```

**验证规则**：

| 订单类型 | 验证规则 |
|---------|---------|
| 所有订单 | Id > 0, Quantity >= LeavesQuantity > 0 |
| 市价单 | 必须是 IOC 或 FOK，不能是冰山订单 |
| 限价单 | 不能有滑点参数 |
| 止损单 | 不能是 AON，不能是冰山订单 |
| 止损限价单 | 不能有滑点 |
| 跟踪止损单 | TrailingDistance != 0，TrailingStep < TrailingDistance |

---

### 3. matching/order_book.cpp

**功能**：OrderBook 类的非内联成员函数实现。

**包含的核心逻辑**：

| 函数 | 说明 |
|------|------|
| `OrderBook()` | 构造函数，初始化所有指针和价格为默认值 |
| `~OrderBook()` | 析构函数，释放所有价格层级的内存池资源 |
| `AddLevel()` | 创建新的价格层级并插入 AVL 树 |
| `DeleteLevel()` | 从 AVL 树中删除价格层级并释放内存 |
| `AddOrder()` | 将订单添加到对应价格层级，更新成交量 |
| `ReduceOrder()` | 减少订单数量，更新价格层级成交量 |
| `DeleteOrder()` | 从价格层级中删除订单 |
| `AddStopLevel()` | 创建止损价格层级 |
| `DeleteStopLevel()` | 删除止损价格层级 |
| `AddStopOrder()` | 添加止损订单到止损队列 |
| `ReduceStopOrder()` | 减少止损订单数量 |
| `DeleteStopOrder()` | 删除止损订单 |
| `AddTrailingStopLevel()` | 创建跟踪止损价格层级 |
| `DeleteTrailingStopLevel()` | 删除跟踪止损价格层级 |
| `AddTrailingStopOrder()` | 添加跟踪止损订单 |
| `ReduceTrailingStopOrder()` | 减少跟踪止损订单数量 |
| `DeleteTrailingStopOrder()` | 删除跟踪止损订单 |
| `CalculateTrailingStopPrice()` | 根据市场价格计算跟踪止损触发价格 |

**数据结构操作**：

```
价格层级管理（以 Bids 为例）

添加价格层级：
  1. 从内存池创建 LevelNode
  2. 插入到 AVL 树 _bids
  3. 更新 _best_bid 指针（如果新价格更优）

删除价格层级：
  1. 从 AVL 树中移除
  2. 更新 _best_bid 指针（指向次优价格）
  3. 释放内存池资源
```

---

### 4. providers/nasdaq/itch_handler.cpp

**功能**：ITCHHandler 类的完整实现，负责解析 NASDAQ ITCH 协议的二进制数据。

**核心逻辑**：

#### 数据流处理流程

```
原始二进制数据
      │
      ▼
┌─────────────┐
│ 读取消息长度 │  (2 字节 Big-Endian)
└──────┬──────┘
       │
       ▼
┌─────────────┐
│ 读取消息体   │  (长度由消息头指定)
└──────┬──────┘
       │
       ▼
┌─────────────┐
│ 解析消息类型 │  (1 字节字符)
│ 'A'=AddOrder│
│ 'E'=Execute │
│ 'D'=Delete  │
│ ...         │
└──────┬──────┘
       │
       ▼
┌─────────────┐
│ 调用对应解析 │
│ 函数并触发   │
│ onMessage() │
│ 回调        │
└─────────────┘
```

#### 消息解析实现

每个消息类型都有对应的解析函数：

```cpp
bool ProcessAddOrderMessage(void* buffer, size_t size)
{
    // 验证消息长度
    assert((size == 36) && "Invalid size of the ITCH message type 'A'");
    
    // 按协议格式逐字段解析
    uint8_t* data = (uint8_t*)buffer;
    AddOrderMessage message;
    message.Type = *data++;
    data += CppCommon::Endian::ReadBigEndian(data, message.StockLocate);
    data += CppCommon::Endian::ReadBigEndian(data, message.TrackingNumber);
    data += ReadTimestamp(data, message.Timestamp);
    // ... 更多字段
    
    // 触发用户回调
    return onMessage(message);
}
```

#### 支持的 ITCH 消息类型

| 类型字符 | 消息名称 | 长度(字节) | 处理函数 |
|---------|---------|-----------|---------|
| 'S' | System Event | 12 | ProcessSystemEventMessage |
| 'R' | Stock Directory | 39 | ProcessStockDirectoryMessage |
| 'H' | Stock Trading Action | 25 | ProcessStockTradingActionMessage |
| 'Y' | Reg SHO | 20 | ProcessRegSHOMessage |
| 'L' | Market Participant Position | 26 | ProcessMarketParticipantPositionMessage |
| 'V' | MWCB Decline | 35 | ProcessMWCBDeclineMessage |
| 'W' | MWCB Status | 12 | ProcessMWCBStatusMessage |
| 'K' | IPO Quoting | 28 | ProcessIPOQuotingMessage |
| 'A' | Add Order | 36 | ProcessAddOrderMessage |
| 'F' | Add Order MPID | 40 | ProcessAddOrderMPIDMessage |
| 'E' | Order Executed | 31 | ProcessOrderExecutedMessage |
| 'C' | Order Executed With Price | 36 | ProcessOrderExecutedWithPriceMessage |
| 'X' | Order Cancel | 23 | ProcessOrderCancelMessage |
| 'D' | Order Delete | 19 | ProcessOrderDeleteMessage |
| 'U' | Order Replace | 35 | ProcessOrderReplaceMessage |
| 'P' | Trade | 44 | ProcessTradeMessage |
| 'Q' | Cross Trade | 40 | ProcessCrossTradeMessage |
| 'B' | Broken Trade | 19 | ProcessBrokenTradeMessage |
| 'I' | NOII | 50 | ProcessNOIIMessage |
| 'N' | RPII | 20 | ProcessRPIIMessage |
| 'J' | LULD Auction Collar | 35 | ProcessLULDAuctionCollarMessage |

---

## 与 include/ 目录的关系

```
include/trader/matching/market_manager.h    (声明)
         │
         │ #include "market_manager.inl"    (内联实现)
         │
         └──────────────────────────────────────┐
                                                │
source/trader/matching/market_manager.cpp    (非内联实现)
         │
         │ 实现 .h 中声明的非内联函数
         │ (析构函数、AddSymbol、AddOrder 等)
```

**分工原则**：
- **头文件 (.h/.inl)**：模板类、内联函数、小型函数的实现
- **源文件 (.cpp)**：大型函数、复杂逻辑、不需要内联的函数

---

## 编译

这些源文件在 CMake 构建时被编译为 `cpptrader` 库：

```cmake
file(GLOB_RECURSE LIB_SOURCE_FILES "include/*.cpp" "source/*.cpp")
add_library(cpptrader ${LIB_SOURCE_FILES})
```

编译后的库文件位于 `bin/` 目录。
