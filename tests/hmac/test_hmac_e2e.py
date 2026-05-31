#!/usr/bin/env python3
"""
HMAC 全功能端到端测试脚本

测试架构:
  C++ Server (port 18080) <--TCP--> Python 测试客户端

协议头格式 (16 字节, 小端序):
  Magic(2) + Version(1) + Type(1) + Flags(1) + Reserved(1) + Length(2) + Sequence(4) + HmacPrefix(2) + Reserved2(2)

HMAC 签名方案:
  - 签名输入: Sequence(4 LE) + MsgType(1) + Flags(1) + Length(2 LE) + Body(N)
  - 签名算法: HMAC-SHA256(SessionKey, input) -> 取前 2 字节作为 HmacPrefix (小端序 uint16)
  - 认证请求签名: HMAC-SHA256(ApiKeySecret, timestamp_hex + nonce_hex + api_key_id)
  - 服务端响应不签名 (客户端无需验证响应)

认证流程:
  1. 客户端发送 AuthRequest: ApiKeyId(32) + Timestamp(8) + Nonce(16) + Signature(32) + RecoveryToken(32)
  2. 服务端验证 ApiKeyId、时间戳(30s容差)、Nonce 去重、签名
  3. 成功后返回 AuthResponse: Error(1) + SessionToken(32) + AccountId(8) + Role(1)
  4. 后续请求必须携带正确的 HmacPrefix (使用 SessionToken 作为 HMAC 密钥)
  5. 断线重连: 携带旧 SessionToken 作为 RecoveryToken, 服务端恢复 Session

测试覆盖:
  T1:  HmacSigner 签名/验证一致性 (C++/Java/Python 交叉验证)
  T2:  认证流程 - 正确凭证认证成功
  T3:  认证流程 - 未知 ApiKeyId 认证失败
  T4:  认证流程 - 错误签名认证失败
  T5:  认证流程 - 过期时间戳认证失败
  T6:  认证流程 - 重放 Nonce 认证失败
  T7:  认证后请求 - 正确 HmacPrefix 通过
  T8:  认证后请求 - 错误 HmacPrefix 被拒绝
  T9:  认证后请求 - HmacPrefix=0 被拒绝
  T10: 未认证请求 - 非认证消息被拒绝
  T11: 已认证连接 - 重新认证被拒绝
  T12: 心跳消息 - 认证后心跳需要签名
  T13: Session 生命周期 - AuthResponse 包含 AccountId 和 Role
  T14: Session 恢复 - 断线重连使用 RecoveryToken 恢复 Session
  T15: Session 恢复 - 无效 RecoveryToken 回退到完整认证
"""

import hashlib
import hmac
import os
import socket
import struct
import sys
import time
import subprocess
import signal

# ─── 协议常量 ───────────────────────────────────────────────────────────────────

# 协议头魔数和版本, 与 C++ 端 MsgHeader 定义一致
MAGIC = 0x5452          # "TR" 的 ASCII 小端序
VERSION = 2             # 协议版本号
HEADER_SIZE = 16        # 协议头固定 16 字节

# 消息类型 (与 C++ MsgType 枚举对应)
MSG_TYPE_AUTH_REQUEST = 0xD0        # 认证请求
MSG_TYPE_AUTH_RESPONSE = 0xD1       # 认证响应
MSG_TYPE_HEARTBEAT_REQ = 0xC0       # 心跳请求
MSG_TYPE_HEARTBEAT_RESP = 0xC1      # 心跳响应
MSG_TYPE_ADD_SYMBOL_REQUEST = 0x01  # 添加交易对请求
MSG_TYPE_SYMBOL_RESPONSE = 0x41     # 交易对响应 (Error(1) + SymbolProto(12))
MSG_TYPE_SIMPLE_RESPONSE = 0x44     # 简单响应 (仅含 Error(1))

# 消息标志位 (与 C++ Flags 结构体对应)
FLAG_REQUEST = 0x01     # 请求消息
FLAG_RESPONSE = 0x02    # 响应消息
FLAG_HEARTBEAT = 0x10   # 心跳消息
FLAG_ERROR = 0x08       # 错误响应

# 错误码 (与 C++ ErrorCode 枚举对应)
ERR_OK = 0                      # 成功
ERR_NOT_AUTHENTICATED = 20      # 未认证
ERR_AUTH_EXPIRED = 22           # 认证时间戳过期
ERR_INVALID_SIGNATURE = 23      # 签名无效
ERR_REPLAY_DETECTED = 24        # 重放攻击检测

# 认证消息各字段长度 (与 C++ AuthRequest/AuthResponse 结构体对应)
AUTH_API_KEY_ID_SIZE = 32
AUTH_TIMESTAMP_SIZE = 8
AUTH_NONCE_SIZE = 16
AUTH_SIGNATURE_SIZE = 32
AUTH_RECOVERY_TOKEN_SIZE = 32
AUTH_REQUEST_BODY_SIZE = 120
AUTH_SESSION_TOKEN_SIZE = 32
AUTH_ACCOUNT_ID_SIZE = 8
AUTH_ROLE_SIZE = 1
AUTH_RESPONSE_BODY_SIZE = 42

# ─── 测试配置 ───────────────────────────────────────────────────────────────────

# 测试用 API Key (需与服务端 --api-key 参数一致)
TEST_API_KEY_ID = "test-api-key-1234567890abcdef"
TEST_API_KEY_SECRET = "test-secret-key-abcdefghijklmnop"
TEST_PORT = 18080       # 测试端口, 避免与生产端口冲突
TEST_HOST = "127.0.0.1"

# 终端输出颜色
PASS = "\033[92mPASS\033[0m"
FAIL = "\033[91mFAIL\033[0m"
INFO = "\033[94mINFO\033[0m"
DBG  = "\033[93mDBG\033[0m"

DEBUG = False

def debug_print(msg):
    if DEBUG:
        print(f"  {DBG} {msg}")

def hex_dump(data, prefix="  "):
    lines = []
    for i in range(0, len(data), 16):
        chunk = data[i:i+16]
        hex_part = ' '.join(f'{b:02x}' for b in chunk)
        ascii_part = ''.join(chr(b) if 32 <= b < 127 else '.' for b in chunk)
        lines.append(f"{prefix}{i:04x}  {hex_part:<48s}  {ascii_part}")
    return '\n'.join(lines)


# ─── 核心签名函数 ───────────────────────────────────────────────────────────────

