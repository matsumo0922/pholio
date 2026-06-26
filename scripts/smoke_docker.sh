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
    INDEX_HTML="$(curl -fsS http://127.0.0.1:18080/)"
    ASSET_PATH="$(printf '%s' "$INDEX_HTML" | grep -Eo '/assets/[^"]+\.js' | head -n 1)"

    if [ -z "$ASSET_PATH" ]; then
      echo "Failed to find frontend asset in index.html" >&2
      exit 1
    fi

    ASSET_HEADERS="$(curl -fsSI "http://127.0.0.1:18080$ASSET_PATH")"

    if ! printf '%s' "$ASSET_HEADERS" | grep -qi 'content-type: .*javascript'; then
      echo "Frontend asset was not served as JavaScript: $ASSET_PATH" >&2
      printf '%s\n' "$ASSET_HEADERS" >&2
      exit 1
    fi

    exit 0
  fi

  sleep 1
done

docker compose --env-file "$TMP_DIR/.env" logs pholio
exit 1
