# HMAC 流程 Bug 修复与测试计划

## 代码审查发现的 Bug 清单

### 高危 Bug（4个）

| # | Bug                                         | 位置                                 | 影响                        |
| - | ------------------------------------------- | ---------------------------------- | ------------------------- |
| 1 | `Broadcast()` 不对消息进行 HMAC 签名                | server.cpp:116-132                 | 客户端验证失败/帧被丢弃；广播消息可被篡改不可检测 |
| 2 | `onMessageReceived()` HMAC 验证失败后仅打日志，消息仍被处理 | ProtocolClientService.java:471-483 | 篡改消息可绕过验证                 |
| 3 | `HandleAuth` 用 `memcmp` 比较签名，存在时序攻击漏洞       | request\_handler.cpp:824           | 攻击者可逐字节推断正确签名             |
| 4 | 核心数据结构多线程无锁保护                               | server.h 多个成员                      | 数据竞争导致 UB/崩溃/安全绕过         |

### 中危 Bug（4个）

| # | Bug                                   | 位置                                      | 影响                   |
| - | ------------------------------------- | --------------------------------------- | -------------------- |
| 5 | `AntiReplayChecker` 清理后 nonce 表仍可无限增长 | anti\_replay.cpp:41-47                  | 内存泄漏                 |
| 6 | 已认证连接 AUTH\_REQUEST 跳过 HMAC 验证        | server.cpp:281                          | 会话劫持风险               |
| 7 | 双重序列号计数器冲突                            | ProtocolClientService + ProtocolEncoder | 序列号重复导致签名失败          |
| 8 | `sessionToken` 竞态条件                   | ProtocolClientService.java:420          | 可能使用已失效的 session key |

### 低危 Bug（5个）

| #  | Bug                                | 位置                        | 影响          |
| -- | ---------------------------------- | ------------------------- | ----------- |
| 9  | `VerifyPrefix` 非恒定时间比较             | hmac.cpp:104-108          | 理论时序侧信道     |
| 10 | `BuildSignInput` 未包含 Magic/Version | hmac.cpp:30-55            | 协议降级攻击（低风险） |
| 11 | Session Token 仅 128 位              | request\_handler.cpp:837  | 非最优密钥长度     |
| 12 | FrameDecoder HMAC 验证失败静默丢弃，无上层通知   | FrameDecoder.java:115-129 | 应用层无法感知攻击   |
| 13 | `RateLimiter` 全局共享且无锁              | server.h:227              | 单连接饿死+数据竞争  |

***

## 实施步骤

### 第一阶段：修复高危 Bug

#### 步骤 1：修复 Bug 1 — Broadcast() 添加 HMAC 签名

**修改文件**: `source/protocol/server.cpp` → `Broadcast()`

将 `Broadcast()` 从使用 `_backend->broadcast()` 发送同一帧，改为遍历所有已认证连接并逐个签名发送：

```cpp
void ProtocolServer::Broadcast(const MsgHeader& header, const void* body, size_t body_len)
{
    if (!_backend)
        return;

    // 构建未签名的帧
    std::vector<uint8_t> frame(sizeof(MsgHeader) + body_len);
    std::memcpy(frame.data(), &header, sizeof(MsgHeader));
    if (body_len > 0 && body != nullptr)
        std::memcpy(frame.data() + sizeof(MsgHeader), body, body_len);

    // 遍历所有连接，按连接签名后发送
    for (const auto& [conn_id, authenticated] : _authenticated_connections)
    {
        auto* verifier = GetHmacVerifier(conn_id);
        if (verifier)
        {
            MsgHeader signed_header = header;
            signed_header.HmacPrefix = verifier->ComputePrefix(signed_header,
                reinterpret_cast<const uint8_t*>(body), body_len);
            std::vector<uint8_t> signed_frame(sizeof(MsgHeader) + body_len);
            std::memcpy(signed_frame.data(), &signed_header, sizeof(MsgHeader));
            if (body_len > 0 && body != nullptr)
                std::memcpy(signed_frame.data() + sizeof(MsgHeader), body, body_len);
            _backend->send(conn_id, signed_frame.data(), signed_frame.size());
        }
        else
        {
            _backend->send(conn_id, frame.data(), frame.size());
        }
    }
}
```

#### 步骤 2：修复 Bug 2 — onMessageReceived() 验证失败后丢弃消息

**修改文件**: `ProtocolClientService.java` → `onMessageReceived()`

在 HMAC 验证失败的 `if (!valid)` 块中添加 `return`：

```java
if (!valid) {
    log.warn("[RECV] HMAC prefix verification FAILED ...");
    return;  // 丢弃消息
}
```

#### 步骤 3：修复 Bug 3 — 使用恒定时间比较签名

**修改文件**: `source/protocol/request_handler.cpp` → `HandleAuth()`

替换 `memcmp` 为恒定时间比较：

```cpp
// 替换: bool signature_valid = (memcmp(computed.data(), request->Signature, 32) == 0);
uint8_t xor_result = 0;
for (size_t i = 0; i < 32; ++i)
    xor_result |= computed[i] ^ static_cast<uint8_t>(request->Signature[i]);
bool signature_valid = (xor_result == 0);
```