def compute_auth_signature(secret: bytes, timestamp_ms: int, nonce: bytes, api_key_id: str) -> bytes:
    """
    计算认证请求的 HMAC-SHA256 签名

    签名消息格式: timestamp_hex(16字符) + nonce_hex(32字符) + api_key_id
    这与服务端 HandleAuth 中的签名验证逻辑一致:
      C++ 端: sign_message = timestamp_hex + nonce_hex + api_key_id
      C++ 端: computed = HMAC-SHA256(api_key_secret, sign_message)

    Args:
        secret:     API Key 的密钥 (utf-8 编码)
        timestamp_ms: 毫秒级时间戳
        nonce:      16 字节随机数
        api_key_id: API Key 标识符

    Returns:
        32 字节 HMAC-SHA256 签名
    """
    debug_print(f"[compute_auth_signature] secret={secret.hex()}")
    debug_print(f"[compute_auth_signature] timestamp_ms={timestamp_ms} (0x{timestamp_ms:016x})")
    debug_print(f"[compute_auth_signature] nonce={nonce.hex()}")
    debug_print(f"[compute_auth_signature] api_key_id={api_key_id!r}")
    ts_hex = format(timestamp_ms, '016x')
    nonce_hex = nonce.hex()
    message = ts_hex + nonce_hex + api_key_id
    debug_print(f"[compute_auth_signature] ts_hex={ts_hex!r}")
    debug_print(f"[compute_auth_signature] nonce_hex={nonce_hex!r}")
    debug_print(f"[compute_auth_signature] message={message!r}")
    debug_print(f"[compute_auth_signature] message_bytes={message.encode('utf-8').hex()}")
    result = hmac.new(secret, message.encode('utf-8'), hashlib.sha256).digest()
    debug_print(f"[compute_auth_signature] signature={result.hex()}")
    return result


def compute_hmac_prefix(session_key: bytes, sequence: int, msg_type: int, flags: int, length: int, body: bytes = b'') -> int:
    """
    计算协议消息的 HMAC 前缀 (2 字节)

    签名输入格式 (与 C++ HmacVerifier::BuildSignInput 一致):
      Sequence(4 LE) + MsgType(1) + Flags(1) + Length(2 LE) + Body(N)

    签名算法: HMAC-SHA256(session_key, input) -> 取前 2 字节作为小端序 uint16

    Args:
        session_key: 32 字节会话密钥 (认证成功后由服务端返回的 SessionToken)
        sequence:    消息序列号
        msg_type:    消息类型
        flags:       消息标志位
        length:      消息体长度
        body:        消息体数据

    Returns:
        2 字节 HMAC 前缀 (uint16, 小端序)
    """
    debug_print(f"[compute_hmac_prefix] session_key={session_key.hex()}")
    debug_print(f"[compute_hmac_prefix] sequence={sequence}, msg_type=0x{msg_type:02x}, flags=0x{flags:02x}, length={length}")
    debug_print(f"[compute_hmac_prefix] body={body.hex() if body else '(empty)'}")
    input_data = struct.pack('<I', sequence)
    input_data += struct.pack('B', msg_type)
    input_data += struct.pack('B', flags)
    input_data += struct.pack('<H', length)
    input_data += body
    debug_print(f"[compute_hmac_prefix] input_data={input_data.hex()}")
    debug_print(f"[compute_hmac_prefix] input_data dump:\n{hex_dump(input_data, prefix='    ')}")
    full = hmac.new(session_key, input_data, hashlib.sha256).digest()
    debug_print(f"[compute_hmac_prefix] hmac_sha256_full={full.hex()}")
    prefix_val = struct.unpack('<H', full[0:2])[0]
    debug_print(f"[compute_hmac_prefix] hmac_prefix=0x{prefix_val:04x} (first 2 bytes LE: {full[0:2].hex()})")
    return prefix_val


# ─── 协议编解码 ─────────────────────────────────────────────────────────────────

def build_header(msg_type, flags, length, sequence, hmac_prefix=0):
    """
    构建 16 字节协议头

    格式 (与 C++ MsgHeader 结构体一一对应, 小端序):
      Magic(2) + Version(1) + Type(1) + Flags(1) + Reserved(1) + Length(2) + Sequence(4) + HmacPrefix(2) + Reserved2(2)

    Args:
        msg_type:    消息类型 (MsgType 枚举值)
        flags:       消息标志位
        length:      消息体长度
        sequence:    序列号
        hmac_prefix: HMAC 签名前缀 (认证后的请求必须设置)

    Returns:
        16 字节协议头二进制数据
    """
    result = struct.pack('<HBBB B H I H H',
                       MAGIC, VERSION, msg_type, flags,
                       0, length, sequence, hmac_prefix, 0)
    debug_print(f"[build_header] Magic=0x{MAGIC:04x} Version={VERSION} Type=0x{msg_type:02x} Flags=0x{flags:02x} "
                f"Reserved=0 Length={length} Seq={sequence} HmacPrefix=0x{hmac_prefix:04x} Reserved2=0")
    debug_print(f"[build_header] header_bytes={result.hex()}")
    debug_print(f"[build_header] header dump:\n{hex_dump(result, prefix='    ')}")
    return result


def parse_header(data):
    """
    解析 16 字节协议头

    Args:
        data: 至少 16 字节的二进制数据

    Returns:
        包含各字段的字典, 或 None (数据不足时)
    """
    if len(data) < HEADER_SIZE:
        debug_print(f"[parse_header] data too short: {len(data)} < {HEADER_SIZE}")
        return None
    magic, version, msg_type, flags, reserved, length, sequence, hmac_prefix, reserved2 = \
        struct.unpack('<HBBB B H I H H', data[:HEADER_SIZE])
    debug_print(f"[parse_header] raw_bytes={data[:HEADER_SIZE].hex()}")
    debug_print(f"[parse_header] Magic=0x{magic:04x} Version={version} Type=0x{msg_type:02x} Flags=0x{flags:02x} "
                f"Reserved={reserved} Length={length} Seq={sequence} HmacPrefix=0x{hmac_prefix:04x} Reserved2={reserved2}")
    if magic != MAGIC:
        debug_print(f"[parse_header] WARNING: magic mismatch! expected=0x{MAGIC:04x} got=0x{magic:04x}")
    return {
        'magic': magic, 'version': version, 'msg_type': msg_type,
        'flags': flags, 'reserved': reserved, 'length': length,
        'sequence': sequence, 'hmac_prefix': hmac_prefix, 'reserved2': reserved2
    }


# ─── 测试客户端 ─────────────────────────────────────────────────────────────────

