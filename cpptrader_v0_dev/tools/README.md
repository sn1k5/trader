# tools 目录说明

## 目录作用

`tools/` 目录存放 **CppTrader 项目的测试工具和数据文件**。包括撮合引擎的测试场景数据、ITCH 协议的示例数据和规范文档，用于单元测试、集成测试和开发调试。

---

## 目录结构

```
tools/
├── itch/                          # ITCH 协议相关资源
│   ├── NQTVITCHspecification.pdf  # NASDAQ ITCH 协议官方规范文档
│   └── sample.itch                # ITCH 二进制示例数据文件
│
└── matching/                      # 撮合引擎测试场景数据
    ├── scenario-01.txt            # 场景1：基本订单簿操作
    ├── scenario-02.txt            # 场景2：市价单撮合
    ├── scenario-03.txt            # 场景3：限价单撮合
    ├── scenario-04.txt            # 场景4：冰山订单
    ├── scenario-05.txt            # 场景5：IOC 订单
    ├── scenario-06.txt            # 场景6：FOK 订单
    ├── scenario-07.txt            # 场景7：AON 订单
    ├── scenario-08.txt            # 场景8：止损单
    ├── scenario-09.txt            # 场景9：止损限价单
    ├── scenario-10.txt            # 场景10：跟踪止损单
    ├── scenario-11.txt            # 场景11：跟踪止损限价单
    ├── scenario-12.txt            # 场景12：订单修改
    ├── scenario-13.txt            # 场景13：订单减少
    ├── scenario-14.txt            # 场景14：订单替换
    └── scenario-15.txt            # 场景15：复杂综合场景
```

---

## ITCH 协议资源 (tools/itch/)

### NQTVITCHspecification.pdf

**说明**：NASDAQ TotalView-ITCH 协议的官方规范文档。

**内容涵盖**：
- ITCH 协议概述和架构
- 会话管理（Session Management）
- 消息格式详解（所有消息类型的字段定义）
- 数据类型和编码规范
- 示例数据解析
- 错误处理

**使用场景**：
- 开发自定义 ITCH 解析器时参考
- 理解 NASDAQ 市场数据格式
- 调试 ITCH 数据解析问题

**在线版本**：http://www.nasdaqtrader.com/content/technicalsupport/specifications/dataproducts/NQTVITCHSpecification.pdf

---

### sample.itch

**说明**：NASDAQ ITCH 协议的示例二进制数据文件。

**用途**：
- 单元测试的输入数据
- 开发调试时的测试数据
- 性能基准测试的小规模数据集

**使用方式**：
```bash
# 作为 ITCH 处理器示例的输入
cat tools/itch/sample.itch | ./bin/cpptrader-example-itch_handler

# 作为性能测试的输入
cat tools/itch/sample.itch | ./bin/cpptrader-performance-itch_handler
```

**获取完整测试数据**：
```bash
# 从纳斯达克官网下载完整的历史数据（约 1-3GB）
wget https://emi.nasdaq.com/ITCH/01302017.NASDAQ_ITCH50

# 或使用压缩版本
wget https://emi.nasdaq.com/ITCH/01302017.NASDAQ_ITCH50.gz
```

---

## 撮合测试场景 (tools/matching/)

### 场景文件格式

每个 `scenario-XX.txt` 文件包含一系列撮合引擎操作命令，格式与交互式示例的命令一致：

```
# 场景1：基本订单簿操作
add symbol 1 AAPL
add book 1
add limit buy 1001 1 100 10
add limit sell 1002 1 101 5
enable matching
add limit buy 1003 1 101 3
exit
```

### 场景详解

#### 场景1：基本订单簿操作 (scenario-01.txt)

**目标**：验证品种、订单簿、价格层级的添加和删除。

**操作序列**：
```
1. add symbol 1 AAPL      → 添加品种
2. add book 1             → 创建订单簿
3. add limit buy 1001...  → 添加买单（创建 BID 价格层级）
4. add limit sell 1002... → 添加卖单（创建 ASK 价格层级）
5. delete order 1001      → 删除买单（删除 BID 价格层级）
6. delete order 1002      → 删除卖单（删除 ASK 价格层级）
7. delete book 1          → 删除订单簿
8. delete symbol 1        → 删除品种
```

---

#### 场景2：市价单撮合 (scenario-02.txt)

**目标**：验证市价单与限价单的撮合逻辑。

**核心验证点**：
- 市价单立即与最优对手方订单撮合
- 撮合价格取对手方订单价格
- 剩余数量继续撮合或取消

---

#### 场景3：限价单撮合 (scenario-03.txt)

**目标**：验证限价单的撮合和价格优先、时间优先原则。

**核心验证点**：
- 价格优先：高价买单优先于低价买单
- 时间优先：同价格订单按添加顺序撮合
- 撮合后订单簿状态正确更新

---

#### 场景4：冰山订单 (scenario-04.txt)

