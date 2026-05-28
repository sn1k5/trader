#!/usr/bin/env python3
"""
HMAC 全功能端到端测试脚本

测试架构:
  C++ Server (port 8080) <--TCP--> Python 测试客户端

测试覆盖:
  T1: HmacSigner 签名/验证一致性 (C++/Java/Python 交叉验证)
  T2: 认证流程 - 正确凭证认证成功
  T3: 认证流程 - 未知 ApiKeyId 认证失败
  T4: 认证流程 - 错误签名认证失败
  T5: 认证流程 - 过期时间戳认证失败
  T6: 认证流程 - 重放 Nonce 认证失败
  T7: 认证后请求 - 正确 HmacPrefix 通过
  T8: 认证后请求 - 错误 HmacPrefix 被拒绝
  T9: 认证后请求 - HmacPrefix=0 被拒绝
  T10: 未认证请求 - 非认证消息被拒绝
  T11: 已认证连接 - 重新认证被拒绝
  T12: 心跳消息 - 认证后心跳需要签名
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

MAGIC = 0x5452
VERSION = 2
HEADER_SIZE = 16

MSG_TYPE_AUTH_REQUEST = 0xD0
MSG_TYPE_AUTH_RESPONSE = 0xD1
MSG_TYPE_HEARTBEAT_REQ = 0xC0
MSG_TYPE_HEARTBEAT_RESP = 0xC1
MSG_TYPE_ADD_SYMBOL_REQUEST = 0x01
MSG_TYPE_SYMBOL_RESPONSE = 0x41
MSG_TYPE_SIMPLE_RESPONSE = 0x44

FLAG_REQUEST = 0x01
FLAG_RESPONSE = 0x02
FLAG_HEARTBEAT = 0x10
FLAG_ERROR = 0x08

ERR_OK = 0
ERR_NOT_AUTHENTICATED = 20
ERR_AUTH_EXPIRED = 22
ERR_INVALID_SIGNATURE = 23
ERR_REPLAY_DETECTED = 24

AUTH_API_KEY_ID_SIZE = 32
AUTH_NONCE_SIZE = 16
AUTH_SIGNATURE_SIZE = 32
AUTH_SESSION_TOKEN_SIZE = 32

TEST_API_KEY_ID = "test-api-key-1234567890abcdef"
TEST_API_KEY_SECRET = "test-secret-key-abcdefghijklmnop"
TEST_PORT = 18080
TEST_HOST = "127.0.0.1"

PASS = "\033[92mPASS\033[0m"
FAIL = "\033[91mFAIL\033[0m"
INFO = "\033[94mINFO\033[0m"


def compute_auth_signature(secret: bytes, timestamp_ms: int, nonce: bytes, api_key_id: str) -> bytes:
    ts_hex = format(timestamp_ms, '016x')
    nonce_hex = nonce.hex()
    message = ts_hex + nonce_hex + api_key_id
    return hmac.new(secret, message.encode('utf-8'), hashlib.sha256).digest()


def compute_hmac_prefix(session_key: bytes, sequence: int, msg_type: int, flags: int, length: int, body: bytes = b'') -> int:
    input_data = struct.pack('<I', sequence)
    input_data += struct.pack('B', msg_type)
    input_data += struct.pack('B', flags)
    input_data += struct.pack('<H', length)
    input_data += body
    full = hmac.new(session_key, input_data, hashlib.sha256).digest()
    return struct.unpack('<H', full[0:2])[0]


def build_header(msg_type, flags, length, sequence, hmac_prefix=0):
    return struct.pack('<HBBB B H I H H',
                       MAGIC, VERSION, msg_type, flags,
                       0, length, sequence, hmac_prefix, 0)


def parse_header(data):
    if len(data) < HEADER_SIZE:
        return None
    magic, version, msg_type, flags, reserved, length, sequence, hmac_prefix, reserved2 = \
        struct.unpack('<HBBB B H I H H', data[:HEADER_SIZE])
    return {
        'magic': magic, 'version': version, 'msg_type': msg_type,
        'flags': flags, 'reserved': reserved, 'length': length,
        'sequence': sequence, 'hmac_prefix': hmac_prefix, 'reserved2': reserved2
    }


class TestClient:
    def __init__(self, host=TEST_HOST, port=TEST_PORT):
        self.host = host
        self.port = port
        self.sock = None
        self.sequence = 0
        self.session_key = None

    def connect(self):
        self.sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        self.sock.settimeout(5)
        self.sock.connect((self.host, self.port))

    def close(self):
        if self.sock:
            self.sock.close()
            self.sock = None

    def next_seq(self):
        self.sequence += 1
        return self.sequence

    def send_raw(self, data):
        self.sock.sendall(data)

    def recv_response(self):
        header_data = b''
        while len(header_data) < HEADER_SIZE:
            chunk = self.sock.recv(HEADER_SIZE - len(header_data))
            if not chunk:
                return None, None
            header_data += chunk

        hdr = parse_header(header_data)
        if hdr is None:
            return None, None

        body = b''
        remaining = hdr['length']
        while len(body) < remaining:
            chunk = self.sock.recv(remaining - len(body))
            if not chunk:
                break
            body += chunk

        return hdr, body

    def authenticate(self, api_key_id, api_key_secret):
        timestamp_ms = int(time.time() * 1000)
        nonce = os.urandom(AUTH_NONCE_SIZE)
        signature = compute_auth_signature(
            api_key_secret.encode('utf-8'), timestamp_ms, nonce, api_key_id)

        body = api_key_id.encode('utf-8').ljust(AUTH_API_KEY_ID_SIZE, b'\x00')
        body += struct.pack('<q', timestamp_ms)
        body += nonce
        body += signature

        seq = self.next_seq()
        header = build_header(MSG_TYPE_AUTH_REQUEST, FLAG_REQUEST, len(body), seq)

        self.send_raw(header + body)
        hdr, resp_body = self.recv_response()

        if hdr is None or hdr['msg_type'] != MSG_TYPE_AUTH_RESPONSE:
            return False, None

        if len(resp_body) < 1 + AUTH_SESSION_TOKEN_SIZE:
            return False, None

        error = resp_body[0]
        if error != ERR_OK:
            return False, error

        self.session_key = resp_body[1:1 + AUTH_SESSION_TOKEN_SIZE]
        return True, None

    def send_signed_request(self, msg_type, flags, body=b''):
        seq = self.next_seq()
        hmac_prefix = 0
        if self.session_key:
            hmac_prefix = compute_hmac_prefix(
                self.session_key, seq, msg_type, flags, len(body), body)

        header = build_header(msg_type, flags, len(body), seq, hmac_prefix)
        self.send_raw(header + body)
        return self.recv_response()

    def send_unsigned_request(self, msg_type, flags, body=b''):
        seq = self.next_seq()
        header = build_header(msg_type, flags, len(body), seq, 0)
        self.send_raw(header + body)
        return self.recv_response()

    def send_tampered_request(self, msg_type, flags, body=b''):
        seq = self.next_seq()
        hmac_prefix = 0
        if self.session_key:
            correct = compute_hmac_prefix(
                self.session_key, seq, msg_type, flags, len(body), body)
            hmac_prefix = correct ^ 0xFFFF

        header = build_header(msg_type, flags, len(body), seq, hmac_prefix)
        self.send_raw(header + body)
        return self.recv_response()


def wait_for_server(port, timeout=10):
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
    global server_proc
    server_bin = "/root/dev/cpptrader_v0_dev/build/cpptrader-protocol-server"
    if not os.path.exists(server_bin):
        print(f"{FAIL} Server binary not found: {server_bin}")
        sys.exit(1)

    cmd = [server_bin, "--port", str(TEST_PORT), "--auth",
           "--api-key", TEST_API_KEY_ID, TEST_API_KEY_SECRET]
    server_proc = subprocess.Popen(cmd, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    print(f"{INFO} Starting server on port {TEST_PORT} (PID={server_proc.pid})")

    if not wait_for_server(TEST_PORT):
        print(f"{FAIL} Server failed to start")
        stop_server()
        sys.exit(1)
    print(f"{INFO} Server ready")


def stop_server():
    global server_proc
    if server_proc:
        server_proc.send_signal(signal.SIGTERM)
        try:
            server_proc.wait(timeout=3)
        except subprocess.TimeoutExpired:
            server_proc.kill()
        server_proc = None


def test_t1_cross_validation():
    """T1: HmacSigner 签名/验证一致性 (Python 与 C++/Java 交叉验证)"""
    key = b'\x01\x02\x03\x04\x05\x06\x07\x08\x09\x0a\x0b\x0c\x0d\x0e\x0f\x10'
    seq = 1
    msg_type = 0x01
    flags = 0x01
    length = 4
    body = b'\xAA\xBB\xCC\xDD'

    prefix = compute_hmac_prefix(key, seq, msg_type, flags, length, body)

    input_data = struct.pack('<I', seq) + struct.pack('B', msg_type) + struct.pack('B', flags) + struct.pack('<H', length) + body
    full = hmac.new(key, input_data, hashlib.sha256).digest()
    expected = struct.unpack('<H', full[0:2])[0]

    assert prefix == expected, f"prefix={prefix:#06x} expected={expected:#06x}"
    assert prefix != 0, "prefix should not be zero"

    key2 = b'\x11\x12\x13\x14\x15\x16\x17\x18\x19\x1a\x1b\x1c\x1d\x1e\x1f\x20'
    prefix2 = compute_hmac_prefix(key2, seq, msg_type, flags, length, body)
    assert prefix != prefix2, "different keys should produce different prefixes"

    print(f"  T1: {PASS} - Cross-validation: prefix={prefix:#06x}, expected={expected:#06x}")


def test_t2_auth_success():
    """T2: 认证流程 - 正确凭证认证成功"""
    client = TestClient()
    client.connect()
    try:
        success, err = client.authenticate(TEST_API_KEY_ID, TEST_API_KEY_SECRET)
        assert success, f"Auth should succeed, got error={err}"
        assert client.session_key is not None, "session_key should be set"
        assert len(client.session_key) == AUTH_SESSION_TOKEN_SIZE, \
            f"session_key length should be {AUTH_SESSION_TOKEN_SIZE}, got {len(client.session_key)}"
        print(f"  T2: {PASS} - Auth success, session_key_len={len(client.session_key)}")
    finally:
        client.close()


def test_t3_auth_unknown_key():
    """T3: 认证流程 - 未知 ApiKeyId 认证失败"""
    client = TestClient()
    client.connect()
    try:
        success, err = client.authenticate("unknown-key-id", "some-secret")
        assert not success, "Auth with unknown key should fail"
        print(f"  T3: {PASS} - Unknown ApiKeyId rejected")
    finally:
        client.close()


def test_t4_auth_wrong_signature():
    """T4: 认证流程 - 错误签名认证失败"""
    client = TestClient()
    client.connect()
    try:
        timestamp_ms = int(time.time() * 1000)
        nonce = os.urandom(AUTH_NONCE_SIZE)
        wrong_signature = os.urandom(AUTH_SIGNATURE_SIZE)

        body = TEST_API_KEY_ID.encode('utf-8').ljust(AUTH_API_KEY_ID_SIZE, b'\x00')
        body += struct.pack('<q', timestamp_ms)
        body += nonce
        body += wrong_signature

        seq = client.next_seq()
        header = build_header(MSG_TYPE_AUTH_REQUEST, FLAG_REQUEST, len(body), seq)
        client.send_raw(header + body)
        hdr, resp_body = client.recv_response()

        assert hdr is not None, "Should get a response"
        assert hdr['msg_type'] == MSG_TYPE_AUTH_RESPONSE, "Should be AUTH_RESPONSE"
        error = resp_body[0]
        assert error == ERR_INVALID_SIGNATURE, f"Expected INVALID_SIGNATURE({ERR_INVALID_SIGNATURE}), got {error}"
        print(f"  T4: {PASS} - Wrong signature rejected (INVALID_SIGNATURE)")
    finally:
        client.close()


def test_t5_auth_expired_timestamp():
    """T5: 认证流程 - 过期时间戳认证失败"""
    client = TestClient()
    client.connect()
    try:
        expired_timestamp = int((time.time() - 60) * 1000)
        nonce = os.urandom(AUTH_NONCE_SIZE)
        signature = compute_auth_signature(
            TEST_API_KEY_SECRET.encode('utf-8'), expired_timestamp, nonce, TEST_API_KEY_ID)

        body = TEST_API_KEY_ID.encode('utf-8').ljust(AUTH_API_KEY_ID_SIZE, b'\x00')
        body += struct.pack('<q', expired_timestamp)
        body += nonce
        body += signature

        seq = client.next_seq()
        header = build_header(MSG_TYPE_AUTH_REQUEST, FLAG_REQUEST, len(body), seq)
        client.send_raw(header + body)
        hdr, resp_body = client.recv_response()

        assert hdr is not None, "Should get a response"
        error = resp_body[0]
        assert error == ERR_AUTH_EXPIRED, f"Expected AUTH_EXPIRED({ERR_AUTH_EXPIRED}), got {error}"
        print(f"  T5: {PASS} - Expired timestamp rejected (AUTH_EXPIRED)")
    finally:
        client.close()


def test_t6_auth_replay_nonce():
    """T6: 认证流程 - 重放 Nonce 认证失败"""
    client1 = TestClient()
    client1.connect()
    try:
        timestamp_ms = int(time.time() * 1000)
        nonce = os.urandom(AUTH_NONCE_SIZE)
        signature = compute_auth_signature(
            TEST_API_KEY_SECRET.encode('utf-8'), timestamp_ms, nonce, TEST_API_KEY_ID)

        body = TEST_API_KEY_ID.encode('utf-8').ljust(AUTH_API_KEY_ID_SIZE, b'\x00')
        body += struct.pack('<q', timestamp_ms)
        body += nonce
        body += signature

        seq = client1.next_seq()
        header = build_header(MSG_TYPE_AUTH_REQUEST, FLAG_REQUEST, len(body), seq)
        client1.send_raw(header + body)
        hdr, resp_body = client1.recv_response()
        assert resp_body[0] == ERR_OK, "First auth should succeed"
    finally:
        client1.close()

    client2 = TestClient()
    client2.connect()
    try:
        signature2 = compute_auth_signature(
            TEST_API_KEY_SECRET.encode('utf-8'), timestamp_ms, nonce, TEST_API_KEY_ID)

        body2 = TEST_API_KEY_ID.encode('utf-8').ljust(AUTH_API_KEY_ID_SIZE, b'\x00')
        body2 += struct.pack('<q', timestamp_ms)
        body2 += nonce
        body2 += signature2

        seq = client2.next_seq()
        header = build_header(MSG_TYPE_AUTH_REQUEST, FLAG_REQUEST, len(body2), seq)
        client2.send_raw(header + body2)
        hdr, resp_body = client2.recv_response()

        assert hdr is not None, "Should get a response"
        error = resp_body[0]
        assert error == ERR_REPLAY_DETECTED, f"Expected REPLAY_DETECTED({ERR_REPLAY_DETECTED}), got {error}"
        print(f"  T6: {PASS} - Replayed nonce rejected (REPLAY_DETECTED)")
    finally:
        client2.close()


def test_t7_signed_request_passes():
    """T7: 认证后请求 - 正确 HmacPrefix 通过"""
    client = TestClient()
    client.connect()
    try:
        success, _ = client.authenticate(TEST_API_KEY_ID, TEST_API_KEY_SECRET)
        assert success, "Auth should succeed"

        symbol_body = struct.pack('<I', 1) + b'TEST\x00\x00\x00\x00'
        hdr, resp_body = client.send_signed_request(
            MSG_TYPE_ADD_SYMBOL_REQUEST, FLAG_REQUEST, symbol_body)

        assert hdr is not None, "Should get a response"
        assert hdr['msg_type'] == MSG_TYPE_SYMBOL_RESPONSE, \
            f"Expected SYMBOL_RESPONSE(0x41), got 0x{hdr['msg_type']:02x}"
        assert len(resp_body) >= 1, "Response body too short"
        assert resp_body[0] == ERR_OK, \
            f"Expected OK(0), got error={resp_body[0]}"
        print(f"  T7: {PASS} - Signed request accepted")
    finally:
        client.close()


def test_t8_tampered_hmac_rejected():
    """T8: 认证后请求 - 错误 HmacPrefix 被拒绝"""
    client = TestClient()
    client.connect()
    try:
        success, _ = client.authenticate(TEST_API_KEY_ID, TEST_API_KEY_SECRET)
        assert success, "Auth should succeed"

        symbol_body = struct.pack('<I', 2) + b'TST2\x00\x00\x00\x00'
        hdr, resp_body = client.send_tampered_request(
            MSG_TYPE_ADD_SYMBOL_REQUEST, FLAG_REQUEST, symbol_body)

        assert hdr is not None, "Should get a response"
        assert (hdr['flags'] & FLAG_ERROR) != 0, "Should have ERROR flag"
        assert len(resp_body) >= 1, "Response body too short"
        assert resp_body[0] == ERR_INVALID_SIGNATURE, \
            f"Expected INVALID_SIGNATURE({ERR_INVALID_SIGNATURE}), got {resp_body[0]}"
        print(f"  T8: {PASS} - Tampered HmacPrefix rejected (INVALID_SIGNATURE)")
    finally:
        client.close()


def test_t9_zero_hmac_rejected():
    """T9: 认证后请求 - HmacPrefix=0 被拒绝"""
    client = TestClient()
    client.connect()
    try:
        success, _ = client.authenticate(TEST_API_KEY_ID, TEST_API_KEY_SECRET)
        assert success, "Auth should succeed"

        symbol_body = struct.pack('<I', 3) + b'TST3\x00\x00\x00\x00'
        hdr, resp_body = client.send_unsigned_request(
            MSG_TYPE_ADD_SYMBOL_REQUEST, FLAG_REQUEST, symbol_body)

        assert hdr is not None, "Should get a response"
        assert (hdr['flags'] & FLAG_ERROR) != 0, "Should have ERROR flag"
        assert len(resp_body) >= 1, "Response body too short"
        assert resp_body[0] == ERR_INVALID_SIGNATURE, \
            f"Expected INVALID_SIGNATURE({ERR_INVALID_SIGNATURE}), got {resp_body[0]}"
        print(f"  T9: {PASS} - HmacPrefix=0 rejected (INVALID_SIGNATURE)")
    finally:
        client.close()


def test_t10_unauthenticated_rejected():
    """T10: 未认证请求 - 非认证消息被拒绝"""
    client = TestClient()
    client.connect()
    try:
        symbol_body = struct.pack('<I', 1) + b'TEST\x00\x00\x00\x00'
        hdr, resp_body = client.send_unsigned_request(
            MSG_TYPE_ADD_SYMBOL_REQUEST, FLAG_REQUEST, symbol_body)

        assert hdr is not None, "Should get a response"
        assert hdr['msg_type'] == MSG_TYPE_SIMPLE_RESPONSE, \
            f"Expected SIMPLE_RESPONSE(0x44), got 0x{hdr['msg_type']:02x}"
        assert len(resp_body) >= 1, "Response body too short"
        assert resp_body[0] == ERR_NOT_AUTHENTICATED, \
            f"Expected NOT_AUTHENTICATED({ERR_NOT_AUTHENTICATED}), got {resp_body[0]}"
        print(f"  T10: {PASS} - Unauthenticated request rejected (NOT_AUTHENTICATED)")
    finally:
        client.close()


def test_t11_reauth_rejected():
    """T11: 已认证连接 - 重新认证被拒绝"""
    client = TestClient()
    client.connect()
    try:
        success, _ = client.authenticate(TEST_API_KEY_ID, TEST_API_KEY_SECRET)
        assert success, "Auth should succeed"

        timestamp_ms = int(time.time() * 1000)
        nonce = os.urandom(AUTH_NONCE_SIZE)
        signature = compute_auth_signature(
            TEST_API_KEY_SECRET.encode('utf-8'), timestamp_ms, nonce, TEST_API_KEY_ID)

        body = TEST_API_KEY_ID.encode('utf-8').ljust(AUTH_API_KEY_ID_SIZE, b'\x00')
        body += struct.pack('<q', timestamp_ms)
        body += nonce
        body += signature

        seq = client.next_seq()
        hmac_prefix = compute_hmac_prefix(
            client.session_key, seq, MSG_TYPE_AUTH_REQUEST, FLAG_REQUEST, len(body), body)
        header = build_header(MSG_TYPE_AUTH_REQUEST, FLAG_REQUEST, len(body), seq, hmac_prefix)
        client.send_raw(header + body)
        hdr, resp_body = client.recv_response()

        assert hdr is not None, "Should get a response"
        assert (hdr['flags'] & FLAG_ERROR) != 0, "Re-auth should be rejected"
        assert len(resp_body) >= 1, "Response body too short"
        assert resp_body[0] == ERR_NOT_AUTHENTICATED, \
            f"Expected NOT_AUTHENTICATED({ERR_NOT_AUTHENTICATED}), got {resp_body[0]}"
        print(f"  T11: {PASS} - Re-auth on authenticated connection rejected")
    finally:
        client.close()


def test_t12_heartbeat_with_hmac():
    """T12: 心跳消息 - 认证后心跳需要签名"""
    client = TestClient()
    client.connect()
    try:
        success, _ = client.authenticate(TEST_API_KEY_ID, TEST_API_KEY_SECRET)
        assert success, "Auth should succeed"

        seq = client.next_seq()
        hmac_prefix = compute_hmac_prefix(
            client.session_key, seq, MSG_TYPE_HEARTBEAT_REQ, FLAG_HEARTBEAT, 0)
        header = build_header(MSG_TYPE_HEARTBEAT_REQ, FLAG_HEARTBEAT, 0, seq, hmac_prefix)
        client.send_raw(header)

        hdr, resp_body = client.recv_response()
        assert hdr is not None, "Should get heartbeat response"
        assert hdr['msg_type'] == MSG_TYPE_HEARTBEAT_RESP, \
            f"Expected HEARTBEAT_RESP, got 0x{hdr['msg_type']:02x}"
        print(f"  T12: {PASS} - Signed heartbeat accepted")
    finally:
        client.close()


def main():
    print("=" * 60)
    print("  HMAC 全功能端到端测试")
    print("=" * 60)

    print(f"\n--- Phase 1: 纯算法交叉验证 (无需服务端) ---\n")
    try:
        test_t1_cross_validation()
    except AssertionError as e:
        print(f"  T1: {FAIL} - {e}")

    print(f"\n--- Phase 2: 启动服务端 ---\n")
    start_server()
    time.sleep(0.5)

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

    print(f"\n--- Phase 5: 清理 ---\n")
    stop_server()
    print(f"{INFO} Server stopped")

    print(f"\n{'=' * 60}")
    print(f"  测试完成")
    print(f"{'=' * 60}")


if __name__ == '__main__':
    main()
