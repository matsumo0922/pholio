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

SAMPLE_PNG_BASE64="iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+/p9sAAAAASUVORK5CYII="
if ! printf '%s' "$SAMPLE_PNG_BASE64" | base64 --decode > "$TMP_DIR/photos/sample.png" 2>/dev/null; then
  printf '%s' "$SAMPLE_PNG_BASE64" | base64 -D > "$TMP_DIR/photos/sample.png"
fi

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
  if curl -fsS http://127.0.0.1:18080/api/v1/health >/dev/null 2>&1; then
    OPENAPI_JSON="$(curl -fsS http://127.0.0.1:18080/api/v1/openapi.json)"
    if ! printf '%s' "$OPENAPI_JSON" | grep -q '"/api/v1/photos"'; then
      echo "OpenAPI document did not include photo paths" >&2
      exit 1
    fi

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

    PHOTOS_JSON="$(curl -fsS 'http://127.0.0.1:18080/api/v1/photos?limit=10')"
    if ! printf '%s' "$PHOTOS_JSON" | grep -q '"filename":"sample.png"'; then
      sleep 1
      continue
    fi

    PHOTO_ID="$(printf '%s' "$PHOTOS_JSON" | grep -Eo '"id":"[^"]+"' | head -n 1 | cut -d '"' -f 4)"
    THUMB_PATH="$(printf '%s' "$PHOTOS_JSON" | grep -Eo '/api/v1/photos/[^"]+/thumbnail/grid_md\?v=[^"]+' | head -n 1)"

    if [ -z "$PHOTO_ID" ] || [ -z "$THUMB_PATH" ]; then
      echo "Failed to extract photo id or thumbnail path from photos response" >&2
      printf '%s\n' "$PHOTOS_JSON" >&2
      exit 1
    fi

    curl -fsS "http://127.0.0.1:18080$THUMB_PATH" >/dev/null
    curl -fsS "http://127.0.0.1:18080/api/v1/photos/$PHOTO_ID/original" >/dev/null

    exit 0
  fi

  sleep 1
done

docker compose --env-file "$TMP_DIR/.env" logs pholio
exit 1
