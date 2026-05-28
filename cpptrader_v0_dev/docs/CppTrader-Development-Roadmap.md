# CppTrader 后续开发工业级落地方案

> 版本: 1.0 | 日期: 2026-05-27 | 密级: 内部机密

---

## 目录

1. [系统架构设计](#1-系统架构设计)
2. [安全防护体系](#2-安全防护体系)
3. [数据加密策略](#3-数据加密策略)
4. [灾备与高可用方案](#4-灾备与高可用方案)
5. [性能优化方案](#5-性能优化方案)
6. [量化交易机器人接入方案](#6-量化交易机器人接入方案)
7. [风险监控系统](#7-风险监控系统)
8. [兼容性测试标准](#8-兼容性测试标准)
9. [部署与运维规范](#9-部署与运维规范)
10. [开发路线图](#10-开发路线图)

---

## 1. 系统架构设计

### 1.1 整体架构概览

```
┌─────────────────────────────────────────────────────────────────────┐
│                        客户端层 (Client Layer)                       │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐              │
│  │ 量化交易机器人 │  │  Web管理后台  │  │  移动端APP   │              │
│  └──────┬───────┘  └──────┬───────┘  └──────┬───────┘              │
│         │                  │                  │                      │
│    Binary Protocol    HTTP/REST API     HTTP/REST API               │
│    (TCP直连C++)       (→Java Admin)     (→Java Admin)               │
└─────────┼──────────────────┼──────────────────┼─────────────────────┘
          │                  │                  │
          │         ┌────────▼────────┐         │
          │         │   Java Admin    │         │
          │         │  (Spring Boot)  │         │
          │         │  ┌────────────┐ │         │
          │         │  │REST API层  │ │         │
          │         │  │JWT认证鉴权 │ │         │
          │         │  │风控引擎    │ │         │
          │         │  │余额管理    │ │         │
          │         │  │对账/报表   │ │         │
          │         │  └─────┬──────┘ │         │
          │         │        │        │         │
          │         │  ┌─────▼──────┐ │         │
          │         │  │Protocol    │ │         │
          │         │  │Client      │ │         │
          │         │  │(Netty/DPDK)│ │         │
          │         │  └─────┬──────┘ │         │
          │         └────────┼────────┘         │
          │                  │                   │
          │         Binary Protocol (TCP)        │
          │         (16B Header + Body)          │
          │                  │                   │
┌─────────┼──────────────────┼───────────────────┼─────────────────────┐
│         │         ┌────────▼────────┐          │                     │
│         │         │  C++ 撮合引擎   │          │                     │
│         └────────►│  ProtocolServer │◄─────────┘                     │
│                   │  ┌────────────┐ │                                │
│                   │  │FrameDecoder│ │                                │
│                   │  │Auth验证    │ │                                │
│                   │  │限流器      │ │                                │
│                   │  │RequestDisp │ │                                │
│                   │  └─────┬──────┘ │                                │
│                   │        │        │                                │
│                   │  ┌─────▼──────┐ │                                │
│                   │  │MarketManager│ │                                │
│                   │  │OrderBook   │ │                                │
│                   │  │AVL树撮合   │ │                                │
│                   │  │WAL预写日志 │ │                                │
│                   │  └────────────┘ │                                │
│                   └────────┬────────┘                                │
│                            │                                         │
│                   ┌────────▼────────┐                                │
│                   │  网络后端层      │                                │
│                   │  TCP (asio)     │                                │
│                   │  DPDK (内核旁路) │                                │
│                   └─────────────────┘                                │
│  核心交易层 (Core Trading Layer)                                     │
└──────────────────────────────────────────────────────────────────────┘

┌──────────────────────────────────────────────────────────────────────┐
│  基础设施层 (Infrastructure Layer)                                    │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐            │
│  │  MySQL   │  │  Redis   │  │ RabbitMQ │  │  WAL/NFS │            │
│  │ 持久存储  │  │ 热缓存   │  │ 消息队列  │  │  日志存储 │            │
│  └──────────┘  └──────────┘  └──────────┘  └──────────┘            │
└──────────────────────────────────────────────────────────────────────┘
```

### 1.2 核心设计原则

| 原则 | 实施策略 |
|------|----------|
| **低延迟优先** | C++撮合核心 + DPDK内核旁路 + 内存池分配 + 无锁数据结构 |
| **数据一致性** | WAL预写日志 + Redis-MySQL双写 + Outbox模式 + Saga补偿 + 对账修复 |
| **安全纵深防御** | 网络层TLS + 传输层HMAC + 应用层JWT + 业务层风控 + 审计日志 |
| **弹性容错** | 断路器 + 限流降级 + 优雅关闭 + 自动重连 + 死信处理 |
| **可观测性** | 结构化日志 + 指标采集 + 分布式追踪 + 告警联动 |

### 1.3 C++ 撮合引擎架构

#### 1.3.1 进程模型

```
主线程 (Event Loop)
├── asio::io_context.poll()          // 网络I/O
├── ProtocolServer::poll()           // 消息分发
│   ├── FrameDecoder::decode()       // 帧解码
│   ├── RateLimiter::check()         // 限流检查
│   ├── AuthCheck()                  // 认证检查
│   ├── RequestHandler::dispatch()   // 请求路由
│   │   ├── MarketManager::AddOrder()    // 撮合
│   │   ├── WALWriter::Write()           // WAL写入
│   │   └── SendResponse()               // 响应发送
│   └── Broadcast()                  // 事件推送
└── WALWriter::sync()               // WAL刷盘
```

**当前限制**: 单线程事件循环，所有操作串行执行。后续需引入多线程分离I/O与业务逻辑。

#### 1.3.2 内存模型

```
┌─────────────────────────────────────────────┐
│              MarketManager 内存布局           │
│                                              │
│  _symbols[]     ──► Symbol 池 (预分配)       │
│  _order_books[] ──► OrderBook 池 (预分配)    │
│  _orders{}      ──► Order 哈希表             │
│                    │                         │
│                    └─► PoolAllocator          │
│                        ├─ OrderNode 池       │
│                        ├─ LevelNode 池       │
│                        └─ Slab 分配器        │
│                                              │
│  OrderBook 内部:                             │
│  bids/asks ──► BinTreeAVL (AVL树)            │
│  buy_stop/sell_stop ──► BinTreeAVL           │
│  trailing_* ──► BinTreeAVL                   │
└─────────────────────────────────────────────┘
```

#### 1.3.3 WAL 预写日志

```
写入流程:
  AddOrder ──► WriteNewOrder(WALEntry{NEW_ORDER, data}) ──► fsync() ──► 撮合
  CancelOrder ──► WriteCancelOrder(WALEntry{CANCEL_ORDER, data}) ──► fsync() ──► 移除
  Trade ──► WriteTrade(WALEntry{TRADE, data}) ──► fsync()

恢复流程:
  启动 ──► 扫描WAL目录 ──► 按LSN排序 ──► 重放所有NEW_ORDER ──► 重放所有TRADE ──► 恢复状态

WALEntry 结构 (148字节):
  ┌──────────┬───────────┬───────────────┬──────────┬──────────┐
  │ LSN (8B) │Timestamp(8B)│OpType(1B)+Res(3B)│ Data (128B) │
  └──────────┴───────────┴───────────────┴──────────┴──────────┘
```

### 1.4 Java Admin 架构

#### 1.4.1 分层架构

```
┌─────────────────────────────────────────────────────────┐
│  Controller 层 (REST API)                                │
│  AuthController / TradingController / BalanceController  │
│  RiskController / ReconcileController / OrderQueryCtrl   │
├─────────────────────────────────────────────────────────┤
│  Service 层 (业务逻辑)                                    │
│  AuthService / BalanceService / RiskCheckService         │
│  ReconcileService / SagaEngine / OutboxMessageService    │
├─────────────────────────────────────────────────────────┤
│  Protocol 层 (C++通信)                                    │
│  ProtocolClientService / NettyTcpBackend / FrameDecoder  │
├─────────────────────────────────────────────────────────┤
│  Infrastructure 层 (中间件)                               │
│  MySQL(JPA) / Redis(Lua) / RabbitMQ(Outbox)             │
└─────────────────────────────────────────────────────────┘
```

#### 1.4.2 数据流架构

```
下单流程:
  Client ──HTTP──► TradingController
    ──JWT验证──► RiskCheckService (Redis计数器风控)
    ──通过──► ProtocolClientService.sendSync()
    ──Binary──► C++ MarketManager.AddOrder()
    ──WAL──► 撮合 ──► OrderUpdateEvent
    ──Push──► Java onMessageReceived()
    ──MQ──► OrderHistoryService (MySQL持久化)
    ──WS──► WebSocket推送前端

余额操作流程:
  Client ──HTTP──► BalanceController
    ──► BalanceService
    ──Lua原子──► BalanceRedisService (Redis Hash)
    ──MQ──► BalanceChangeProducer (RabbitMQ)
    ──异步──► BalanceChangeConsumer (MySQL持久化)
    ──定时──► ReconcileService (Redis vs MySQL对账)
```

---

## 2. 安全防护体系

### 2.1 安全架构总览

```
┌──────────────────────────────────────────────────────────────┐
│                     安全纵深防御体系                            │
│                                                               │
│  Layer 5: 审计与合规    │  操作审计 │ 合规报告 │ 不可篡改日志    │
│  ─────────────────────────────────────────────────────────── │
│  Layer 4: 业务安全      │  风控引擎 │ 限额管理 │ 异常检测       │
│  ─────────────────────────────────────────────────────────── │
│  Layer 3: 应用安全      │  JWT认证 │ HMAC签名 │ 幂等去重       │
│  ─────────────────────────────────────────────────────────── │
│  Layer 2: 传输安全      │  TLS 1.3 │ 证书双向认证 │ 前向保密    │
│  ─────────────────────────────────────────────────────────── │
│  Layer 1: 网络安全      │  防火墙 │ DDoS防护 │ IP白名单      │
│  ─────────────────────────────────────────────────────────── │
│  Layer 0: 物理安全      │  机房安全 │ 硬件加密 │ HSM          │
│                                                               │
└──────────────────────────────────────────────────────────────┘
```

### 2.2 网络层安全

#### 2.2.1 网络隔离方案

```
┌─────────────────────────────────────────────────────────┐
│  DMZ 区 (公网可达)                                       │
│  ┌───────────────┐  ┌───────────────┐                   │
│  │  WAF/Nginx    │  │  DDoS防护     │                   │
│  │  反向代理     │  │  流量清洗     │                   │
│  └───────┬───────┘  └───────┬───────┘                   │
└──────────┼──────────────────┼───────────────────────────┘
           │                  │
┌──────────┼──────────────────┼───────────────────────────┐
│  应用区 (内网)              │                            │
│  ┌────────▼───────┐  ┌─────▼────────┐                   │
│  │  Java Admin    │  │  API Gateway │                   │
│  │  :8082         │  │  (限流/鉴权)  │                   │
│  └───────┬───────┘  └──────────────┘                   │
│          │                                               │
└──────────┼──────────────────────────────────────────────┘
           │
┌──────────┼──────────────────────────────────────────────┐
│  核心交易区 (隔离子网)                                    │
│  ┌───────▼───────┐                                      │
│  │  C++ Server   │                                      │
│  │  :8080        │                                      │
│  └───────┬───────┘                                      │
│          │                                               │
│  ┌───────▼───────┐  ┌───────────────┐                   │
│  │  WAL Storage  │  │  DPDK NIC     │                   │
│  │  (NFS/SAN)    │  │  (专用网卡)    │                   │
│  └───────────────┘  └───────────────┘                   │
└─────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────┐
│  数据区 (最高隔离)                                       │
│  ┌───────────────┐  ┌───────────────┐                   │
│  │  MySQL主从    │  │  Redis集群    │                   │
│  └───────────────┘  └───────────────┘                   │
│  ┌───────────────┐  ┌───────────────┐                   │
│  │  RabbitMQ集群 │  │  备份存储     │                   │
│  └───────────────┘  └───────────────┘                   │
└─────────────────────────────────────────────────────────┘
```

#### 2.2.2 防火墙规则

| 规则 | 源 | 目标 | 端口 | 协议 | 说明 |
|------|----|------|------|------|------|
| ALLOW | 量化机器人IP | C++ Server | 8080 | TCP | 二进制协议直连 |
| ALLOW | Java Admin IP | C++ Server | 8080 | TCP | 管理通道 |
| ALLOW | DMZ/Nginx | Java Admin | 8082 | TCP | HTTP API |
| ALLOW | Java Admin | MySQL | 3306 | TCP | 数据库 |
| ALLOW | Java Admin | Redis | 6379 | TCP | 缓存 |
| ALLOW | Java Admin | RabbitMQ | 5672 | TCP | 消息队列 |
| DENY | * | C++ Server | * | * | 其他全部拒绝 |
| DENY | * | MySQL/Redis/MQ | * | * | 外部不可直连 |

### 2.3 传输层安全 (TLS)

#### 2.3.1 TLS 配置规范

```
C++ Server TLS 配置 (新增):
  - 协议版本: TLS 1.3 (最低 TLS 1.2)
  - 密码套件:
    TLS_AES_256_GCM_SHA384
    TLS_CHACHA20_POLY1305_SHA256
    TLS_AES_128_GCM_SHA256
  - 证书: X.509 v3, RSA-4096 或 ECDSA P-384
  - 双向认证 (mTLS): 客户端需提供有效证书
  - 会话恢复: PSK (Pre-Shared Key) 或 Session Ticket
  - OCSP Stapling: 启用
  - 前向保密 (PFS): ECDHE 密钥交换
```

#### 2.3.2 实施方案

**C++ asio SSL 集成**:

```cpp
// 新增: include/trader/protocol/tls_backend.h
class TlsBackend : public TcpBackend {
public:
    TlsBackend(asio::io_context& io_context, const TlsConfig& config);

    struct TlsConfig {
        std::string cert_file;      // 服务器证书
        std::string key_file;       // 私钥
        std::string ca_file;        // CA证书 (客户端验证)
        bool verify_client = true;  // mTLS
        std::string cipher_list = "ECDHE+AESGCM:ECDHE+CHACHA20";
        std::string min_version = "TLS1.2";
    };

private:
    asio::ssl::context ssl_context_;
};
```

**Java Netty SSL 集成**:

```java
// 新增: SslContextFactory.java
public class SslContextFactory {
    public static SslContext createClientContext(String certPath, String keyPath, String caPath) {
        SslContextBuilder builder = SslContextBuilder.forClient()
            .trustManager(new File(caPath))          // CA证书
            .keyManager(new File(certPath), new File(keyPath)) // 客户端证书
            .protocols("TLSv1.3", "TLSv1.2")
            .ciphers(Http2SecurityUtil.CIPHERS, SupportedCipherSuiteFilter.INSTANCE);
        return builder.build();
    }
}
```

### 2.4 应用层安全

#### 2.4.1 HMAC 签名协议增强

当前协议头中 `HmacPrefix` 字段 (2字节) 未使用。需实施完整的HMAC签名方案:

```
签名流程:
  1. 构造签名输入: Sequence(4) + MsgType(1) + Flags(1) + Length(2) + Body(N)
  2. 计算 HMAC-SHA256: signature = HMAC-SHA256(session_key, input)
  3. 填充 HmacPrefix: 取 signature 的前2字节
  4. 验证方: 重新计算签名, 比较前2字节

完整签名验证 (可选增强):
  - 在 Body 尾部追加完整 32字节 HMAC 签名
  - Length 字段包含签名长度
  - 验证方计算并比对完整签名
```

**C++ 端实现**:

```cpp
// 新增: include/trader/protocol/hmac.h
class HmacVerifier {
public:
    HmacVerifier(const std::string& session_key);

    // 计算HMAC前缀 (2字节)
    uint16_t ComputePrefix(const MsgHeader& header, const uint8_t* body, size_t body_len);

    // 验证HMAC前缀
    bool VerifyPrefix(const MsgHeader& header, const uint8_t* body, size_t body_len);

    // 计算完整HMAC (32字节)
    std::array<uint8_t, 32> ComputeFull(const MsgHeader& header, const uint8_t* body, size_t body_len);

private:
    std::array<uint8_t, 32> session_key_;
};
```

#### 2.4.2 认证流程增强

```
当前认证流程 (简化版):
  Client ──AUTH_REQUEST──► Server
  Server ──AUTH_RESPONSE──► Client (固定SessionToken)

增强认证流程 (工业级):
  1. 连接建立
  2. TLS握手 (mTLS)
  3. AUTH_REQUEST:
     - ApiKeyId[32]: API Key标识
     - Timestamp[8]: 当前时间戳 (毫秒)
     - Nonce[16]: 随机数 (防重放)
     - Signature[32]: HMAC-SHA256(ApiKeySecret, ApiKeyId + Timestamp + Nonce)
  4. Server验证:
     a. 查找ApiKeySecret (从数据库/配置)
     b. 验证Timestamp在±30秒内 (防重放)
     c. 检查Nonce是否已使用 (Redis SETNX, TTL=60s)
     d. 重新计算Signature并比对
     e. 生成随机SessionToken (32字节, CSPRNG)
  5. AUTH_RESPONSE:
     - Error[1]: 错误码
     - SessionToken[16→32]: 随机生成的会话令牌
  6. 后续请求:
     - HmacPrefix字段使用SessionToken派生的HMAC密钥签名
```

#### 2.4.3 防重放攻击

```cpp
// 新增: AntiReplayChecker
class AntiReplayChecker {
public:
    // 检查Nonce是否重复 (Redis SETNX)
    bool CheckNonce(const std::array<uint8_t, 16>& nonce, uint64_t timestamp);

    // 检查Timestamp是否在有效窗口内
    bool CheckTimestamp(uint64_t timestamp, int64_t tolerance_ms = 30000);

    // 检查Sequence单调递增
    bool CheckSequence(uint32_t sequence, uint32_t last_sequence);

private:
    // 本地滑动窗口缓存 (减少Redis调用)
    static constexpr size_t WINDOW_SIZE = 1024;
    std::unordered_set<std::array<uint8_t, 16>, ArrayHash> recent_nonces_;
    std::mutex mutex_;
};
```

#### 2.4.4 JWT 安全增强

```java
// 当前: HMAC-SHA签名, Base64密钥
// 增强:
public class JwtTokenProvider {
    // 1. 使用 RS256 或 ES256 非对称签名
    private final RSAPrivateKey privateKey;
    private final RSAPublicKey publicKey;

    // 2. Token 绑定客户端指纹
    public String generateToken(Long userId, String clientFingerprint) {
        return Jwts.builder()
            .subject(userId.toString())
            .claim("fp", hash(clientFingerprint))  // 客户端指纹
            .claim("ip", currentIp)                 // IP地址
            .issuedAt(now)
            .expiration(accessExpiry)
            .id(UUID.randomUUID().toString())       // JWT ID (jti)
            .signWith(privateKey, SignatureAlgorithm.RS256)
            .compact();
    }

    // 3. Token 黑名单 (Redis)
    public boolean isRevoked(String jti) {
        return redisTemplate.hasKey("jwt:blacklist:" + jti);
    }
}
```

### 2.5 业务安全

#### 2.5.1 风控引擎增强

```
当前风控规则:
  - SINGLE_LIMIT: 单笔限额
  - DAILY_LIMIT: 日累计限额
  - POSITION_LIMIT: 持仓限额
  - FREQ_LIMIT: 频率限制

增强风控规则:
  ┌─────────────────────────────────────────────────────────────┐
  │  实时风控引擎                                                │
  │                                                              │
  │  1. 价格偏离检测:                                            │
  │     - 最新成交价偏离参考价超过阈值 → 暂停交易                 │
  │     - 参考源: 外部行情源 (WebSocket)                          │
  │                                                              │
  │  2. 自成交检测:                                               │
  │     - 同一用户的买卖单不能撮合                                 │
  │     - 关联账户检测 (IP/设备指纹)                               │
  │                                                              │
  │  3. 异常行为检测:                                             │
  │     - 短时间大量撤单 (>80%撤单率)                              │
  │     - 频繁修改订单                                            │
  │     - Spoofing检测 (挂大单后撤单)                             │
  │                                                              │
  │  4. 市场操纵检测:                                             │
  │     - Wash Trading (洗盘)                                    │
  │     - Layering (分层挂单)                                     │
  │     - Momentum Ignition (点火)                                │
  │                                                              │
  │  5. 流动性保护:                                               │
  │     - 订单簿深度不足时限制大额订单                              │
  │     - 价格档位密度检测                                        │
  │                                                              │
  │  6. 熔断机制:                                                 │
  │     - 单品种涨跌幅超限 → 品种熔断                              │
  │     - 全市场异常 → 全局熔断                                   │
  │     - 熔断恢复: 冷却期后逐步放开                               │
  └─────────────────────────────────────────────────────────────┘
```

#### 2.5.2 审计日志

```cpp
// 新增: AuditLogger (C++ 端)
struct AuditEntry {
    uint64_t timestamp;       // 微秒精度
    uint16_t conn_id;         // 连接ID
    uint8_t  msg_type;        // 消息类型
    uint32_t sequence;        // 序列号
    uint8_t  error_code;      // 结果码
    uint32_t latency_us;      // 处理延迟(微秒)
    char     client_ip[46];   // 客户端IP
};

// 写入: 独立文件, 异步批量写入, 不可修改
// 格式: 二进制追加写, 每小时滚动
// 保留: 至少90天, 合规要求7年
```

```java
// Java 端审计日志
@Entity
@Table(name = "audit_log")
public class AuditLog {
    @Id private Long id;
    private Long userId;
    private String action;        // TRADE/ORDER/CANCEL/DEPOSIT/WITHDRAW
    private String resource;      // 资源标识
    private String detail;        // JSON详情
    private String clientIp;
    private String userAgent;
    private Instant timestamp;
    private String traceId;       // 分布式追踪ID
}
```

---

## 3. 数据加密策略

### 3.1 加密体系总览

| 数据类别 | 存储加密 | 传输加密 | 密钥管理 |
|----------|----------|----------|----------|
| 用户密码 | BCrypt (cost=12) | TLS | N/A |
| API Key Secret | AES-256-GCM | TLS + HMAC | KMS |
| JWT Secret | RSA-4096 私钥 | TLS | HSM/KMS |
| Session Token | 不存储 (内存) | TLS + HMAC | 进程内 |
| 余额数据 | 透明加密 (TDE) | TLS | KMS |
| 交易记录 | 透明加密 (TDE) | TLS | KMS |
| WAL日志 | AES-256-GCM 文件加密 | TLS | KMS |
| 审计日志 | 签名防篡改 | TLS | HSM |

### 3.2 密钥管理体系

```
┌──────────────────────────────────────────────────────────┐
│                    密钥管理体系                            │
│                                                           │
│  ┌─────────────────────────────────────────────────────┐ │
│  │  HSM (硬件安全模块) - 根密钥存储                      │ │
│  │  - RSA-4096 主签名密钥                               │ │
│  │  - 根CA证书私钥                                      │ │
│  │  - FIPS 140-2 Level 3                               │ │
│  └────────────────────┬────────────────────────────────┘ │
│                       │                                   │
│  ┌────────────────────▼────────────────────────────────┐ │
│  │  KMS (密钥管理服务) - 密钥分发与轮换                   │ │
│  │  - 数据加密密钥 (DEK): AES-256, 90天轮换             │ │
│  │  - HMAC签名密钥: 30天轮换                            │ │
│  │  - TLS证书: 1年有效期, 自动续期                      │ │
│  │  - 密钥版本管理 + 前向保密                            │ │
│  └────────────────────┬────────────────────────────────┘ │
│                       │                                   │
│  ┌────────────────────▼────────────────────────────────┐ │
│  │  应用层密钥缓存                                       │ │
│  │  - 内存中缓存当前DEK (mlock防交换)                   │ │
│  │  - 密钥使用计数 + 自动轮换                            │ │
│  │  - 进程退出时安全擦除 (explicit_bzero)               │ │
│  └─────────────────────────────────────────────────────┘ │
└──────────────────────────────────────────────────────────┘
```

### 3.3 数据库加密

```sql
-- MySQL 透明数据加密 (TDE)
ALTER TABLE account_balance ENCRYPTION='Y';
ALTER TABLE balance_change_log ENCRYPTION='Y';
ALTER TABLE order_history ENCRYPTION='Y';
ALTER TABLE execution ENCRYPTION='Y';

-- 敏感字段应用层加密
-- API Key Secret: AES-256-GCM, 存储格式: base64(iv + ciphertext + tag)
-- 示例:
UPDATE trading_account SET api_secret = AES_ENCRYPT(?, encryption_key);
```

### 3.4 WAL 日志加密

```cpp
// WAL 加密方案
class EncryptedWALWriter : public WALWriter {
public:
    EncryptedWALWriter(const std::string& dir, const std::vector<uint8_t>& dek);

    void Write(const WALEntry& entry) override {
        // 1. 序列化WALEntry
        auto plaintext = Serialize(entry);

        // 2. AES-256-GCM加密
        auto iv = GenerateIV();  // 12字节, 递增计数器
        auto ciphertext = aes_.Encrypt(plaintext, iv);

        // 3. 写入: iv(12) + ciphertext(N) + tag(16)
        file_.write(iv.data(), 12);
        file_.write(ciphertext.data(), ciphertext.size());
        file_.write(tag.data(), 16);
        file_.flush();
    }

private:
    Aes256Gcm aes_;
    uint64_t iv_counter_ = 0;  // IV计数器 (永不重复)
};
```

---

## 4. 灾备与高可用方案

### 4.1 高可用架构

```
┌─────────────────────────────────────────────────────────────┐
│                     生产环境高可用部署                        │
│                                                              │
│  ┌─────────────┐    ┌─────────────┐    ┌─────────────┐     │
│  │  C++ Server │    │  C++ Server │    │  C++ Server │     │
│  │  (Primary)  │    │  (Hot Standby)│   │  (Warm Stdby)│    │
│  │  Active     │    │  Active     │    │  Passive    │     │
│  │  :8080      │    │  :8080      │    │  :8080      │     │
│  └──────┬──────┘    └──────┬──────┘    └──────┬──────┘     │
│         │                  │                  │              │
│         └──────────┬───────┘                  │              │
│                    │                          │              │
│            ┌───────▼───────┐                  │              │
│            │  TCP Load     │                  │              │
│            │  Balancer     │                  │              │
│            │  (HAProxy)    │                  │              │
│            └───────┬───────┘                  │              │
│                    │                          │              │
│  ┌─────────┬──────▼──────┬──────────┐        │              │
│  │         │             │          │        │              │
│  ▼         ▼             ▼          ▼        │              │
│ ┌─────┐  ┌─────┐    ┌─────┐   ┌─────┐      │              │
│ │Java │  │Java │    │Java │   │Java │      │              │
│ │Admin│  │Admin│    │Admin│   │Admin│      │              │
│ │ :8082│  │:8082│    │:8082│   │:8082│      │              │
│ └──┬──┘  └──┬──┘    └──┬──┘   └──┬──┘      │              │
│    │         │          │         │          │              │
│    └────┬────┘          └────┬────┘          │              │
│         │                    │               │              │
│  ┌──────▼──────┐      ┌─────▼───────┐       │              │
│  │ MySQL主从   │      │ Redis集群   │       │              │
│  │ ┌───┐ ┌───┐│      │┌───┐┌───┐┌───┐│     │              │
│  │ │ M │→│ S ││      ││ M ││ S ││ S ││     │              │
│  │ └───┘ └───┘│      │└───┘└───┘└───┘│     │              │
│  └─────────────┘      └─────────────┘       │              │
│                                              │              │
│  ┌─────────────┐      ┌─────────────┐       │              │
│  │ RabbitMQ    │      │ WAL共享存储  │◄──────┘              │
│  │ 镜像队列    │      │ (NFS/SAN)   │                      │
│  └─────────────┘      └─────────────┘                      │
└─────────────────────────────────────────────────────────────┘
```

### 4.2 C++ 撮合引擎灾备

#### 4.2.1 主备切换方案

```
状态同步方案:
  ┌──────────────────────────────────────────────────────────┐
  │  Primary C++ Server                                      │
  │  ┌──────────┐  ┌──────────┐  ┌──────────┐              │
  │  │ Market   │  │ WAL      │  │ State    │              │
  │  │ Manager  │──►│ Writer   │──►│ Snapshot │              │
  │  │ (内存)   │  │ (本地)   │  │ (定时)   │              │
  │  └──────────┘  └────┬─────┘  └────┬─────┘              │
  │                      │             │                     │
  │                      ▼             ▼                     │
  │              ┌──────────────────────────┐                │
  │              │  共享存储 (NFS/SAN)       │                │
  │              │  - WAL日志文件           │                │
  │              │  - 状态快照文件          │                │
  │              │  - 心跳标记文件          │                │
  │              └──────────────────────────┘                │
  │                      │             ▲                     │
  │                      ▼             │                     │
  │  ┌──────────────────────────────────────┐               │
  │  │  Standby C++ Server                  │               │
  │  │  1. 监控Primary心跳 (文件锁/网络)     │               │
  │  │  2. 检测到Primary故障                │               │
  │  │  3. 加载最新快照                      │               │
  │  │  4. 重放WAL日志 (从快照点开始)        │               │
  │  │  5. 接管服务 (绑定VIP)               │               │
  │  └──────────────────────────────────────┘               │
  └──────────────────────────────────────────────────────────┘
```

#### 4.2.2 状态快照

```cpp
// 新增: StateSnapshot
class StateSnapshot {
public:
    // 定时快照 (每60秒)
    void TakeSnapshot(const MarketManager& market, const std::string& path);

    // 加载快照
    bool LoadSnapshot(MarketManager& market, const std::string& path);

    struct SnapshotHeader {
        uint32_t magic = 0x54524E53;  // "TRNS"
        uint32_t version = 1;
        uint64_t timestamp;
        uint64_t last_lsn;           // 对应WAL的LSN
        uint32_t symbol_count;
        uint32_t order_book_count;
        uint32_t order_count;
        uint8_t  checksum[32];       // SHA-256
    };
};

// 快照文件格式:
// [SnapshotHeader] [Symbol*] [OrderBook*] [Order*] [SHA256]
```

#### 4.2.3 故障切换流程

```
故障检测:
  1. Standby每100ms检查Primary心跳
  2. 连续3次心跳丢失 → 判定Primary故障
  3. 验证: 尝试TCP连接Primary端口
  4. 确认故障 → 启动切换

切换流程 (RTO < 10秒):
  T+0s:   检测到Primary故障
  T+0.5s: 加载最新快照到内存
  T+2s:   重放WAL日志 (快照点 → 最新LSN)
  T+5s:   验证数据完整性 (SHA-256校验)
  T+6s:   绑定VIP, 开始接受连接
  T+7s:   通知Java Admin重连
  T+8s:   恢复正常服务

数据丢失窗口 (RPO):
  - 同步WAL模式: RPO = 0 (无数据丢失)
  - 异步WAL模式: RPO < 1秒 (可能丢失最后1秒WAL)
```

### 4.3 数据库灾备

```
MySQL 高可用:
  ┌─────────────────────────────────────────────────────┐
  │  MySQL InnoDB Cluster                               │
  │                                                      │
  │  ┌────────┐  ┌────────┐  ┌────────┐                │
  │  │ Node 1 │  │ Node 2 │  │ Node 3 │                │
  │  │(Primary)│  │(Secondary)│ │(Secondary)│            │
  │  │ RW     │  │ RO     │  │ RO     │                │
  │  └───┬────┘  └───┬────┘  └───┬────┘                │
  │      │           │           │                      │
  │      └─────┬─────┘───────────┘                      │
  │            │                                        │
  │      ┌─────▼─────┐                                 │
  │      │  MySQL    │                                 │
  │      │  Router   │                                 │
  │      │  (读写分离)│                                 │
  │      └───────────┘                                 │
  │                                                      │
  │  备份策略:                                           │
  │  - 全量备份: 每日 02:00 (xtrabackup)                │
  │  - 增量备份: 每小时                                  │
  │  - Binlog: 实时归档到异地                            │
  │  - 保留: 全量30天, Binlog 7天                        │
  └─────────────────────────────────────────────────────┘
```

### 4.4 Redis 高可用

```
Redis Cluster (6节点, 3主3从):
  ┌──────────────────────────────────────────────────────┐
  │  Slot 0-5460    Slot 5461-10922    Slot 10923-16383  │
  │  ┌──────┐       ┌──────┐         ┌──────┐           │
  │  │Master│       │Master│         │Master│           │
  │  │ :6379│       │ :6380│         │ :6381│           │
  │  └──┬───┘       └──┬───┘         └──┬───┘           │
  │     │              │                │               │
  │  ┌──▼───┐       ┌──▼───┐         ┌──▼───┐           │
  │  │Slave │       │Slave │         │Slave │           │
  │  │ :6382│       │ :6383│         │ :6384│           │
  │  └──────┘       └──────┘         └──────┘           │
  │                                                       │
  │  持久化: AOF (everysec) + RDB (每小时)               │
  │  故障转移: Sentinel 自动切换 (< 30秒)                │
  └──────────────────────────────────────────────────────┘
```

### 4.5 RTO/RPO 目标

| 组件 | RTO (恢复时间) | RPO (数据丢失) | 方案 |
|------|---------------|---------------|------|
| C++ 撮合引擎 | < 10秒 | 0 (同步WAL) | 主备热切 + WAL重放 |
| Java Admin | < 30秒 | 0 | 多实例 + 无状态 |
| MySQL | < 60秒 | 0 (同步复制) | InnoDB Cluster |
| Redis | < 30秒 | < 1秒 | Cluster + AOF |
| RabbitMQ | < 60秒 | 0 (镜像队列) | 镜像队列 + 持久化 |
| 全系统 | < 5分钟 | 0 | 异地灾备 |

---

## 5. 性能优化方案

### 5.1 C++ 撮合引擎优化

#### 5.1.1 多线程架构升级

```
当前: 单线程事件循环
目标: 多线程分离 I/O 与业务

优化架构:
┌──────────────────────────────────────────────────────────────┐
│  I/O 线程 (1-4个)                                            │
│  ┌──────────────┐  ┌──────────────┐                          │
│  │ asio io_ctx  │  │ asio io_ctx  │  ...                     │
│  │ accept/read  │  │ accept/read  │                          │
│  └──────┬───────┘  └──────┬───────┘                          │
│         │                  │                                  │
│         └──────┬───────────┘                                  │
│                │  无锁MPSC队列                                 │
│  ┌─────────────▼──────────────────────────────────────────┐  │
│  │  业务线程 (1个, 核心撮合)                                │  │
│  │  ┌────────────────────────────────────────────────────┐│  │
│  │  │ 1. 从队列取请求                                     ││  │
│  │  │ 2. FrameDecoder 解码                                ││  │
│  │  │ 3. Auth + RateLimit 检查                            ││  │
│  │  │ 4. MarketManager 撮合                               ││  │
│  │  │ 5. WAL 写入 (异步提交)                              ││  │
│  │  │ 6. 构造响应 → 响应队列                               ││  │
│  │  └────────────────────────────────────────────────────┘│  │
│  └─────────────────────────┬──────────────────────────────┘  │
│                            │  无锁MPSC队列                     │
│  ┌─────────────────────────▼──────────────────────────────┐  │
│  │  写入线程 (1-2个)                                        │  │
│  │  ┌────────────────────────────────────────────────────┐│  │
│  │  │ 1. 从响应队列取响应                                  ││  │
│  │  │ 2. asio::async_write 发送                           ││  │
│  │  │ 3. WAL fsync (批量)                                 ││  │
│  │  └────────────────────────────────────────────────────┘│  │
│  └────────────────────────────────────────────────────────┘  │
└──────────────────────────────────────────────────────────────┘
```

#### 5.1.2 无锁数据结构

```cpp
// 新增: 无锁MPSC队列 (I/O线程 → 业务线程)
template<typename T>
class MPSCQueue {
public:
    void Push(T&& item);     // 生产者 (多线程安全)
    bool Pop(T& item);       // 消费者 (单线程)
    bool Empty() const;

private:
    struct Node {
        T data;
        std::atomic<Node*> next;
    };
    std::atomic<Node*> head_;
    Node* tail_;
};

// 使用: I/O线程 Push, 业务线程 Pop
MPSCQueue<Request> request_queue_;
MPSCQueue<Response> response_queue_;
```

#### 5.1.3 内存优化

```
当前: PoolAllocator (已实现)
增强:
  1. 大页内存 (Huge Pages): 2MB/1GB大页, 减少TLB miss
     - 启动参数: --huge-pages=512
     - mmap(MAP_HUGETLB) 分配OrderBook内存

  2. CPU缓存友好:
     - OrderNode 大小对齐到 64字节缓存行
     - 热路径数据 (price, quantity) 放在缓存行前部
     - 冷数据 (timestamp, id) 放在缓存行后部

  3. 内存预分配:
     - 启动时预分配最大容量的Symbol/OrderBook/Order池
     - 避免运行时动态分配
     - 最大容量: 1024 Symbols, 1024 OrderBooks, 10M Orders

  4. NUMA感知:
     - 撮合线程绑定到指定NUMA节点
     - 内存分配从对应NUMA节点
     - 网卡中断绑定到同一NUMA节点
```

#### 5.1.4 DPDK 内核旁路

```
当前: DPDK后端已定义 (DpdkBackend, DpdkConnection)
增强: 完整DPDK集成

  ┌─────────────────────────────────────────────────────────┐
  │  DPDK 数据路径                                           │
  │                                                          │
  │  NIC (专用网卡)                                           │
  │    │                                                     │
  │    ▼                                                     │
  │  DPDK PMD (用户态轮询)                                    │
  │    │                                                     │
  │    ▼                                                     │
  │  RSS 哈希 → 多队列分发                                    │
  │    │                                                     │
  │    ├──► lcore 0: 连接管理 + 心跳                          │
  │    ├──► lcore 1: 撮合核心                                 │
  │    └──► lcore 2: WAL + 响应发送                           │
  │                                                          │
  │  性能目标:                                                │
  │  - 延迟: < 1μs (同机房)                                  │
  │  - 吞吐: > 100万单/秒                                    │
  │  - 抖动: P99 < 5μs                                      │
  └─────────────────────────────────────────────────────────┘
```

### 5.2 Java Admin 性能优化

#### 5.2.1 协议客户端优化

```
当前问题:
  1. sendSync 10秒超时, 单请求串行
  2. 每次请求等待上一请求完成
  3. 心跳占用主连接

优化方案:
  ┌─────────────────────────────────────────────────────────┐
  │  多路复用协议客户端                                      │
  │                                                          │
  │  ┌──────────────────────────────────────────────────┐   │
  │  │  连接池 (N个TCP连接)                               │   │
  │  │  ┌────┐ ┌────┐ ┌────┐ ┌────┐                    │   │
  │  │  │conn│ │conn│ │conn│ │conn│                    │   │
  │  │  │ 0  │ │ 1  │ │ 2  │ │ 3  │                    │   │
  │  │  └────┘ └────┘ └────┘ └────┘                    │   │
  │  └──────────────────────────────────────────────────┘   │
  │                                                          │
  │  请求路由:                                               │
  │  - 按userId哈希到固定连接 (保证顺序性)                    │
  │  - 独立心跳连接                                          │
  │  - 订阅推送连接                                          │
  │                                                          │
  │  响应匹配:                                               │
  │  - Sequence号 + MsgType 双重匹配                         │
  │  - CompletableFuture<Map<Seq, Future>>                  │
  │  - 超时自动清理                                          │
  └─────────────────────────────────────────────────────────┘
```

#### 5.2.2 Redis 优化

```
1. Pipeline: 批量执行Lua脚本, 减少RTT
2. 连接池: Lettuce (异步) 替代 Jedis (同步)
3. 本地缓存: Caffeine 二级缓存 (热点数据)
4. Lua脚本优化: 合并多次操作为单次调用

余额操作优化:
  当前: Redis Lua → MQ → MySQL (3步)
  优化: Redis Lua (原子) → 异步批量写MySQL
        └── 对账保证最终一致性
```

### 5.3 性能基准目标

| 指标 | 当前 | 目标 | 优化手段 |
|------|------|------|----------|
| 撮合延迟 (P50) | ~50μs | < 5μs | 多线程 + 大页 + DPDK |
| 撮合延迟 (P99) | ~200μs | < 20μs | 无锁队列 + 缓存优化 |
| 吞吐量 | ~10万单/秒 | > 100万单/秒 | DPDK + 多核并行 |
| 下单端到端 | ~5ms | < 1ms | 连接池 + Pipeline |
| 订单簿深度查询 | ~100μs | < 10μs | 预计算快照 |
| Java API延迟 | ~50ms | < 10ms | 异步化 + 缓存 |

---

## 6. 量化交易机器人接入方案

### 6.1 接入架构

```
┌──────────────────────────────────────────────────────────────────┐
│                    量化交易机器人接入架构                           │
│                                                                   │
│  ┌─────────────────────────────────────────────────────────────┐ │
│  │  量化交易机器人 (多策略并行)                                  │ │
│  │  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐      │ │
│  │  │ 做市策略  │ │ 趋势策略  │ │ 套利策略  │ │ 高频策略  │      │ │
│  │  └─────┬────┘ └─────┬────┘ └─────┬────┘ └─────┬────┘      │ │
│  │        └────────────┼────────────┼────────────┘            │ │
│  │                     │            │                          │ │
│  │           ┌─────────▼────────────▼─────────┐               │ │
│  │           │       SDK 统一接入层             │               │ │
│  │           │  ┌──────────────────────────┐  │               │ │
│  │           │  │ 1. 连接管理 (自动重连)     │  │               │ │
│  │           │  │ 2. 认证签名 (HMAC)        │  │               │ │
│  │           │  │ 3. 请求序列化 (二进制)     │  │               │ │
│  │           │  │ 4. 响应路由 (Sequence)     │  │               │ │
│  │           │  │ 5. 事件订阅 (OrderBook)   │  │               │ │
│  │           │  │ 6. 心跳保活               │  │               │ │
│  │           │  │ 7. 限流控制               │  │               │ │
│  │           │  │ 8. 错误重试               │  │               │ │
│  │           │  └──────────────────────────┘  │               │ │
│  │           └───────────────┬────────────────┘               │ │
│  └───────────────────────────┼────────────────────────────────┘ │
│                              │                                   │
│              Binary Protocol (TCP / DPDK)                        │
│              (16B Header + Body, Little-Endian)                  │
│                              │                                   │
│  ┌───────────────────────────▼────────────────────────────────┐ │
│  │  C++ 撮合引擎                                              │ │
│  │  - 接受机器人直连 (绕过Java Admin)                          │ │
│  │  - 独立认证通道 (API Key + HMAC)                           │ │
│  │  - 专用限流配额 (按API Key分配)                             │ │
│  │  - 推送: OrderBook + Order 事件                            │ │
│  └────────────────────────────────────────────────────────────┘ │
└──────────────────────────────────────────────────────────────────┘
```

### 6.2 SDK 接口规范

#### 6.2.1 Python SDK

```python
# cpptrader_sdk.py - 量化交易机器人SDK

from dataclasses import dataclass
from typing import Optional, Callable
from enum import IntEnum
import struct
import hashlib
import hmac
import socket
import threading
import time

class OrderSide(IntEnum):
    BUY = 1
    SELL = 2

class OrderType(IntEnum):
    LIMIT = 1
    MARKET = 2
    STOP = 3
    STOP_LIMIT = 4
    TRAILING_STOP = 5
    TRAILING_STOP_LIMIT = 6

class TimeInForce(IntEnum):
    GTC = 0   # Good Till Cancel
    IOC = 1   # Immediate Or Cancel
    FOK = 2   # Fill Or Kill
    DAY = 3   # Day

@dataclass
class OrderResult:
    success: bool
    order_id: int
    error_code: int
    error_msg: str
    executed_qty: int
    leaves_qty: int

@dataclass
class OrderBookSnapshot:
    symbol_id: int
    bids: list  # [(price, qty), ...]
    asks: list  # [(price, qty), ...]
    timestamp: int

class CppTraderClient:
    """
    CppTrader 量化交易SDK

    使用示例:
        client = CppTraderClient(
            host="38.76.219.145",
            port=8080,
            api_key_id="quant-bot-001",
            api_key_secret="secret-key-here"
        )
        client.connect()

        # 订阅行情
        client.subscribe_order_book(symbol_id=1, callback=on_orderbook_update)

        # 下单
        result = client.add_order(
            symbol_id=1,
            side=OrderSide.BUY,
            order_type=OrderType.LIMIT,
            price=50000,
            quantity=10
        )

        # 撤单
        client.delete_order(order_id=result.order_id)

        # 查询订单
        order = client.get_order(order_id=result.order_id)
    """

    def __init__(self, host: str, port: int,
                 api_key_id: str, api_key_secret: str,
                 timeout: float = 10.0,
                 auto_reconnect: bool = True):
        self._host = host
        self._port = port
        self._api_key_id = api_key_id
        self._api_key_secret = api_key_secret
        self._timeout = timeout
        self._auto_reconnect = auto_reconnect
        self._sequence = 0
        self._connected = False
        self._authenticated = False
        self._session_token = None
        self._pending_requests = {}  # seq -> Future
        self._subscriptions = {}     # symbol_id -> callback
        self._lock = threading.Lock()

    def connect(self) -> bool:
        """建立TCP连接并完成认证"""
        ...

    def disconnect(self):
        """断开连接"""
        ...

    def add_order(self, symbol_id: int, side: OrderSide,
                  order_type: OrderType, price: int = 0,
                  quantity: int = 0, stop_price: int = 0,
                  time_in_force: TimeInForce = TimeInForce.GTC,
                  slippage: int = -1,
                  trailing_distance: int = 0,
                  trailing_step: int = 0,
                  order_id: Optional[int] = None) -> OrderResult:
        """提交订单"""
        ...

    def delete_order(self, order_id: int) -> OrderResult:
        """撤销订单"""
        ...

    def reduce_order(self, order_id: int, quantity: int) -> OrderResult:
        """减量订单"""
        ...

    def replace_order(self, order_id: int, new_id: int,
                      price: int, quantity: int) -> OrderResult:
        """替换订单"""
        ...

    def get_order(self, order_id: int) -> Optional[dict]:
        """查询订单"""
        ...

    def get_order_book(self, symbol_id: int, depth: int = 5) -> Optional[OrderBookSnapshot]:
        """查询订单簿深度"""
        ...

    def subscribe_order_book(self, symbol_id: int,
                             callback: Callable[[OrderBookSnapshot], None]):
        """订阅订单簿更新"""
        ...

    def subscribe_orders(self, callback: Callable[[dict], None]):
        """订阅订单状态更新"""
        ...

    def enable_matching(self) -> bool:
        """启用撮合引擎 (管理员权限)"""
        ...

    def disable_matching(self) -> bool:
        """禁用撮合引擎 (管理员权限)"""
        ...
```

#### 6.2.2 C++ SDK (Header-Only)

```cpp
// cpptrader_client.h - C++量化交易SDK
#pragma once

#include <cstdint>
#include <string>
#include <functional>
#include <future>
#include <memory>

namespace CppTrader {

struct OrderResult {
    bool success;
    uint64_t order_id;
    uint8_t error_code;
    int64_t executed_qty;
    int64_t leaves_qty;
};

struct Level {
    int64_t price;
    int64_t total_volume;
    int64_t visible_volume;
    int64_t orders;
};

struct OrderBookUpdate {
    uint32_t symbol_id;
    Level best_bid;
    Level best_ask;
    std::vector<Level> bids;
    std::vector<Level> asks;
};

struct OrderUpdate {
    uint64_t id;
    uint32_t symbol_id;
    uint8_t type;
    uint8_t side;
    int64_t price;
    int64_t quantity;
    int64_t executed_quantity;
    int64_t leaves_quantity;
    uint8_t status;
};

class Client {
public:
    using OrderBookCallback = std::function<void(const OrderBookUpdate&)>;
    using OrderCallback = std::function<void(const OrderUpdate&)>;

    struct Config {
        std::string host = "127.0.0.1";
        uint16_t port = 8080;
        std::string api_key_id;
        std::string api_key_secret;
        uint32_t timeout_ms = 10000;
        bool auto_reconnect = true;
        bool use_tls = false;
    };

    explicit Client(const Config& config);
    ~Client();

    // 连接管理
    bool Connect();
    void Disconnect();
    bool IsConnected() const;

    // 订单操作
    std::future<OrderResult> AddOrder(uint32_t symbol_id, uint8_t side,
                                       uint8_t type, int64_t price, int64_t quantity,
                                       int64_t stop_price = 0,
                                       uint8_t time_in_force = 0,
                                       int64_t slippage = -1,
                                       int64_t trailing_distance = 0,
                                       int64_t trailing_step = 0,
                                       uint64_t order_id = 0);

    std::future<OrderResult> DeleteOrder(uint64_t order_id);
    std::future<OrderResult> ReduceOrder(uint64_t order_id, int64_t quantity);
    std::future<OrderResult> ReplaceOrder(uint64_t order_id, uint64_t new_id,
                                           int64_t price, int64_t quantity);

    // 查询
    std::future<OrderUpdate> GetOrder(uint64_t order_id);
    std::future<OrderBookUpdate> GetOrderBook(uint32_t symbol_id, uint32_t depth = 5);

    // 订阅
    bool SubscribeOrderBook(uint32_t symbol_id, OrderBookCallback callback);
    bool SubscribeOrders(OrderCallback callback);

private:
    struct Impl;
    std::unique_ptr<Impl> impl_;
};

} // namespace CppTrader
```

### 6.3 数据交互协议

#### 6.3.1 机器人专用消息流

```
连接建立流程:
  Robot                    C++ Server
    │                          │
    │──── TCP Connect ────────►│
    │◄─── TCP Accept ─────────│
    │                          │
    │──── AUTH_REQUEST ───────►│  ApiKeyId + Timestamp + Nonce + HMAC-Signature
    │◄─── AUTH_RESPONSE ──────│  Error + SessionToken
    │                          │
    │──── SUBSCRIBE_OB ───────►│  订阅订单簿
    │◄─── SIMPLE_RESPONSE ────│  订阅确认
    │                          │
    │──── SUBSCRIBE_ORDERS ──►│  订阅订单更新
    │◄─── SIMPLE_RESPONSE ────│  订阅确认
    │                          │
    │◄─── OB_UPDATE (PUSH) ───│  订单簿实时推送
    │──── EVENT_ACK ──────────►│  确认收到
    │                          │
    │──── ADD_ORDER ──────────►│  下单
    │◄─── ORDER_RESPONSE ─────│  下单结果
    │                          │
    │◄─── ORDER_UPDATE (PUSH)─│  订单状态变更
    │──── EVENT_ACK ──────────►│  确认收到
    │                          │
    │──── HEARTBEAT_REQ ──────►│  心跳
    │◄─── HEARTBEAT_RESP ─────│  心跳响应
    │                          │
```

#### 6.3.2 二进制帧格式

```
帧格式 (小端序):
┌──────────────────────────────────────────────────────────────┐
│  Header (16字节)                                              │
│  ┌────────┬─────────┬─────────┬───────┬─────────┬─────────┐ │
│  │ Magic  │ Version │ MsgType │ Flags │ Reserved│ Length  │ │
│  │ 2B     │ 1B      │ 1B      │ 1B    │ 1B      │ 2B      │ │
│  │ 0x5452 │ 0x02    │         │       │ 0x00    │ body长度│ │
│  ├────────┴─────────┴─────────┴───────┴─────────┴─────────┤ │
│  │ Sequence(4B) │ HmacPrefix(2B) │ Reserved2(2B)          │ │
│  └─────────────────────────────────────────────────────────┘ │
│  Body (Length字节)                                            │
│  ┌─────────────────────────────────────────────────────────┐ │
│  │ 请求/响应/推送数据 (按MsgType解析)                        │ │
│  └─────────────────────────────────────────────────────────┘ │
└──────────────────────────────────────────────────────────────┘

序列号规则:
  - 客户端: 从1开始递增
  - 服务端: 响应回显请求的Sequence
  - 推送: Sequence = 0
  - 心跳: Sequence = 0

HMAC签名规则:
  - 签名输入: Sequence(4) + MsgType(1) + Flags(1) + Length(2) + Body(N)
  - 签名算法: HMAC-SHA256(SessionToken, input)
  - HmacPrefix: 取签名前2字节
```

### 6.4 权限控制机制

#### 6.4.1 角色权限模型

```
┌─────────────────────────────────────────────────────────────┐
│  角色权限矩阵                                                │
│                                                              │
│  操作                    │ Admin │ Trader │ QuantBot │ Viewer│
│  ───────────────────────────────────────────────────────── │
│  启用/禁用撮合            │  ✓    │   ✗    │    ✗     │  ✗   │
│  添加/删除交易品种        │  ✓    │   ✗    │    ✗     │  ✗   │
│  添加/删除订单簿          │  ✓    │   ✗    │    ✗     │  ✗   │
│  下单/撤单/改单           │  ✓    │   ✓    │    ✓     │  ✗   │
│  查询订单/订单簿          │  ✓    │   ✓    │    ✓     │  ✓   │
│  订阅行情/订单更新        │  ✓    │   ✓    │    ✓     │  ✓   │
│  对账操作                │  ✓    │   ✗    │    ✗     │  ✗   │
│  风控规则管理             │  ✓    │   ✗    │    ✗     │  ✗   │
│  用户管理                │  ✓    │   ✗    │    ✗     │  ✗   │
│  余额操作                │  ✓    │   ✗    │    ✗     │  ✗   │
│                                                              │
│  API Key 绑定角色:                                           │
│  - Admin Key: 所有权限                                       │
│  - Trader Key: 交易 + 查询                                   │
│  - QuantBot Key: 交易 + 查询 + 高频限流配额                   │
│  - Viewer Key: 只读查询                                      │
└─────────────────────────────────────────────────────────────┘
```

#### 6.4.2 API Key 管理

```sql
-- 新增: API Key 管理表
CREATE TABLE api_key (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    key_id VARCHAR(32) NOT NULL UNIQUE,     -- API Key ID
    key_secret_encrypted TEXT NOT NULL,       -- AES-256-GCM加密
    role ENUM('ADMIN','TRADER','QUANTBOT','VIEWER') NOT NULL,
    rate_limit INT NOT NULL DEFAULT 100,     -- 每秒请求限制
    daily_limit BIGINT NOT NULL DEFAULT 0,   -- 日交易限额(0=不限)
    ip_whitelist JSON,                       -- IP白名单 ["1.2.3.4", ...]
    allowed_symbols JSON,                    -- 允许交易的品种 [1,2,3]
    enabled BOOLEAN DEFAULT TRUE,
    expires_at DATETIME,                     -- 过期时间
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    last_used_at DATETIME,
    INDEX idx_user_id (user_id),
    INDEX idx_key_id (key_id)
);
```

#### 6.4.3 C++ 端权限检查

```cpp
// 新增: 权限检查器
class PermissionChecker {
public:
    struct Permissions {
        bool can_trade;
        bool can_admin;
        bool can_view;
        int rate_limit;           // req/s
        int64_t daily_limit;      // 日限额
        std::vector<uint32_t> allowed_symbols;
    };

    // 根据API Key ID查询权限
    Permissions GetPermissions(const std::string& api_key_id);

    // 检查消息类型权限
    bool CheckPermission(uint16_t conn_id, MsgType msg_type);

private:
    std::unordered_map<std::string, Permissions> permissions_cache_;
    std::mutex cache_mutex_;
    std::chrono::steady_clock::time_point last_refresh_;
};
```

### 6.5 量化机器人专用限流

```
┌─────────────────────────────────────────────────────────────┐
│  多级限流策略                                                │
│                                                              │
│  Level 1: 连接级限流 (C++ 令牌桶)                            │
│  ┌──────────────────────────────────────────────────────┐   │
│  │  默认: 1000 req/s                                    │   │
│  │  QuantBot: 5000 req/s (高频配额)                     │   │
│  │  Admin: 100 req/s                                    │   │
│  └──────────────────────────────────────────────────────┘   │
│                                                              │
│  Level 2: 用户级限流 (Redis)                                 │
│  ┌──────────────────────────────────────────────────────┐   │
│  │  日交易额: 按API Key累计                              │   │
│  │  下单频率: 每分钟N单 (按策略类型)                      │   │
│  │  撤单率: 撤单/下单 < 80%                              │   │
│  └──────────────────────────────────────────────────────┘   │
│                                                              │
│  Level 3: 品种级限流 (C++ 内存)                              │
│  ┌──────────────────────────────────────────────────────┐   │
│  │  单品种每秒订单数: < 10000                            │   │
│  │  单品种订单簿深度: < 1000档                           │   │
│  │  单品种活跃订单: < 100000                             │   │
│  └──────────────────────────────────────────────────────┘   │
│                                                              │
│  Level 4: 全局限流 (C++ 全局)                                │
│  ┌──────────────────────────────────────────────────────┐   │
│  │  总连接数: < 1000                                    │   │
│  │  总活跃订单: < 10,000,000                            │   │
│  │  总吞吐: < 1,000,000 req/s                          │   │
│  └──────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────┘
```

---

## 7. 风险监控系统

### 7.1 实时监控架构

```
┌──────────────────────────────────────────────────────────────┐
│                    风险监控系统架构                             │
│                                                               │
│  ┌─────────────────────────────────────────────────────────┐ │
│  │  数据采集层                                              │ │
│  │  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐  │ │
│  │  │ C++ 指标 │ │ Java指标 │ │ 系统指标 │ │ 业务指标 │  │ │
│  │  │ 撮合延迟 │ │ API延迟  │ │ CPU/MEM  │ │ 持仓分布 │  │ │
│  │  │ 订单吞吐 │ │ 错误率   │ │ 网络I/O  │ │ 余额异常 │  │ │
│  │  │ 队列深度 │ │ QPS      │ │ 磁盘I/O  │ │ 交易量   │  │ │
│  │  └─────┬────┘ └─────┬────┘ └─────┬────┘ └─────┬────┘  │ │
│  └────────┼────────────┼────────────┼────────────┼────────┘ │
│           │            │            │            │          │
│           └────────────┼────────────┼────────────┘          │
│                        │            │                       │
│  ┌─────────────────────▼────────────▼─────────────────────┐ │
│  │  Prometheus + Grafana (指标聚合与可视化)                 │ │
│  │  - 时序数据库存储                                       │ │
│  │  - 自定义Dashboard                                      │ │
│  │  - 告警规则引擎                                         │ │
│  └─────────────────────────┬──────────────────────────────┘ │
│                            │                                 │
│  ┌─────────────────────────▼──────────────────────────────┐ │
│  │  告警分发层                                              │ │
│  │  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐  │ │
│  │  │ 钉钉/企微│ │  短信    │ │  邮件    │ │  电话    │  │ │
│  │  │ (P3/P4) │ │ (P2)    │ │ (P3)    │ │ (P1)    │  │ │
│  │  └──────────┘ └──────────┘ └──────────┘ └──────────┘  │ │
│  └────────────────────────────────────────────────────────┘ │
└──────────────────────────────────────────────────────────────┘
```

### 7.2 关键监控指标

#### 7.2.1 交易风控指标

| 指标 | 采集频率 | 告警阈值 | 级别 |
|------|----------|----------|------|
| 单品种价格波动 | 实时 | 1分钟内涨跌>5% | P1 |
| 单用户下单频率 | 实时 | >100单/秒 | P2 |
| 全市场撤单率 | 10秒 | >80% | P2 |
| 单用户日交易额 | 实时 | 超过限额 | P3 |
| 订单簿深度异常 | 实时 | 档位数<5 | P2 |
| 自成交检测 | 实时 | 任何自成交 | P1 |
| 大额订单 | 实时 | 单笔>100万 | P3 |

#### 7.2.2 系统健康指标

| 指标 | 采集频率 | 告警阈值 | 级别 |
|------|----------|----------|------|
| C++ 撮合延迟 P99 | 1秒 | >100μs | P2 |
| C++ 撮合延迟 P99.9 | 1秒 | >1ms | P1 |
| Java API 延迟 P99 | 10秒 | >500ms | P2 |
| 订单处理失败率 | 10秒 | >0.1% | P1 |
| WAL写入延迟 | 1秒 | >10ms | P2 |
| TCP连接数 | 10秒 | >800 | P3 |
| 内存使用率 | 10秒 | >85% | P2 |
| CPU使用率 | 10秒 | >90% | P1 |
| 磁盘使用率 | 1分钟 | >80% | P2 |
| Redis连接数 | 10秒 | >500 | P3 |
| MySQL慢查询 | 实时 | >1秒 | P2 |
| RabbitMQ积压 | 10秒 | >10000 | P2 |

### 7.3 熔断机制

```cpp
// C++ 端熔断器
class CircuitBreaker {
public:
    enum class State { CLOSED, OPEN, HALF_OPEN };

    // 品种级熔断
    void CheckSymbolCircuit(uint32_t symbol_id, int64_t price);

    // 全局熔断
    void CheckGlobalCircuit();

    struct CircuitConfig {
        double price_change_pct = 5.0;     // 价格波动阈值(%)
        int64_t window_ms = 60000;          // 检测窗口(毫秒)
        int64_t cooldown_ms = 300000;       // 熔断冷却期(5分钟)
        int64_t recovery_pct = 1.0;         // 恢复阈值(%)
    };

private:
    std::unordered_map<uint32_t, State> symbol_states_;
    State global_state_ = State::CLOSED;
    CircuitConfig config_;
};
```

---

## 8. 兼容性测试标准

### 8.1 协议兼容性测试

#### 8.1.1 二进制协议一致性测试

```
测试矩阵:
┌─────────────────────────────────────────────────────────────┐
│  协议一致性测试用例                                          │
│                                                              │
│  1. 消息头解析测试                                           │
│     [H-001] Magic校验: 正确值0x5452 / 错误值                │
│     [H-002] Version校验: 正确值0x02 / 错误值0x01/0x03      │
│     [H-003] MsgType校验: 所有合法类型 / 非法类型             │
│     [H-004] Flags校验: REQUEST/RESPONSE/PUSH/ERROR/HEARTBEAT│
│     [H-005] Length校验: 正确长度 / 超大长度 / 零长度         │
│     [H-006] Sequence校验: 单调递增 / 回退 / 跳跃 / 零值     │
│     [H-007] HmacPrefix校验: 正确签名 / 错误签名 / 零值      │
│                                                              │
│  2. 帧解码测试                                               │
│     [F-001] 完整帧: 16字节头 + 完整Body                     │
│     [F-002] 分片帧: 头部和Body分多次到达                     │
│     [F-003] 粘包: 多个帧合并到达                             │
│     [F-004] 空Body帧: 仅16字节头(心跳)                      │
│     [F-005] 变长帧: OrderBookResponse(72+变长)              │
│     [F-006] 错误恢复: 非法Magic后重新同步                    │
│                                                              │
│  3. 请求/响应匹配测试                                        │
│     [R-001] Sequence回显: 请求seq=N, 响应seq=N              │
│     [R-002] MsgType匹配: 请求0x07→响应0x43                  │
│     [R-003] 超时处理: 10秒无响应→返回null                   │
│     [R-004] 并发请求: 多请求Sequence不冲突                   │
│                                                              │
│  4. 结构体对齐测试                                           │
│     [S-001] MsgHeader: sizeof==16, 字段偏移正确              │
│     [S-002] AddSymbolRequest: sizeof==12                     │
│     [S-003] AddOrderRequest: sizeof==88                      │
│     [S-004] SymbolResponse: sizeof==13                       │
│     [S-005] OrderResponse: sizeof==89                        │
│     [S-006] SimpleResponse: sizeof==1                        │
│     [S-007] AuthRequest: sizeof==88                          │
│     [S-008] AuthResponse: sizeof==17                         │
│     [S-009] 字节序: 全部小端序                               │
│     [S-010] 对齐: #pragma pack(push, 1) 无填充              │
│                                                              │
│  5. 错误码一致性测试                                         │
│     [E-001] 业务错误码: C++值==Java值 (0-10)                │
│     [E-002] 安全错误码: C++值==Java值 (20-27)               │
│     [E-003] 未知错误码: 客户端优雅处理                       │
└─────────────────────────────────────────────────────────────┘
```

#### 8.1.2 自动化测试框架

```python
# test_protocol_compatibility.py

import struct
import socket
import time
import unittest

class ProtocolCompatibilityTest(unittest.TestCase):
    """协议兼容性自动化测试"""

    MAGIC = 0x5452
    VERSION = 2
    HEADER_SIZE = 16

    def setUp(self):
        self.sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        self.sock.connect(('127.0.0.1', 8080))
        self.sock.settimeout(5.0)
        self.sequence = 0

    def tearDown(self):
        self.sock.close()

    def build_header(self, msg_type, flags, body_len):
        self.sequence += 1
        return struct.pack('<HBBBHHIHH',
            self.MAGIC, self.VERSION, msg_type, flags,
            0, body_len, self.sequence, 0, 0)

    def send_and_recv(self, msg_type, flags, body=b''):
        header = self.build_header(msg_type, flags, len(body))
        self.sock.sendall(header + body)
        resp_header = self.sock.recv(self.HEADER_SIZE)
        self.assertEqual(len(resp_header), self.HEADER_SIZE)
        magic, ver, rtype, rflags, _, rlen, rseq, _, _ = \
            struct.unpack('<HBBBHHIHH', resp_header)
        self.assertEqual(magic, self.MAGIC)
        self.assertEqual(ver, self.VERSION)
        resp_body = b''
        while len(resp_body) < rlen:
            chunk = self.sock.recv(rlen - len(resp_body))
            resp_body += chunk
        return rtype, rflags, rseq, resp_body

    def test_heartbeat(self):
        """[H-004] 心跳标志测试"""
        rtype, rflags, rseq, _ = self.send_and_recv(0xC0, 0x10)
        self.assertEqual(rtype, 0xC1)
        self.assertEqual(rflags, 0x10)  # FLAG_HEARTBEAT

    def test_sequence_echo(self):
        """[R-001] Sequence回显测试"""
        _, _, rseq, _ = self.send_and_recv(0x0F, 0x01)  # ENABLE_MATCHING
        self.assertEqual(rseq, self.sequence)

    def test_add_order_response_type(self):
        """[R-002] MsgType匹配测试"""
        # 先启用撮合和添加品种/订单簿
        self.send_and_recv(0x0F, 0x01)  # ENABLE_MATCHING
        body = struct.pack('<I8s', 1, b'BTCUSD\x00\x00')
        self.send_and_recv(0x01, 0x01, body)  # ADD_SYMBOL
        body = struct.pack('<I', 1)
        self.send_and_recv(0x04, 0x01, body)  # ADD_ORDER_BOOK

        # 下单
        body = struct.pack('<QIBBqqqqBbqqqq',
            1001, 1, 1, 1,  # id, symbol, LIMIT, BUY
            50000, 0, 10, 10,  # price, stop, qty, executed
            10, 0,  # tif, pad, leaves
            10, -1, 0, 0)  # maxVisible, slippage, trailing*
        rtype, _, _, _ = self.send_and_recv(0x07, 0x01, body)
        self.assertEqual(rtype, 0x43)  # ORDER_RESPONSE

    # ... 更多测试用例
```

### 8.2 性能基准测试

```
基准测试矩阵:
┌─────────────────────────────────────────────────────────────┐
│  性能基准测试                                                │
│                                                              │
│  1. 撮合引擎基准                                             │
│     [B-001] 单线程吞吐: 目标 > 50万单/秒                    │
│     [B-002] 撮合延迟 P50: 目标 < 5μs                        │
│     [B-003] 撮合延迟 P99: 目标 < 20μs                       │
│     [B-004] 撮合延迟 P99.9: 目标 < 100μs                    │
│     [B-005] 10万活跃订单: 延迟增长 < 2x                     │
│     [B-006] 100万活跃订单: 延迟增长 < 5x                    │
│                                                              │
│  2. 网络基准                                                 │
│     [B-010] TCP往返延迟: 目标 < 100μs (同机房)              │
│     [B-011] DPDK往返延迟: 目标 < 10μs (同机房)              │
│     [B-012] 并发连接: 1000连接稳定运行                       │
│     [B-013] 长连接稳定性: 24小时无断连                       │
│                                                              │
│  3. Java Admin 基准                                          │
│     [B-020] HTTP API延迟 P50: 目标 < 5ms                    │
│     [B-021] HTTP API延迟 P99: 目标 < 50ms                   │
│     [B-022] 并发请求: 100 QPS稳定                            │
│     [B-023] Redis操作延迟: 目标 < 1ms                        │
│     [B-024] MySQL写入延迟: 目标 < 10ms                       │
│                                                              │
│  4. 故障恢复基准                                             │
│     [B-030] C++主备切换: RTO < 10秒                         │
│     [B-031] Java Admin重启: RTO < 30秒                      │
│     [B-032] MySQL故障切换: RTO < 60秒                       │
│     [B-033] Redis故障切换: RTO < 30秒                       │
│     [B-034] 网络抖动恢复: < 5秒                             │
└─────────────────────────────────────────────────────────────┘
```

### 8.3 安全测试标准

```
安全测试矩阵:
┌─────────────────────────────────────────────────────────────┐
│  安全渗透测试                                                │
│                                                              │
│  1. 认证测试                                                 │
│     [SEC-001] 无效API Key: 拒绝连接                          │
│     [SEC-002] 过期Timestamp: 拒绝认证 (>30秒)               │
│     [SEC-003] 重放Nonce: 拒绝重复Nonce                       │
│     [SEC-004] 错误HMAC签名: 拒绝请求                         │
│     [SEC-005] 未认证发交易请求: 拒绝                          │
│                                                              │
│  2. 注入测试                                                 │
│     [SEC-010] SQL注入: 所有API参数                           │
│     [SEC-011] 命令注入: 系统调用参数                         │
│     [SEC-012] 二进制协议畸形数据: 超长Body/负数Length        │
│                                                              │
│  3. 拒绝服务测试                                             │
│     [SEC-020] 连接泛洪: 限流生效                             │
│     [SEC-021] 请求泛洪: 令牌桶限流                           │
│     [SEC-022] 大Body攻击: 拒绝超限帧                         │
│     [SEC-023] 慢连接攻击: 超时断开                           │
│                                                              │
│  4. 数据安全测试                                             │
│     [SEC-030] 传输加密: TLS 1.3验证                          │
│     [SEC-031] 密码存储: BCrypt验证                           │
│     [SEC-032] API Key加密: AES-256-GCM验证                   │
│     [SEC-033] 日志脱敏: 无明文密码/密钥                      │
│                                                              │
│  5. 权限测试                                                 │
│     [SEC-040] 越权操作: Trader无法执行Admin操作              │
│     [SEC-041] 跨用户访问: 无法查看他人订单                   │
│     [SEC-042] Token过期: 过期Token拒绝访问                   │
│     [SEC-043] Token伪造: 无效签名拒绝                       │
└─────────────────────────────────────────────────────────────┘
```

---

## 9. 部署与运维规范

### 9.1 容器化部署

```dockerfile
# C++ Server Dockerfile
FROM ubuntu:22.04 AS builder
RUN apt-get update && apt-get install -y cmake g++ make
WORKDIR /build
COPY . .
RUN cmake -DCMAKE_BUILD_TYPE=Release . && make -j$(nproc)

FROM ubuntu:22.04
RUN apt-get update && apt-get install -y libstdc++6 libgcc-s1
COPY --from=builder /build/cpptrader-protocol-server /usr/local/bin/
EXPOSE 8080
VOLUME /data/wal
ENTRYPOINT ["cpptrader-protocol-server", "--port=8080", "--wal-dir=/data/wal"]
```

```dockerfile
# Java Admin Dockerfile
FROM eclipse-temurin:21-jdk AS builder
WORKDIR /build
COPY . .
RUN ./mvnw package -DskipTests

FROM eclipse-temurin:21-jre
COPY --from=builder /build/target/java-admin-*.jar /app/app.jar
EXPOSE 8082
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
```

### 9.2 Kubernetes 编排

```yaml
# cpptrader-namespace.yaml
apiVersion: v1
kind: Namespace
metadata:
  name: cpptrader

---
# cpptrader-cpp-server.yaml
apiVersion: apps/v1
kind: StatefulSet
metadata:
  name: cpp-server
  namespace: cpptrader
spec:
  replicas: 2  # Primary + Hot Standby
  serviceName: cpp-server
  selector:
    matchLabels:
      app: cpp-server
  template:
    metadata:
      labels:
        app: cpp-server
    spec:
      nodeSelector:
        node-role: trading-core
      containers:
      - name: cpp-server
        image: cpptrader/cpp-server:latest
        ports:
        - containerPort: 8080
        resources:
          requests:
            cpu: "4"
            memory: "8Gi"
            hugepages-2Mi: "1Gi"
          limits:
            cpu: "8"
            memory: "16Gi"
            hugepages-2Mi: "2Gi"
        volumeMounts:
        - name: wal-storage
          mountPath: /data/wal
        - name: config
          mountPath: /etc/cpptrader
        env:
        - name: CPPTRADER_PORT
          value: "8080"
        - name: CPPTRADER_WAL_DIR
          value: "/data/wal"
        - name: CPPTRADER_AUTH_ENABLED
          value: "true"
        livenessProbe:
          tcpSocket:
            port: 8080
          initialDelaySeconds: 5
          periodSeconds: 3
        readinessProbe:
          tcpSocket:
            port: 8080
          initialDelaySeconds: 3
          periodSeconds: 2
      volumes:
      - name: config
        configMap:
          name: cpp-server-config
  volumeClaimTemplates:
  - metadata:
      name: wal-storage
    spec:
      accessModes: [ReadWriteOnce]
      storageClassName: local-ssd
      resources:
        requests:
          storage: 100Gi
```

### 9.3 运维监控仪表盘

```
Grafana Dashboard 布局:
┌──────────────────────────────────────────────────────────────┐
│  CppTrader 运维监控大屏                                       │
│                                                               │
│  ┌────────────────────┐  ┌────────────────────┐             │
│  │ 系统状态           │  │ 撮合引擎           │             │
│  │ ● C++ Server: UP   │  │ 延迟 P50: 3.2μs   │             │
│  │ ● Java Admin: UP   │  │ 延迟 P99: 12.5μs  │             │
│  │ ● MySQL: UP        │  │ 吞吐: 52万单/秒    │             │
│  │ ● Redis: UP        │  │ 活跃订单: 123,456  │             │
│  │ ● RabbitMQ: UP     │  │ 撮合成交: 1,234    │             │
│  └────────────────────┘  └────────────────────┘             │
│                                                               │
│  ┌────────────────────┐  ┌────────────────────┐             │
│  │ API性能            │  │ 风控状态           │             │
│  │ QPS: 1,234         │  │ 日交易额: 5.6亿    │             │
│  │ P50: 5ms           │  │ 限额使用率: 56%    │             │
│  │ P99: 45ms          │  │ 告警: 3条          │             │
│  │ 错误率: 0.01%      │  │ 熔断: 无           │             │
│  └────────────────────┘  └────────────────────┘             │
│                                                               │
│  ┌──────────────────────────────────────────────────────┐   │
│  │  撮合延迟趋势图 (1小时)                                │   │
│  │  ▁▂▃▂▁▁▂▃▅▃▂▁▁▂▃▂▁▁▂▃▅▆▅▃▂▁▁▂▃▂▁▁▂▃▂▁▁▂▃▂▁     │   │
│  └──────────────────────────────────────────────────────┘   │
└──────────────────────────────────────────────────────────────┘
```

---

## 10. 开发路线图

### Phase 1: 安全加固 (4周)

| 周次 | 任务 | 交付物 |
|------|------|--------|
| W1 | TLS集成 (C++ asio::ssl + Java Netty SslContext) | 双向TLS通信 |
| W1 | HMAC签名实现 (C++ HmacVerifier + Java签名) | 完整HMAC签名链路 |
| W2 | 认证流程增强 (Nonce防重放 + Timestamp校验 + 随机SessionToken) | 安全认证流程 |
| W2 | API Key管理 (数据库表 + CRUD接口 + 权限模型) | 权限控制体系 |
| W3 | JWT安全增强 (RS256 + Token黑名单 + 客户端指纹) | 增强JWT认证 |
| W3 | 审计日志 (C++ AuditLogger + Java AuditLog) | 完整审计链 |
| W4 | 安全测试 (渗透测试 + 修复) | 安全测试报告 |

### Phase 2: 高可用 (4周)

| 周次 | 任务 | 交付物 |
|------|------|--------|
| W5 | C++状态快照 (StateSnapshot) | 定时快照功能 |
| W5 | C++主备切换 (心跳检测 + VIP接管) | 自动故障切换 |
| W6 | MySQL InnoDB Cluster部署 | 数据库高可用 |
| W6 | Redis Cluster部署 | 缓存高可用 |
| W7 | RabbitMQ镜像队列 | 消息队列高可用 |
| W7 | 容器化 (Dockerfile + K8s YAML) | 容器化部署 |
| W8 | 灾备演练 (全链路故障切换测试) | 灾备演练报告 |

### Phase 3: 性能优化 (4周)

| 周次 | 任务 | 交付物 |
|------|------|--------|
| W9 | C++多线程架构 (I/O线程 + 业务线程 + 写入线程) | 多线程撮合引擎 |
| W9 | 无锁队列 (MPSC) | 无锁数据路径 |
| W10 | 内存优化 (大页 + NUMA + 缓存行对齐) | 内存优化 |
| W10 | DPDK完整集成 | 内核旁路数据路径 |
| W11 | Java协议客户端优化 (连接池 + 多路复用) | 高性能客户端 |
| W11 | Redis优化 (Lettuce + Pipeline + 本地缓存) | 缓存层优化 |
| W12 | 性能基准测试 + 调优 | 性能测试报告 |

### Phase 4: 量化接入 (4周)

| 周次 | 任务 | 交付物 |
|------|------|--------|
| W13 | Python SDK开发 | cpptrader-sdk PyPI包 |
| W13 | C++ SDK开发 (Header-Only) | cpptrader_client.h |
| W14 | 机器人专用限流 + 权限 | 量化接入权限体系 |
| W14 | 订阅增强 (增量OrderBook + 快照+增量) | 高效行情推送 |
| W15 | 风控引擎增强 (自成交检测 + 撤单率 + 熔断) | 增强风控 |
| W15 | 兼容性测试框架 | 自动化测试套件 |
| W16 | 集成测试 + 文档 + 示例 | 完整接入文档 |

### Phase 5: 生产就绪 (2周)

| 周次 | 任务 | 交付物 |
|------|------|--------|
| W17 | 监控告警 (Prometheus + Grafana) | 运维监控体系 |
| W17 | 运维手册 (部署/升级/回滚/应急) | 运维SOP |
| W18 | 全链路压测 + 混沌工程 | 生产就绪报告 |
| W18 | 安全审计 + 合规检查 | 合规审计报告 |

---

## 附录 A: 协议常量速查表

### A.1 MsgType 完整枚举

| 值 | 名称 | 方向 | Body大小 |
|----|------|------|----------|
| 0x01 | ADD_SYMBOL_REQUEST | C→S | 12 |
| 0x02 | DELETE_SYMBOL_REQUEST | C→S | 4 |
| 0x03 | GET_SYMBOL_REQUEST | C→S | 4 |
| 0x04 | ADD_ORDER_BOOK_REQUEST | C→S | 4 |
| 0x05 | DELETE_ORDER_BOOK_REQUEST | C→S | 4 |
| 0x06 | GET_ORDER_BOOK_REQUEST | C→S | 8 |
| 0x07 | ADD_ORDER_REQUEST | C→S | 88 |
| 0x08 | REDUCE_ORDER_REQUEST | C→S | 16 |
| 0x09 | MODIFY_ORDER_REQUEST | C→S | 24 |
| 0x0A | MITIGATE_ORDER_REQUEST | C→S | 24 |
| 0x0B | REPLACE_ORDER_REQUEST | C→S | 32 |
| 0x0C | DELETE_ORDER_REQUEST | C→S | 8 |
| 0x0D | EXECUTE_ORDER_REQUEST | C→S | 24 |
| 0x0E | GET_ORDER_REQUEST | C→S | 8 |
| 0x0F | ENABLE_MATCHING_REQUEST | C→S | 0 |
| 0x10 | DISABLE_MATCHING_REQUEST | C→S | 0 |
| 0x11 | SUBSCRIBE_ORDER_BOOK_REQUEST | C→S | 4 |
| 0x12 | SUBSCRIBE_ORDERS_REQUEST | C→S | 4 |
| 0x41 | SYMBOL_RESPONSE | S→C | 13 |
| 0x42 | ORDER_BOOK_RESPONSE | S→C | 72+ |
| 0x43 | ORDER_RESPONSE | S→C | 89 |
| 0x44 | SIMPLE_RESPONSE | S→C | 1 |
| 0x81 | ORDER_BOOK_UPDATE_EVENT | S→C(Push) | 40 |
| 0x82 | ORDER_UPDATE_EVENT | S→C(Push) | 105 |
| 0xC0 | HEARTBEAT_REQ | C→S | 0 |
| 0xC1 | HEARTBEAT_RESP | S→C | 0 |
| 0xCF | SHUTDOWN_NOTIFY | S→C | - |
| 0xD0 | AUTH_REQUEST | C→S | 88 |
| 0xD1 | AUTH_RESPONSE | S→C | 17 |
| 0xE0 | EVENT_ACK | C→S | 12 |
| 0xE1 | RECONCILE_REQUEST | C→S | 4 |
| 0xE2 | RECONCILE_RESPONSE | S→C | 37+ |

### A.2 ErrorCode 完整枚举

| 值 | 名称 | 说明 |
|----|------|------|
| 0 | OK | 成功 |
| 1 | SYMBOL_DUPLICATE | 品种重复 |
| 2 | SYMBOL_NOT_FOUND | 品种不存在 |
| 3 | ORDER_BOOK_DUPLICATE | 订单簿重复 |
| 4 | ORDER_BOOK_NOT_FOUND | 订单簿不存在 |
| 5 | ORDER_DUPLICATE | 订单重复 |
| 6 | ORDER_NOT_FOUND | 订单不存在 |
| 7 | ORDER_ID_INVALID | 订单ID无效 |
| 8 | ORDER_TYPE_INVALID | 订单类型无效 |
| 9 | ORDER_PARAMETER_INVALID | 订单参数无效 |
| 10 | ORDER_QUANTITY_INVALID | 订单数量无效 |
| 20 | NOT_AUTHENTICATED | 未认证 |
| 21 | NOT_AUTHORIZED | 无权限 |
| 22 | AUTH_EXPIRED | 认证过期 |
| 23 | INVALID_SIGNATURE | 签名无效 |
| 24 | REPLAY_DETECTED | 重放检测 |
| 25 | RATE_LIMITED | 限流 |
| 26 | CONNECTION_REJECTED | 连接拒绝 |
| 27 | SERVER_SHUTTING_DOWN | 服务器关闭中 |

### A.3 Flags 位定义

| 位 | 值 | 名称 | 说明 |
|----|----|------|------|
| 0 | 0x01 | REQUEST | 请求消息 |
| 1 | 0x02 | RESPONSE | 响应消息 |
| 2 | 0x04 | PUSH | 推送消息 |
| 3 | 0x08 | ERROR | 错误标志 |
| 4 | 0x10 | HEARTBEAT | 心跳消息 |

---

## 附录 B: 已知问题与待修复项

| 编号 | 问题 | 优先级 | 状态 |
|------|------|--------|------|
| BUG-001 | 余额增加/冻结/解冻操作失败 | P1 | 待修复 |
| BUG-002 | C++ HandleGetOrderBook 对不存在的OrderBook返回SIMPLE_RESPONSE而非ORDER_BOOK_RESPONSE | P2 | 待确认 |
| BUG-003 | Java CodecFactory.encodeMessageWithHeader 使用8字节头而非16字节 | P2 | 待修复 |
| BUG-004 | C++ 同步写(asio::write)阻塞事件循环 | P3 | 待优化 |
| BUG-005 | C++ 无请求-响应关联(仅靠MsgType) | P2 | 已修复(Sequence回显) |
| BUG-006 | Java @PostConstruct同步连接阻塞启动 | P1 | 已修复(异步连接) |
| BUG-007 | C++ DeleteOrder对不存在订单触发assert崩溃 | P1 | 已修复(移除assert) |
| BUG-008 | ErrorCode值C++/Java不一致 | P1 | 已修复(对齐到20-27) |
| BUG-009 | AuthRequest/AuthResponse结构C++/Java不一致 | P1 | 已修复(对齐) |
| BUG-010 | SHUTDOWN_NOTIFY值C++/Java不一致 | P2 | 已修复(0xCF) |

---

*文档结束 - CppTrader 后续开发工业级落地方案 v1.0*
