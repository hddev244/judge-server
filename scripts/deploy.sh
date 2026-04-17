#!/bin/bash
set -e
echo "=== Judge Server Deploy ==="

echo "Pre-pulling sandbox images..."
docker pull gcc:13
docker pull openjdk:21-slim
docker pull python:3.12-slim

docker compose build --no-cache judge-api
docker compose up -d

echo "Waiting for health check..."
for i in {1..30}; do
    if curl -sf http://localhost:8080/actuator/health > /dev/null; then
        echo "Judge Server is UP"
        exit 0
    fi
    sleep 2
done
echo "ERROR: Health check failed"
exit 1
