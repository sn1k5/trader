# HMAC 签名方案实施计划

## 概述

当前协议头中 `HmacPrefix` 字段（2字节）始终为 0，未实际使用。需实施完整的 HMAC 签名方案，使认证后的所有消息都经过 HMAC-SHA256 签名，HmacPrefix 取签名前 2 字节，接收方重新计算并验证。

## 签名流程设计

```
签名输入: Sequence(4) + MsgType(1) + Flags(1) + Length(2) + Body(N)
签名算法: HMAC-SHA256(SessionToken, input)
HmacPrefix: 取 HMAC-SHA256 结果的前 2 字节（小端序 uint16_t）
验证方: 重新计算签名，比较前 2 字节是否一致
```

**注意**: AUTH\_REQUEST 和 HEARTBEAT\_REQ 消息在认证前发送，HmacPrefix 保持为 0，不参与签名。

***

## 实施步骤

### 第一阶段：C++ 服务端 — HMAC 核心模块

#### 步骤 1：创建 HmacVerifier 类

**新建文件**: `include/trader/protocol/hmac.h`

```cpp
class HmacVerifier {
public:
    explicit HmacVerifier(const uint8_t* session_key, size_t key_len);
    uint16_t ComputePrefix(const MsgHeader& header, const uint8_t* body, size_t body_len);
    bool VerifyPrefix(const MsgHeader& header, const uint8_t* body, size_t body_len);
    std::array<uint8_t, 32> ComputeFull(const MsgHeader& header, const uint8_t* body, size_t body_len);
private:
    std::vector<uint8_t> session_key_;
    static std::vector<uint8_t> BuildSignInput(const MsgHeader& header, const uint8_t* body, size_t body_len);
};
```

**新建文件**: `source/protocol/hmac.cpp`

* `BuildSignInput`: 拼接 `Sequence(4 LE) + MsgType(1) + Flags(1) + Length(2 LE) + Body(N)`

* `ComputeFull`: 使用 OpenSSL HMAC-SHA256 计算完整 32 字节签名

* `ComputePrefix`: 调用 ComputeFull，取前 2 字节作为 uint16\_t（小端序）

* `VerifyPrefix`: 重新计算前缀并与 header.HmacPrefix 比较

**依赖**: OpenSSL（HMAC 函数）— 需在 CMakeLists.txt 中添加 `find_package(OpenSSL REQUIRED)` 并链接

#### 步骤 2：增强 HandleAuth — 完整认证验证

**修改文件**: `source/protocol/request_handler.cpp` → `HandleAuth()`

当前 HandleAuth 仅检查 `ApiKeyId[0] != '\0'`，需增强为：

1. **时间戳验证**: 检查 `Timestamp` 在当前时间 ±30 秒内
2. **HMAC 签名验证**: 使用 ApiKeyId 对应的 ApiKeySecret 重新计算 `HMAC-SHA256(ApiKeySecret, timestampHex + nonceHex + apiKeyId)`，与请求中的 Signature 比对
3. **Nonce 去重**: 检查 Nonce 是否已使用（本地滑动窗口缓存）
4. **生成随机 SessionToken**: 使用 CSPRNG 生成 16 字节随机令牌
5. **存储会话密钥**: 将 SessionToken 与 conn\_id 关联存储，用于后续 HmacPrefix 验证

**新增数据结构**:

* `server.h` 中添加 `std::unordered_map<uint16_t, std::vector<uint8_t>> _session_keys;` 存储 conn\_id → SessionToken 映射

* `server.h` 中添加 `std::unordered_map<uint16_t, HmacVerifier> _hmac_verifiers;` 存储 conn\_id → HmacVerifier 实例

* `server.h` 中添加 API Key 存储接口（从配置文件加载 ApiKeyId → ApiKeySecret 映射）

**新增方法**:

* `ProtocolServer::SetSessionKey(uint16_t conn_id, const uint8_t* key, size_t key_len)`

