#!/bin/bash
set -e
echo "Pre-pulling sandbox images..."
docker pull gcc:13
docker pull openjdk:21-slim
docker pull python:3.12-slim
echo "Done."
