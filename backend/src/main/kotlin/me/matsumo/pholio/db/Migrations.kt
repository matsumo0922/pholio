package me.matsumo.pholio.db

import java.sql.Connection

/**
 * SQLite schema migration を実行する。
 */
class Migrations(
    private val connection: Connection,
) {
    /**
     * 最新 schema まで migration する。
     */
    fun migrate() {
        createSchema()
        setSchemaVersion()
    }

    private fun createSchema() {
        statements.forEach { sql ->
            connection.createStatement().use { statement ->
                statement.execute(sql)
            }
        }
    }

    private fun setSchemaVersion() {
        connection.prepareStatement(
            """
            INSERT INTO app_meta (key, value, updated_at_epoch_ms)
            VALUES ('schema_version', '1', strftime('%s','now') * 1000)
            ON CONFLICT(key) DO UPDATE SET
              value = excluded.value,
              updated_at_epoch_ms = excluded.updated_at_epoch_ms
            """.trimIndent(),
        ).use { statement ->
            statement.executeUpdate()
        }
    }

    /**
     * migration 用 SQL 定義。
     */
    private companion object {
        /**
         * schema version 1 を構成する SQL 群。
         */
        private val statements = listOf(
            """
            CREATE TABLE IF NOT EXISTS app_meta (
              key TEXT PRIMARY KEY,
              value TEXT NOT NULL,
              updated_at_epoch_ms INTEGER NOT NULL
            )
            """.trimIndent(),
            """
            CREATE TABLE IF NOT EXISTS photos (
              id TEXT PRIMARY KEY,
              relative_path TEXT NOT NULL UNIQUE,
              filename TEXT NOT NULL,
              filename_sort_key TEXT NOT NULL,
              extension TEXT NOT NULL,
              media_type TEXT NOT NULL CHECK (media_type IN ('image', 'video')),
              mime_type TEXT NOT NULL,
              file_size_bytes INTEGER NOT NULL,
              file_mtime_epoch_ms INTEGER NOT NULL,
              source_fingerprint TEXT NOT NULL,
              first_seen_at_epoch_ms INTEGER NOT NULL,
              indexed_at_epoch_ms INTEGER NOT NULL,
              last_seen_at_epoch_ms INTEGER NOT NULL,
              missing_since_epoch_ms INTEGER,
              excluded_at_epoch_ms INTEGER,
              width INTEGER,
              height INTEGER,
              duration_ms INTEGER,
              orientation INTEGER,
              taken_at_epoch_ms INTEGER NOT NULL,
              taken_at_source TEXT NOT NULL CHECK (taken_at_source IN ('takeout_json', 'exif', 'video_metadata', 'file_mtime', 'unknown')),
              exif_taken_at_epoch_ms INTEGER,
              takeout_taken_at_epoch_ms INTEGER,
              video_created_at_epoch_ms INTEGER,
              timezone_offset_minutes INTEGER,
              gps_lat REAL,
              gps_lng REAL,
              gps_alt REAL,
              gps_source TEXT CHECK (gps_source IN ('takeout_geoData', 'takeout_geoDataExif', 'exif', 'none')),
              camera_make TEXT,
              camera_model TEXT,
              sidecar_relative_path TEXT,
              metadata_status TEXT NOT NULL DEFAULT 'pending' CHECK (metadata_status IN ('pending', 'ready', 'failed')),
              metadata_error TEXT,
              metadata_version INTEGER NOT NULL DEFAULT 1,
              created_at_epoch_ms INTEGER NOT NULL,
              updated_at_epoch_ms INTEGER NOT NULL
            )
            """.trimIndent(),
            """
            CREATE TABLE IF NOT EXISTS albums (
              id TEXT PRIMARY KEY,
              name TEXT NOT NULL,
              name_sort_key TEXT NOT NULL,
              cover_photo_id TEXT REFERENCES photos(id) ON DELETE SET NULL,
              created_at_epoch_ms INTEGER NOT NULL,
              updated_at_epoch_ms INTEGER NOT NULL,
              deleted_at_epoch_ms INTEGER
            )
            """.trimIndent(),
            """
            CREATE TABLE IF NOT EXISTS album_photos (
              album_id TEXT NOT NULL REFERENCES albums(id) ON DELETE RESTRICT,
              photo_id TEXT NOT NULL REFERENCES photos(id) ON DELETE RESTRICT,
              added_at_epoch_ms INTEGER NOT NULL,
              removed_at_epoch_ms INTEGER,
              PRIMARY KEY (album_id, photo_id)
            )
            """.trimIndent(),
            """
            CREATE TABLE IF NOT EXISTS photo_thumbnails (
              photo_id TEXT NOT NULL REFERENCES photos(id) ON DELETE RESTRICT,
              variant TEXT NOT NULL CHECK (variant IN ('grid_sm', 'grid_md', 'preview_lg')),
              status TEXT NOT NULL CHECK (status IN ('pending', 'ready', 'failed', 'stale')),
              format TEXT NOT NULL DEFAULT 'webp',
              relative_cache_path TEXT,
              width INTEGER,
              height INTEGER,
              size_bytes INTEGER,
              source_fingerprint TEXT NOT NULL,
              attempts INTEGER NOT NULL DEFAULT 0,
              locked_until_epoch_ms INTEGER,
              last_error TEXT,
              generated_at_epoch_ms INTEGER,
              created_at_epoch_ms INTEGER NOT NULL,
              updated_at_epoch_ms INTEGER NOT NULL,
              PRIMARY KEY (photo_id, variant)
            )
            """.trimIndent(),
            """
            CREATE TABLE IF NOT EXISTS scan_jobs (
              id TEXT PRIMARY KEY,
              mode TEXT NOT NULL CHECK (mode IN ('full', 'diff')),
              status TEXT NOT NULL CHECK (status IN ('queued', 'running', 'completed', 'failed', 'cancelled')),
              total_files_estimated INTEGER,
              files_seen INTEGER NOT NULL DEFAULT 0,
              media_files_seen INTEGER NOT NULL DEFAULT 0,
              sidecar_json_seen INTEGER NOT NULL DEFAULT 0,
              photos_inserted INTEGER NOT NULL DEFAULT 0,
              photos_updated INTEGER NOT NULL DEFAULT 0,
              photos_unchanged INTEGER NOT NULL DEFAULT 0,
              photos_marked_missing INTEGER NOT NULL DEFAULT 0,
              thumbnail_tasks_enqueued INTEGER NOT NULL DEFAULT 0,
              errors_count INTEGER NOT NULL DEFAULT 0,
              current_relative_path TEXT,
              cancel_requested INTEGER NOT NULL DEFAULT 0,
              error_summary TEXT,
              started_at_epoch_ms INTEGER,
              finished_at_epoch_ms INTEGER,
              created_at_epoch_ms INTEGER NOT NULL,
              updated_at_epoch_ms INTEGER NOT NULL
            )
            """.trimIndent(),
            """
            CREATE TABLE IF NOT EXISTS scan_errors (
              id INTEGER PRIMARY KEY AUTOINCREMENT,
              job_id TEXT NOT NULL REFERENCES scan_jobs(id) ON DELETE CASCADE,
              relative_path TEXT,
              phase TEXT NOT NULL CHECK (phase IN ('walk', 'sidecar', 'metadata', 'db', 'thumbnail_enqueue')),
              message TEXT NOT NULL,
              stack_trace TEXT,
              created_at_epoch_ms INTEGER NOT NULL
            )
            """.trimIndent(),
            "CREATE INDEX IF NOT EXISTS idx_photos_active_taken_desc ON photos (taken_at_epoch_ms DESC, id ASC) WHERE excluded_at_epoch_ms IS NULL AND missing_since_epoch_ms IS NULL",
            "CREATE INDEX IF NOT EXISTS idx_photos_active_taken_asc ON photos (taken_at_epoch_ms ASC, id ASC) WHERE excluded_at_epoch_ms IS NULL AND missing_since_epoch_ms IS NULL",
            "CREATE INDEX IF NOT EXISTS idx_photos_active_name_asc ON photos (filename_sort_key ASC, id ASC) WHERE excluded_at_epoch_ms IS NULL AND missing_since_epoch_ms IS NULL",
            "CREATE INDEX IF NOT EXISTS idx_photos_active_name_desc ON photos (filename_sort_key DESC, id ASC) WHERE excluded_at_epoch_ms IS NULL AND missing_since_epoch_ms IS NULL",
            "CREATE INDEX IF NOT EXISTS idx_photos_active_indexed_desc ON photos (indexed_at_epoch_ms DESC, id ASC) WHERE excluded_at_epoch_ms IS NULL AND missing_since_epoch_ms IS NULL",
            "CREATE INDEX IF NOT EXISTS idx_photos_active_indexed_asc ON photos (indexed_at_epoch_ms ASC, id ASC) WHERE excluded_at_epoch_ms IS NULL AND missing_since_epoch_ms IS NULL",
            "CREATE INDEX IF NOT EXISTS idx_photos_excluded_desc ON photos (excluded_at_epoch_ms DESC, id ASC) WHERE excluded_at_epoch_ms IS NOT NULL AND missing_since_epoch_ms IS NULL",
            "CREATE INDEX IF NOT EXISTS idx_photos_relative_path ON photos (relative_path)",
            "CREATE INDEX IF NOT EXISTS idx_photos_source_fingerprint ON photos (source_fingerprint)",
            "CREATE INDEX IF NOT EXISTS idx_albums_active_created_desc ON albums (created_at_epoch_ms DESC, id ASC) WHERE deleted_at_epoch_ms IS NULL",
            "CREATE INDEX IF NOT EXISTS idx_albums_active_name ON albums (name_sort_key ASC, id ASC) WHERE deleted_at_epoch_ms IS NULL",
            "CREATE INDEX IF NOT EXISTS idx_album_photos_album_active ON album_photos (album_id, removed_at_epoch_ms, photo_id)",
            "CREATE INDEX IF NOT EXISTS idx_album_photos_photo_active ON album_photos (photo_id, removed_at_epoch_ms, album_id)",
            "CREATE INDEX IF NOT EXISTS idx_photo_thumbnails_pending ON photo_thumbnails (status, updated_at_epoch_ms)",
            "CREATE INDEX IF NOT EXISTS idx_photo_thumbnails_photo ON photo_thumbnails (photo_id)",
            "CREATE INDEX IF NOT EXISTS idx_scan_jobs_created_desc ON scan_jobs (created_at_epoch_ms DESC)",
            "CREATE INDEX IF NOT EXISTS idx_scan_errors_job ON scan_errors (job_id, created_at_epoch_ms ASC)",
        )
    }
}
