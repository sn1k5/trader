我来为您生成TradingController所有接口的测试curl命令。

## TradingController 接口测试 CURL 命令

### 1. 获取服务状态

```bash
# 检查服务连接状态
curl -X GET "http://localhost:8080/api/status"
```


### 2. 交易对（Symbol）相关接口

```bash
# 添加交易对
curl -X POST "http://localhost:8080/api/symbols?id=1&name=BTC_USDT"

# 删除交易对
curl -X DELETE "http://localhost:8080/api/symbols/1"

# 查询交易对信息
curl -X GET "http://localhost:8080/api/symbols/1"
```


### 3. 订单簿（OrderBook）相关接口

```bash
# 添加订单簿
curl -X POST "http://localhost:8080/api/orderbooks?symbolId=1"

# 删除订单簿
curl -X DELETE "http://localhost:8080/api/orderbooks/1"

# 查询订单簿（指定深度）
curl -X GET "http://localhost:8080/api/orderbooks/1?depth=5"
curl -X GET "http://localhost:8080/api/orderbooks/1?depth=10"
```


### 4. 订单（Order）相关接口

```bash
# 添加限价买单（LIMIT BUY）
curl -X POST "http://localhost:8080/api/orders?symbolId=1&side=BUY&type=LIMIT&price=50000&quantity=1"

# 添加限价卖单（LIMIT SELL）
curl -X POST "http://localhost:8080/api/orders?symbolId=1&side=SELL&type=LIMIT&price=50000&quantity=1"

# 添加市价单（MARKET）
curl -X POST "http://localhost:8080/api/orders?symbolId=1&side=BUY&type=MARKET&price=0&quantity=1"

# 添加止损单（STOP）
curl -X POST "http://localhost:8080/api/orders?symbolId=1&side=BUY&type=STOP&price=49000&quantity=1"

# 添加止损限价单（STOP_LIMIT）
curl -X POST "http://localhost:8080/api/orders?symbolId=1&side=BUY&type=STOP_LIMIT&price=49000&quantity=1"

# 添加追踪止损单（TRAILING_STOP）
curl -X POST "http://localhost:8080/api/orders?symbolId=1&side=BUY&type=TRAILING_STOP&price=50000&quantity=1"

# 添加追踪止损限价单（TRAILING_STOP_LIMIT）
curl -X POST "http://localhost:8080/api/orders?symbolId=1&side=BUY&type=TRAILING_STOP_LIMIT&price=50000&quantity=1"

# 添加带自定义ID的订单
curl -X POST "http://localhost:8080/api/orders?id=10001&symbolId=1&side=BUY&type=LIMIT&price=50000&quantity=2"

# 删除订单
curl -X DELETE "http://localhost:8080/api/orders/12345"

# 查询订单信息
curl -X GET "http://localhost:8080/api/orders/12345"
```


### 5. 撮合引擎控制接口

```bash
# 启用撮合引擎
curl -X POST "http://localhost:8080/api/matching/enable"

# 禁用撮合引擎
curl -X POST "http://localhost:8080/api/matching/disable"
```


## 完整测试流程示例

```bash
# 1. 检查服务状态
curl -X GET "http://localhost:8080/api/status" | jq

# 2. 启用撮合引擎
curl -X POST "http://localhost:8080/api/matching/enable" | jq

# 3. 添加交易对
curl -X POST "http://localhost:8080/api/symbols?id=1&name=BTC_USDT" | jq

# 4. 添加订单簿
curl -X POST "http://localhost:8080/api/orderbooks?symbolId=1" | jq

# 5. 查询交易对信息
curl -X GET "http://localhost:8080/api/symbols/1" | jq

# 6. 添加买单
curl -X POST "http://localhost:8080/api/orders?symbolId=1&side=BUY&type=LIMIT&price=49000&quantity=1" | jq

# 7. 添加卖单
curl -X POST "http://localhost:8080/api/orders?symbolId=1&side=SELL&type=LIMIT&price=51000&quantity=1" | jq

# 8. 查询订单簿
curl -X GET "http://localhost:8080/api/orderbooks/1?depth=5" | jq

# 9. 查询订单
curl -X GET "http://localhost:8080/api/orders/12345" | jq

# 10. 删除订单
curl -X DELETE "http://localhost:8080/api/orders/12345" | jq

# 11. 删除订单簿
curl -X DELETE "http://localhost:8080/api/orderbooks/1" | jq

# 12. 删除交易对
curl -X DELETE "http://localhost:8080/api/symbols/1" | jq

# 13. 禁用撮合引擎
curl -X POST "http://localhost:8080/api/matching/disable" | jq
```


