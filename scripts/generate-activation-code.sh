#!/usr/bin/env bash
#
# 生成激活码并写入 D1 数据库
#
# 用法:
#   ./scripts/generate-activation-code.sh --uuid <device-uuid> --plan <1|2|3> [--days <N>] [--note <text>]
#
# 示例:
#   ./scripts/generate-activation-code.sh --uuid 550e8400-e29b-41d4-a716-446655440000 --plan 2 --days 365 --note "张三 Pro 年卡"
#   ./scripts/generate-activation-code.sh --uuid 550e8400-e29b-41d4-a716-446655440000 --plan 3              # 永久 Ultimate
#
# 依赖: wrangler (已登录), uuidgen 或 /proc/sys/kernel/random/uuid
#

set -euo pipefail

# ── 参数解析 ──

UUID=""
PLAN=""
DAYS=0
NOTE=""

while [[ $# -gt 0 ]]; do
    case "$1" in
        --uuid)  UUID="$2";  shift 2 ;;
        --plan)  PLAN="$2";  shift 2 ;;
        --days)  DAYS="$2";  shift 2 ;;
        --note)  NOTE="$2";  shift 2 ;;
        *)       echo "未知参数: $1"; exit 1 ;;
    esac
done

if [[ -z "$UUID" || -z "$PLAN" ]]; then
    echo "用法: $0 --uuid <device-uuid> --plan <1|2|3> [--days <N>] [--note <text>]"
    echo ""
    echo "  --plan 1  Basic"
    echo "  --plan 2  Pro"
    echo "  --plan 3  Ultimate"
    echo "  --days 0  永久 (默认)"
    echo "  --days N  激活后 N 天过期"
    exit 1
fi

if [[ "$PLAN" != "1" && "$PLAN" != "2" && "$PLAN" != "3" ]]; then
    echo "错误: plan 必须是 1 (Basic), 2 (Pro), 或 3 (Ultimate)"
    exit 1
fi

# ── 生成激活码 (UUID 格式) ──

if command -v uuidgen &>/dev/null; then
    CODE=$(uuidgen | tr '[:upper:]' '[:lower:]')
else
    CODE=$(cat /proc/sys/kernel/random/uuid 2>/dev/null || python3 -c "import uuid; print(uuid.uuid4())")
fi

# ── 等级名称 ──

case "$PLAN" in
    1) PLAN_NAME="Basic" ;;
    2) PLAN_NAME="Pro" ;;
    3) PLAN_NAME="Ultimate" ;;
esac

# ── 有效期描述 ──

if [[ "$DAYS" -eq 0 ]]; then
    EXPIRY_DESC="永久"
else
    EXPIRY_DESC="${DAYS} 天"
fi

# ── 写入 D1 ──

ESCAPED_NOTE=$(echo "$NOTE" | sed "s/'/''/g")

SQL="INSERT INTO activation_codes (code, device_uuid, plan, valid_days, note) VALUES ('${CODE}', '${UUID}', ${PLAN}, ${DAYS}, '${ESCAPED_NOTE}');"

echo "────────────────────────────────────"
echo "  激活码: ${CODE}"
echo "  设备:   ${UUID}"
echo "  等级:   ${PLAN_NAME} (${PLAN})"
echo "  有效期: ${EXPIRY_DESC}"
if [[ -n "$NOTE" ]]; then
    echo "  备注:   ${NOTE}"
fi
echo "────────────────────────────────────"
echo ""

cd "$(dirname "$0")/../donate-worker"

echo "正在写入 D1 数据库..."
npx wrangler d1 execute om-donate-db --remote --command="$SQL"

echo ""
echo "完成! 请将以下激活码发送给用户:"
echo ""
echo "  ${CODE}"
echo ""
