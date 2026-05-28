# CppTrader Java-Admin 全功能测试文档

## 1. 概述

本文档描述 CppTrader Java-Admin 系统的完整功能测试方案，覆盖 9 大功能模块、30+ 个 API 端点。每个测试项包含测试方法（HTTP 请求方式与参数）、测试作用（验证什么功能）、测试含义（业务场景与通过条件）。

### 1.1 测试环境要求

| 项目 | 要求 |
|------|------|
| Java-Admin 服务 | 已启动，监听端口 8082 |
| MySQL 数据库 | 已初始化（执行 init.sql 建表） |
| C++ Trader 引擎 | 已启动（交易模块依赖） |
| 测试工具 | PowerShell 5.1+，运行 `test-full-api.ps1` |

### 1.2 认证说明

除 `/api/auth/*` 和 `/api/status` 外，所有接口均需在请求头中携带 JWT Token：
```
Authorization: Bearer <accessToken>
```

### 1.3 运行测试脚本

```powershell
# 自动注册新用户测试
.\test-full-api.ps1

# 使用已有账户测试
.\test-full-api.ps1 -Username admin -Password yourpassword
```

---

## 2. 模块一：认证鉴权（Auth）

### 2.1 注册新用户

| 项目 | 内容 |
|------|------|
| **测试方法** | `POST /api/auth/register` |
| **请求体** | `{ "username": "testuser", "password": "Test@123456" }` |
| **期望状态码** | 200 |
| **响应字段** | `userId`, `username`, `accessToken`, `refreshToken` |

**测试作用**：验证用户注册功能是否正常，新用户能否成功创建并获取 JWT 令牌。

**测试含义**：注册是系统入口功能，成功意味着：
- 数据库 `sys_user` 表写入正常
- 密码加密存储（BCrypt）正常
- JWT 令牌签发正常
- 返回的 `accessToken` 可用于后续所有认证请求

**通过条件**：HTTP 200，响应包含非空的 `userId`、`username`、`accessToken`、`refreshToken` 字段。

---

### 2.2 用户登录

| 项目 | 内容 |
|------|------|
| **测试方法** | `POST /api/auth/login` |
| **请求体** | `{ "username": "testuser", "password": "Test@123456" }` |
| **期望状态码** | 200 |
| **响应字段** | `accessToken`, `refreshToken` |

**测试作用**：验证已注册用户能否通过用户名密码登录并获取新的 JWT 令牌。

**测试含义**：登录是日常使用最频繁的操作，成功意味着：
- 密码验证逻辑正确（BCrypt 匹配）
- JWT 令牌签发正常
- 用户状态检查（`status=1` 为启用）通过

**通过条件**：HTTP 200，响应包含非空的 `accessToken` 和 `refreshToken`。

---

### 2.3 刷新令牌

| 项目 | 内容 |
|------|------|
| **测试方法** | `POST /api/auth/refresh` |
| **请求体** | `{ "refreshToken": "<refreshToken>" }` |
| **期望状态码** | 200 |
| **响应字段** | `accessToken` |

**测试作用**：验证使用 refreshToken 能否换取新的 accessToken，实现无感续期。

**测试含义**：令牌刷新是 JWT 长会话机制的核心，成功意味着：
- refreshToken 验证逻辑正确
- 新 accessToken 签发正常
- 用户无需重新登录即可继续操作

**通过条件**：HTTP 200，响应包含非空的 `accessToken`。

---

### 2.4 未认证访问拒绝

| 项目 | 内容 |
|------|------|
| **测试方法** | `GET /api/users/me`（不携带 Authorization 头） |
| **期望状态码** | 401 或 403 |

**测试作用**：验证未携带 Token 的请求是否被安全框架拦截。

**测试含义**：这是安全基线测试，成功意味着：
- Spring Security 过滤链正常工作
- JwtAuthenticationFilter 正确拦截未认证请求
- 受保护资源不会被匿名访问

**通过条件**：HTTP 401（未认证）或 403（禁止访问）。

---

## 3. 模块二：用户与账户（User & Account）

### 3.1 获取当前用户信息

