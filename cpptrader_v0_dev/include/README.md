# include 目录说明

## 目录作用

`include/` 目录是 CppTrader 项目的**公共头文件目录**，采用标准的 C++ 库目录结构。所有对外暴露的 API 接口、数据结构定义和模板实现都存放于此。其他项目使用 CppTrader 时，只需将此目录加入编译器的头文件搜索路径即可。

---

## 目录结构

```
include/
└── trader/
    ├── matching/              # 撮合引擎模块
    │   ├── errors.h           # 错误码定义
    │   ├── errors.inl         # 错误码内联实现
    │   ├── fast_hash.h        # 快速哈希函数
    │   ├── fast_hash.inl      # 哈希函数内联实现
    │   ├── level.h            # 价格层级定义
    │   ├── level.inl          # 价格层级内联实现
    │   ├── market_handler.h   # 市场事件处理器接口
    │   ├── market_manager.h   # 市场管理器主类
    │   ├── market_manager.inl # 市场管理器内联实现
    │   ├── order.h            # 订单定义
    │   ├── order.inl          # 订单内联实现
    │   ├── order_book.h       # 订单簿定义
    │   ├── order_book.inl     # 订单簿内联实现
    │   ├── symbol.h           # 交易品种定义
    │   ├── symbol.inl         # 交易品种内联实现
    │   ├── update.h           # 更新事件定义
    │   └── update.inl         # 更新事件内联实现
    ├── providers/             # 数据提供方模块
    │   └── nasdaq/            # 纳斯达克数据
    │       ├── itch_handler.h # ITCH 协议处理器
    │       └── itch_handler.inl # ITCH 处理器内联实现
    └── version.h              # 版本信息
```

---

## 核心模块详解

### 1. matching/ - 撮合引擎模块

这是 CppTrader 最核心的模块，实现了完整的金融订单撮合逻辑。

#### 核心类关系图

```
┌─────────────────────────────────────────────────────────────┐
│                    MarketManager                             │
│  市场管理器 - 管理所有品种、订单簿和订单                      │
│  ├─ symbols: vector<Symbol*>                                │
│  ├─ order_books: vector<OrderBook*>                         │
│  ├─ orders: HashMap<uint64_t, OrderNode*>                   │
│  └─ _matching: bool (自动撮合开关)                           │
└──────────────────────────┬──────────────────────────────────┘
                           │ 管理
                           ▼
┌─────────────────────────────────────────────────────────────┐
│                    OrderBook                                 │
│  订单簿 - 管理单个品种的买卖订单队列                          │
│  ├─ _bids: AVLTree<LevelNode>    (买单价格树)               │
│  ├─ _asks: AVLTree<LevelNode>    (卖单价格树)               │
│  ├─ _buy_stop: AVLTree<LevelNode> (买入止损队列)            │
│  ├─ _sell_stop: AVLTree<LevelNode> (卖出止损队列)           │
│  ├─ _trailing_buy_stop: AVLTree<LevelNode>                 │
│  └─ _trailing_sell_stop: AVLTree<LevelNode>                │
└──────────────────────────┬──────────────────────────────────┘
                           │ 包含多个
                           ▼
┌─────────────────────────────────────────────────────────────┐
│                    LevelNode                                 │
│  价格层级 - 同一价格的所有订单集合                            │
│  ├─ Price: uint64_t                                          │
│  ├─ TotalVolume: uint64_t                                   │
│  ├─ HiddenVolume: uint64_t                                  │
│  ├─ VisibleVolume: uint64_t                                 │
│  ├─ Orders: size_t                                           │
│  └─ OrderList: List<OrderNode>   (订单链表)                 │
└──────────────────────────┬──────────────────────────────────┘
                           │ 包含多个
                           ▼
┌─────────────────────────────────────────────────────────────┐
│                    OrderNode                                 │
│  订单节点 - 继承自 Order 结构体                               │
│  ├─ Id: uint64_t              (订单ID)                      │
│  ├─ SymbolId: uint32_t        (品种ID)                      │
│  ├─ Type: OrderType           (订单类型)                    │
│  ├─ Side: OrderSide           (买卖方向)                    │
│  ├─ Price: uint64_t           (价格)                        │
│  ├─ StopPrice: uint64_t       (止损价)                      │
│  ├─ Quantity: uint64_t        (总数量)                      │
│  ├─ ExecutedQuantity: uint64_t(已成交数量)                  │
│  ├─ LeavesQuantity: uint64_t  (剩余数量)                    │
│  ├─ TimeInForce: OrderTimeInForce (时效类型)                │
│  ├─ MaxVisibleQuantity: uint64_t (最大可见量 - 冰山订单)    │
│  ├─ Slippage: uint64_t        (滑点)                        │
│  ├─ TrailingDistance: int64_t (跟踪距离)                    │
│  └─ TrailingStep: int64_t     (跟踪步长)                    │
└─────────────────────────────────────────────────────────────┘
```

