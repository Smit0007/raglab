#!/usr/bin/env bash
set -e

./scripts/build.sh

echo "Starting Docker Compose..."
docker compose -f infrastructure/docker-compose.yml up --build -d