| 项目 | 内容 |
|------|------|
| **测试方法** | `GET /api/users/me` |
| **请求头** | `Authorization: Bearer <token>` |
| **期望状态码** | 200 |
| **响应字段** | `id`, `username`, `role`, `status` |

**测试作用**：验证 JWT Token 解析后能否正确返回当前登录用户的基本信息。

**测试含义**：此接口是前端展示用户身份的基础，成功意味着：
- JWT Token 中的用户标识解析正确
- `UserService.getCurrentUser()` 能从安全上下文中提取用户
- 用户角色（`role`）和状态（`status`）返回正确

**通过条件**：HTTP 200，响应包含非空的 `id`、`username`、`role` 字段。

---

### 3.2 获取账户列表

| 项目 | 内容 |
|------|------|
| **测试方法** | `GET /api/accounts` |
| **请求头** | `Authorization: Bearer <token>` |
| **期望状态码** | 200 |

**测试作用**：验证当前用户关联的交易账户列表能否正常返回。

**测试含义**：账户是交易和余额操作的载体，成功意味着：
- `AccountService.getCurrentUserAccounts()` 查询正常
- 用户与账户的关联关系正确
- 返回的账户数据结构完整

**通过条件**：HTTP 200，返回数组（可为空）。

---

### 3.3 获取持仓列表

| 项目 | 内容 |
|------|------|
| **测试方法** | `GET /api/positions` |
| **请求头** | `Authorization: Bearer <token>` |
| **期望状态码** | 200 |

**测试作用**：验证当前用户的持仓信息能否正常返回。

**测试含义**：持仓反映用户的交易头寸，成功意味着：
- `AccountService.getCurrentUserPositions()` 查询正常
- 持仓数据（品种、方向、数量、均价、浮动盈亏）完整

**通过条件**：HTTP 200，返回数组（可为空）。

---

## 4. 模块三：余额管理（Balance）

### 4.1 初始化账户余额

| 项目 | 内容 |
|------|------|
| **测试方法** | `POST /api/balance/init?userId={userId}&initialBalance=10000` |
| **请求头** | `Authorization: Bearer <token>` |
| **期望状态码** | 200 |
| **响应字段** | `userId`, `initialBalance`, `status` |
| **断言值** | `status = "initialized"` |

**测试作用**：验证为指定用户初始化账户余额的功能。

**测试含义**：余额初始化是资金操作的前提，成功意味着：
- `BalanceService.initAccount()` 正确创建余额记录
- 初始金额写入 `balance` 表
- 幂等性：重复初始化不会报错

**通过条件**：HTTP 200，`status` 字段值为 `"initialized"`。

---

### 4.2 查询余额

| 项目 | 内容 |
|------|------|
| **测试方法** | `GET /api/balance/{userId}` |
| **请求头** | `Authorization: Bearer <token>` |
| **期望状态码** | 200 |
| **响应字段** | `userId`, `available` |

**测试作用**：验证查询指定用户的可用余额。

**测试含义**：余额查询是资金操作的基础，成功意味着：
- `BalanceService.getAvailableBalance()` 正确计算可用余额
- 可用余额 = 总余额 - 冻结余额

**通过条件**：HTTP 200，响应包含非空的 `userId` 和 `available` 字段。

---

### 4.3 增加余额

| 项目 | 内容 |
|------|------|
| **测试方法** | `POST /api/balance/add?userId={userId}&amount=5000&bizId=test-add-001` |
| **请求头** | `Authorization: Bearer <token>` |
| **期望状态码** | 200 |
| **响应字段** | `userId`, `amount`, `success` |
| **断言值** | `success = True` |

**测试作用**：验证为账户充值（增加余额）的功能。

**测试含义**：充值是资金流入的主要方式，成功意味着：
- `BalanceService.add()` 正确增加余额
- 流水记录（`balance_journal`）写入正常
- `bizId` 幂等校验通过（相同 bizId 不重复入账）

**通过条件**：HTTP 200，`success` 字段值为 `True`。

---

### 4.4 冻结余额

