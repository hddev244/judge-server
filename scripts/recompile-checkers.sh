#!/bin/bash
# Recompile every custom checker binary via the admin API.
# Needed after migrating to a server with a different CPU architecture,
# since /data/problems/*/checker/ holds compiled executables.
# Usage: ADMIN_API_KEY=xxx ./scripts/recompile-checkers.sh
set -euo pipefail

PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$PROJECT_DIR"
set -a; source .env; set +a
PORT="${SERVER_PORT:-8433}"
: "${ADMIN_API_KEY:?set ADMIN_API_KEY to an admin API key}"

IDS="$(docker compose exec -T postgres psql -U "${DB_USER:-judge}" -d "${DB_NAME:-judgedb}" -tAc \
    "SELECT id FROM problems WHERE checker_type='CUSTOM' AND checker_source IS NOT NULL ORDER BY id")"

if [ -z "$IDS" ]; then
    echo "[recompile] no problems with custom checkers — nothing to do"
    exit 0
fi

FAIL=0
for id in $IDS; do
    printf '[recompile] problem %s... ' "$id"
    code="$(curl -s -o /dev/null -w '%{http_code}' -X POST \
        -H "X-API-Key: $ADMIN_API_KEY" \
        "http://localhost:$PORT/api/v1/admin/problems/$id/recompile-checker")"
    if [ "$code" = "200" ]; then
        echo "OK"
    else
        echo "FAILED (HTTP $code)"
        FAIL=1
    fi
done
exit $FAIL