* `ProtocolServer::GetHmacVerifier(uint16_t conn_id)` → 返回 HmacVerifier\*

* `ProtocolServer::RemoveSessionKey(uint16_t conn_id)` （断开连接时调用）

#### 步骤 3：创建 AntiReplayChecker 防重放模块

**新建文件**: `include/trader/protocol/anti_replay.h`

```cpp
class AntiReplayChecker {
public:
    bool CheckNonce(const uint8_t* nonce, size_t nonce_len, uint64_t timestamp);
    bool CheckTimestamp(uint64_t timestamp, int64_t tolerance_ms = 30000);
    void Cleanup(); // 清理过期条目
private:
    static constexpr size_t WINDOW_SIZE = 1024;
    std::unordered_set<std::vector<uint8_t>> recent_nonces_;
    std::mutex mutex_;
};
```

**新建文件**: `source/protocol/anti_replay.cpp`

* `CheckNonce`: 将 nonce 插入 set，若已存在则返回 false

* `CheckTimestamp`: 比较时间戳与当前时间的差值

* `Cleanup`: 定期清理超过 60 秒的 nonce 条目

#### 步骤 4：OnMessage 中添加 HmacPrefix 验证

**修改文件**: `source/protocol/server.cpp` → `OnMessage()`

在认证检查之后、消息分发之前，添加 HmacPrefix 验证逻辑：

```cpp
// 在 _auth_enabled && is_authenticated 条件下
if (_auth_enabled && is_authenticated) {
    auto* verifier = GetHmacVerifier(conn_id);
    if (verifier && !verifier->VerifyPrefix(header, body, body_len)) {
        // HMAC 验证失败
        SimpleResponse response(ErrorCode::INVALID_SIGNATURE);
        MsgHeader resp_header(MsgType::SIMPLE_RESPONSE, Flags::RESPONSE | Flags::ERROR, sizeof(response));
        SendResponse(conn_id, resp_header, &response, sizeof(response));
        return;
    }
}
```

**豁免消息**: AUTH\_REQUEST、HEARTBEAT\_REQ/RESP 在认证前或无 body 时 HmacPrefix 为 0，跳过验证。

#### 步骤 5：SendResponse/Broadcast 中添加 HmacPrefix 签名

**修改文件**: `source/protocol/server.cpp` → `SendResponse()` / `Broadcast()` / `BroadcastToSymbol()`

在发送响应/广播消息前，对已认证连接的消息签名：

```cpp
void ProtocolServer::SendResponse(uint16_t conn_id, const MsgHeader& header, const void* body, size_t body_len) {
    MsgHeader signed_header = header;
    auto* verifier = GetHmacVerifier(conn_id);
    if (verifier) {
        signed_header.HmacPrefix = verifier->ComputePrefix(signed_header, (const uint8_t*)body, body_len);
    }
    // ... 原有发送逻辑
}
```

#### 步骤 6：CMakeLists.txt 添加 OpenSSL 依赖

**修改文件**: `CMakeLists.txt`

```cmake
find_package(OpenSSL REQUIRED)
target_link_libraries(cpptrader ${LINKLIBS} Threads::Threads OpenSSL::SSL OpenSSL::Crypto)
```

***

### 第二阶段：Java 客户端 — HMAC 签名与验证

#### 步骤 7：创建 HmacSigner 工具类

**新建文件**: `src/main/java/com/cpptrader/admin/protocol/security/HmacSigner.java`

```java
public class HmacSigner {
    private static final String HMAC_ALGORITHM = "HmacSHA256";

    public static short computeHmacPrefix(byte[] sessionKey, int sequence, byte msgType, byte flags, short length, byte[] body);
    public static byte[] computeFullHmac(byte[] sessionKey, int sequence, byte msgType, byte flags, short length, byte[] body);
    public static boolean verifyHmacPrefix(byte[] sessionKey, short expectedPrefix, int sequence, byte msgType, byte flags, short length, byte[] body);

    private static byte[] buildSignInput(int sequence, byte msgType, byte flags, short length, byte[] body);
}
```