**目标**：验证冰山订单（Iceberg Order）的隐藏数量和刷新机制。

**核心验证点**：
- 仅显示 MaxVisibleQuantity 的数量
- 部分成交后自动刷新显示数量
- 隐藏数量不影响撮合优先级

---

#### 场景5：IOC 订单 (scenario-05.txt)

**目标**：验证 Immediate-Or-Cancel 订单。

**核心验证点**：
- 能成交的部分立即成交
- 不能成交的部分立即取消
- 不留在订单簿中

---

#### 场景6：FOK 订单 (scenario-06.txt)

**目标**：验证 Fill-Or-Kill 订单。

**核心验证点**：
- 能全部成交则执行
- 不能全部成交则完全取消
- 原子性操作

---

#### 场景7：AON 订单 (scenario-07.txt)

**目标**：验证 All-Or-None 订单。

**核心验证点**：
- 必须全部成交，否则不执行
- 可以部分留在订单簿中等待完全匹配
- 撮合时考虑整个订单数量

---

#### 场景8：止损单 (scenario-08.txt)

**目标**：验证 Stop 订单的触发和执行。

**核心验证点**：
- 买入止损：市场价格 >= 止损价时触发
- 卖出止损：市场价格 <= 止损价时触发
- 触发后转为市价单执行

---

#### 场景9：止损限价单 (scenario-09.txt)

**目标**：验证 Stop-Limit 订单。

**核心验证点**：
- 触发条件同止损单
- 触发后转为限价单（不是市价单）
- 限价范围内执行

---

#### 场景10：跟踪止损单 (scenario-10.txt)

**目标**：验证 Trailing Stop 订单的动态止损价调整。

**核心验证点**：
- 买入跟踪止损：市场价格下跌时，止损价跟随下降
- 卖出跟踪止损：市场价格上涨时，止损价跟随上升
- TrailingStep 控制调整步长
- 价格反弹时触发

---

#### 场景11：跟踪止损限价单 (scenario-11.txt)

**目标**：验证 Trailing Stop-Limit 订单。

**核心验证点**：
- 跟踪止损的触发机制
- 触发后转为限价单执行
- 限价与跟踪止损的组合逻辑

---

#### 场景12：订单修改 (scenario-12.txt)

**目标**：验证 ModifyOrder 操作。

**核心验证点**：
- 修改价格后重新排序
- 修改数量后更新价格层级
- 修改后触发重新撮合

---

#### 场景13：订单减少 (scenario-13.txt)

**目标**：验证 ReduceOrder 操作。

**核心验证点**：
- 部分减少订单数量
- 更新价格层级成交量
- 数量减为0时删除订单

---

#### 场景14：订单替换 (scenario-14.txt)

**目标**：验证 ReplaceOrder 操作。

**核心验证点**：
- 删除旧订单
- 创建新订单（保留新ID）
- 原子性操作

---

#### 场景15：复杂综合场景 (scenario-15.txt)

**目标**：验证多品种、多订单类型混合场景。

**核心验证点**：
- 多个品种独立管理
- 多种订单类型同时存在
- 复杂的撮合交互

---

## 使用方式

### 在单元测试中使用

```cpp
// test_matching_engine.cpp 中使用场景数据
TEST_CASE("Matching engine - Scenario 01", "[CppTrader][MatchingEngine]")
{
    // 测试代码直接模拟 scenario-01.txt 中的操作序列
    MarketManager market;
    MyMarketHandler handler(market);
    
    Test(market.AddSymbol({1, "AAPL"}));
    Test(market.AddOrderBook({1, 1}));
    // ... 对应 scenario-01.txt 的操作
}
```

### 在交互式示例中使用

```bash
# 将场景文件内容逐行输入到交互式示例
./bin/cpptrader-example-matching_engine < tools/matching/scenario-01.txt
```

### 自定义测试场景

可以创建新的场景文件进行手动测试：

```bash
# 创建自定义场景
cat > my_scenario.txt << 'EOF'
add symbol 1 TEST
add book 1
add limit buy 1001 1 100 10
add limit sell 1002 1 101 5
enable matching
add limit buy 1003 1 101 3
exit
EOF

# 运行测试
./bin/cpptrader-example-matching_engine < my_scenario.txt
```

---

## 文件大小参考

| 文件 | 大小 | 说明 |
|------|------|------|
| `NQTVITCHspecification.pdf` | ~1 MB | ITCH 协议规范文档 |
| `sample.itch` | ~10 KB | 小型示例数据 |
| `scenario-*.txt` | 1-5 KB | 各场景命令文件 |
| `01302017.NASDAQ_ITCH50` (需下载) | ~1-3 GB | 完整历史数据 |

---

## 注意事项

1. `sample.itch` 是小型示例数据，仅用于快速测试
2. 完整性能测试需要下载纳斯达克官网的历史数据
3. 场景文件中的命令格式必须与交互式示例的命令格式一致
4. 所有场景文件使用 UTF-8 编码
