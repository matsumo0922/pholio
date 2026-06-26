# syntax=docker/dockerfile:1

FROM oven/bun:1.3.14-slim AS frontend-build
WORKDIR /work/frontend
COPY frontend/package.json frontend/bun.lock ./
RUN bun install --frozen-lockfile
COPY frontend/ ./
RUN bun run build

FROM eclipse-temurin:21-jdk-jammy AS backend-build
WORKDIR /work
COPY gradlew settings.gradle.kts build.gradle.kts gradle.properties ./
COPY gradle ./gradle
COPY backend ./backend
COPY --from=frontend-build /work/frontend/dist ./backend/src/main/resources/public
RUN ./gradlew :backend:installDist --no-daemon

FROM eclipse-temurin:21-jre-jammy AS runtime

RUN apt-get update \
    && apt-get install -y --no-install-recommends \
       ca-certificates \
       ffmpeg \
       libvips-tools \
       wget \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app
COPY --from=backend-build /work/backend/build/install/backend /app
RUN chmod -R a+rX /app

ENV PORT=8080 \
    PHOTO_ROOT=/photos \
    DATA_DIR=/data \
    DATABASE_URL=jdbc:sqlite:/data/pholio.sqlite3 \
    THUMB_DIR=/data/thumbs \
    APP_DEFAULT_TIMEZONE=Asia/Tokyo \
    SCAN_ON_STARTUP=true \
    THUMBNAIL_WORKERS=2 \
    THUMBNAIL_PREVIEW_LAZY=true \
    TRUST_PROXY_HEADERS=false \
    CLOUDFLARE_ACCESS_ENABLED=false

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=30s --retries=3 \
    CMD wget -qO- http://127.0.0.1:8080/api/v1/health >/dev/null || exit 1

ENTRYPOINT ["/app/bin/backend"]