#### 步骤 4：修复 Bug 4 — 核心数据结构添加互斥锁保护

**修改文件**: `include/trader/protocol/server.h`

添加一个 `std::mutex _state_mutex` 用于保护以下共享数据：

* `_hmac_verifiers`

* `_authenticated_connections`

* `_order_book_subscriptions`

* `_order_subscriptions`

在 `GetHmacVerifier()`、`SetSessionKey()`、`RemoveSessionKey()`、`IsAuthenticated()`、`SetAuthenticated()`、`SubscribeOrderBook()`、`SubscribeOrders()`、`RemoveConnection()`、`BroadcastToSymbol()`、`Broadcast()` 中加锁。

### 第二阶段：修复中危 Bug

#### 步骤 5：修复 Bug 5 — AntiReplayChecker 清理后仍超限

**修改文件**: `source/protocol/anti_replay.cpp` → `CheckNonce()`

```cpp
if (recent_nonces_.size() >= MAX_NONCE_ENTRIES)
{
    CleanupLocked();
    if (recent_nonces_.size() >= MAX_NONCE_ENTRIES)
        return false;  // 清理后仍超限，拒绝新 nonce
}
```

#### 步骤 6：修复 Bug 6 — 已认证连接禁止重新认证

**修改文件**: `source/protocol/server.cpp` → `OnMessage()`

将已认证连接的 AUTH\_REQUEST 处理改为拒绝：

```cpp
else  // is_authenticated == true
{
    // 已认证连接不允许重新认证
    if (header.Type == MsgType::AUTH_REQUEST)
    {
        SimpleResponse response(ErrorCode::NOT_AUTHENTICATED);
        MsgHeader resp_header(MsgType::SIMPLE_RESPONSE, Flags::RESPONSE | Flags::ERROR, sizeof(response));
        SendResponse(conn_id, resp_header, &response, sizeof(response));
        return;
    }

    auto* verifier = GetHmacVerifier(conn_id);
    if (verifier && !verifier->VerifyPrefix(header, body, body_len))
    {
        // HMAC 验证失败...
    }
}
```

#### 步骤 7：修复 Bug 7 — 统一序列号计数器

**修改文件**: `ProtocolClientService.java`

在 `propagateSessionKey()` 方法中，将 `ProtocolClientService.sequenceCounter` 的当前值同步给 `ProtocolEncoder`：

1. 在 `ProtocolEncoder` 中添加 `setSequenceBase(int base)` 方法
2. 在 `propagateSessionKey()` 中调用 `encoder.setSequenceBase(sequenceCounter.get())`

```java
// ProtocolEncoder.java
public void setSequenceBase(int base) {
    sequenceCounter.set(base);
}

// ProtocolClientService.java propagateSessionKey()
private void propagateSessionKey() {
    if (backend instanceof NettyTcpBackend) {
        ProtocolEncoder encoder = ((NettyTcpBackend) backend).getProtocolEncoder();
        if (encoder != null && sessionToken != null) {
            encoder.setSessionKey(sessionToken);
            encoder.setSequenceBase(sequenceCounter.get());
        }
    }
}
```

#### 步骤 8：修复 Bug 8 — sessionToken 竞态条件

**修改文件**: `ProtocolClientService.java`

在 `sendHeartbeat()` 和 `sendHeartbeatResponse()` 中将 `sessionToken` 读取到局部变量：

```java
byte[] token = this.sessionToken;
if (token != null) {
    short prefix = HmacSigner.computeHmacPrefix(token, seq, ...);
    // ...
}
```

### 第三阶段：修复低危 Bug

#### 步骤 9：修复 Bug 9 — VerifyPrefix 使用恒定时间比较

**修改文件**: `source/protocol/hmac.cpp` → `VerifyPrefix()`

```cpp
bool HmacVerifier::VerifyPrefix(const MsgHeader& header, const uint8_t* body, size_t body_len)
{
    uint16_t expected = ComputePrefix(header, body, body_len);
    return (expected ^ header.HmacPrefix) == 0;
}
```

#### 步骤 10：修复 Bug 11 — Session Token 增至 32 字节

**修改文件**: `source/protocol/request_handler.cpp` → `HandleAuth()`

```cpp
// 替换: std::array<uint8_t, 16> session_token;
std::array<uint8_t, 32> session_token;
```

同时修改 `AuthResponse.SessionToken` 大小从 16 到 32 字节（需同步修改 `message.h` 和 `ProtocolConstants.java` 中的 `AUTH_SESSION_TOKEN_SIZE`）。

**注意**: 此修改会破坏协议兼容性，需要同步更新 C++ 和 Java 两端。如果当前已有生产环境使用 16 字节 token，需要考虑版本协商。建议在本次修复中一并更新协议版本号（Version 从 2 升至 3）。

#### 步骤 11：修复 Bug 12 — FrameDecoder 添加 HMAC 验证失败回调

