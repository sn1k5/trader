# tests 目录说明

## 目录作用

`tests/` 目录包含 **CppTrader 的单元测试和集成测试**。这些测试使用 **Catch2** 测试框架编写，用于验证撮合引擎、ITCH 协议处理器等核心组件的正确性和稳定性。

---

## 文件列表

| 文件名 | 说明 | 测试范围 |
|--------|------|---------|
| `test.cpp` | 测试入口主文件 | 定义 `main()` 函数和 `Test()` 辅助函数 |
| `test.h` | 测试头文件 | 声明 `Test()` 函数，包含 Catch2 框架头文件 |
| `test_matching_engine.cpp` | 撮合引擎测试 | 完整的撮合引擎功能测试（15个测试场景） |
| `test_market_manager.cpp` | 市场管理器测试 | 市场管理器 API 测试 |
| `test_itch_handler.cpp` | ITCH 处理器测试 | ITCH 协议解析测试 |

---

## 测试框架

### Catch2 集成

项目使用 **Catch2 v3.x** (amalgamated 单文件版本) 作为测试框架：

```cpp
// test.h
#include <catch_amalgamated.hpp>

// 辅助函数：运行测试并检查错误码
inline ErrorCode Test(ErrorCode result)
{
    if (result != ErrorCode::OK)
        FAIL("Error: " << result);
    return result;
}
```

### 测试标签系统

| 标签 | 说明 |
|------|------|
| `[matching_engine]` | 撮合引擎相关测试 |
| `[market_manager]` | 市场管理器相关测试 |
| `[itch_handler]` | ITCH 处理器相关测试 |

---

## 测试详解

### 1. test_matching_engine.cpp - 撮合引擎测试

**测试目标**：验证撮合引擎在各种场景下的正确性。

#### 测试场景架构

```
MarketManager
    │
    ├── Symbol (AAPL)
    │     └── OrderBook (AAPL)
    │           ├── Bids (买单队列)
    │           │     ├── Level 100: Order 1001 (10)
    │           │     └── Level 99:  Order 1002 (5)
    │           ├── Asks (卖单队列)
    │           │     ├── Level 101: Order 1003 (3)
    │           │     └── Level 102: Order 1004 (7)
    │           ├── Buy Stop Orders
    │           └── Sell Stop Orders
    │
    └── Symbol (MSFT)
          └── OrderBook (MSFT)
                └── ...
```

#### 15 个测试场景

| 场景 | 测试内容 | 核心验证点 |
|------|---------|-----------|
| 场景1 | 基本订单簿操作 | 添加/删除品种、订单簿、价格层级 |
| 场景2 | 市价单撮合 | 市价单与限价单的即时撮合 |
| 场景3 | 限价单撮合 | 同价格限价单的 FIFO 撮合 |
| 场景4 | 冰山订单 | 隐藏数量、部分显示、刷新机制 |
| 场景5 | IOC 订单 | 立即成交或取消 |
| 场景6 | FOK 订单 | 全部成交或取消 |
| 场景7 | AON 订单 | 全部成交或不成交 |
| 场景8 | 止损单 | 触发条件、市价执行 |
| 场景9 | 止损限价单 | 触发后转为限价单 |
| 场景10 | 跟踪止损单 | 价格跟踪、动态调整止损价 |
| 场景11 | 跟踪止损限价单 | 跟踪止损+限价组合 |
| 场景12 | 订单修改 | 修改价格和数量 |
| 场景13 | 订单减少 | 部分减少数量 |
| 场景14 | 订单替换 | 删除旧订单+创建新订单 |
| 场景15 | 复杂场景 | 多品种、多订单类型混合测试 |

#### 测试流程示例

```cpp
TEST_CASE("Matching engine - Scenario 01", "[CppTrader][MatchingEngine]")
{
    // 1. 创建市场管理器和处理器
    MarketManager market;
    MyMarketHandler handler(market);
    
    // 2. 添加品种
    Test(market.AddSymbol({1, "AAPL"}));  // 验证返回 OK
    
    // 3. 创建订单簿
    Test(market.AddOrderBook({1, 1}));    // SymbolId=1, OrderBookId=1
    
    // 4. 添加买单
    Test(market.AddOrder(Order::BuyLimit(1, 1, 100, 10)));
    
    // 5. 验证状态
    REQUIRE(handler.updates.size() == 1);
    REQUIRE(handler.updates[0].Type == UpdateType::ADD);
    REQUIRE(handler.updates[0].Order.Id == 1);
    REQUIRE(handler.updates[0].Order.Price == 100);
    REQUIRE(handler.updates[0].Order.Quantity == 10);
    
    // 6. 清理
    Test(market.DeleteOrder(1));
    Test(market.DeleteOrderBook(1));
    Test(market.DeleteSymbol(1));
}
```

---

### 2. test_market_manager.cpp - 市场管理器测试

**测试目标**：验证市场管理器的 API 正确性和边界条件处理。

#### 测试内容

| 测试项 | 说明 |
|--------|------|
| 品种管理 | 添加/删除 Symbol，重复添加检测 |
| 订单簿管理 | 创建/删除 OrderBook，与 Symbol 关联 |
| 订单生命周期 | 添加→修改→减少→执行→删除 |
| 错误处理 | 无效订单ID、重复订单、不存在的订单 |
| 自动撮合开关 | EnableMatching / DisableMatching |
| 手动撮合 | Match() 方法调用 |