| 项目 | 内容 |
|------|------|
| **测试方法** | `POST /api/balance/freeze?userId={userId}&amount=2000&bizId=test-freeze-001` |
| **请求头** | `Authorization: Bearer <token>` |
| **期望状态码** | 200 |
| **响应字段** | `userId`, `amount`, `success` |
| **断言值** | `success = True` |

**测试作用**：验证冻结指定金额的余额（下单时预扣保证金）。

**测试含义**：冻结是交易风控的关键环节，成功意味着：
- `BalanceService.freeze()` 正确将可用余额转为冻结余额
- 冻结金额不超过可用余额
- 流水记录写入正常

**通过条件**：HTTP 200，`success` 字段值为 `True`。

---

### 4.5 解冻余额

| 项目 | 内容 |
|------|------|
| **测试方法** | `POST /api/balance/unfreeze?userId={userId}&amount=2000&bizId=test-unfreeze-001` |
| **请求头** | `Authorization: Bearer <token>` |
| **期望状态码** | 200 |
| **响应字段** | `userId`, `amount`, `success` |
| **断言值** | `success = True` |

**测试作用**：验证解冻之前冻结的余额（撤单时释放保证金）。

**测试含义**：解冻是撤单和风控释放的逆操作，成功意味着：
- `BalanceService.unfreeze()` 正确将冻结余额转回可用余额
- 解冻金额不超过冻结余额
- 流水记录写入正常

**通过条件**：HTTP 200，`success` 字段值为 `True`。

---

### 4.6 扣减余额

| 项目 | 内容 |
|------|------|
| **测试方法** | `POST /api/balance/deduct?userId={userId}&amount=1000&bizId=test-deduct-001` |
| **请求头** | `Authorization: Bearer <token>` |
| **期望状态码** | 200 |
| **响应字段** | `userId`, `amount`, `success` |

**测试作用**：验证从账户扣减余额（结算亏损、手续费等）。

**测试含义**：扣减是资金流出的主要方式，成功意味着：
- `BalanceService.deduct()` 正确减少总余额
- 可用余额不足时返回 `success=false`
- 流水记录写入正常

**通过条件**：HTTP 200，响应包含非空的 `userId`、`amount`、`success` 字段。

---

## 5. 模块四：风控管理（Risk）

### 5.1 获取风控规则列表

| 项目 | 内容 |
|------|------|
| **测试方法** | `GET /api/risk/rules` |
| **请求头** | `Authorization: Bearer <token>` |
| **期望状态码** | 200 |

**测试作用**：验证获取系统中所有风控规则的功能。

**测试含义**：风控规则列表是风控管理的基础视图，成功意味着：
- `RiskRuleService.getAllRules()` 查询正常
- 返回的规则包含 `ruleName`、`ruleType`、`params`、`enabled` 等字段

**通过条件**：HTTP 200，返回数组。

---

### 5.2 创建风控规则

| 项目 | 内容 |
|------|------|
| **测试方法** | `POST /api/risk/rules` |
| **请求头** | `Authorization: Bearer <token>` |
| **请求体** | `{ "ruleName": "test_single_limit", "ruleType": "SINGLE_LIMIT", "params": "{\"maxAmount\":50000}", "description": "API test rule", "enabled": 1, "priority": 0 }` |
| **期望状态码** | 200 |
| **响应字段** | `ruleName`, `ruleType` |

**测试作用**：验证创建新的风控规则。

**测试含义**：风控规则创建是风控配置的入口，成功意味着：
- `RiskRuleService.createRule()` 正确持久化规则
- 规则类型（`SINGLE_LIMIT` 单笔限额）和参数正确存储
- 新规则在后续下单时生效

**通过条件**：HTTP 200，响应包含非空的 `ruleName` 和 `ruleType` 字段。

---

### 5.3 更新风控规则

| 项目 | 内容 |
|------|------|
| **测试方法** | `PUT /api/risk/rules/{id}` |
| **请求头** | `Authorization: Bearer <token>` |
| **请求体** | `{ "ruleName": "test_single_limit_updated", "ruleType": "SINGLE_LIMIT", "params": "{\"maxAmount\":80000}", "enabled": 1 }` |
| **期望状态码** | 200 |
| **响应字段** | `ruleName` |
| **断言值** | `ruleName = "test_single_limit_updated"` |