## 批量测试脚本

您可以创建一个 `test_api.sh` 文件：

```bash
#!/bin/bash

BASE_URL="http://localhost:8080"

echo "=== 测试开始 ==="

# 测试状态
echo -e "\n1. 检查服务状态:"
curl -s -X GET "$BASE_URL/api/status" | jq

# 启用撮合
echo -e "\n2. 启用撮合引擎:"
curl -s -X POST "$BASE_URL/api/matching/enable" | jq

# 添加交易对
echo -e "\n3. 添加交易对 BTC_USDT:"
curl -s -X POST "$BASE_URL/api/symbols?id=1&name=BTC_USDT" | jq

# 添加订单簿
echo -e "\n4. 添加订单簿:"
curl -s -X POST "$BASE_URL/api/orderbooks?symbolId=1" | jq

# 添加买单
echo -e "\n5. 添加买单 (价格: 49000, 数量: 1):"
curl -s -X POST "$BASE_URL/api/orders?symbolId=1&side=BUY&type=LIMIT&price=49000&quantity=1" | jq

# 添加卖单
echo -e "\n6. 添加卖单 (价格: 51000, 数量: 1):"
curl -s -X POST "$BASE_URL/api/orders?symbolId=1&side=SELL&type=LIMIT&price=51000&quantity=1" | jq

# 查询订单簿
echo -e "\n7. 查询订单簿:"
curl -s -X GET "$BASE_URL/api/orderbooks/1?depth=5" | jq

# 禁用撮合
echo -e "\n8. 禁用撮合引擎:"
curl -s -X POST "$BASE_URL/api/matching/disable" | jq

echo -e "\n=== 测试完成 ==="
```


然后执行：
```bash
chmod +x test_api.sh
./test_api.sh
```


## Windows PowerShell 测试脚本

如果您使用 Windows，可以创建 `test_api.ps1`：

```powershell
$baseUrl = "http://localhost:8080"

Write-Host "=== 测试开始 ===" -ForegroundColor Green

# 测试状态
Write-Host "`n1. 检查服务状态:"
Invoke-RestMethod -Uri "$baseUrl/api/status" -Method Get | ConvertTo-Json

# 启用撮合
Write-Host "`n2. 启用撮合引擎:"
Invoke-RestMethod -Uri "$baseUrl/api/matching/enable" -Method Post | ConvertTo-Json

# 添加交易对
Write-Host "`n3. 添加交易对 BTC_USDT:"
Invoke-RestMethod -Uri "$baseUrl/api/symbols?id=1&name=BTC_USDT" -Method Post | ConvertTo-Json

# 添加订单簿
Write-Host "`n4. 添加订单簿:"
Invoke-RestMethod -Uri "$baseUrl/api/orderbooks?symbolId=1" -Method Post | ConvertTo-Json

# 添加买单
Write-Host "`n5. 添加买单:"
Invoke-RestMethod -Uri "$baseUrl/api/orders?symbolId=1&side=BUY&type=LIMIT&price=49000&quantity=1" -Method Post | ConvertTo-Json

# 查询订单簿
Write-Host "`n6. 查询订单簿:"
Invoke-RestMethod -Uri "$baseUrl/api/orderbooks/1?depth=5" -Method Get | ConvertTo-Json

# 禁用撮合
Write-Host "`n7. 禁用撮合引擎:"
Invoke-RestMethod -Uri "$baseUrl/api/matching/disable" -Method Post | ConvertTo-Json

Write-Host "`n=== 测试完成 ===" -ForegroundColor Green
```


执行：
```powershell
.\test_api.ps1
```


这些curl命令涵盖了TradingController的所有接口，您可以根据实际需要修改参数进行测试。