---

### 3. test_itch_handler.cpp - ITCH 处理器测试

**测试目标**：验证 ITCH 协议解析的正确性。

#### 测试内容

| 测试项 | 说明 |
|--------|------|
| 消息解析 | 各类型 ITCH 消息的正确解析 |
| 字节序处理 | Big-Endian 数据的正确转换 |
| 边界条件 | 空数据、不完整消息、未知消息类型 |
| 性能基准 | 解析速度的基础验证 |

---

## 辅助类

### MyMarketHandler

所有测试共用的市场事件处理器，用于捕获和验证市场事件：

```cpp
class MyMarketHandler : public MarketHandler
{
public:
    std::vector<Update> updates;  // 记录所有更新事件
    
protected:
    void onAddOrder(const Order& order) override
    {
        updates.emplace_back(UpdateType::ADD, order);
    }
    
    void onUpdateOrder(const Order& order) override
    {
        updates.emplace_back(UpdateType::UPDATE, order);
    }
    
    void onDeleteOrder(const Order& order) override
    {
        updates.emplace_back(UpdateType::DELETE, order);
    }
    
    void onExecuteOrder(const Order& order, uint64_t price, uint64_t quantity) override
    {
        updates.emplace_back(UpdateType::EXECUTE, order, price, quantity);
    }
    
    // ... 其他回调
};
```

---

## 运行测试

### 编译测试

```bash
cd build
cmake ..
make cpptrader-tests
```

### 运行所有测试

```bash
# 直接运行
./bin/cpptrader-tests

# 使用 CTest
cd build
ctest --output-on-failure

# 详细输出
ctest -V
```

### 运行特定测试

```bash
# 按标签运行
./bin/cpptrader-tests "[matching_engine]"
./bin/cpptrader-tests "[market_manager]"
./bin/cpptrader-tests "[itch_handler]"

# 按场景名称运行
./bin/cpptrader-tests "Matching engine - Scenario 01"

# 运行多个匹配项
./bin/cpptrader-tests "[matching_engine]" "[market_manager]"
```

### Catch2 命令行选项

```bash
# 显示所有测试列表
./bin/cpptrader-tests --list-tests

# 显示执行时间
./bin/cpptrader-tests --durations yes

# 失败时中断
./bin/cpptrader-tests --abort

# 输出到 XML（用于 CI 集成）
./bin/cpptrader-tests --reporter junit --out results.xml
```

---

## 测试数据

### 撮合测试场景数据

`tools/matching/` 目录下包含 15 个测试场景的数据文件：

```
tools/matching/
├── scenario-01.txt  # 基本操作
├── scenario-02.txt  # 市价单撮合
├── scenario-03.txt  # 限价单撮合
├── scenario-04.txt  # 冰山订单
├── scenario-05.txt  # IOC 订单
├── scenario-06.txt  # FOK 订单
├── scenario-07.txt  # AON 订单
├── scenario-08.txt  # 止损单
├── scenario-09.txt  # 止损限价单
├── scenario-10.txt  # 跟踪止损单
├── scenario-11.txt  # 跟踪止损限价单
├── scenario-12.txt  # 订单修改
├── scenario-13.txt  # 订单减少
├── scenario-14.txt  # 订单替换
└── scenario-15.txt  # 复杂场景
```

### ITCH 测试数据

```
tools/itch/
├── NQTVITCHspecification.pdf  # ITCH 协议规范文档
└── sample.itch                # 示例 ITCH 数据文件
```

---

## 持续集成

测试在 CI/CD 流程中自动运行：

```
GitHub Actions Workflow
        │
        ├── build-linux-clang.yml
        ├── build-linux-gcc.yml
        ├── build-macos.yml
        └── build-windows-*.yml
                │
                └── 步骤：ctest --output-on-failure
```

所有 Pull Request 必须通过全部测试才能合并。

---

## 添加新测试

### 步骤

1. 在 `tests/` 目录创建新的 `.cpp` 文件
2. 包含 `test.h` 头文件
3. 使用 `TEST_CASE` 宏定义测试用例
4. 使用 `Test()` 辅助函数验证操作结果
5. 使用 `REQUIRE` 宏验证状态

### 示例

```cpp
#include "test.h"

using namespace CppTrader::Matching;

TEST_CASE("My new test", "[CppTrader][MyFeature]")
{
    MarketManager market;
    MyMarketHandler handler(market);
    
    // 准备测试数据
    Test(market.AddSymbol({1, "TEST"}));
    Test(market.AddOrderBook({1, 1}));
    
    // 执行操作
    Test(market.AddOrder(Order::BuyLimit(1, 1, 100, 10)));
    
    // 验证结果
    REQUIRE(handler.updates.size() == 1);
    REQUIRE(handler.updates[0].Order.Price == 100);
    
    // 清理
    Test(market.DeleteOrderBook(1));
    Test(market.DeleteSymbol(1));
}
```

### 注意事项

- 每个测试用例应独立，不依赖其他测试的执行顺序
- 测试结束后应清理所有资源（删除订单、订单簿、品种）
- 使用 `Test()` 函数包装 MarketManager API 调用，自动检查错误码
- 使用 `REQUIRE` 进行断言验证