**测试作用**：验证更新已有风控规则的配置。

**测试含义**：规则更新是风控运营的日常操作，成功意味着：
- `RiskRuleService.updateRule()` 正确更新规则字段
- 更新后的参数立即生效
- 规则 ID 不变，仅修改配置内容

**通过条件**：HTTP 200，`ruleName` 字段值为 `"test_single_limit_updated"`。

---

### 5.4 查询风控告警

| 项目 | 内容 |
|------|------|
| **测试方法** | `GET /api/risk/alerts?userId={userId}` |
| **请求头** | `Authorization: Bearer <token>` |
| **期望状态码** | 200 |

**测试作用**：验证查询指定用户触发的风控告警记录。

**测试含义**：告警记录反映用户交易行为是否触发风控规则，成功意味着：
- `RiskRuleService.getAlerts()` 查询正常
- 告警包含触发时间、规则名称、触发金额等信息

**通过条件**：HTTP 200，返回数组（可为空）。

---

## 6. 模块五：交易管理（Trading）

> 交易模块通过二进制协议与 C++ 撮合引擎通信，所有操作需引擎在线。

### 6.1 系统状态

| 项目 | 内容 |
|------|------|
| **测试方法** | `GET /api/status` |
| **期望状态码** | 200 |
| **响应字段** | `connected`, `timestamp` |

**测试作用**：验证 Java-Admin 与 C++ 撮合引擎的连接状态。

**测试含义**：系统状态是交易操作的前提检查，成功意味着：
- `ProtocolClientService.isConnected()` 返回 true
- TCP 长连接正常
- 后续交易操作可以执行

**通过条件**：HTTP 200，`connected` 字段非空。

---

### 6.2 启用撮合引擎

| 项目 | 内容 |
|------|------|
| **测试方法** | `POST /api/matching/enable` |
| **请求头** | `Authorization: Bearer <token>` |
| **期望状态码** | 200 |
| **响应字段** | `error` |

**测试作用**：验证启用 C++ 撮合引擎的撮合功能。

**测试含义**：撮合引擎启用后，新提交的订单才会参与价格匹配和成交，成功意味着：
- 二进制协议请求 `EnableMatchingRequest` 发送成功
- C++ 引擎切换到撮合模式
- 响应 `error` 字段为 `"OK"`

**通过条件**：HTTP 200，响应包含 `error` 字段。

---

### 6.3 添加交易品种

| 项目 | 内容 |
|------|------|
| **测试方法** | `POST /api/symbols?id=1&name=BTCUSD` |
| **请求头** | `Authorization: Bearer <token>` |
| **期望状态码** | 200 |
| **响应字段** | `error`, `id`, `name` |

**测试作用**：验证在 C++ 引擎中注册新的交易品种。

**测试含义**：交易品种是撮合的基础配置，成功意味着：
- `AddSymbolRequest` 协议请求发送成功
- C++ 引擎内部创建品种对象
- 后续可基于此品种创建订单簿和下单

**通过条件**：HTTP 200，响应包含 `error` 字段。

---

### 6.4 获取交易品种

| 项目 | 内容 |
|------|------|
| **测试方法** | `GET /api/symbols/1` |
| **请求头** | `Authorization: Bearer <token>` |
| **期望状态码** | 200 |
| **响应字段** | `error`, `id`, `name` |

**测试作用**：验证查询已注册的交易品种详情。

**测试含义**：品种查询确认注册操作生效，成功意味着：
- `GetSymbolRequest` 协议请求发送成功
- C++ 引擎返回品种 ID 和名称
- 品种数据与注册时一致

**通过条件**：HTTP 200，响应包含 `error` 字段。

---

### 6.5 添加订单簿

| 项目 | 内容 |
|------|------|
| **测试方法** | `POST /api/orderbooks?symbolId=1` |
| **请求头** | `Authorization: Bearer <token>` |
| **期望状态码** | 200 |
| **响应字段** | `error` |

