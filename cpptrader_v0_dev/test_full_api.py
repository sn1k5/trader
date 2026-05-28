#!/usr/bin/env python3
"""
CppTrader Java-Admin 全功能 API 测试脚本
用法: python test_full_api.py [--username admin] [--password 123456]
"""

import sys
import json
import base64
import argparse
from datetime import datetime
from urllib.parse import urlencode

import requests

BASE_URL = "http://localhost:8082/api"


class TestRunner:
    def __init__(self):
        self.access_token = ""
        self.refresh_token = ""
        self.user_id = None
        self.pass_count = 0
        self.fail_count = 0
        self.skip_count = 0
        self.results = []

    # ── 输出辅助 ──────────────────────────────────────────

    @staticmethod
    def _color(text, code):
        return f"\033[{code}m{text}\033[0m"

    def print_color(self, text, color="white"):
        color_map = {
            "white": "0", "red": "31", "green": "32",
            "yellow": "33", "cyan": "36", "dark_gray": "90",
        }
        code = color_map.get(color, "0")
        print(self._color(text, code))

    def print_section(self, title):
        print()
        self.print_color("=" * 60, "dark_gray")
        self.print_color(f"  {title}", "cyan")
        self.print_color("=" * 60, "dark_gray")

    def print_test(self, name, status, detail=""):
        icon = {"PASS": "✓", "FAIL": "✗", "SKIP": "○"}.get(status, "?")
        color = {"PASS": "green", "FAIL": "red", "SKIP": "yellow"}.get(status, "white")
        msg = f"  {icon} {name}"
        if detail:
            msg += f" — {detail}"
        self.print_color(msg, color)

    # ── HTTP 请求 ─────────────────────────────────────────

    def invoke_api(self, method, path, query=None, body=None, no_auth=False):
        url = f"{BASE_URL}{path}"
        if query:
            url += "?" + urlencode(query)

        headers = {"Content-Type": "application/json", "Accept": "application/json"}
        if not no_auth and self.access_token:
            headers["Authorization"] = f"Bearer {self.access_token}"

        try:
            resp = requests.request(method, url, headers=headers,
                                    json=body, timeout=10)
            try:
                data = resp.json()
            except ValueError:
                data = {"raw": resp.text}
            return {"status": resp.status_code, "data": data}
        except requests.ConnectionError:
            return {"status": 0, "data": {}, "error": "连接失败"}
        except requests.Timeout:
            return {"status": 0, "data": {}, "error": "请求超时"}
        except Exception as e:
            return {"status": 0, "data": {}, "error": str(e)}

    # ── 断言辅助 ──────────────────────────────────────────

    @staticmethod
    def get_field(result, field):
        data = result.get("data", {})
        if isinstance(data, dict):
            return data.get(field)
        return None

    def assert_field(self, result, field, expected=None, not_empty=False):
        val = self.get_field(result, field)
        if not_empty:
            return val is not None and str(val).strip() != ""
        if expected is not None:
            return str(val) == str(expected)
        return val is not None

    # ── JWT 解析 ──────────────────────────────────────────

    @staticmethod
    def parse_jwt_payload(token):
        try:
            payload_b64 = token.split(".")[1]
            pad = 4 - len(payload_b64) % 4
            if pad < 4:
                payload_b64 += "=" * pad
            payload_bytes = base64.urlsafe_b64decode(payload_b64)
            return json.loads(payload_bytes)
        except Exception:
            return None

    # ── 核心测试函数 ──────────────────────────────────────

    def test_api(self, name, method="GET", path="", query=None, body=None,
                 no_auth=False, expect_status=200, assert_fields=None,
                 assert_values=None, custom_assert=None):
        result = self.invoke_api(method, path, query=query, body=body, no_auth=no_auth)

        passed = True
        detail = ""

        # 断言1: HTTP 状态码
        if result["status"] != expect_status:
            passed = False
            detail = f"HTTP {result['status']} (expected {expect_status})"

        # 断言2: 字段非空
        if passed and assert_fields:
            for field in assert_fields:
                if not self.assert_field(result, field, not_empty=True):
                    passed = False
                    detail = f"missing field: {field}"
                    break

        # 断言3: 字段值
        if passed and assert_values:
            for key, expected in assert_values.items():
                if not self.assert_field(result, key, expected=expected):
                    passed = False
                    actual = self.get_field(result, key)
                    detail = f"{key}={actual} (expected {expected})"
                    break

        # 断言4: 自定义
        if passed and custom_assert:
            try:
                msg = custom_assert(result)
                if isinstance(msg, str):
                    passed = False
                    detail = msg
            except Exception as e:
                passed = False
                detail = f"custom assert error: {e}"

        if passed:
            self.pass_count += 1
            self.print_test(name, "PASS")
        else:
            self.fail_count += 1
            self.print_test(name, "FAIL", detail)

        self.results.append({"name": name, "status": "PASS" if passed else "FAIL", "detail": detail})
        return result

    def skip_test(self, name, reason=""):
        self.skip_count += 1
        self.print_test(name, "SKIP", reason)
        self.results.append({"name": name, "status": "SKIP", "detail": reason})

    # ── 摘要输出 ──────────────────────────────────────────

    def show_summary(self):
        self.print_section("测试摘要")
        total = self.pass_count + self.fail_count + self.skip_count
        print()
        self.print_color(f"  总计: {total}", "white")
        self.print_color(f"  通过: {self.pass_count}", "green")
        self.print_color(f"  失败: {self.fail_count}", "red")
        self.print_color(f"  跳过: {self.skip_count}", "yellow")
        print()

        if self.fail_count > 0:
            self.print_color("  失败测试详情:", "red")
            for r in self.results:
                if r["status"] == "FAIL":
                    self.print_color(f"    ✗ {r['name']} — {r['detail']}", "red")
            print()

        rate = round(self.pass_count / total * 100, 1) if total > 0 else 0
        rate_color = "green" if rate >= 80 else ("yellow" if rate >= 50 else "red")
        self.print_color(f"  通过率: {rate}%", rate_color)
        print()

        if self.fail_count > 0:
            self.print_color("  结果: FAILED", "red")
            sys.exit(1)
        else:
            self.print_color("  结果: PASSED", "green")
            sys.exit(0)


