# performance 目录说明

## 目录作用

`performance/` 目录包含 **CppTrader 的性能基准测试程序**。这些程序用于测量和评估核心组件在高负载场景下的性能表现，包括处理延迟和吞吐量指标。

---

## 文件列表

| 文件名 | 说明 | 测试对象 |
|--------|------|---------|
| `itch_handler.cpp` | ITCH 协议解析性能测试 | ITCHHandler 类 |
| `market_manager.cpp` | 市场管理器性能测试（标准版） | MarketManager 完整功能 |
| `market_manager_optimized.cpp` | 市场管理器性能测试（优化版） | 预分配数组优化版本 |
| `market_manager_optimized_aggressive.cpp` | 市场管理器性能测试（极致优化版） | 极限性能优化版本 |
| `matching_engine.cpp` | 撮合引擎性能测试 | 撮合逻辑 |

---

## 性能测试详解

### 1. itch_handler.cpp - ITCH 解析性能测试

**测试目标**：测量 ITCHHandler 解析 NASDAQ ITCH 二进制数据流的性能。

**测试流程**：
```
读取 ITCH 文件 ──▶ 分块读入 8KB 缓冲区 ──▶ itch_handler.Process() ──▶ 统计耗时
```

**输出指标**：
- 处理时间
- 总 ITCH 消息数
- 单消息延迟（ns/msg）
- 消息吞吐量（msg/s）

**参考性能**（Intel i7-4790K @ 4.00GHz）：
```
Processing time: 6.831 s
Total ITCH messages: 283,238,832
ITCH message latency: 24 ns
ITCH message throughput: 41,460,256 msg/s
```

---

### 2. market_manager.cpp - 标准版性能测试

**测试目标**：测量完整 MarketManager 处理 ITCH 数据并维护订单簿的性能。

**特点**：
- 使用完整的 MarketHandler 事件回调
- 使用 HashMap 存储订单
- 使用 AVL 树维护价格层级
- 维护订单链表

**输出指标**：
- ITCH 消息处理性能
- 市场更新事件性能
- 最大品种数、订单簿数、价格层级数、订单数统计

**参考性能**：
```
Processing time: 1:27.616 m
Total ITCH messages: 283,238,832
ITCH message latency: 309 ns
ITCH message throughput: 3,232,727 msg/s
Total market updates: 631,217,516
Market update latency: 138 ns
Market update throughput: 7,204,359 upd/s
```

---

### 3. market_manager_optimized.cpp - 优化版性能测试

**测试目标**：使用优化数据结构后的 MarketManager 性能。

**优化策略**：

| 优化项 | 标准版 | 优化版 |
|--------|--------|--------|
| Symbol 存储 | HashMap | 预分配数组 |
| Order 存储 | HashMap | 预分配数组（O(1) 访问） |
| Price Level | AVL 树 | 排序数组（最优价格缓存友好） |
| Order List | 链表 | 仅计数（不维护链表） |
| Level 分配 | 动态 | 内存池预分配 |

**性能提升**：
- 处理时间从 87.6 秒降至 34.2 秒（**提升 2.6 倍**）
- ITCH 消息吞吐量从 323万 提升至 829万 msg/s
- 市场更新吞吐量从 720万 提升至 1848万 upd/s

**参考性能**：
```
Processing time: 34.150 s
ITCH message latency: 120 ns
ITCH message throughput: 8,293,747 msg/s
Market update latency: 54 ns
Market update throughput: 18,483,195 upd/s
```

---

### 4. market_manager_optimized_aggressive.cpp - 极致优化版

**测试目标**：追求极限性能的 MarketManager 变体。

**极致优化策略**：

| 优化项 | 优化版 | 极致优化版 |
|--------|--------|-----------|
| Symbol 维护 | 预分配数组 | **不维护 Symbol** |
| Order 结构 | 完整字段 | **精简字段** |
| Price 类型 | uint64_t | **int32_t**（正数买/负数卖） |
| MarketHandler | 完整回调 | **无回调** |
| 订单簿更新 | 完整更新 | **简化更新** |

**适用场景**：
- 纯内部撮合系统（不需要外部事件通知）
- 已知固定品种集合的系统
- 对延迟要求极高的场景

**参考性能**：
```
Processing time: 29.047 s
ITCH messages latency: 102 ns
ITCH messages throughput: 9,751,044 msg/s
```

---

### 5. matching_engine.cpp - 撮合引擎性能测试

**测试目标**：测量撮合引擎处理订单并执行撮合的性能。

**特点**：
- 启用自动撮合模式
- 处理 ITCH 数据流中的添加、执行、取消、删除操作
- 统计撮合结果

---

## 运行性能测试

### 编译

```bash
cd build
cmake ..

# 编译单个性能测试
make cpptrader-performance-itch_handler
make cpptrader-performance-market_manager
make cpptrader-performance-market_manager_optimized
make cpptrader-performance-market_manager_optimized_aggressive
```

### 运行

```bash
# 使用标准输入（从 ITCH 文件重定向）
./bin/cpptrader-performance-itch_handler < 01302017.NASDAQ_ITCH50

# 或使用 -i 参数指定文件
./bin/cpptrader-performance-market_manager -i 01302017.NASDAQ_ITCH50

# 使用管道
zcat 01302017.NASDAQ_ITCH50.gz | ./bin/cpptrader-performance-itch_handler
```

### 获取 ITCH 测试数据

```bash
# 从纳斯达克官网下载示例数据
wget https://emi.nasdaq.com/ITCH/01302017.NASDAQ_ITCH50

# 或使用项目自带的示例数据
cat tools/itch/sample.itch | ./bin/cpptrader-performance-itch_handler
```

---

## 性能对比总结

| 版本 | 处理时间 | ITCH 延迟 | ITCH 吞吐量 | 市场更新吞吐量 |
|------|---------|----------|------------|--------------|
| ITCH Handler | 6.8 s | 24 ns | 41,460,256 msg/s | - |
| Market Manager (标准) | 87.6 s | 309 ns | 3,232,727 msg/s | 7,204,359 upd/s |
| Market Manager (优化) | 34.2 s | 120 ns | 8,293,747 msg/s | 18,483,195 upd/s |
| Market Manager (极致) | 29.0 s | 102 ns | 9,751,044 msg/s | - |

---

## 性能优化建议

1. **选择合适的版本**：根据业务需求选择标准版/优化版/极致优化版
2. **预分配内存**：根据预期负载预先分配足够的 Symbol/Order/Level 内存池
3. **禁用不必要的事件回调**：如果不需要 MarketHandler 通知，可简化或禁用
4. **使用合适的编译优化**：`-O3 -march=native -flto`（GCC/Clang）
5. **CPU 亲和性**：将进程绑定到特定 CPU 核心，减少缓存失效