核心逻辑：

* `buildSignInput`: 拼接 `Sequence(4 LE) + MsgType(1) + Flags(1) + Length(2 LE) + Body(N)`

* `computeFullHmac`: 使用 `javax.crypto.Mac` 计算 HMAC-SHA256

* `computeHmacPrefix`: 取签名前 2 字节作为 short（小端序）

* `verifyHmacPrefix`: 重新计算并比较

#### 步骤 8：修改 ProtocolEncoder — 发送时签名

**修改文件**: `src/main/java/com/cpptrader/admin/protocol/client/ProtocolEncoder.java`

```java
public class ProtocolEncoder extends MessageToByteEncoder<ProtocolMessage> {
    private final AtomicInteger sequenceCounter = new AtomicInteger(0);
    private volatile byte[] sessionKey = null;

    public void setSessionKey(byte[] key) { this.sessionKey = key; }

    @Override
    protected void encode(ChannelHandlerContext ctx, ProtocolMessage msg, ByteBuf out) throws Exception {
        msg.setSequence(sequenceCounter.incrementAndGet());
        if (sessionKey != null) {
            short prefix = HmacSigner.computeHmacPrefix(sessionKey, msg.getSequence(),
                msg.getMsgType(), msg.getFlags(), (short) msg.getBodySize(), msg.getBodyBytes());
            msg.setHmacPrefix(prefix);
        } else {
            msg.setHmacPrefix((short) 0);
        }
        byte[] data = msg.toBytes();
        out.writeBytes(data);
    }
}
```

**注意**: `ProtocolMessage` 需要新增 `getBodyBytes()` 方法以获取编码后的 body 字节数组供签名使用。

#### 步骤 9：修改 ProtocolMessage — 添加 getBodyBytes() 支持

**修改文件**: `src/main/java/com/cpptrader/admin/protocol/ProtocolMessage.java`

新增方法：

```java
public byte[] getBodyBytes() {
    ByteBuffer buf = ByteBuffer.allocate(getBodySize());
    buf.order(ByteOrder.LITTLE_ENDIAN);
    encode(buf);
    return buf.array();
}
```

#### 步骤 10：修改 ProtocolClientService — 认证后存储 SessionKey 并签名

**修改文件**: `src/main/java/com/cpptrader/admin/protocol/client/ProtocolClientService.java`

1. 认证成功后，将 `sessionToken` 传递给 `ProtocolEncoder.setSessionKey()`
2. 手动构建认证请求和心跳消息时，也需要签名（认证请求除外）
3. 接收消息时验证 HmacPrefix

具体修改：

* `authenticate()` 方法中，认证成功后调用 `protocolEncoder.setSessionKey(sessionToken)`

* `sendHeartbeat()` 方法中，认证后对心跳消息签名

* `onMessageReceived()` 方法中，认证后验证服务端响应的 HmacPrefix

#### 步骤 11：修改 FrameDecoder — 接收时验证 HmacPrefix

**修改文件**: `src/main/java/com/cpptrader/admin/protocol/FrameDecoder.java`

添加 HmacPrefix 验证支持：

```java
private byte[] sessionKey = null;

public void setSessionKey(byte[] key) { this.sessionKey = key; }

// 在帧解析完成后，验证 HmacPrefix
private boolean verifyFrameHmac(short hmacPrefix, int sequence, byte msgType, byte flags, short bodyLen, byte[] body) {
    if (sessionKey == null) return true; // 未认证，跳过验证
    return HmacSigner.verifyHmacPrefix(sessionKey, hmacPrefix, sequence, msgType, flags, bodyLen, body);
}
```

#### 步骤 12：修改 ProtocolValidator — 添加 HmacPrefix 验证

