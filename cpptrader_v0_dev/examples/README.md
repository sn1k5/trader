# examples 目录说明

## 目录作用

`examples/` 目录包含 **CppTrader 的使用示例程序**，展示如何调用库的核心功能。每个示例都是一个独立的可执行程序，编译后可通过命令行运行。

---

## 文件列表

| 文件名 | 说明 | 核心展示内容 |
|--------|------|-------------|
| `itch_handler.cpp` | ITCH 协议解析示例 | 如何继承 `ITCHHandler` 处理 NASDAQ ITCH 消息 |
| `market_manager.cpp` | 市场管理器示例 | 如何将 ITCH 数据流接入撮合引擎进行订单簿重建 |
| `matching_engine.cpp` | 交互式撮合引擎示例 | 完整的命令行交互式撮合系统 |

---

## 示例详解

### 1. itch_handler.cpp - ITCH 协议解析示例

**功能**：展示如何解析 NASDAQ ITCH 协议的二进制数据流。

**核心代码结构**：
```cpp
class MyITCHHandler : public ITCHHandler
{
protected:
    // 重写所有消息处理回调
    bool onMessage(const AddOrderMessage& message) override { ... }
    bool onMessage(const OrderExecutedMessage& message) override { ... }
    // ... 其他消息类型
};
```

**运行方式**：
```bash
# 从标准输入读取 ITCH 数据
cat sample.itch | ./cpptrader-example-itch_handler

# 或重定向输入
./cpptrader-example-itch_handler < sample.itch
```

**输出**：将每条 ITCH 消息的内容打印到控制台。

---

### 2. market_manager.cpp - 市场管理器集成示例

**功能**：展示如何将 ITCH 实时数据流与撮合引擎结合，实现订单簿的自动重建。

**核心架构**：
```
ITCH 数据文件
     │
     ▼
┌─────────────┐     ┌─────────────┐     ┌─────────────┐
│ MyITCHHandler│────▶│MarketManager│────▶│MyMarketHandler│
│ (解析 ITCH)  │     │ (撮合引擎)   │     │ (事件处理)   │
└─────────────┘     └─────────────┘     └─────────────┘
```

**关键实现**：
- `MyITCHHandler` 将 ITCH 消息转换为 `MarketManager` 的订单操作
- `MyMarketHandler` 接收并统计所有市场事件

**运行方式**：
```bash
./cpptrader-example-market_manager < sample.itch
```

---

### 3. matching_engine.cpp - 交互式撮合引擎

**功能**：提供一个完整的命令行交互式撮合系统，支持所有订单类型和操作。

**支持的命令**：

| 命令 | 说明 |
|------|------|
| `add symbol {Id} {Name}` | 添加交易品种 |
| `add book {Id}` | 为品种创建订单簿 |
| `add market {buy/sell} {Id} {SymbolId} {Qty}` | 添加市价单 |
| `add limit {buy/sell} {Id} {SymbolId} {Price} {Qty}` | 添加限价单 |
| `add stop {buy/sell} {Id} {SymbolId} {StopPrice} {Qty}` | 添加止损单 |
| `add stop-limit {buy/sell} ...` | 添加止损限价单 |
| `add trailing stop ...` | 添加跟踪止损单 |
| `modify order {Id} {NewPrice} {NewQty}` | 修改订单 |
| `reduce order {Id} {Qty}` | 减少订单数量 |
| `delete order {Id}` | 删除订单 |
| `enable matching` | 启用自动撮合 |
| `disable matching` | 禁用自动撮合 |

**使用示例**：
```bash
$ ./cpptrader-example-matching_engine

> add symbol 1 AAPL
Add symbol: AAPL

> add book 1
Add order book: AAPL

> add limit buy 1001 1 100 10
Add order: Id=1001, Symbol=1, Type=LIMIT, Side=BUY, Price=100, Quantity=10
Add level: BID@100 - Top of the book!

> enable matching

> add limit sell 1002 1 100 5
Add order: Id=1002, Symbol=1, Type=LIMIT, Side=SELL, Price=100, Quantity=5
Execute order: Id=1002... with price 100 and quantity 5
Update order: Id=1001... Quantity=5

> exit
```

---

## 编译示例

所有示例程序在 CMake 构建时自动生成：

```bash
cd build
cmake ..
make cpptrader-example-matching_engine
make cpptrader-example-market_manager
make cpptrader-example-itch_handler
```

编译后的可执行文件位于 `bin/` 目录。

---

## 学习建议

1. **初学者**：从 `matching_engine.cpp` 开始，理解基本的订单操作流程
2. **进阶用户**：研究 `market_manager.cpp`，学习 ITCH 数据与撮合引擎的集成
3. **协议开发**：参考 `itch_handler.cpp`，了解如何扩展自定义消息处理