**测试作用**：验证为指定品种创建订单簿（买卖盘）。

**测试含义**：订单簿是撮合的核心数据结构，成功意味着：
- `AddOrderBookRequest` 协议请求发送成功
- C++ 引擎为该品种创建买卖盘内存结构
- 后续可在此订单簿上提交订单

**通过条件**：HTTP 200，响应包含 `error` 字段。

---

### 6.6 添加买单

| 项目 | 内容 |
|------|------|
| **测试方法** | `POST /api/orders?symbolId=1&side=BUY&type=LIMIT&price=50000&quantity=10` |
| **请求头** | `Authorization: Bearer <token>` |
| **期望状态码** | 200 |
| **响应字段** | `error`, `orderId`, `symbolId`, `side`, `price`, `quantity`, `leavesQty` |

**测试作用**：验证提交限价买单到撮合引擎。

**测试含义**：买单是交易的核心操作，成功意味着：
- 风控检查通过（`RiskCheckService.check()`）
- `AddOrderRequest` 协议请求发送成功
- 订单进入买盘等待撮合
- 返回 `orderId` 可用于后续查询和撤单

**通过条件**：HTTP 200，响应包含 `error` 字段。

---

### 6.7 添加卖单

| 项目 | 内容 |
|------|------|
| **测试方法** | `POST /api/orders?symbolId=1&side=SELL&type=LIMIT&price=51000&quantity=5` |
| **请求头** | `Authorization: Bearer <token>` |
| **期望状态码** | 200 |
| **响应字段** | `error`, `orderId`, `symbolId`, `side`, `price`, `quantity`, `leavesQty` |

**测试作用**：验证提交限价卖单到撮合引擎。

**测试含义**：与买单对称，卖单进入卖盘。此测试中卖单价格（51000）高于买单价格（50000），不会触发撮合成交，成功意味着：
- 卖单正确进入卖盘
- 订单簿买卖盘数据完整

**通过条件**：HTTP 200，响应包含 `error` 字段。

---

### 6.8 获取订单簿深度

| 项目 | 内容 |
|------|------|
| **测试方法** | `GET /api/orderbooks/1?depth=5` |
| **请求头** | `Authorization: Bearer <token>` |
| **期望状态码** | 200 |
| **响应字段** | `symbolId`, `bestBidPrice`, `bestBidVolume`, `bestAskPrice`, `bestAskVolume` |

**测试作用**：验证查询订单簿的买卖盘深度数据。

**测试含义**：订单簿深度是行情数据的核心，成功意味着：
- `GetOrderBookRequest` 协议请求发送成功
- 买一价 = 50000、买一量 = 10
- 卖一价 = 51000、卖一量 = 5
- 深度数据与提交的订单一致

**通过条件**：HTTP 200，响应包含 `symbolId` 字段。

---

### 6.9 获取订单详情

| 项目 | 内容 |
|------|------|
| **测试方法** | `GET /api/orders/1001` |
| **请求头** | `Authorization: Bearer <token>` |
| **期望状态码** | 200 |
| **响应字段** | `error`, `orderId`, `symbolId`, `side`, `price`, `quantity`, `executedQty`, `leavesQty` |

**测试作用**：验证查询指定订单的详细信息。

**测试含义**：订单详情反映订单的当前状态，成功意味着：
- `GetOrderRequest` 协议请求发送成功
- 返回订单的委托量（`quantity`）、已成交量（`executedQty`）、剩余量（`leavesQty`）
- 未成交订单的 `leavesQty` > 0

**通过条件**：HTTP 200，响应包含 `error` 字段。

---

### 6.10 添加可成交卖单（触发撮合）

| 项目 | 内容 |
|------|------|
| **测试方法** | `POST /api/orders?symbolId=1&side=SELL&type=LIMIT&price=50000&quantity=3` |
| **请求头** | `Authorization: Bearer <token>` |
| **期望状态码** | 200 |
| **响应字段** | `error`, `orderId`, `leavesQty` |

**测试作用**：验证提交与买单价格匹配的卖单，触发撮合成交。

