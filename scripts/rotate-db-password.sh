#!/bin/bash
# Rotate the PostgreSQL password: ALTER USER in the running container,
# update .env, recreate judge-api so it picks up the new credential.
set -euo pipefail

PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$PROJECT_DIR"
set -a; source .env; set +a
DB_USER="${DB_USER:-judge}"
DB_NAME="${DB_NAME:-judgedb}"

NEW_PASSWORD="$(openssl rand -hex 24)"

echo "[rotate] ALTER USER $DB_USER..."
docker compose exec -T postgres psql -U "$DB_USER" -d "$DB_NAME" \
    -c "ALTER USER \"$DB_USER\" WITH PASSWORD '$NEW_PASSWORD';" > /dev/null

echo "[rotate] updating .env..."
cp .env ".env.bak.$(date +%s)"
sed -i "s|^DB_PASSWORD=.*|DB_PASSWORD=$NEW_PASSWORD|" .env

echo "[rotate] recreating judge-api..."
docker compose up -d judge-api

echo "[rotate] waiting for health..."
PORT="${SERVER_PORT:-8433}"
for i in {1..30}; do
    if curl -sf "http://localhost:$PORT/actuator/health" > /dev/null; then
        echo "[rotate] OK — new password active (backup of old .env kept as .env.bak.*)"
        exit 0
    fi
    sleep 2
done
echo "[rotate] ERROR: judge-api did not become healthy — old .env saved as .env.bak.*" >&2
exit 1
