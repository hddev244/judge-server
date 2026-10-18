#!/usr/bin/env bash
# Tạo admin key (một lần) + key GRADER cho Thi247.
#   ./scripts/bootstrap-grader.sh --local
#   ./scripts/bootstrap-grader.sh --url http://127.0.0.1:8080
# Ghi .local/credentials.env (gitignore).
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

LOCAL=0
BASE_URL="${JUDGE_BASE_URL:-}"
ADMIN_KEY="${ADMIN_KEY:-}"
RATE="${RATE_LIMIT_PER_HOUR:-5000}"

usage() {
  cat <<'EOF'
Usage:
  ./scripts/bootstrap-grader.sh --local
  ./scripts/bootstrap-grader.sh --url http://127.0.0.1:8080

Biến tùy chọn: ADMIN_KEY, RATE_LIMIT_PER_HOUR, JUDGE_BASE_URL
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --local) LOCAL=1; shift ;;
    --url) BASE_URL="${2:-}"; shift 2 ;;
    -h|--help) usage; exit 0 ;;
    *) echo "Tham số lạ: $1" >&2; usage; exit 1 ;;
  esac
done

if [[ "$LOCAL" -eq 1 ]]; then
  COMPOSE=(docker compose -f docker-compose.yml -f docker-compose.local.yml)
  BASE_URL="${BASE_URL:-http://127.0.0.1:8433}"
elif [[ -f docker-compose.vps.yml ]]; then
  COMPOSE=(docker compose -f docker-compose.yml -f docker-compose.vps.yml)
  BASE_URL="${BASE_URL:-http://127.0.0.1:8080}"
else
  COMPOSE=(docker compose -f docker-compose.yml)
  BASE_URL="${BASE_URL:-http://127.0.0.1:8080}"
fi

if [[ -z "$BASE_URL" ]]; then
  usage >&2
  exit 1
fi

OUT_DIR="$ROOT/.local"
CREDS="$OUT_DIR/credentials.env"
mkdir -p "$OUT_DIR"

if ! "${COMPOSE[@]}" ps --status running --services 2>/dev/null | grep -qx judge-api; then
  echo "judge-api chưa chạy. Khởi động trước, ví dụ:" >&2
  echo "  ${COMPOSE[*]} up -d --build" >&2
  exit 1
fi

echo "Đợi health $BASE_URL ..."
ok=0
for _ in $(seq 1 90); do
  if curl -sf "$BASE_URL/actuator/health" >/dev/null 2>&1; then
    ok=1
    break
  fi
  sleep 2
done
if [[ "$ok" -ne 1 ]]; then
  echo "Judge không lên health. Xem: ${COMPOSE[*]} logs judge-api" >&2
  exit 1
fi

if [[ -z "$ADMIN_KEY" && -f "$CREDS" ]]; then
  # shellcheck disable=SC1090
  source "$CREDS"
  ADMIN_KEY="${ADMIN_KEY:-}"
fi

if [[ -z "$ADMIN_KEY" ]]; then
  ADMIN_KEY="sk_admin_$(openssl rand -hex 12)"
  HASH="$(printf '%s' "$ADMIN_KEY" | sha256sum | awk '{print $1}')"
  PREFIX="${ADMIN_KEY:0:8}"
  "${COMPOSE[@]}" exec -T postgres psql -U judge -d judgedb -v ON_ERROR_STOP=1 -c \
    "INSERT INTO api_keys(key_hash,key_prefix,client_name,is_active,is_admin,rate_limit_per_hour,role)
     VALUES ('${HASH}','${PREFIX}','admin',true,true,9999,'ADMIN')
     ON CONFLICT (key_hash) DO NOTHING;"
  echo "Đã tạo admin key (lưu trong $CREDS)."
fi

RESP="$(curl -sf -X POST "$BASE_URL/api/v1/admin/api-keys" \
  -H "X-API-Key: $ADMIN_KEY" \
  -H "Content-Type: application/json" \
  -d "{\"clientName\":\"thi247\",\"role\":\"GRADER\",\"rateLimitPerHour\":${RATE}}")"

GRADER_KEY="$(python3 -c "import json,sys; print(json.loads(sys.argv[1]).get('key') or '')" "$RESP")"
if [[ -z "$GRADER_KEY" ]]; then
  echo "Không đọc được key GRADER. Response: $RESP" >&2
  exit 1
fi

umask 077
cat > "$CREDS" <<EOF
# Sinh bởi scripts/bootstrap-grader.sh — không commit.
JUDGE_BASE_URL=$BASE_URL
ADMIN_KEY=$ADMIN_KEY
GRADER_KEY=$GRADER_KEY
EOF

echo
echo "Máy (health): $BASE_URL"
echo "GRADER:       $GRADER_KEY"
echo "Đã ghi:       $CREDS"
echo
echo "Trên Thi247 (Bài tập lập trình → Máy chấm riêng):"
echo "  URL production = https://<tên-miền-của-bạn>   (không dùng $BASE_URL nếu đó là localhost)"
echo "  URL local-dev  = http://127.0.0.1:8433"
echo "  Key            = $GRADER_KEY"
echo "Sau khi Thi247 hiện JUDGE_WEBHOOK_SECRET, dán vào .env rồi: ${COMPOSE[*]} up -d"