**修改文件**: `FrameDecoder.java`

添加回调接口：

```java
public interface HmacVerificationCallback {
    void onHmacVerificationFailed(short hmacPrefix, int sequence, byte msgType);
}

private HmacVerificationCallback hmacCallback;

public void setHmacVerificationCallback(HmacVerificationCallback callback) {
    this.hmacCallback = callback;
}
```

在验证失败时调用回调：

```java
if (!valid) {
    if (hmacCallback != null)
        hmacCallback.onHmacVerificationFailed(pendingHmacPrefix, pendingSequence, pendingMsgType);
    // ... 原有丢弃逻辑
}
```

### 第四阶段：编写 HMAC 单元测试

#### 步骤 12：C++ HmacVerifier 单元测试

**新建文件**: `tests/test_hmac.cpp`（使用 Catch2 框架，与现有测试一致）

测试用例：

1. `ComputePrefix` 返回非零值（非零密钥+非零输入）
2. `VerifyPrefix` 正确签名通过验证
3. `VerifyPrefix` 篡改 body 后验证失败
4. `VerifyPrefix` 篡改 sequence 后验证失败
5. `VerifyPrefix` 篡改 HmacPrefix 后验证失败
6. `BuildSignInput` 字节序正确（小端序）
7. `HmacSHA256` 与 Java 端结果一致（交叉验证）
8. 空 body 消息签名正确
9. 不同密钥产生不同签名

#### 步骤 13：C++ AntiReplayChecker 单元测试

**新建文件**: `tests/test_anti_replay.cpp`

测试用例：

1. 首次 nonce 通过
2. 重复 nonce 被拒绝
3. 过期时间戳被拒绝
4. 有效时间戳通过
5. 超过 MAX\_NONCE\_ENTRIES 后清理+拒绝
6. Cleanup 清理过期条目

#### 步骤 14：Java HmacSigner 单元测试

**新建文件**: `src/test/java/com/cpptrader/admin/protocol/security/HmacSignerTest.java`（使用 JUnit 5）

测试用例：

1. `computeHmacPrefix` 返回非零值
2. `verifyHmacPrefix` 正确签名通过
3. `verifyHmacPrefix` 篡改后失败
4. `buildSignInput` 字节序正确
5. 与 C++ 端交叉验证（使用固定密钥和输入，验证两端结果一致）
6. 空 body 签名正确
7. 不同密钥产生不同签名

#### 步骤 15：C++ HandleAuth 集成测试

**新建文件**: `tests/test_auth.cpp`

测试用例：

1. 正确凭证认证成功，返回 SessionToken
2. 未知 ApiKeyId 认证失败
3. 错误签名认证失败（INVALID\_SIGNATURE）
4. 过期时间戳认证失败（AUTH\_EXPIRED）
5. 重放 nonce 认证失败（REPLAY\_DETECTED）
6. 空 ApiKeyId 认证失败

### 第五阶段：构建验证

#### 步骤 16：C++ 构建并运行测试

```bash
cd /root/dev/cpptrader_v0_dev/build
cmake .. && make -j$(nproc)
./cpptrader-tests
```

#### 步骤 17：Java 构建并运行测试

```bash
cd /root/dev/java-admin
mvn test
```

***

## 涉及文件清单

### C++ 修改

| 文件                                    | 修改内容                                       |
| ------------------------------------- | ------------------------------------------ |
| `source/protocol/server.cpp`          | Broadcast() 按连接签名；OnMessage 禁止已认证重认证；各方法加锁 |
| `include/trader/protocol/server.h`    | 添加 \_state\_mutex                          |
| `source/protocol/request_handler.cpp` | 恒定时间比较签名；Session Token 32 字节               |
| `source/protocol/hmac.cpp`            | VerifyPrefix 恒定时间比较                        |
| `source/protocol/anti_replay.cpp`     | CheckNonce 清理后仍超限则拒绝                       |
| `include/trader/protocol/message.h`   | AuthResponse.SessionToken 16→32            |
| `CMakeLists.txt`                      | 添加新测试文件                                    |

### C++ 新建

| 文件                           | 说明                     |
| ---------------------------- | ---------------------- |
| `tests/test_hmac.cpp`        | HmacVerifier 单元测试      |
| `tests/test_anti_replay.cpp` | AntiReplayChecker 单元测试 |
| `tests/test_auth.cpp`        | HandleAuth 集成测试        |

### Java 修改

| 文件                           | 修改内容                                |
| ---------------------------- | ----------------------------------- |
| `ProtocolClientService.java` | 验证失败 return；sessionToken 局部变量；序列号同步 |
| `ProtocolEncoder.java`       | 添加 setSequenceBase()                |
| `FrameDecoder.java`          | 添加 HMAC 验证失败回调                      |
| `ProtocolConstants.java`     | AUTH\_SESSION\_TOKEN\_SIZE 16→32    |

### Java 新建

| 文件                    | 说明              |
| --------------------- | --------------- |
| `HmacSignerTest.java` | HmacSigner 单元测试 |