**测试含义**：这是撮合引擎的核心功能测试，成功意味着：
- 卖单价格（50000）与买单价格匹配
- 撮合引擎正确匹配买卖订单
- 成交量 = min(买量, 卖量) = 3
- 买单 `leavesQty` 从 10 变为 7
- 成交记录写入数据库

**通过条件**：HTTP 200，响应包含 `error` 字段。

---

### 6.11 删除订单（撤单）

| 项目 | 内容 |
|------|------|
| **测试方法** | `DELETE /api/orders/1001` |
| **请求头** | `Authorization: Bearer <token>` |
| **期望状态码** | 200 |
| **响应字段** | `error` |

**测试作用**：验证撤销指定订单。

**测试含义**：撤单是交易的基本权利，成功意味着：
- `DeleteOrderRequest` 协议请求发送成功
- C++ 引擎从订单簿中移除该订单
- 部分成交的订单撤单后，剩余量释放

**通过条件**：HTTP 200，响应包含 `error` 字段。

---

### 6.12 删除订单簿

| 项目 | 内容 |
|------|------|
| **测试方法** | `DELETE /api/orderbooks/1` |
| **请求头** | `Authorization: Bearer <token>` |
| **期望状态码** | 200 |
| **响应字段** | `error` |

**测试作用**：验证删除指定品种的订单簿。

**测试含义**：订单簿删除是清理测试数据的关键步骤，成功意味着：
- `DeleteOrderBookRequest` 协议请求发送成功
- C++ 引擎释放订单簿内存
- 关联的未成交订单一并清除

**通过条件**：HTTP 200，响应包含 `error` 字段。

---

### 6.13 删除交易品种

| 项目 | 内容 |
|------|------|
| **测试方法** | `DELETE /api/symbols/1` |
| **请求头** | `Authorization: Bearer <token>` |
| **期望状态码** | 200 |
| **响应字段** | `error` |

**测试作用**：验证删除已注册的交易品种。

**测试含义**：品种删除是清理测试数据的关键步骤，成功意味着：
- `DeleteSymbolRequest` 协议请求发送成功
- C++ 引擎移除品种配置

**通过条件**：HTTP 200，响应包含 `error` 字段。

---

### 6.14 禁用撮合引擎

| 项目 | 内容 |
|------|------|
| **测试方法** | `POST /api/matching/disable` |
| **请求头** | `Authorization: Bearer <token>` |
| **期望状态码** | 200 |
| **响应字段** | `error` |

**测试作用**：验证禁用 C++ 撮合引擎的撮合功能。

**测试含义**：禁用后新订单不再参与撮合，成功意味着：
- `DisableMatchingRequest` 协议请求发送成功
- C++ 引擎切换到非撮合模式

**通过条件**：HTTP 200，响应包含 `error` 字段。

---

## 7. 模块六：订单查询（Order Query）

### 7.1 查询订单历史

| 项目 | 内容 |
|------|------|
| **测试方法** | `GET /api/orders/history?page=0&size=10` |
| **请求头** | `Authorization: Bearer <token>` |
| **期望状态码** | 200 |
| **响应字段** | `content`, `totalElements`, `totalPages`, `page`, `size` |

**测试作用**：验证分页查询当前用户的订单历史记录。

**测试含义**：订单历史是用户交易记录的核心视图，成功意味着：
- `OrderHistoryService.findByUserId()` 查询正常
- 分页参数正确传递
- 返回标准分页结构（`content` + 分页元数据）

**通过条件**：HTTP 200，响应包含非空的 `content`、`totalElements`、`totalPages`、`page` 字段。

---

### 7.2 查询成交明细

| 项目 | 内容 |
|------|------|
| **测试方法** | `GET /api/orders/executions?page=0&size=10` |
| **请求头** | `Authorization: Bearer <token>` |
| **期望状态码** | 200 |
| **响应字段** | `content`, `totalElements`, `totalPages`, `page`, `size` |

**测试作用**：验证分页查询当前用户的成交明细记录。

**测试含义**：成交明细记录每笔撮合成交的详情，成功意味着：
- `ExecutionService.findByUserId()` 查询正常
- 成交记录包含成交价、成交量、手续费等信息
- 分页结构完整

