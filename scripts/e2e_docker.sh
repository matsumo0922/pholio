#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TMP_DIR="$(mktemp -d)"
PLAYWRIGHT_IMAGE="mcr.microsoft.com/playwright:v1.61.1-noble"

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
PORT=18082
APP_DEFAULT_TIMEZONE=Asia/Tokyo
SCAN_ON_STARTUP=true
THUMBNAIL_WORKERS=2
THUMBNAIL_PREVIEW_LAZY=true
TRUST_PROXY_HEADERS=false
CLOUDFLARE_ACCESS_ENABLED=false
ENV

cd "$ROOT_DIR"

rm -rf "$ROOT_DIR/frontend/test-results" "$ROOT_DIR/frontend/playwright-report"

docker compose --env-file "$TMP_DIR/.env" up --build -d

for _ in $(seq 1 60); do
  if curl -fsS http://127.0.0.1:18082/api/v1/health >/dev/null 2>&1 \
    && curl -fsS 'http://127.0.0.1:18082/api/v1/photos?limit=10' | grep -q '"filename":"sample.png"'; then
    break
  fi

  sleep 1
done

if ! curl -fsS 'http://127.0.0.1:18082/api/v1/photos?limit=10' | grep -q '"filename":"sample.png"'; then
  docker compose --env-file "$TMP_DIR/.env" logs pholio
  exit 1
fi

NETWORK_NAME="$(docker inspect pholio --format '{{range $name, $_ := .NetworkSettings.Networks}}{{$name}}{{end}}')"

docker run --rm \
  --network "$NETWORK_NAME" \
  -e PHOLIO_E2E_BASE_URL=http://pholio:8080 \
  -v "$ROOT_DIR/frontend:/work" \
  -w /work \
  "$PLAYWRIGHT_IMAGE" \
  npx playwright test

rm -rf "$ROOT_DIR/frontend/test-results" "$ROOT_DIR/frontend/playwright-report"