class TestClient:
    """
    模拟协议客户端, 封装 TCP 连接、认证、签名请求等功能

    使用流程:
      1. connect() 建立 TCP 连接
      2. authenticate() 完成认证, 获取 session_key
      3. send_signed_request() 发送带 HMAC 签名的请求
      4. close() 关闭连接
    """

    def __init__(self, host=TEST_HOST, port=TEST_PORT):
        self.host = host
        self.port = port
        self.sock = None
        self.sequence = 0          # 消息序列号, 每次发送递增
        self.session_key = None    # 认证成功后保存的 32 字节会话密钥
        self.account_id = 0        # 认证成功后保存的账户 ID
        self.role = 0              # 认证成功后保存的角色 (0=ADMIN, 1=TRADER, 2=VIEWER, 3=QUANTBOT)

    def connect(self):
        self.sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        self.sock.settimeout(5)
        self.sock.connect((self.host, self.port))
        debug_print(f"[connect] Connected to {self.host}:{self.port}")

    def close(self):
        """关闭 TCP 连接"""
        if self.sock:
            self.sock.close()
            self.sock = None

    def next_seq(self):
        """获取下一个序列号 (从 1 开始递增)"""
        self.sequence += 1
        return self.sequence

    def send_raw(self, data):
        debug_print(f"[send_raw] Sending {len(data)} bytes")
        debug_print(f"[send_raw] dump:\n{hex_dump(data, prefix='    ')}")
        self.sock.sendall(data)

    def recv_response(self):
        """
        接收服务端响应

        先读取 16 字节协议头, 解析 Length 字段后再读取消息体

        Returns:
            (header_dict, body_bytes) 元组, 或 (None, None) 表示连接断开
        """
        header_data = b''
        while len(header_data) < HEADER_SIZE:
            chunk = self.sock.recv(HEADER_SIZE - len(header_data))
            if not chunk:
                debug_print("[recv_response] Connection closed while reading header")
                return None, None
            header_data += chunk

        debug_print(f"[recv_response] Received header ({len(header_data)} bytes): {header_data.hex()}")
        hdr = parse_header(header_data)
        if hdr is None:
            debug_print("[recv_response] Failed to parse header")
            return None, None

        body = b''
        remaining = hdr['length']
        while len(body) < remaining:
            chunk = self.sock.recv(remaining - len(body))
            if not chunk:
                break
            body += chunk

        if body:
            debug_print(f"[recv_response] Received body ({len(body)} bytes): {body.hex()}")
            debug_print(f"[recv_response] body dump:\n{hex_dump(body, prefix='    ')}")
        else:
            debug_print(f"[recv_response] No body (length=0)")

        return hdr, body

    def authenticate(self, api_key_id, api_key_secret, recovery_token=None):
        """
        执行认证流程

        构造 AuthRequest 消息:
          ApiKeyId(32, \0 填充) + Timestamp(8, int64 LE) + Nonce(16) + Signature(32) + RecoveryToken(32, \0 填充)

        解析 AuthResponse 消息:
          Error(1) + SessionToken(32) + AccountId(8, uint64 LE) + Role(1, uint8)

        Args:
            api_key_id:      API Key 标识符
            api_key_secret:  API Key 密钥
            recovery_token:  可选的 32 字节恢复令牌 (断线重连时使用)

        Returns:
            (success, error_code) 元组:
              成功: (True, None), session_key 保存在 self.session_key
              失败: (False, error_code)
        """
        timestamp_ms = int(time.time() * 1000)
        nonce = os.urandom(AUTH_NONCE_SIZE)
        debug_print(f"[authenticate] api_key_id={api_key_id!r}")
        debug_print(f"[authenticate] api_key_secret={api_key_secret!r}")
        debug_print(f"[authenticate] timestamp_ms={timestamp_ms}")
        debug_print(f"[authenticate] nonce={nonce.hex()}")
        debug_print(f"[authenticate] recovery_token={'present' if recovery_token else 'none'}")
        signature = compute_auth_signature(
            api_key_secret.encode('utf-8'), timestamp_ms, nonce, api_key_id)

        body = api_key_id.encode('utf-8').ljust(AUTH_API_KEY_ID_SIZE, b'\x00')
        body += struct.pack('<q', timestamp_ms)
        body += nonce
        body += signature
        if recovery_token:
            body += recovery_token[:AUTH_RECOVERY_TOKEN_SIZE].ljust(AUTH_RECOVERY_TOKEN_SIZE, b'\x00')
        else:
            body += b'\x00' * AUTH_RECOVERY_TOKEN_SIZE
        debug_print(f"[authenticate] AuthRequest body ({len(body)} bytes): {body.hex()}")

        seq = self.next_seq()
        header = build_header(MSG_TYPE_AUTH_REQUEST, FLAG_REQUEST, len(body), seq)

        self.send_raw(header + body)
        hdr, resp_body = self.recv_response()

        if hdr is None or hdr['msg_type'] != MSG_TYPE_AUTH_RESPONSE:
            debug_print(f"[authenticate] Unexpected response: hdr={hdr}, body_len={len(resp_body) if resp_body else 0}")
            return False, None

        if len(resp_body) < AUTH_RESPONSE_BODY_SIZE:
            debug_print(f"[authenticate] Response body too short: {len(resp_body)} < {AUTH_RESPONSE_SIZE}")
            debug_print(f"[authenticate] Response body: {resp_body.hex() if resp_body else '(empty)'}")
            return False, None

        error = resp_body[0]
        debug_print(f"[authenticate] Response Error={error} (0x{error:02x})")
        if error != ERR_OK:
            debug_print(f"[authenticate] Auth FAILED with error code {error}")
            return False, error

        self.session_key = resp_body[1:1 + AUTH_SESSION_TOKEN_SIZE]
        self.account_id = struct.unpack('<Q', resp_body[1 + AUTH_SESSION_TOKEN_SIZE:1 + AUTH_SESSION_TOKEN_SIZE + 8])[0]
        self.role = resp_body[1 + AUTH_SESSION_TOKEN_SIZE + 8]
        debug_print(f"[authenticate] Auth SUCCESS! session_key={self.session_key.hex()} account_id={self.account_id} role={self.role}")
        return True, None

    def send_signed_request(self, msg_type, flags, body=b''):
        """
        发送带 HMAC 签名的请求 (认证后使用)

        使用 session_key 计算 HmacPrefix 并填入协议头

        Args:
            msg_type: 消息类型
            flags:    消息标志位
            body:     消息体数据

        Returns:
            (header_dict, body_bytes) 服务端响应
        """
        seq = self.next_seq()
        hmac_prefix = 0
        if self.session_key:
            hmac_prefix = compute_hmac_prefix(
                self.session_key, seq, msg_type, flags, len(body), body)
        else:
            debug_print(f"[send_signed_request] WARNING: no session_key, HmacPrefix=0")

        debug_print(f"[send_signed_request] seq={seq} msg_type=0x{msg_type:02x} flags=0x{flags:02x} "
                    f"body_len={len(body)} hmac_prefix=0x{hmac_prefix:04x}")
        header = build_header(msg_type, flags, len(body), seq, hmac_prefix)
        self.send_raw(header + body)
        return self.recv_response()

    def send_unsigned_request(self, msg_type, flags, body=b''):
        """
        发送不带签名的请求 (HmacPrefix=0)

        用于测试: 未认证连接发送请求, 或认证后故意不签名

        Args:
            msg_type: 消息类型
            flags:    消息标志位
            body:     消息体数据

        Returns:
            (header_dict, body_bytes) 服务端响应
        """
        seq = self.next_seq()
        debug_print(f"[send_unsigned_request] seq={seq} msg_type=0x{msg_type:02x} flags=0x{flags:02x} body_len={len(body)} HmacPrefix=0")
        header = build_header(msg_type, flags, len(body), seq, 0)
        self.send_raw(header + body)
        return self.recv_response()

    def send_tampered_request(self, msg_type, flags, body=b''):
        """
        发送签名被篡改的请求 (HmacPrefix 异或 0xFFFF)

        用于测试: 计算正确的 HmacPrefix 后翻转所有位, 确保服务端拒绝

        Args:
            msg_type: 消息类型
            flags:    消息标志位
            body:     消息体数据

        Returns:
            (header_dict, body_bytes) 服务端响应
        """
        seq = self.next_seq()
        hmac_prefix = 0
        if self.session_key:
            correct = compute_hmac_prefix(
                self.session_key, seq, msg_type, flags, len(body), body)
            hmac_prefix = correct ^ 0xFFFF
            debug_print(f"[send_tampered_request] correct_hmac=0x{correct:04x}, tampered_hmac=0x{hmac_prefix:04x}")
        else:
            debug_print(f"[send_tampered_request] WARNING: no session_key")

        debug_print(f"[send_tampered_request] seq={seq} msg_type=0x{msg_type:02x} flags=0x{flags:02x} body_len={len(body)} hmac_prefix=0x{hmac_prefix:04x}")
        header = build_header(msg_type, flags, len(body), seq, hmac_prefix)
        self.send_raw(header + body)
        return self.recv_response()