**通过条件**：HTTP 200，响应包含非空的 `content`、`totalElements`、`totalPages`、`page` 字段。

---

### 7.3 查询指定订单成交明细

| 项目 | 内容 |
|------|------|
| **测试方法** | `GET /api/orders/1001/executions` |
| **请求头** | `Authorization: Bearer <token>` |
| **期望状态码** | 200 |
| **响应字段** | `orderId`, `executions`, `count` |

**测试作用**：验证查询指定订单的所有成交明细。

**测试含义**：订单级成交明细用于查看单笔订单的撮合过程，成功意味着：
- `ExecutionService.findByOrderId()` 查询正常
- 一笔订单可能有多笔部分成交
- `count` 字段正确反映成交笔数

**通过条件**：HTTP 200，响应包含非空的 `orderId`、`executions`、`count` 字段。

---

## 8. 模块七：报表统计（Report）

### 8.1 日交易统计

| 项目 | 内容 |
|------|------|
| **测试方法** | `GET /api/reports/daily?date=2026-05-26` |
| **请求头** | `Authorization: Bearer <token>` |
| **期望状态码** | 200 |

**测试作用**：验证查询指定日期的交易统计数据。

**测试含义**：日报是运营监控的核心报表，成功意味着：
- `ReportService.getDailyReport()` 查询正常
- 统计数据包含当日成交量、成交额、手续费等
- 日期参数格式为 `yyyy-MM-dd`

**通过条件**：HTTP 200，响应非空。

---

### 8.2 盈亏分析

| 项目 | 内容 |
|------|------|
| **测试方法** | `GET /api/reports/pnl` |
| **请求头** | `Authorization: Bearer <token>` |
| **期望状态码** | 200 |

**测试作用**：验证查询盈亏分析报表。

**测试含义**：盈亏分析是用户最关心的报表，成功意味着：
- `ReportService.getPnlReport()` 查询正常
- 包含已实现盈亏、未实现盈亏、总盈亏等数据

**通过条件**：HTTP 200，响应非空。

---

### 8.3 资金流水

| 项目 | 内容 |
|------|------|
| **测试方法** | `GET /api/reports/fund-flow` |
| **请求头** | `Authorization: Bearer <token>` |
| **期望状态码** | 200 |

**测试作用**：验证查询资金流水报表。

**测试含义**：资金流水记录所有资金变动，成功意味着：
- `ReportService.getFundFlow()` 查询正常
- 包含充值、扣款、冻结、解冻、手续费等流水明细

**通过条件**：HTTP 200，响应非空。

---

### 8.4 账户总览

| 项目 | 内容 |
|------|------|
| **测试方法** | `GET /api/reports/summary` |
| **请求头** | `Authorization: Bearer <token>` |
| **期望状态码** | 200 |

**测试作用**：验证查询账户总览报表。

**测试含义**：账户总览是用户资产的全景视图，成功意味着：
- `ReportService.getSummary()` 查询正常
- 包含总资产、可用余额、冻结余额、持仓市值等

**通过条件**：HTTP 200，响应非空。

---

## 9. 模块八：对账管理（Reconcile）

### 9.1 执行对账

| 项目 | 内容 |
|------|------|
| **测试方法** | `POST /api/reconcile/run` |
| **请求头** | `Authorization: Bearer <token>` |
| **期望状态码** | 200 |
| **响应字段** | `mismatchCount`, `diffs` |

**测试作用**：验证执行一次完整的对账操作。

**测试含义**：对账是保证资金一致性的关键操作，成功意味着：
- `ReconcileService.reconcile()` 正确比对系统余额与实际余额
- 差异记录（`diffs`）包含不一致的明细
- `mismatchCount` 反映差异数量

**通过条件**：HTTP 200，响应包含非空的 `mismatchCount` 和 `diffs` 字段。

---

### 9.2 查询未修复差异

| 项目 | 内容 |
|------|------|
| **测试方法** | `GET /api/reconcile/unfixed` |
| **请求头** | `Authorization: Bearer <token>` |
| **期望状态码** | 200 |
| **响应字段** | `count`, `diffs` |