#### 文件说明

| 文件 | 说明 |
|------|------|
| `market_manager.h` / `.inl` | 市场管理器主类，提供 Add/Delete/Modify/Execute Order 等核心 API |
| `order_book.h` / `.inl` | 订单簿类，管理 Bids/Asks/Stop Orders 的价格层级结构 |
| `order.h` / `.inl` | 订单结构体定义，包含 OrderType、OrderSide、OrderTimeInForce 枚举 |
| `level.h` / `.inl` | 价格层级定义，包含 LevelType、Level、LevelNode、LevelUpdate |
| `market_handler.h` | 市场事件处理器抽象基类，用户需继承此类接收市场事件 |
| `symbol.h` / `.inl` | 交易品种定义 |
| `errors.h` / `.inl` | 错误码枚举定义 |
| `update.h` / `.inl` | 更新类型枚举定义 |
| `fast_hash.h` / `.inl` | 针对 uint64_t 优化的快速哈希函数 |

---

### 2. providers/nasdaq/ - NASDAQ 数据提供方

实现 NASDAQ ITCH (Incremental Trading Consolidated Feed) 协议的解析器。

#### ITCH 消息类型

| 消息结构体 | 类型字符 | 说明 |
|-----------|---------|------|
| `SystemEventMessage` | 'S' | 系统事件 |
| `StockDirectoryMessage` | 'R' | 股票目录 |
| `StockTradingActionMessage` | 'H' | 交易状态 |
| `AddOrderMessage` | 'A' | 添加订单 |
| `AddOrderMPIDMessage` | 'F' | 添加订单(含MPID) |
| `OrderExecutedMessage` | 'E' | 订单执行 |
| `OrderExecutedWithPriceMessage` | 'C' | 订单执行(含价格) |
| `OrderCancelMessage` | 'X' | 订单部分取消 |
| `OrderDeleteMessage` | 'D' | 订单删除 |
| `OrderReplaceMessage` | 'U' | 订单替换 |
| `TradeMessage` | 'P' | 成交报告 |
| `CrossTradeMessage` | 'Q' | 交叉交易 |
| `BrokenTradeMessage` | 'B' | 交易中断 |
| `NOIIMessage` | 'I' | 净订单失衡指标 |
| `RPIIMessage` | 'N' | 零售价格改善指标 |
| `LULDAuctionCollarMessage` | 'J' | 涨跌停拍卖区间 |
| `UnknownMessage` | - | 未知消息 |

#### 文件说明

| 文件 | 说明 |
|------|------|
| `itch_handler.h` / `.inl` | ITCH 协议处理器基类，提供 Process() 方法解析二进制数据流，虚函数回调各消息类型 |

---

## 头文件与内联文件 (.h / .inl)

项目采用 **头文件 + 内联实现文件** 的组织方式：

- **`.h` 文件**：包含类/结构体定义、接口声明、枚举定义
- **`.inl` 文件**：包含模板实现、内联函数实现

**优点**：
1. 接口与实现分离，提高可读性
2. 内联实现保证模板类/函数在头文件中的可见性
3. 便于用户快速浏览 API 接口

**使用方式**：
```cpp
// 只需包含 .h 文件，.inl 文件会在 .h 末尾自动包含
#include "trader/matching/market_manager.h"
```

---

## 使用方式

### 作为库使用

```cmake
# 在 CMakeLists.txt 中添加
target_include_directories(your_target PRIVATE 
    ${CPPTRADER_ROOT}/include)

target_link_libraries(your_target cpptrader)
```

### 直接引用头文件

```cpp
#include "trader/matching/market_manager.h"
#include "trader/providers/nasdaq/itch_handler.h"

using namespace CppTrader::Matching;
using namespace CppTrader::ITCH;
```