**修改文件**: `src/main/java/com/cpptrader/admin/protocol/validation/ProtocolValidator.java`

在 `validateHeader()` 和 `validateCompleteMessage()` 中添加 HmacPrefix 验证逻辑（可选，作为警告级别）：

* 如果消息类型不是 AUTH/HEARTBEAT 且 HmacPrefix 为 0，发出警告

* 提供独立的 `validateHmacPrefix()` 方法供外部调用

***

### 第三阶段：集成与测试

#### 步骤 13：C++ API Key 配置

**修改文件**: `source/protocol/server_main.cpp` 或新增配置文件

* 添加 API Key 配置加载逻辑（从 JSON/INI 文件读取 ApiKeyId → ApiKeySecret 映射）

* 在 `RequestHandler` 构造函数中传入 API Key 存储

#### 步骤 14：端到端集成测试

* 验证 Java 客户端签名 → C++ 服务端验证（通过）

* 验证 C++ 服务端签名 → Java 客户端验证（通过）

* 验证篡改消息 → 验证失败（INVALID\_SIGNATURE）

* 验证未认证消息 → HmacPrefix 为 0（正常工作）

* 验证重放攻击 → Nonce 去重拒绝

***

## 涉及文件清单

### C++ 端（新建）

| 文件                                      | 说明                                   |
| --------------------------------------- | ------------------------------------ |
| `include/trader/protocol/hmac.h`        | HmacVerifier 类声明                     |
| `source/protocol/hmac.cpp`              | HmacVerifier 实现（OpenSSL HMAC-SHA256） |
| `include/trader/protocol/anti_replay.h` | AntiReplayChecker 类声明                |
| `source/protocol/anti_replay.cpp`       | AntiReplayChecker 实现                 |

### C++ 端（修改）

| 文件                                    | 修改内容                                             |
| ------------------------------------- | ------------------------------------------------ |
| `include/trader/protocol/server.h`    | 添加 session\_keys、hmac\_verifiers 存储，新增方法         |
| `source/protocol/server.cpp`          | OnMessage 添加 HMAC 验证，SendResponse/Broadcast 添加签名 |
| `source/protocol/request_handler.cpp` | HandleAuth 增强为完整认证验证                             |
| `CMakeLists.txt`                      | 添加 OpenSSL 依赖                                    |

### Java 端（新建）

| 文件                                  | 说明            |
| ----------------------------------- | ------------- |
| `protocol/security/HmacSigner.java` | HMAC 签名/验证工具类 |

### Java 端（修改）

| 文件                           | 修改内容                     |
| ---------------------------- | ------------------------ |
| `ProtocolMessage.java`       | 添加 getBodyBytes() 方法     |
| `ProtocolEncoder.java`       | 发送时使用 HmacSigner 签名      |
| `ProtocolClientService.java` | 认证后存储 SessionKey，签名/验证消息 |
| `FrameDecoder.java`          | 接收时验证 HmacPrefix         |
| `ProtocolValidator.java`     | 添加 HmacPrefix 验证方法       |

***

## 关键设计决策

1. **签名输入不包含 HmacPrefix 自身**: 签名输入为 `Sequence + MsgType + Flags + Length + Body`，HmacPrefix 字段不参与签名计算（否则循环依赖）
2. **SessionToken 作为 HMAC 密钥**: 认证成功后服务端生成的随机 SessionToken 直接用作后续消息的 HMAC 密钥
3. **2 字节前缀 vs 完整签名**: 采用 2 字节前缀方案（碰撞概率 1/65536），兼顾安全性和性能；完整 32 字节签名作为可选增强
4. **豁免消息**: AUTH\_REQUEST 和认证前的 HEARTBEAT\_REQ 的 HmacPrefix 为 0，不参与签名验证
5. **向后兼容**: 通过 `_auth_enabled` 开关控制，默认关闭，开启后才强制 HMAC 验证

