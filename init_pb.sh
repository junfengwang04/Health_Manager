#!/usr/bin/env bash
set -e

# PocketBase 初始化脚本
# 创建老人、子女、绑定关系、健康快照、绑定验证码、总药品库六个核心 collection

HOST="${PB_HOST:-http://your-pocketbase-host:8090}"
ADMIN_EMAIL="${PB_ADMIN_EMAIL:-your_admin_email@example.com}"
ADMIN_PASS="${PB_ADMIN_PASSWORD:-your_admin_password}"

echo "[1/4] 管理员登录..."
TOKEN=$(curl -s -X POST \
  "$HOST/api/admins/auth-with-password" \
  -H "Content-Type: application/json" \
  -d "{\"identity\":\"$ADMIN_EMAIL\",\"password\":\"$ADMIN_PASS\"}" | \
  jq -r '.token')

if [ "$TOKEN" = "null" ] || [ -z "$TOKEN" ]; then
  echo "管理员登录失败，请检查账号密码或服务器状态"
  exit 1
fi

echo "Token: $TOKEN"

# 通用函数：创建 collection
create_collection() {
  local name="$1"
  local schema="$2"
  echo "[2/4] 创建 collection: $name..."
  curl -s -X POST \
    "$HOST/api/collections" \
    -H "Authorization: Bearer $TOKEN" \
    -H "Content-Type: application/json" \
    -d "$schema" | jq -r '.id'
}

# 1. 老人用户表
echo "创建 elder_users..."
elder_schema='{
  "name": "elder_users",
  "type": "auth",
  "schema": [
    {
      "name": "phone",
      "type": "text",
      "required": true,
      "unique": true,
      "options": {
        "min": 11,
        "max": 11
      }
    },
    {
      "name": "name",
      "type": "text",
      "required": false
    },
    {
      "name": "deviceCode",
      "type": "text",
      "required": false
    }
  ],
  "indexes": ["phone"],
  "authRule": {
    "allowEmailAuth": false,
    "allowOAuth2Auth": false,
    "allowUsernameAuth": true,
    "exceptEmailDomains": [],
    "manageRule": null,
    "minPasswordLength": 6,
    "onlyEmailDomains": [],
    "requireEmail": false
  }
}'
create_collection "elder_users" "$elder_schema"

# 2. 子女用户表
echo "创建 guardian_users..."
guardian_schema='{
  "name": "guardian_users",
  "type": "auth",
  "schema": [
    {
      "name": "phone",
      "type": "text",
      "required": true,
      "unique": true,
      "options": {
        "min": 11,
        "max": 11
      }
    },
    {
      "name": "name",
      "type": "text",
      "required": false
    }
  ],
  "indexes": ["phone"],
  "authRule": {
    "allowEmailAuth": false,
    "allowOAuth2Auth": false,
    "allowUsernameAuth": true,
    "exceptEmailDomains": [],
    "manageRule": null,
    "minPasswordLength": 6,
    "onlyEmailDomains": [],
    "requireEmail": false
  }
}'
create_collection "guardian_users" "$guardian_schema"

# 3. 绑定关系表
echo "创建 guardian_elder..."
bind_schema='{
  "name": "guardian_elder",
  "type": "base",
  "schema": [
    {
      "name": "guardianPhone",
      "type": "text",
      "required": true
    },
    {
      "name": "elderPhone",
      "type": "text",
      "required": true
    },
    {
      "name": "status",
      "type": "select",
      "required": true,
      "options": {
        "values": ["ACTIVE", "INACTIVE"],
        "maxSelect": 1
      }
    }
  ],
  "indexes": ["guardianPhone", "elderPhone"]
}'
create_collection "guardian_elder" "$bind_schema"

# 4. 老人健康快照表
echo "创建 elder_health_sync..."
sync_schema='{
  "name": "elder_health_sync",
  "type": "base",
  "schema": [
    {
      "name": "elderPhone",
      "type": "text",
      "required": true,
      "unique": true
    },
    {
      "name": "payload",
      "type": "json",
      "required": true
    },
    {
      "name": "updatedAtClient",
      "type": "number",
      "required": true
    }
  ],
  "indexes": ["elderPhone"]
}'
create_collection "elder_health_sync" "$sync_schema"

# 5. 绑定验证码表（老人生成验证码，子女输入验证码完成绑定）
echo "创建 bind_verify_codes..."
code_schema='{
  "name": "bind_verify_codes",
  "type": "base",
  "schema": [
    {
      "name": "elderPhone",
      "type": "text",
      "required": true
    },
    {
      "name": "code",
      "type": "text",
      "required": true
    },
    {
      "name": "expiresAt",
      "type": "number",
      "required": true
    },
    {
      "name": "used",
      "type": "bool",
      "required": false
    }
  ],
  "indexes": ["elderPhone", "code", "used"]
}'
create_collection "bind_verify_codes" "$code_schema"

# 6. 总药品库（与本地Room Medication结构对齐）
echo "创建 medication_catalog..."
catalog_schema='{
  "name": "medication_catalog",
  "type": "base",
  "schema": [
    { "name": "approvalNumber", "type": "text", "required": true, "unique": true },
    { "name": "name", "type": "text", "required": true },
    { "name": "manufacturer", "type": "text", "required": false },
    { "name": "timesPerDay", "type": "number", "required": false },
    { "name": "dosePerTime", "type": "text", "required": false },
    { "name": "mealRelation", "type": "text", "required": false },
    { "name": "reminderTimesJson", "type": "text", "required": false },
    { "name": "contraindications", "type": "text", "required": false },
    { "name": "expiryDate", "type": "text", "required": false }
  ],
  "indexes": ["approvalNumber"]
}'
create_collection "medication_catalog" "$catalog_schema"

echo "[3/4] 插入测试数据..."
# 老人测试账号
curl -s -X POST \
  "$HOST/api/collections/elder_users/records" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "username": "13800000001",
    "password": "your_test_password",
    "passwordConfirm": "your_test_password",
    "phone": "13800000001",
    "name": "测试老人A",
    "deviceCode": "100001"
  }' | jq -r '.id'

# 子女测试账号
curl -s -X POST \
  "$HOST/api/collections/guardian_users/records" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "username": "13900000001",
    "password": "your_test_password",
    "passwordConfirm": "your_test_password",
    "phone": "13900000001",
    "name": "测试子女A"
  }' | jq -r '.id'

# 绑定关系
curl -s -X POST \
  "$HOST/api/collections/guardian_elder/records" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "guardianPhone": "13900000001",
    "elderPhone": "13800000001",
    "status": "ACTIVE"
  }' | jq -r '.id'

# 总药品库测试数据
curl -s -X POST \
  "$HOST/api/collections/medication_catalog/records" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "approvalNumber": "国药准字H20003263",
    "name": "阿莫西林胶囊",
    "manufacturer": "珠海联邦制药有限公司",
    "timesPerDay": 3,
    "dosePerTime": "1粒",
    "mealRelation": "饭后",
    "reminderTimesJson": "[]",
    "contraindications": "对青霉素过敏者禁用"
  }' | jq -r '.id'

echo "[4/4] 初始化完成！"
echo "测试账号："
echo "  老人端 - 账号：13800000001  密码：your_test_password"
echo "  子女端 - 账号：13900000001  密码：your_test_password"
echo "绑定方式：老人端生成6位验证码，子女端输入老人手机号+验证码完成绑定。"