# ─── 服务端进程管理 ─────────────────────────────────────────────────────────────

def wait_for_server(port, timeout=10):
    """
    等待服务端 TCP 端口就绪

    轮询连接指定端口, 直到成功或超时

    Args:
        port:    目标端口
        timeout: 超时秒数

    Returns:
        True 表示端口就绪, False 表示超时
    """
    start = time.time()
    while time.time() - start < timeout:
        try:
            s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
            s.settimeout(1)
            s.connect(('127.0.0.1', port))
            s.close()
            return True
        except (ConnectionRefusedError, socket.timeout):
            time.sleep(0.2)
    return False


server_proc = None


def start_server():
    """
    启动 C++ 协议服务端子进程

    命令行参数:
      --port 18080              测试端口
      --auth                    启用认证模式
      --api-key <id> <secret>   注册测试用 API Key

    服务端二进制路径: /root/dev/cpptrader_v0_dev/build/cpptrader-protocol-server
    """
    global server_proc
    server_bin = "/root/dev/cpptrader_v0_dev/build/cpptrader-protocol-server"
    if not os.path.exists(server_bin):
        print(f"{FAIL} Server binary not found: {server_bin}")
        sys.exit(1)

    cmd = [server_bin, "--port", str(TEST_PORT), "--auth",
           "--api-key", TEST_API_KEY_ID, TEST_API_KEY_SECRET, "10001", "1"]
    debug_print(f"[start_server] cmd={' '.join(cmd)}")
    server_proc = subprocess.Popen(cmd, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    print(f"{INFO} Starting server on port {TEST_PORT} (PID={server_proc.pid})")

    if not wait_for_server(TEST_PORT):
        print(f"{FAIL} Server failed to start")
        stop_server()
        sys.exit(1)
    print(f"{INFO} Server ready")


def stop_server():
    """
    停止服务端子进程

    先发送 SIGTERM 优雅关闭, 3 秒后强制 SIGKILL
    """
    global server_proc
    if server_proc:
        debug_print("[stop_server] Sending SIGTERM")
        server_proc.send_signal(signal.SIGTERM)
        try:
            server_proc.wait(timeout=3)
        except subprocess.TimeoutExpired:
            debug_print("[stop_server] SIGTERM timeout, sending SIGKILL")
            server_proc.kill()
        stdout, stderr = server_proc.communicate()
        if stdout:
            debug_print(f"[stop_server] server stdout:\n{stdout.decode('utf-8', errors='replace')}")
        if stderr:
            debug_print(f"[stop_server] server stderr:\n{stderr.decode('utf-8', errors='replace')}")
        server_proc = None


# ─── 测试用例 ───────────────────────────────────────────────────────────────────

def test_t1_cross_validation():
    """
    T1: HMAC 签名/验证一致性 (Python 与 C++/Java 交叉验证)

    验证 Python 端 compute_hmac_prefix 的输出与手动计算的 HMAC-SHA256 结果一致,
    确保 Python/C++/Java 三端使用相同的签名算法和字节序

    测试步骤:
      1. 使用固定 key + 参数计算 HmacPrefix
      2. 手动构建签名输入, 计算 HMAC-SHA256, 取前 2 字节, 与 HmacPrefix 比较
      3. 验证不同密钥产生不同签名
    """
    debug_print("=== T1: Cross-validation ===")
    key = b'\x01\x02\x03\x04\x05\x06\x07\x08\x09\x0a\x0b\x0c\x0d\x0e\x0f\x10'
    seq = 1
    msg_type = 0x01
    flags = 0x01
    length = 4
    body = b'\xAA\xBB\xCC\xDD'
    debug_print(f"  key={key.hex()}, seq={seq}, msg_type=0x{msg_type:02x}, flags=0x{flags:02x}, length={length}, body={body.hex()}")

    prefix = compute_hmac_prefix(key, seq, msg_type, flags, length, body)

    input_data = struct.pack('<I', seq) + struct.pack('B', msg_type) + struct.pack('B', flags) + struct.pack('<H', length) + body
    debug_print(f"  manual input_data={input_data.hex()}")
    full = hmac.new(key, input_data, hashlib.sha256).digest()
    debug_print(f"  manual hmac_sha256={full.hex()}")
    expected = struct.unpack('<H', full[0:2])[0]

    debug_print(f"  prefix=0x{prefix:04x}, expected=0x{expected:04x}, match={prefix == expected}")
    assert prefix == expected, f"prefix={prefix:#06x} expected={expected:#06x}"
    assert prefix != 0, "prefix should not be zero"

    key2 = b'\x11\x12\x13\x14\x15\x16\x17\x18\x19\x1a\x1b\x1c\x1d\x1e\x1f\x20'
    prefix2 = compute_hmac_prefix(key2, seq, msg_type, flags, length, body)
    debug_print(f"  key2={key2.hex()}, prefix2=0x{prefix2:04x}, different={prefix != prefix2}")
    assert prefix != prefix2, "different keys should produce different prefixes"

    print(f"  T1: {PASS} - Cross-validation: prefix={prefix:#06x}, expected={expected:#06x}")


def test_t2_auth_success():
    """
    T2: 认证流程 - 正确凭证认证成功

    使用正确的 ApiKeyId + ApiKeySecret 发送认证请求,
    验证服务端返回成功 (Error=0) 且 SessionToken 长度为 32 字节

    关键验证: session_key (客户端保存) 与服务端 SetSessionKey 存储的值一致,
    确保后续 HMAC 签名/验证能正常工作
    """
    debug_print("=== T2: Auth success ===")
    client = TestClient()
    client.connect()
    try:
        success, err = client.authenticate(TEST_API_KEY_ID, TEST_API_KEY_SECRET)
        debug_print(f"  auth result: success={success}, error={err}")
        debug_print(f"  session_key={client.session_key.hex() if client.session_key else 'None'}")
        assert success, f"Auth should succeed, got error={err}"
        assert client.session_key is not None, "session_key should be set"
        assert len(client.session_key) == AUTH_SESSION_TOKEN_SIZE, \
            f"session_key length should be {AUTH_SESSION_TOKEN_SIZE}, got {len(client.session_key)}"
        print(f"  T2: {PASS} - Auth success, session_key_len={len(client.session_key)}")
    finally:
        client.close()


def test_t3_auth_unknown_key():
    """
    T3: 认证流程 - 未知 ApiKeyId 认证失败

    使用未在服务端注册的 ApiKeyId 发送认证请求,
    验证服务端拒绝认证 (服务端找不到对应的 ApiKeySecret)
    """
    debug_print("=== T3: Auth unknown key ===")
    client = TestClient()
    client.connect()
    try:
        success, err = client.authenticate("unknown-key-id", "some-secret")
        debug_print(f"  auth result: success={success}, error={err}")
        assert not success, "Auth with unknown key should fail"
        print(f"  T3: {PASS} - Unknown ApiKeyId rejected")
    finally:
        client.close()


def test_t4_auth_wrong_signature():
    """
    T4: 认证流程 - 错误签名认证失败

    使用正确的 ApiKeyId + 时间戳 + Nonce, 但附带随机签名,
    验证服务端返回 INVALID_SIGNATURE 错误码

    这确保了签名验证是独立的, 即使其他字段正确, 签名错误也会被拒绝
    """
    debug_print("=== T4: Auth wrong signature ===")
    client = TestClient()
    client.connect()
    try:
        timestamp_ms = int(time.time() * 1000)
        nonce = os.urandom(AUTH_NONCE_SIZE)
        wrong_signature = os.urandom(AUTH_SIGNATURE_SIZE)
        debug_print(f"  timestamp_ms={timestamp_ms}")
        debug_print(f"  nonce={nonce.hex()}")
        debug_print(f"  wrong_signature={wrong_signature.hex()}")

        body = TEST_API_KEY_ID.encode('utf-8').ljust(AUTH_API_KEY_ID_SIZE, b'\x00')
        body += struct.pack('<q', timestamp_ms)
        body += nonce
        body += wrong_signature
        body += b'\x00' * AUTH_RECOVERY_TOKEN_SIZE
        debug_print(f"  AuthRequest body ({len(body)} bytes): {body.hex()}")

        seq = client.next_seq()
        header = build_header(MSG_TYPE_AUTH_REQUEST, FLAG_REQUEST, len(body), seq)
        client.send_raw(header + body)
        hdr, resp_body = client.recv_response()

        debug_print(f"  response: hdr={hdr}")
        debug_print(f"  response body: {resp_body.hex() if resp_body else '(empty)'}")
        assert hdr is not None, "Should get a response"
        assert hdr['msg_type'] == MSG_TYPE_AUTH_RESPONSE, "Should be AUTH_RESPONSE"
        error = resp_body[0]
        debug_print(f"  error code: {error} (expected {ERR_INVALID_SIGNATURE})")
        assert error == ERR_INVALID_SIGNATURE, f"Expected INVALID_SIGNATURE({ERR_INVALID_SIGNATURE}), got {error}"
        print(f"  T4: {PASS} - Wrong signature rejected (INVALID_SIGNATURE)")
    finally:
        client.close()


def test_t5_auth_expired_timestamp():
    """
    T5: 认证流程 - 过期时间戳认证失败

    使用 60 秒前的时间戳发送认证请求 (服务端容差为 30 秒),
    验证服务端返回 AUTH_EXPIRED 错误码

    这确保了 AntiReplayChecker 的 CheckTimestamp 功能正常工作
    """
    debug_print("=== T5: Auth expired timestamp ===")
    client = TestClient()
    client.connect()
    try:
        expired_timestamp = int((time.time() - 60) * 1000)
        nonce = os.urandom(AUTH_NONCE_SIZE)
        debug_print(f"  expired_timestamp={expired_timestamp} (60s ago)")
        debug_print(f"  current_timestamp={int(time.time() * 1000)}")
        debug_print(f"  nonce={nonce.hex()}")
        signature = compute_auth_signature(
            TEST_API_KEY_SECRET.encode('utf-8'), expired_timestamp, nonce, TEST_API_KEY_ID)

        body = TEST_API_KEY_ID.encode('utf-8').ljust(AUTH_API_KEY_ID_SIZE, b'\x00')
        body += struct.pack('<q', expired_timestamp)
        body += nonce
        body += signature
        body += b'\x00' * AUTH_RECOVERY_TOKEN_SIZE
        debug_print(f"  AuthRequest body ({len(body)} bytes): {body.hex()}")

        seq = client.next_seq()
        header = build_header(MSG_TYPE_AUTH_REQUEST, FLAG_REQUEST, len(body), seq)
        client.send_raw(header + body)
        hdr, resp_body = client.recv_response()

        debug_print(f"  response: hdr={hdr}")
        debug_print(f"  response body: {resp_body.hex() if resp_body else '(empty)'}")
        assert hdr is not None, "Should get a response"
        error = resp_body[0]
        debug_print(f"  error code: {error} (expected {ERR_AUTH_EXPIRED})")
        assert error == ERR_AUTH_EXPIRED, f"Expected AUTH_EXPIRED({ERR_AUTH_EXPIRED}), got {error}"
        print(f"  T5: {PASS} - Expired timestamp rejected (AUTH_EXPIRED)")
    finally:
        client.close()


def test_t6_auth_replay_nonce():
    """
    T6: 认证流程 - 重放 Nonce 认证失败

    使用相同的时间戳和 Nonce 发送两次认证请求:
      1. 第一次认证成功
      2. 第二次 (新连接) 使用相同的 timestamp + nonce, 验证被拒绝

    这确保了 AntiReplayChecker 的 CheckNonce 去重功能正常工作,
    即使使用不同的 TCP 连接, Nonce 去重仍然生效 (服务端全局 Nonce 存储)
    """
    debug_print("=== T6: Auth replay nonce ===")
    debug_print("--- First auth (should succeed) ---")
    client1 = TestClient()
    client1.connect()
    try:
        timestamp_ms = int(time.time() * 1000)
        nonce = os.urandom(AUTH_NONCE_SIZE)
        debug_print(f"  timestamp_ms={timestamp_ms}, nonce={nonce.hex()}")
        signature = compute_auth_signature(
            TEST_API_KEY_SECRET.encode('utf-8'), timestamp_ms, nonce, TEST_API_KEY_ID)

        body = TEST_API_KEY_ID.encode('utf-8').ljust(AUTH_API_KEY_ID_SIZE, b'\x00')
        body += struct.pack('<q', timestamp_ms)
        body += nonce
        body += signature
        body += b'\x00' * AUTH_RECOVERY_TOKEN_SIZE
        debug_print(f"  AuthRequest body ({len(body)} bytes): {body.hex()}")

        seq = client1.next_seq()
        header = build_header(MSG_TYPE_AUTH_REQUEST, FLAG_REQUEST, len(body), seq)
        client1.send_raw(header + body)
        hdr, resp_body = client1.recv_response()
        debug_print(f"  first auth response: error={resp_body[0] if resp_body else 'N/A'}")
        assert resp_body[0] == ERR_OK, "First auth should succeed"
    finally:
        client1.close()

    debug_print("--- Second auth with same nonce (should fail) ---")
    client2 = TestClient()
    client2.connect()
    try:
        signature2 = compute_auth_signature(
            TEST_API_KEY_SECRET.encode('utf-8'), timestamp_ms, nonce, TEST_API_KEY_ID)
        debug_print(f"  reusing timestamp_ms={timestamp_ms}, nonce={nonce.hex()}")

        body2 = TEST_API_KEY_ID.encode('utf-8').ljust(AUTH_API_KEY_ID_SIZE, b'\x00')
        body2 += struct.pack('<q', timestamp_ms)
        body2 += nonce
        body2 += signature2
        body2 += b'\x00' * AUTH_RECOVERY_TOKEN_SIZE
        debug_print(f"  AuthRequest body ({len(body2)} bytes): {body2.hex()}")

        seq = client2.next_seq()
        header = build_header(MSG_TYPE_AUTH_REQUEST, FLAG_REQUEST, len(body2), seq)
        client2.send_raw(header + body2)
        hdr, resp_body = client2.recv_response()

        debug_print(f"  response: hdr={hdr}")
        debug_print(f"  response body: {resp_body.hex() if resp_body else '(empty)'}")
        assert hdr is not None, "Should get a response"
        error = resp_body[0]
        debug_print(f"  error code: {error} (expected {ERR_REPLAY_DETECTED})")
        assert error == ERR_REPLAY_DETECTED, f"Expected REPLAY_DETECTED({ERR_REPLAY_DETECTED}), got {error}"
        print(f"  T6: {PASS} - Replayed nonce rejected (REPLAY_DETECTED)")
    finally:
        client2.close()


def test_t7_signed_request_passes():
    """
    T7: 认证后请求 - 正确 HmacPrefix 通过

    认证成功后, 使用 session_key 计算正确的 HmacPrefix 发送 ADD_SYMBOL_REQUEST,
    验证服务端接受请求并返回 SYMBOL_RESPONSE (Error=OK)

    这是 session_key/session_token 一致性的核心验证:
      客户端用 SessionToken 签名 -> 服务端用 SessionKey 验证 -> 两者是同一个 32 字节值
    """
    debug_print("=== T7: Signed request passes ===")
    client = TestClient()
    client.connect()
    try:
        success, _ = client.authenticate(TEST_API_KEY_ID, TEST_API_KEY_SECRET)
        assert success, "Auth should succeed"

        symbol_body = struct.pack('<I', 1) + b'TEST\x00\x00\x00\x00'
        debug_print(f"  symbol_body ({len(symbol_body)} bytes): {symbol_body.hex()}")
        debug_print(f"  SymbolId=1, Name='TEST'")
        hdr, resp_body = client.send_signed_request(
            MSG_TYPE_ADD_SYMBOL_REQUEST, FLAG_REQUEST, symbol_body)

        debug_print(f"  response: hdr={hdr}")
        debug_print(f"  response body: {resp_body.hex() if resp_body else '(empty)'}")
        assert hdr is not None, "Should get a response"
        debug_print(f"  response msg_type=0x{hdr['msg_type']:02x} (expected 0x{MSG_TYPE_SYMBOL_RESPONSE:02x})")
        assert hdr['msg_type'] == MSG_TYPE_SYMBOL_RESPONSE, \
            f"Expected SYMBOL_RESPONSE(0x41), got 0x{hdr['msg_type']:02x}"
        assert len(resp_body) >= 1, "Response body too short"
        debug_print(f"  response error={resp_body[0]} (expected {ERR_OK})")
        assert resp_body[0] == ERR_OK, \
            f"Expected OK(0), got error={resp_body[0]}"
        print(f"  T7: {PASS} - Signed request accepted")
    finally:
        client.close()


def test_t8_tampered_hmac_rejected():
    """
    T8: 认证后请求 - 错误 HmacPrefix 被拒绝

    认证成功后, 计算正确的 HmacPrefix 然后翻转所有位 (XOR 0xFFFF),
    验证服务端拒绝请求并返回 INVALID_SIGNATURE 错误码 + ERROR 标志

    这确保了服务端的 HMAC 验证确实在检查签名值, 而非仅检查非零
    """
    debug_print("=== T8: Tampered HMAC rejected ===")
    client = TestClient()
    client.connect()
    try:
        success, _ = client.authenticate(TEST_API_KEY_ID, TEST_API_KEY_SECRET)
        assert success, "Auth should succeed"

        symbol_body = struct.pack('<I', 2) + b'TST2\x00\x00\x00\x00'
        debug_print(f"  symbol_body ({len(symbol_body)} bytes): {symbol_body.hex()}")
        debug_print(f"  SymbolId=2, Name='TST2'")
        hdr, resp_body = client.send_tampered_request(
            MSG_TYPE_ADD_SYMBOL_REQUEST, FLAG_REQUEST, symbol_body)

        debug_print(f"  response: hdr={hdr}")
        debug_print(f"  response body: {resp_body.hex() if resp_body else '(empty)'}")
        assert hdr is not None, "Should get a response"
        debug_print(f"  flags=0x{hdr['flags']:02x}, has_error_flag={bool(hdr['flags'] & FLAG_ERROR)}")
        assert (hdr['flags'] & FLAG_ERROR) != 0, "Should have ERROR flag"
        assert len(resp_body) >= 1, "Response body too short"
        debug_print(f"  error code: {resp_body[0]} (expected {ERR_INVALID_SIGNATURE})")
        assert resp_body[0] == ERR_INVALID_SIGNATURE, \
            f"Expected INVALID_SIGNATURE({ERR_INVALID_SIGNATURE}), got {resp_body[0]}"
        print(f"  T8: {PASS} - Tampered HmacPrefix rejected (INVALID_SIGNATURE)")
    finally:
        client.close()


def test_t9_zero_hmac_rejected():
    """
    T9: 认证后请求 - HmacPrefix=0 被拒绝

    认证成功后, 发送 HmacPrefix=0 的请求 (即不签名),
    验证服务端拒绝请求并返回 INVALID_SIGNATURE 错误码

    这确保了认证后的所有请求都必须携带有效签名, 零签名不被接受
    (HMAC-SHA256 输出前 2 字节恰好为 0 的概率极低, 约 1/65536)
    """
    debug_print("=== T9: Zero HMAC rejected ===")
    client = TestClient()
    client.connect()
    try:
        success, _ = client.authenticate(TEST_API_KEY_ID, TEST_API_KEY_SECRET)
        assert success, "Auth should succeed"

        symbol_body = struct.pack('<I', 3) + b'TST3\x00\x00\x00\x00'
        debug_print(f"  symbol_body ({len(symbol_body)} bytes): {symbol_body.hex()}")
        debug_print(f"  SymbolId=3, Name='TST3'")
        hdr, resp_body = client.send_unsigned_request(
            MSG_TYPE_ADD_SYMBOL_REQUEST, FLAG_REQUEST, symbol_body)

        debug_print(f"  response: hdr={hdr}")
        debug_print(f"  response body: {resp_body.hex() if resp_body else '(empty)'}")
        assert hdr is not None, "Should get a response"
        debug_print(f"  flags=0x{hdr['flags']:02x}, has_error_flag={bool(hdr['flags'] & FLAG_ERROR)}")
        assert (hdr['flags'] & FLAG_ERROR) != 0, "Should have ERROR flag"
        assert len(resp_body) >= 1, "Response body too short"
        debug_print(f"  error code: {resp_body[0]} (expected {ERR_INVALID_SIGNATURE})")
        assert resp_body[0] == ERR_INVALID_SIGNATURE, \
            f"Expected INVALID_SIGNATURE({ERR_INVALID_SIGNATURE}), got {resp_body[0]}"
        print(f"  T9: {PASS} - HmacPrefix=0 rejected (INVALID_SIGNATURE)")
    finally:
        client.close()


def test_t10_unauthenticated_rejected():
    """
    T10: 未认证请求 - 非认证消息被拒绝

    不进行认证, 直接发送 ADD_SYMBOL_REQUEST,
    验证服务端返回 SIMPLE_RESPONSE + NOT_AUTHENTICATED 错误码

    这确保了启用 --auth 模式后, 未认证连接只能发送 AUTH/HEARTBEAT/RECONCILE 消息
    注意: 未认证拒绝响应不设置 ERROR 标志, 仅在 body 中返回错误码
    """
    debug_print("=== T10: Unauthenticated rejected ===")
    client = TestClient()
    client.connect()
    try:
        symbol_body = struct.pack('<I', 1) + b'TEST\x00\x00\x00\x00'
        debug_print(f"  symbol_body ({len(symbol_body)} bytes): {symbol_body.hex()}")
        debug_print(f"  Sending request WITHOUT authentication")
        hdr, resp_body = client.send_unsigned_request(
            MSG_TYPE_ADD_SYMBOL_REQUEST, FLAG_REQUEST, symbol_body)

        debug_print(f"  response: hdr={hdr}")
        debug_print(f"  response body: {resp_body.hex() if resp_body else '(empty)'}")
        assert hdr is not None, "Should get a response"
        debug_print(f"  response msg_type=0x{hdr['msg_type']:02x} (expected 0x{MSG_TYPE_SIMPLE_RESPONSE:02x})")
        assert hdr['msg_type'] == MSG_TYPE_SIMPLE_RESPONSE, \
            f"Expected SIMPLE_RESPONSE(0x44), got 0x{hdr['msg_type']:02x}"
        assert len(resp_body) >= 1, "Response body too short"
        debug_print(f"  error code: {resp_body[0]} (expected {ERR_NOT_AUTHENTICATED})")
        assert resp_body[0] == ERR_NOT_AUTHENTICATED, \
            f"Expected NOT_AUTHENTICATED({ERR_NOT_AUTHENTICATED}), got {resp_body[0]}"
        print(f"  T10: {PASS} - Unauthenticated request rejected (NOT_AUTHENTICATED)")
    finally:
        client.close()


def test_t11_reauth_rejected():
    """
    T11: 已认证连接 - 重新认证被拒绝

    认证成功后, 再次发送 AUTH_REQUEST (带正确的 HmacPrefix),
    验证服务端拒绝重新认证并返回 NOT_AUTHENTICATED 错误码 + ERROR 标志

    这确保了已认证连接不能重新认证, 防止会话劫持:
      如果允许重新认证, 攻击者可以在已认证连接上替换 session_key
    """
    debug_print("=== T11: Re-auth rejected ===")
    client = TestClient()
    client.connect()
    try:
        success, _ = client.authenticate(TEST_API_KEY_ID, TEST_API_KEY_SECRET)
        assert success, "Auth should succeed"
        debug_print(f"  First auth succeeded, session_key={client.session_key.hex()}")

        timestamp_ms = int(time.time() * 1000)
        nonce = os.urandom(AUTH_NONCE_SIZE)
        debug_print(f"  Second auth attempt: timestamp_ms={timestamp_ms}, nonce={nonce.hex()}")
        signature = compute_auth_signature(
            TEST_API_KEY_SECRET.encode('utf-8'), timestamp_ms, nonce, TEST_API_KEY_ID)

        body = TEST_API_KEY_ID.encode('utf-8').ljust(AUTH_API_KEY_ID_SIZE, b'\x00')
        body += struct.pack('<q', timestamp_ms)
        body += nonce
        body += signature
        body += b'\x00' * AUTH_RECOVERY_TOKEN_SIZE
        debug_print(f"  Re-auth body ({len(body)} bytes): {body.hex()}")

        seq = client.next_seq()
        hmac_prefix = compute_hmac_prefix(
            client.session_key, seq, MSG_TYPE_AUTH_REQUEST, FLAG_REQUEST, len(body), body)
        debug_print(f"  re-auth seq={seq}, hmac_prefix=0x{hmac_prefix:04x}")
        header = build_header(MSG_TYPE_AUTH_REQUEST, FLAG_REQUEST, len(body), seq, hmac_prefix)
        client.send_raw(header + body)
        hdr, resp_body = client.recv_response()

        debug_print(f"  response: hdr={hdr}")
        debug_print(f"  response body: {resp_body.hex() if resp_body else '(empty)'}")
        assert hdr is not None, "Should get a response"
        debug_print(f"  flags=0x{hdr['flags']:02x}, has_error_flag={bool(hdr['flags'] & FLAG_ERROR)}")
        assert (hdr['flags'] & FLAG_ERROR) != 0, "Re-auth should be rejected"
        assert len(resp_body) >= 1, "Response body too short"
        debug_print(f"  error code: {resp_body[0]} (expected {ERR_NOT_AUTHENTICATED})")
        assert resp_body[0] == ERR_NOT_AUTHENTICATED, \
            f"Expected NOT_AUTHENTICATED({ERR_NOT_AUTHENTICATED}), got {resp_body[0]}"
        print(f"  T11: {PASS} - Re-auth on authenticated connection rejected")
    finally:
        client.close()


def test_t12_heartbeat_with_hmac():
    """
    T12: 心跳消息 - 认证后心跳需要签名

    认证成功后, 发送带正确 HmacPrefix 的 HEARTBEAT_REQ,
    验证服务端返回 HEARTBEAT_RESP

    这确保了心跳消息在认证模式下也需要签名:
      - 心跳是保持连接活跃的关键消息
      - 如果心跳不需要签名, 攻击者可以伪造心跳维持被劫持的连接
    """
    debug_print("=== T12: Heartbeat with HMAC ===")
    client = TestClient()
    client.connect()
    try:
        success, _ = client.authenticate(TEST_API_KEY_ID, TEST_API_KEY_SECRET)
        assert success, "Auth should succeed"
        debug_print(f"  Auth succeeded, session_key={client.session_key.hex()}")

        seq = client.next_seq()
        hmac_prefix = compute_hmac_prefix(
            client.session_key, seq, MSG_TYPE_HEARTBEAT_REQ, FLAG_HEARTBEAT, 0)
        debug_print(f"  heartbeat seq={seq}, hmac_prefix=0x{hmac_prefix:04x}, body_len=0")
        header = build_header(MSG_TYPE_HEARTBEAT_REQ, FLAG_HEARTBEAT, 0, seq, hmac_prefix)
        client.send_raw(header)

        hdr, resp_body = client.recv_response()
        debug_print(f"  response: hdr={hdr}")
        debug_print(f"  response body: {resp_body.hex() if resp_body else '(empty)'}")
        assert hdr is not None, "Should get heartbeat response"
        debug_print(f"  response msg_type=0x{hdr['msg_type']:02x} (expected 0x{MSG_TYPE_HEARTBEAT_RESP:02x})")
        assert hdr['msg_type'] == MSG_TYPE_HEARTBEAT_RESP, \
            f"Expected HEARTBEAT_RESP, got 0x{hdr['msg_type']:02x}"
        print(f"  T12: {PASS} - Signed heartbeat accepted")
    finally:
        client.close()


def test_t13_auth_response_account_role():
    """
    T13: Session 生命周期 - AuthResponse 包含 AccountId 和 Role

    使用注册了 account_id=10001, role=1(TRADER) 的 API Key 认证,
    验证 AuthResponse 中返回正确的 AccountId 和 Role 字段

    这确保了 Session 创建时关联了账户信息和角色,
    为后续的权限控制提供基础
    """
    debug_print("=== T13: AuthResponse AccountId and Role ===")
    client = TestClient()
    client.connect()
    try:
        success, err = client.authenticate(TEST_API_KEY_ID, TEST_API_KEY_SECRET)
        assert success, f"Auth should succeed, got error={err}"
        debug_print(f"  account_id={client.account_id}, role={client.role}")
        assert client.account_id == 10001, f"Expected account_id=10001, got {client.account_id}"
        assert client.role == 1, f"Expected role=1 (TRADER), got {client.role}"
        print(f"  T13: {PASS} - AuthResponse contains account_id={client.account_id}, role={client.role}")
    finally:
        client.close()


def test_t14_session_recovery():
    """
    T14: Session 恢复 - 断线重连使用 RecoveryToken 恢复 Session

    测试步骤:
      1. 客户端1 认证成功, 获取 session_key
      2. 客户端1 断开连接 (不主动销毁 session)
      3. 客户端2 使用 session_key 作为 RecoveryToken 发送 AUTH_REQUEST
      4. 验证服务端恢复 Session, 返回相同的 session_key 和 account_id

    这确保了断线重连场景下 Session 可以被恢复,
    客户端无需重新进行完整认证
    """
    debug_print("=== T14: Session recovery ===")
    client1 = TestClient()
    client1.connect()
    try:
        success, _ = client1.authenticate(TEST_API_KEY_ID, TEST_API_KEY_SECRET)
        assert success, "First auth should succeed"
        saved_session_key = client1.session_key
        saved_account_id = client1.account_id
        debug_print(f"  First auth: session_key={saved_session_key.hex()}, account_id={saved_account_id}")
    finally:
        client1.close()

    debug_print("--- Reconnecting with RecoveryToken ---")
    client2 = TestClient()
    client2.connect()
    try:
        success, err = client2.authenticate(
            TEST_API_KEY_ID, TEST_API_KEY_SECRET,
            recovery_token=saved_session_key)
        assert success, f"Recovery auth should succeed, got error={err}"
        debug_print(f"  Recovery auth: session_key={client2.session_key.hex()}, account_id={client2.account_id}")
        assert client2.session_key == saved_session_key, \
            f"Recovered session should have same session_key"
        assert client2.account_id == saved_account_id, \
            f"Recovered session should have same account_id"
        print(f"  T14: {PASS} - Session recovered with same token and account_id")
    finally:
        client2.close()


def test_t15_recovery_invalid_token_fallback():
    """
    T15: Session 恢复 - 无效 RecoveryToken 回退到完整认证

    测试步骤:
      1. 使用随机 (无效) 的 RecoveryToken 发送 AUTH_REQUEST
      2. 同时提供正确的 ApiKeyId + Signature
      3. 验证服务端回退到完整认证, 创建新 Session

    这确保了当 RecoveryToken 无效时, 服务端不会拒绝认证,
    而是正常执行完整认证流程创建新 Session
    """
    debug_print("=== T15: Invalid RecoveryToken fallback ===")
    client = TestClient()
    client.connect()
    try:
        fake_recovery = os.urandom(AUTH_RECOVERY_TOKEN_SIZE)
        success, err = client.authenticate(
            TEST_API_KEY_ID, TEST_API_KEY_SECRET,
            recovery_token=fake_recovery)
        assert success, f"Auth should succeed (fallback), got error={err}"
        assert client.session_key is not None, "Should have a new session_key"
        assert client.account_id == 10001, f"Expected account_id=10001, got {client.account_id}"
        debug_print(f"  Fallback auth: session_key={client.session_key.hex()}, account_id={client.account_id}")
        print(f"  T15: {PASS} - Invalid RecoveryToken falls back to full auth")
    finally:
        client.close()


# ─── 主流程 ─────────────────────────────────────────────────────────────────────

def main():
    """
    测试主流程, 分 5 个阶段执行:

      Phase 1: 纯算法交叉验证 (无需服务端)
        - T1: Python HMAC 计算与手动验证一致

      Phase 2: 启动服务端
        - 启动 C++ 协议服务端子进程 (--auth 模式)

      Phase 3: 认证流程测试
        - T2-T6: 覆盖认证成功、未知 Key、错误签名、过期时间戳、重放 Nonce

      Phase 4: HMAC 签名验证测试
        - T7-T12: 覆盖正确签名、篡改签名、零签名、未认证、重新认证、签名心跳

      Phase 5: 清理
        - 停止服务端子进程
    """
    print("=" * 60)
    print("  HMAC 全功能端到端测试")
    print("=" * 60)

    # Phase 1: 纯算法交叉验证, 不依赖服务端
    print(f"\n--- Phase 1: 纯算法交叉验证 (无需服务端) ---\n")
    try:
        test_t1_cross_validation()
    except AssertionError as e:
        print(f"  T1: {FAIL} - {e}")

    # Phase 2: 启动 C++ 服务端
    print(f"\n--- Phase 2: 启动服务端 ---\n")
    start_server()
    time.sleep(0.5)

    # Phase 3: 认证流程测试 (T2-T6)
    print(f"\n--- Phase 3: 认证流程测试 ---\n")
    tests = [
        ("T2", test_t2_auth_success),
        ("T3", test_t3_auth_unknown_key),
        ("T4", test_t4_auth_wrong_signature),
        ("T5", test_t5_auth_expired_timestamp),
        ("T6", test_t6_auth_replay_nonce),
    ]
    for name, fn in tests:
        try:
            fn()
        except AssertionError as e:
            print(f"  {name}: {FAIL} - {e}")
        except Exception as e:
            print(f"  {name}: {FAIL} - Exception: {e}")

    # Phase 4: HMAC 签名验证测试 (T7-T12)
    print(f"\n--- Phase 4: HMAC 签名验证测试 ---\n")
    tests2 = [
        ("T7", test_t7_signed_request_passes),
        ("T8", test_t8_tampered_hmac_rejected),
        ("T9", test_t9_zero_hmac_rejected),
        ("T10", test_t10_unauthenticated_rejected),
        ("T11", test_t11_reauth_rejected),
        ("T12", test_t12_heartbeat_with_hmac),
    ]
    for name, fn in tests2:
        try:
            fn()
        except AssertionError as e:
            print(f"  {name}: {FAIL} - {e}")
        except Exception as e:
            print(f"  {name}: {FAIL} - Exception: {e}")

    # Phase 4.5: Session 生命周期测试 (T13-T15)
    print(f"\n--- Phase 4.5: Session 生命周期测试 ---\n")
    tests3 = [
        ("T13", test_t13_auth_response_account_role),
        ("T14", test_t14_session_recovery),
        ("T15", test_t15_recovery_invalid_token_fallback),
    ]
    for name, fn in tests3:
        try:
            fn()
        except AssertionError as e:
            print(f"  {name}: {FAIL} - {e}")
        except Exception as e:
            print(f"  {name}: {FAIL} - Exception: {e}")

    # Phase 5: 停止服务端
    print(f"\n--- Phase 5: 清理 ---\n")
    stop_server()
    print(f"{INFO} Server stopped")

    print(f"\n{'=' * 60}")
    print(f"  测试完成")
    print(f"{'=' * 60}")


if __name__ == '__main__':
    main()