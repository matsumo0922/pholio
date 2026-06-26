#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TMP_DIR="$(mktemp -d)"

cleanup() {
  (cd "$ROOT_DIR" && docker compose --env-file "$TMP_DIR/.env" down --volumes >/dev/null 2>&1 || true)
  rm -rf "$TMP_DIR"
}

trap cleanup EXIT

mkdir -p "$TMP_DIR/photos" "$TMP_DIR/data"

cat > "$TMP_DIR/.env" <<ENV
PUID=$(id -u)
PGID=$(id -g)
PHOTOS_PATH=$TMP_DIR/photos
DATA_PATH=$TMP_DIR/data
PORT=18080
APP_DEFAULT_TIMEZONE=Asia/Tokyo
SCAN_ON_STARTUP=true
THUMBNAIL_WORKERS=2
THUMBNAIL_PREVIEW_LAZY=true
TRUST_PROXY_HEADERS=false
CLOUDFLARE_ACCESS_ENABLED=false
ENV

cd "$ROOT_DIR"

docker compose --env-file "$TMP_DIR/.env" up --build -d

for _ in $(seq 1 60); do
  if curl -fsS http://127.0.0.1:18080/api/v1/health >/dev/null; then
    curl -fsS http://127.0.0.1:18080/api/v1/openapi.json >/dev/null
    curl -fsS http://127.0.0.1:18080/api/docs >/dev/null
    exit 0
  fi

  sleep 1
done

docker compose --env-file "$TMP_DIR/.env" logs pholio
exit 1
