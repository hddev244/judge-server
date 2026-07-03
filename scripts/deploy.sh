#!/bin/bash
set -e
echo "=== Judge Server Deploy ==="

echo "Pre-pulling sandbox images..."
docker pull gcc:13
docker pull eclipse-temurin:21
docker pull python:3.12-slim

docker compose build --no-cache judge-api
docker compose up -d

[ -f .env ] && source .env
PORT="${SERVER_PORT:-8433}"
echo "Waiting for health check on :$PORT..."
for i in {1..30}; do
    if curl -sf "http://localhost:$PORT/actuator/health" > /dev/null; then
        echo "Judge Server is UP"
        exit 0
    fi
    sleep 2
done
echo "ERROR: Health check failed"
exit 1
