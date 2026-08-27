#!/bin/bash
# Build the derived sandbox images used by the judge.
# Each adds GNU `time` (for CPU-time + max-RSS measurement) and a non-root
# `runner` user (uid 1000) on top of the stock language image.
set -e

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../deploy/images" && pwd)"

echo "Pulling base images..."
docker pull gcc:13
docker pull eclipse-temurin:21
docker pull python:3.12-slim

echo "Building judge sandbox images..."
docker build -t judge-cpp:1    "$DIR/cpp"
docker build -t judge-java:1   "$DIR/java"
docker build -t judge-python:1 "$DIR/python"

echo "Done."