def main():
    parser = argparse.ArgumentParser(description="CppTrader Java-Admin 全功能 API 测试脚本")
    parser.add_argument("--username", default="", help="登录用户名")
    parser.add_argument("--password", default="", help="登录密码")
    args = parser.parse_args()

    t = TestRunner()

    t.print_color(
        "╔══════════════════════════════════════════════════════════════╗\n"
        "║         CppTrader Java-Admin 全功能 API 测试脚本            ║\n"
        "║  用法: python test_full_api.py [--username user] [--password pwd] ║\n"
        "╚══════════════════════════════════════════════════════════════╝",
        "yellow",
    )

    # ================================================================
    # 阶段 0: 服务连通性预检
    # ================================================================
    t.print_section("阶段 0: 服务连通性预检")

    health = t.invoke_api("GET", "/status", no_auth=True)
    if health["status"] == 0:
        print()
        t.print_color(f"  ✗ 无法连接到 {BASE_URL}", "red")
        t.print_color("    请确认 java-admin 服务已启动（端口 8082）", "red")
        t.print_color("    启动命令: cd java-admin && mvn spring-boot:run", "yellow")
        print()
        t.fail_count += 1
        t.results.append({"name": "服务连通性", "status": "FAIL", "detail": f"无法连接到 {BASE_URL}"})
        t.show_summary()
    t.print_color(f"  ✓ 服务可达 (HTTP {health['status']})", "green")

    # ================================================================
    # 阶段 1: 认证登录
    # ================================================================
    t.print_section("阶段 1: 认证登录")

    if args.username and args.password:
        test_username = args.username
        test_password = args.password
        t.print_color(f"  → 使用命令行传入的凭据: {test_username}", "dark_gray")
    else:
        ts = datetime.now().strftime("%Y%m%d_%H%M%S")
        test_username = f"apitest_{ts}"
        test_password = "Test@123456"
        t.print_color(f"  → 使用自动生成的凭据: {test_username}", "dark_gray")

    auth_success = False

    # 策略1: 注册新用户
    t.print_color("  [策略1] 尝试注册新用户...", "dark_gray")
    reg = t.invoke_api("POST", "/auth/register",
                       body={"username": test_username, "password": test_password}, no_auth=True)
    if reg["status"] == 200:
        t.access_token = t.get_field(reg, "accessToken") or ""
        t.refresh_token = t.get_field(reg, "refreshToken") or ""
        t.user_id = t.get_field(reg, "userId")
        if t.access_token:
            auth_success = True
            t.print_test("注册新用户", "PASS")
            t.print_color(f"    → userId={t.user_id}, token 已获取", "dark_gray")
    else:
        err = reg.get("error") or f"HTTP {reg['status']}"
        t.print_color(f"    → 注册失败: {err}", "yellow")

    # 策略2: 用相同凭据登录
    if not auth_success:
        t.print_color("  [策略2] 尝试用相同凭据登录...", "dark_gray")
        login = t.invoke_api("POST", "/auth/login",
                             body={"username": test_username, "password": test_password}, no_auth=True)
        if login["status"] == 200:
            t.access_token = t.get_field(login, "accessToken") or ""
            t.refresh_token = t.get_field(login, "refreshToken") or ""
            t.user_id = t.get_field(login, "userId")
            if t.access_token:
                auth_success = True
                t.print_test("登录已有用户", "PASS")
                t.print_color(f"    → userId={t.user_id}, token 已获取", "dark_gray")
        else:
            err = login.get("error") or f"HTTP {login['status']}"
            t.print_color(f"    → 登录失败: {err}", "yellow")

    # 策略3: 尝试默认测试账户
    if not auth_success:
        t.print_color("  [策略3] 尝试默认测试账户 (admin/admin123)...", "dark_gray")
        default = t.invoke_api("POST", "/auth/login",
                               body={"username": "admin", "password": "admin123"}, no_auth=True)
        if default["status"] == 200:
            t.access_token = t.get_field(default, "accessToken") or ""
            t.refresh_token = t.get_field(default, "refreshToken") or ""
            t.user_id = t.get_field(default, "userId")
            if t.access_token:
                auth_success = True
                test_username = "admin"
                t.print_test("默认账户登录", "PASS")
                t.print_color(f"    → userId={t.user_id}, token 已获取", "dark_gray")
        else:
            t.print_color("    → 默认账户登录也失败", "yellow")

    # 从 JWT 解析 userId
    if auth_success and t.user_id is None and t.access_token:
        payload = t.parse_jwt_payload(t.access_token)
        if payload:
            t.user_id = payload.get("userId") or payload.get("sub")
            t.print_color(f"    → 从 JWT 解析 userId={t.user_id}", "dark_gray")

    # 认证失败则终止
    if not auth_success or not t.access_token:
        print()
        t.print_color("  ✗ 所有认证策略均失败，无法获取 Token", "red")
        t.print_color("    后续所有认证接口都会返回 401，测试无意义", "red")
        print()
        t.print_color("  可能原因:", "yellow")
        t.print_color("    1. 数据库未初始化（检查 MySQL 连接和建表脚本）", "yellow")
        t.print_color("    2. 用户不存在（先手动注册: POST /api/auth/register）", "yellow")
        t.print_color("    3. 密码错误（用 --username --password 传入正确凭据）", "yellow")
        print()
        t.print_color("  用法示例:", "cyan")
        t.print_color("    python test_full_api.py --username admin --password yourpassword", "cyan")
        print()
        t.fail_count += 1
        t.results.append({"name": "认证登录", "status": "FAIL", "detail": "所有策略均失败"})
        t.show_summary()

    t.pass_count += 1
    t.print_color(f"  ✓ 认证成功! 用户: {test_username}, userId: {t.user_id}", "green")

    # 测试刷新 Token
    refresh = t.test_api("刷新 Token", method="POST", path="/auth/refresh",
                         body={"refreshToken": t.refresh_token}, no_auth=True,
                         assert_fields=["accessToken"])
    if refresh["status"] == 200:
        new_token = t.get_field(refresh, "accessToken")
        if new_token:
            t.access_token = new_token
            new_refresh = t.get_field(refresh, "refreshToken")
            if new_refresh:
                t.refresh_token = new_refresh

    # 测试无 Token 访问
    unauth = t.invoke_api("GET", "/users/me", no_auth=True)
    if unauth["status"] in (401, 403):
        t.pass_count += 1
        t.print_test("无 Token 访问受保护接口返回 401/403", "PASS", f"HTTP {unauth['status']}")
    else:
        t.fail_count += 1
        t.print_test("无 Token 访问受保护接口返回 401/403", "FAIL", f"HTTP {unauth['status']}")

    # ================================================================
    # 模块 2: 用户与账户
    # ================================================================
    t.print_section("模块 2: 用户与账户 (User & Account)")

    t.test_api("获取当前用户信息", method="GET", path="/users/me",
               assert_fields=["id", "username", "role"])

    t.test_api("获取账户列表", method="GET", path="/accounts")

    t.test_api("获取持仓列表", method="GET", path="/positions")

    # ================================================================
    # 模块 3: 余额
    # ================================================================
    t.print_section("模块 3: 余额 (Balance)")

    if t.user_id is not None:
        uid = str(t.user_id)

        t.test_api("初始化账户余额", method="POST", path="/balance/init",
                   query={"userId": uid, "initialBalance": "10000"},
                   assert_fields=["userId", "initialBalance", "status"],
                   assert_values={"status": "initialized"})

        t.test_api("查询余额", method="GET", path=f"/balance/{uid}",
                   assert_fields=["userId", "available"])

        t.test_api("增加余额", method="POST", path="/balance/add",
                   query={"userId": uid, "amount": "5000", "bizId": "test-add-001"},
                   assert_fields=["userId", "amount", "success"],
                   assert_values={"success": True})

        t.test_api("冻结余额", method="POST", path="/balance/freeze",
                   query={"userId": uid, "amount": "2000", "bizId": "test-freeze-001"},
                   assert_fields=["userId", "amount", "success"],
                   assert_values={"success": True})

        t.test_api("解冻余额", method="POST", path="/balance/unfreeze",
                   query={"userId": uid, "amount": "2000", "bizId": "test-unfreeze-001"},
                   assert_fields=["userId", "amount", "success"],
                   assert_values={"success": True})

        t.test_api("扣减余额", method="POST", path="/balance/deduct",
                   query={"userId": uid, "amount": "1000", "bizId": "test-deduct-001"},
                   assert_fields=["userId", "amount", "success"])
    else:
        for name in ["初始化账户余额", "查询余额", "增加余额", "冻结余额", "解冻余额", "扣减余额"]:
            t.skip_test(name, "无 userId")

    # ================================================================
    # 模块 4: 风控
    # ================================================================
    t.print_section("模块 4: 风控 (Risk)")

    t.test_api("获取风控规则列表", method="GET", path="/risk/rules")

    rule_body = {
        "ruleName": "test_single_limit",
        "ruleType": "SINGLE_LIMIT",
        "params": '{"maxAmount":50000}',
        "description": "API test rule",
        "enabled": 1,
        "priority": 0,
    }

    rule_result = t.test_api("创建风控规则", method="POST", path="/risk/rules",
                             body=rule_body, assert_fields=["ruleName", "ruleType"])

    rule_id = None
    if rule_result["status"] == 200:
        rule_id = t.get_field(rule_result, "id")

    if rule_id is not None:
        t.test_api("更新风控规则", method="PUT", path=f"/risk/rules/{rule_id}",
                   body={"ruleName": "test_single_limit_updated", "ruleType": "SINGLE_LIMIT",
                         "params": '{"maxAmount":80000}', "enabled": 1},
                   assert_fields=["ruleName"],
                   assert_values={"ruleName": "test_single_limit_updated"})
    else:
        t.skip_test("更新风控规则", "无 ruleId")

    if t.user_id is not None:
        t.test_api("查询风控告警", method="GET", path="/risk/alerts",
                   query={"userId": str(t.user_id)})
    else:
        t.skip_test("查询风控告警", "无 userId")

    # ================================================================
    # 模块 5: 交易
    # ================================================================
    t.print_section("模块 5: 交易 (Trading)")

    t.test_api("系统状态", method="GET", path="/status",
               assert_fields=["connected"])

    t.test_api("启用撮合引擎", method="POST", path="/matching/enable")

    t.test_api("添加交易品种 BTCUSD", method="POST", path="/symbols",
               query={"id": "1", "name": "BTCUSD"})

    t.test_api("获取交易品种", method="GET", path="/symbols/1")

    t.test_api("添加订单簿", method="POST", path="/orderbooks",
               query={"symbolId": "1"})

    t.test_api("添加买单 (50000 x 10)", method="POST", path="/orders",
               query={"symbolId": "1", "side": "BUY", "type": "LIMIT",
                      "price": "50000", "quantity": "10"})

    t.test_api("添加卖单 (51000 x 5)", method="POST", path="/orders",
               query={"symbolId": "1", "side": "SELL", "type": "LIMIT",
                      "price": "51000", "quantity": "5"})

    t.test_api("获取订单簿深度", method="GET", path="/orderbooks/1",
               query={"depth": "5"})

    t.test_api("获取订单详情 (1001)", method="GET", path="/orders/1001")

    t.test_api("添加可成交卖单 (50000 x 3)", method="POST", path="/orders",
               query={"symbolId": "1", "side": "SELL", "type": "LIMIT",
                      "price": "50000", "quantity": "3"})

    t.test_api("删除订单 (1001)", method="DELETE", path="/orders/1001")

    t.test_api("删除订单簿", method="DELETE", path="/orderbooks/1")

    t.test_api("删除交易品种", method="DELETE", path="/symbols/1")

    t.test_api("禁用撮合引擎", method="POST", path="/matching/disable")

    # ================================================================
    # 模块 6: 订单查询
    # ================================================================
    t.print_section("模块 6: 订单查询 (Order Query)")

    t.test_api("查询订单历史", method="GET", path="/orders/history",
               query={"page": "0", "size": "10"},
               assert_fields=["content", "totalElements", "totalPages", "page"])

    t.test_api("查询成交明细", method="GET", path="/orders/executions",
               query={"page": "0", "size": "10"},
               assert_fields=["content", "totalElements", "totalPages", "page"])

    t.test_api("查询指定订单成交明细", method="GET", path="/orders/1001/executions",
               assert_fields=["orderId", "executions", "count"])

    # ================================================================
    # 模块 7: 报表
    # ================================================================
    t.print_section("模块 7: 报表 (Report)")

    today = datetime.now().strftime("%Y-%m-%d")

    t.test_api("日交易统计", method="GET", path="/reports/daily",
               query={"date": today})

    t.test_api("盈亏分析", method="GET", path="/reports/pnl")

    t.test_api("资金流水", method="GET", path="/reports/fund-flow")

    t.test_api("账户总览", method="GET", path="/reports/summary")

    # ================================================================
    # 模块 8: 对账
    # ================================================================
    t.print_section("模块 8: 对账 (Reconcile)")

    t.test_api("执行对账", method="POST", path="/reconcile/run",
               assert_fields=["mismatchCount", "diffs"])

    t.test_api("查询未修复差异", method="GET", path="/reconcile/unfixed",
               assert_fields=["count", "diffs"])

    if t.user_id is not None:
        t.test_api("自动修复差异", method="POST", path=f"/reconcile/fix/{t.user_id}",
                   assert_fields=["userId", "status"],
                   assert_values={"status": "fixed"})
    else:
        t.skip_test("自动修复差异", "无 userId")

    # ================================================================
    # 模块 9: Outbox 管理
    # ================================================================
    t.print_section("模块 9: Outbox 管理")

    dead_result = t.test_api("查询死信消息", method="GET", path="/outbox/dead",
                             assert_fields=["count", "messages"])

    dead_msg_id = None
    if dead_result["status"] == 200:
        messages = t.get_field(dead_result, "messages") or []
        if messages:
            first = messages[0]
            dead_msg_id = first.get("id") if isinstance(first, dict) else None

    if dead_msg_id is not None:
        t.test_api("重新激活死信消息", method="POST", path=f"/outbox/reactivate/{dead_msg_id}",
                   assert_fields=["id", "status"],
                   assert_values={"status": "reactivated"})
    else:
        t.skip_test("重新激活死信消息", "无死信消息可激活")

    # ================================================================
    # 测试摘要
    # ================================================================
    t.show_summary()


if __name__ == "__main__":
    main()
