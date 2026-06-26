package me.matsumo.pholio.thumbs

import java.sql.ResultSet
import java.sql.Connection
import me.matsumo.pholio.db.Database
import me.matsumo.pholio.db.Jdbc.getNullableInt
import me.matsumo.pholio.db.Jdbc.getNullableLong
import me.matsumo.pholio.db.Jdbc.setNullableInt
import me.matsumo.pholio.db.Jdbc.setNullableLong
import me.matsumo.pholio.db.Jdbc.setNullableString
import me.matsumo.pholio.index.ThumbnailQueueResponse
import me.matsumo.pholio.photos.ThumbnailVariant

/**
 * thumbnail queue と cache metadata の DAO。
 */
class ThumbnailDao(
    private val database: Database,
) {
    /**
     * thumbnail task を必要に応じて pending / stale として登録する。
     */
    fun enqueue(photoId: String, variant: ThumbnailVariant, sourceFingerprint: String, now: Long): Boolean {
        return database.withConnection { connection ->
            val current = find(connection, photoId, variant)
            val hasReadyCurrent = current?.status == "ready" && current.sourceFingerprint == sourceFingerprint

            if (hasReadyCurrent) {
                return@withConnection false
            }

            connection.prepareStatement(
                """
                INSERT INTO photo_thumbnails (
                  photo_id, variant, status, format, source_fingerprint,
                  attempts, created_at_epoch_ms, updated_at_epoch_ms
                ) VALUES (?, ?, 'pending', 'webp', ?, 0, ?, ?)
                ON CONFLICT(photo_id, variant) DO UPDATE SET
                  status = CASE
                    WHEN photo_thumbnails.source_fingerprint = excluded.source_fingerprint THEN 'pending'
                    ELSE 'stale'
                  END,
                  source_fingerprint = excluded.source_fingerprint,
                  attempts = 0,
                  locked_until_epoch_ms = NULL,
                  last_error = NULL,
                  updated_at_epoch_ms = excluded.updated_at_epoch_ms
                """.trimIndent(),
            ).use { statement ->
                statement.setString(1, photoId)
                statement.setString(2, variant.dbValue)
                statement.setString(3, sourceFingerprint)
                statement.setLong(4, now)
                statement.setLong(5, now)

                statement.executeUpdate()
            }

            true
        }
    }

    /**
     * thumbnail record を取得する。
     */
    fun find(photoId: String, variant: ThumbnailVariant): ThumbnailRecord? = database.withConnection { connection ->
        find(connection, photoId, variant)
    }

    private fun find(connection: Connection, photoId: String, variant: ThumbnailVariant): ThumbnailRecord? {
        return connection.prepareStatement(
            """
            SELECT *
            FROM photo_thumbnails
            WHERE photo_id = ?
              AND variant = ?
            """.trimIndent(),
        ).use { statement ->
            statement.setString(1, photoId)
            statement.setString(2, variant.dbValue)

            statement.executeQuery().use { resultSet ->
                if (resultSet.next()) resultSet.toThumbnailRecord() else null
            }
        }
    }

    /**
     * worker 用に task を lock して取得する。
     */
    fun lockNext(now: Long, lockedUntil: Long, maxAttempts: Int): ThumbnailTask? {
        return database.withConnection { connection ->
            connection.autoCommit = false

            try {
                val task = connection.prepareStatement(
                    """
                    SELECT *
                    FROM photo_thumbnails
                    WHERE status IN ('pending', 'stale', 'failed')
                      AND attempts < ?
                      AND (locked_until_epoch_ms IS NULL OR locked_until_epoch_ms < ?)
                    ORDER BY
                      CASE variant WHEN 'grid_sm' THEN 0 WHEN 'grid_md' THEN 1 ELSE 2 END,
                      updated_at_epoch_ms ASC
                    LIMIT 1
                    """.trimIndent(),
                ).use { statement ->
                    statement.setInt(1, maxAttempts)
                    statement.setLong(2, now)

                    statement.executeQuery().use { resultSet ->
                        if (resultSet.next()) resultSet.toThumbnailRecord() else null
                    }
                } ?: run {
                    connection.commit()

                    return@withConnection null
                }

                connection.prepareStatement(
                    """
                    UPDATE photo_thumbnails
                    SET locked_until_epoch_ms = ?,
                        updated_at_epoch_ms = ?
                    WHERE photo_id = ?
                      AND variant = ?
                    """.trimIndent(),
                ).use { statement ->
                    statement.setLong(1, lockedUntil)
                    statement.setLong(2, now)
                    statement.setString(3, task.photoId)
                    statement.setString(4, task.variant)
                    statement.executeUpdate()
                }

                connection.commit()

                ThumbnailTask(
                    photoId = task.photoId,
                    variant = ThumbnailVariant.fromPath(task.variant),
                    sourceFingerprint = task.sourceFingerprint,
                    attempts = task.attempts,
                )
            } catch (throwable: Throwable) {
                connection.rollback()

                throw throwable
            } finally {
                connection.autoCommit = true
            }
        }
    }

    /**
     * thumbnail 生成成功を記録する。
     */
    fun markReady(
        photoId: String,
        variant: ThumbnailVariant,
        relativeCachePath: String,
        width: Int?,
        height: Int?,
        sizeBytes: Long,
        now: Long,
    ) {
        database.withConnection { connection ->
            connection.prepareStatement(
                """
                UPDATE photo_thumbnails
                SET status = 'ready',
                    relative_cache_path = ?,
                    width = ?,
                    height = ?,
                    size_bytes = ?,
                    locked_until_epoch_ms = NULL,
                    last_error = NULL,
                    generated_at_epoch_ms = ?,
                    updated_at_epoch_ms = ?
                WHERE photo_id = ?
                  AND variant = ?
                """.trimIndent(),
            ).use { statement ->
                statement.setString(1, relativeCachePath)
                statement.setNullableInt(2, width)
                statement.setNullableInt(3, height)
                statement.setLong(4, sizeBytes)
                statement.setLong(5, now)
                statement.setLong(6, now)
                statement.setString(7, photoId)
                statement.setString(8, variant.dbValue)
                statement.executeUpdate()
            }
        }
    }

    /**
     * thumbnail 生成失敗を記録する。
     */
    fun markFailed(photoId: String, variant: ThumbnailVariant, error: String, now: Long, maxAttempts: Int) {
        database.withConnection { connection ->
            connection.prepareStatement(
                """
                UPDATE photo_thumbnails
                SET status = CASE WHEN attempts + 1 >= ? THEN 'failed' ELSE 'pending' END,
                    attempts = attempts + 1,
                    locked_until_epoch_ms = NULL,
                    last_error = ?,
                    updated_at_epoch_ms = ?
                WHERE photo_id = ?
                  AND variant = ?
                """.trimIndent(),
            ).use { statement ->
                statement.setInt(1, maxAttempts)
                statement.setString(2, error.take(2000))
                statement.setLong(3, now)
                statement.setString(4, photoId)
                statement.setString(5, variant.dbValue)
                statement.executeUpdate()
            }
        }
    }

    /**
     * thumbnail task が現在不要になったことを記録する。
     */
    fun markSkipped(photoId: String, variant: ThumbnailVariant, reason: String, now: Long) {
        database.withConnection { connection ->
            connection.prepareStatement(
                """
                UPDATE photo_thumbnails
                SET status = 'skipped',
                    locked_until_epoch_ms = NULL,
                    last_error = ?,
                    updated_at_epoch_ms = ?
                WHERE photo_id = ?
                  AND variant = ?
                """.trimIndent(),
            ).use { statement ->
                statement.setString(1, reason.take(2000))
                statement.setLong(2, now)
                statement.setString(3, photoId)
                statement.setString(4, variant.dbValue)
                statement.executeUpdate()
            }
        }
    }

    /**
     * thumbnail queue 件数を返す。
     */
    fun queueSummary(): ThumbnailQueueResponse = database.withConnection { connection ->
        connection.createStatement().use { statement ->
            statement.executeQuery(
                """
                SELECT
                  SUM(CASE WHEN status IN ('pending', 'stale') THEN 1 ELSE 0 END) AS pending,
                  SUM(CASE WHEN status = 'ready' THEN 1 ELSE 0 END) AS ready,
                  SUM(CASE WHEN status = 'failed' THEN 1 ELSE 0 END) AS failed
                FROM photo_thumbnails
                """.trimIndent(),
            ).use { resultSet ->
                resultSet.next()

                ThumbnailQueueResponse(
                    pending = resultSet.getLong("pending"),
                    ready = resultSet.getLong("ready"),
                    failed = resultSet.getLong("failed"),
                )
            }
        }
    }

    private fun ResultSet.toThumbnailRecord(): ThumbnailRecord = ThumbnailRecord(
        photoId = getString("photo_id"),
        variant = getString("variant"),
        status = getString("status"),
        relativeCachePath = getString("relative_cache_path"),
        width = getNullableInt("width"),
        height = getNullableInt("height"),
        sizeBytes = getNullableLong("size_bytes"),
        sourceFingerprint = getString("source_fingerprint"),
        attempts = getInt("attempts"),
        lockedUntilEpochMs = getNullableLong("locked_until_epoch_ms"),
    )
}

/**
 * thumbnail record。
 */
data class ThumbnailRecord(
    val photoId: String,
    val variant: String,
    val status: String,
    val relativeCachePath: String?,
    val width: Int?,
    val height: Int?,
    val sizeBytes: Long?,
    val sourceFingerprint: String,
    val attempts: Int,
    val lockedUntilEpochMs: Long?,
)

/**
 * worker が処理する thumbnail task。
 */
data class ThumbnailTask(
    val photoId: String,
    val variant: ThumbnailVariant,
    val sourceFingerprint: String,
    val attempts: Int,
)
