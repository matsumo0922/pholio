package me.matsumo.pholio.index

import com.github.f4b6a3.ulid.UlidCreator
import java.sql.Connection
import java.sql.ResultSet
import me.matsumo.pholio.db.Database
import me.matsumo.pholio.db.Jdbc.getNullableLong

/**
 * scan job と app meta の DAO。
 */
class IndexDao(
    private val database: Database,
) {
    /**
     * library revision を取得する。
     */
    fun libraryRevision(): Long = database.withConnection { connection ->
        libraryRevision(connection)
    }

    private fun libraryRevision(connection: Connection): Long {
        connection.prepareStatement("SELECT value FROM app_meta WHERE key = 'library_revision'").use { statement ->
            statement.executeQuery().use { resultSet ->
                return if (resultSet.next()) resultSet.getString("value").toLongOrNull() ?: 0L else 0L
            }
        }
    }

    /**
     * library revision を増やす。
     */
    fun incrementLibraryRevision(now: Long): Long = database.withConnection { connection ->
        val next = libraryRevision(connection) + 1

        connection.prepareStatement(
            """
            INSERT INTO app_meta (key, value, updated_at_epoch_ms)
            VALUES ('library_revision', ?, ?)
            ON CONFLICT(key) DO UPDATE SET
              value = excluded.value,
              updated_at_epoch_ms = excluded.updated_at_epoch_ms
            """.trimIndent(),
        ).use { statement ->
            statement.setString(1, next.toString())
            statement.setLong(2, now)
            statement.executeUpdate()
        }

        next
    }

    /**
     * scan job を作成する。
     */
    fun createJob(mode: ScanMode, now: Long): ScanJobRecord {
        val id = UlidCreator.getUlid().toString()

        database.withConnection { connection ->
            connection.prepareStatement(
                """
                INSERT INTO scan_jobs (
                  id, mode, status, created_at_epoch_ms, updated_at_epoch_ms
                ) VALUES (?, ?, 'queued', ?, ?)
                """.trimIndent(),
            ).use { statement ->
                statement.setString(1, id)
                statement.setString(2, mode.dbValue)
                statement.setLong(3, now)
                statement.setLong(4, now)
                statement.executeUpdate()
            }
        }

        return latestJob() ?: error("作成した scan job が見つかりません")
    }

    /**
     * 最新 scan job を取得する。
     */
    fun latestJob(): ScanJobRecord? = database.withConnection { connection ->
        connection.prepareStatement("SELECT * FROM scan_jobs ORDER BY created_at_epoch_ms DESC LIMIT 1").use { statement ->
            statement.executeQuery().use { resultSet ->
                if (resultSet.next()) resultSet.toScanJobRecord() else null
            }
        }
    }

    /**
     * 実行中 scan job を取得する。
     */
    fun runningJob(): ScanJobRecord? = database.withConnection { connection ->
        connection.prepareStatement(
            """
            SELECT *
            FROM scan_jobs
            WHERE status IN ('queued', 'running')
            ORDER BY created_at_epoch_ms DESC
            LIMIT 1
            """.trimIndent(),
        ).use { statement ->
            statement.executeQuery().use { resultSet ->
                if (resultSet.next()) resultSet.toScanJobRecord() else null
            }
        }
    }

    /**
     * scan job を running にする。
     */
    fun markRunning(jobId: String, now: Long) {
        updateJob(jobId, now, "status = 'running', started_at_epoch_ms = COALESCE(started_at_epoch_ms, ?)", now)
    }

    /**
     * scan job の現在 path と counters を更新する。
     */
    fun updateProgress(jobId: String, progress: ScanProgress, now: Long) {
        database.withConnection { connection ->
            connection.prepareStatement(
                """
                UPDATE scan_jobs
                SET files_seen = ?,
                    media_files_seen = ?,
                    sidecar_json_seen = ?,
                    photos_inserted = ?,
                    photos_updated = ?,
                    photos_unchanged = ?,
                    photos_marked_missing = ?,
                    thumbnail_tasks_enqueued = ?,
                    errors_count = ?,
                    current_relative_path = ?,
                    updated_at_epoch_ms = ?
                WHERE id = ?
                """.trimIndent(),
            ).use { statement ->
                statement.setLong(1, progress.filesSeen)
                statement.setLong(2, progress.mediaFilesSeen)
                statement.setLong(3, progress.sidecarJsonSeen)
                statement.setLong(4, progress.photosInserted)
                statement.setLong(5, progress.photosUpdated)
                statement.setLong(6, progress.photosUnchanged)
                statement.setLong(7, progress.photosMarkedMissing)
                statement.setLong(8, progress.thumbnailTasksEnqueued)
                statement.setLong(9, progress.errorsCount)
                statement.setString(10, progress.currentRelativePath)
                statement.setLong(11, now)
                statement.setString(12, jobId)
                statement.executeUpdate()
            }
        }
    }

    /**
     * scan cancel を要求する。
     */
    fun requestCancel(jobId: String, now: Long): Boolean = database.withConnection { connection ->
        connection.prepareStatement(
            """
            UPDATE scan_jobs
            SET cancel_requested = 1,
                updated_at_epoch_ms = ?
            WHERE id = ?
              AND status IN ('queued', 'running')
            """.trimIndent(),
        ).use { statement ->
            statement.setLong(1, now)
            statement.setString(2, jobId)

            statement.executeUpdate() > 0
        }
    }

    /**
     * scan cancel が要求されているか返す。
     */
    fun isCancelRequested(jobId: String): Boolean = database.withConnection { connection ->
        connection.prepareStatement("SELECT cancel_requested FROM scan_jobs WHERE id = ?").use { statement ->
            statement.setString(1, jobId)

            statement.executeQuery().use { resultSet ->
                resultSet.next() && resultSet.getInt("cancel_requested") == 1
            }
        }
    }

    /**
     * scan job を完了にする。
     */
    fun markCompleted(jobId: String, now: Long) {
        updateJob(jobId, now, "status = 'completed', finished_at_epoch_ms = ? ", now)
    }

    /**
     * scan job を cancelled にする。
     */
    fun markCancelled(jobId: String, now: Long) {
        updateJob(jobId, now, "status = 'cancelled', finished_at_epoch_ms = ? ", now)
    }

    /**
     * scan job を failed にする。
     */
    fun markFailed(jobId: String, error: String, now: Long) {
        database.withConnection { connection ->
            connection.prepareStatement(
                """
                UPDATE scan_jobs
                SET status = 'failed',
                    error_summary = ?,
                    finished_at_epoch_ms = ?,
                    updated_at_epoch_ms = ?
                WHERE id = ?
                """.trimIndent(),
            ).use { statement ->
                statement.setString(1, error.take(2000))
                statement.setLong(2, now)
                statement.setLong(3, now)
                statement.setString(4, jobId)
                statement.executeUpdate()
            }
        }
    }

    /**
     * scan error を保存する。
     */
    fun addError(jobId: String, relativePath: String?, phase: String, throwable: Throwable, now: Long) {
        database.withConnection { connection ->
            connection.prepareStatement(
                """
                INSERT INTO scan_errors (
                  job_id, relative_path, phase, message, stack_trace, created_at_epoch_ms
                ) VALUES (?, ?, ?, ?, ?, ?)
                """.trimIndent(),
            ).use { statement ->
                statement.setString(1, jobId)
                statement.setString(2, relativePath)
                statement.setString(3, phase)
                statement.setString(4, throwable.message ?: throwable::class.java.name)
                statement.setString(5, throwable.stackTraceToString().take(4000))
                statement.setLong(6, now)
                statement.executeUpdate()
            }
        }
    }

    private fun updateJob(jobId: String, now: Long, assignmentSql: String, value: Long) {
        database.withConnection { connection ->
            connection.prepareStatement(
                """
                UPDATE scan_jobs
                SET $assignmentSql,
                    updated_at_epoch_ms = ?
                WHERE id = ?
                """.trimIndent(),
            ).use { statement ->
                statement.setLong(1, value)
                statement.setLong(2, now)
                statement.setString(3, jobId)
                statement.executeUpdate()
            }
        }
    }

    private fun ResultSet.toScanJobRecord(): ScanJobRecord = ScanJobRecord(
        id = getString("id"),
        mode = getString("mode"),
        status = getString("status"),
        filesSeen = getLong("files_seen"),
        mediaFilesSeen = getLong("media_files_seen"),
        sidecarJsonSeen = getLong("sidecar_json_seen"),
        photosInserted = getLong("photos_inserted"),
        photosUpdated = getLong("photos_updated"),
        photosUnchanged = getLong("photos_unchanged"),
        photosMarkedMissing = getLong("photos_marked_missing"),
        thumbnailTasksEnqueued = getLong("thumbnail_tasks_enqueued"),
        errorsCount = getLong("errors_count"),
        currentRelativePath = getString("current_relative_path"),
        cancelRequested = getInt("cancel_requested") == 1,
        errorSummary = getString("error_summary"),
        startedAtEpochMs = getNullableLong("started_at_epoch_ms"),
        finishedAtEpochMs = getNullableLong("finished_at_epoch_ms"),
        createdAtEpochMs = getLong("created_at_epoch_ms"),
        updatedAtEpochMs = getLong("updated_at_epoch_ms"),
    )
}

/**
 * scan 中に更新する progress counters。
 */
data class ScanProgress(
    val filesSeen: Long = 0,
    val mediaFilesSeen: Long = 0,
    val sidecarJsonSeen: Long = 0,
    val photosInserted: Long = 0,
    val photosUpdated: Long = 0,
    val photosUnchanged: Long = 0,
    val photosMarkedMissing: Long = 0,
    val thumbnailTasksEnqueued: Long = 0,
    val errorsCount: Long = 0,
    val currentRelativePath: String? = null,
)