**测试作用**：验证查询尚未修复的对账差异记录。

**测试含义**：未修复差异是运营需要关注的问题，成功意味着：
- `ReconcileService.getUnfixedDiffs()` 查询正常
- 返回所有 `fixed=false` 的差异记录
- `count` 反映未修复数量

**通过条件**：HTTP 200，响应包含非空的 `count` 和 `diffs` 字段。

---

### 9.3 自动修复差异

| 项目 | 内容 |
|------|------|
| **测试方法** | `POST /api/reconcile/fix/{userId}` |
| **请求头** | `Authorization: Bearer <token>` |
| **期望状态码** | 200 |
| **响应字段** | `userId`, `status` |
| **断言值** | `status = "fixed"` |

**测试作用**：验证自动修复指定用户的对账差异。

**测试含义**：自动修复将系统余额调整为实际余额，成功意味着：
- `ReconcileService.autoFix()` 正确执行修复
- 差异记录标记为 `fixed=true`
- 余额数据修正完成

**通过条件**：HTTP 200，`status` 字段值为 `"fixed"`。

---

## 10. 模块九：Outbox 消息管理

### 10.1 查询死信消息

| 项目 | 内容 |
|------|------|
| **测试方法** | `GET /api/outbox/dead` |
| **请求头** | `Authorization: Bearer <token>` |
| **期望状态码** | 200 |
| **响应字段** | `count`, `messages` |

**测试作用**：验证查询所有死信（发送失败且重试耗尽）的 Outbox 消息。

**测试含义**：死信消息是消息一致性的兜底机制，成功意味着：
- `OutboxMessageService.findDeadMessages()` 查询正常
- 返回所有状态为 `DEAD` 的消息
- `count` 反映死信数量

**通过条件**：HTTP 200，响应包含非空的 `count` 和 `messages` 字段。

---

### 10.2 重新激活死信消息

| 项目 | 内容 |
|------|------|
| **测试方法** | `POST /api/outbox/reactivate/{id}` |
| **请求头** | `Authorization: Bearer <token>` |
| **期望状态码** | 200 |
| **响应字段** | `id`, `status` |
| **断言值** | `status = "reactivated"` |

**测试作用**：验证将死信消息重新激活，使其重新进入发送队列。

**测试含义**：重新激活是消息恢复的手动操作，成功意味着：
- `OutboxMessageService.reactivateDeadMessage()` 正确执行
- 消息状态从 `DEAD` 变为 `PENDING`
- 消息将被异步重新发送

**通过条件**：HTTP 200，`status` 字段值为 `"reactivated"`。

---

## 11. 测试流程与依赖关系

```
阶段 0: 服务连通性预检
  │
  ▼
阶段 1: 认证登录（注册/登录 → 获取 Token）
  │
  ├─→ 模块 2: 用户与账户
  ├─→ 模块 3: 余额管理（依赖 userId）
  ├─→ 模块 4: 风控管理（依赖 userId）
  ├─→ 模块 5: 交易管理（依赖撮合引擎连接）
  │       │
  │       ▼
  ├─→ 模块 6: 订单查询（依赖交易模块产生数据）
  ├─→ 模块 7: 报表统计（依赖交易和余额数据）
  ├─→ 模块 8: 对账管理（依赖余额数据）
  └─→ 模块 9: Outbox 管理（独立模块）
```

**关键依赖**：
- 所有模块依赖阶段 1 的 Token，认证失败则终止
- 余额模块依赖 `userId`，无 userId 则跳过
- 交易模块依赖 C++ 撮合引擎在线
- 订单查询和报表依赖交易模块产生的数据

---

## 12. 测试结果判定

| 结果 | 含义 |
|------|------|
| ✓ PASS | 请求成功，状态码和断言字段均符合预期 |
| ✗ FAIL | 请求失败或断言不通过，需检查服务日志 |
| ○ SKIP | 前置条件不满足（如无 userId、无死信消息），跳过测试 |

**退出码**：
- `0`：全部通过
- `1`：存在失败项
