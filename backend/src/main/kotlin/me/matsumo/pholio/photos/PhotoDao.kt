package me.matsumo.pholio.photos

import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import me.matsumo.pholio.db.Database
import me.matsumo.pholio.db.Jdbc.getNullableDouble
import me.matsumo.pholio.db.Jdbc.getNullableInt
import me.matsumo.pholio.db.Jdbc.getNullableLong
import me.matsumo.pholio.db.Jdbc.setNullableDouble
import me.matsumo.pholio.db.Jdbc.setNullableInt
import me.matsumo.pholio.db.Jdbc.setNullableLong
import me.matsumo.pholio.db.Jdbc.setNullableString
import me.matsumo.pholio.util.Hashing

/**
 * 写真・動画 metadata の DAO。
 */
class PhotoDao(
    private val database: Database,
) {
    /**
     * active photo 件数を返す。
     */
    fun countActive(albumId: String? = null): Long = database.withConnection { connection ->
        val sql = if (albumId == null) {
            "SELECT COUNT(*) FROM photos p WHERE ${activeWhere("p")}"
        } else {
            """
            SELECT COUNT(*)
            FROM album_photos ap
            JOIN albums a ON a.id = ap.album_id
            JOIN photos p ON p.id = ap.photo_id
            WHERE ap.album_id = ?
              AND ap.removed_at_epoch_ms IS NULL
              AND a.deleted_at_epoch_ms IS NULL
              AND ${activeWhere("p")}
            """.trimIndent()
        }

        connection.prepareStatement(sql).use { statement ->
            if (albumId != null) {
                statement.setString(1, albumId)
            }

            statement.executeQuery().use { resultSet ->
                resultSet.next()

                resultSet.getLong(1)
            }
        }
    }

    /**
     * 除外済みかつ missing ではない写真件数を返す。
     */
    fun countExcluded(): Long = database.withConnection { connection ->
        connection.createStatement().use { statement ->
            statement.executeQuery(
                """
                SELECT COUNT(*)
                FROM photos
                WHERE excluded_at_epoch_ms IS NOT NULL
                  AND missing_since_epoch_ms IS NULL
                """.trimIndent(),
            ).use { resultSet ->
                resultSet.next()

                resultSet.getLong(1)
            }
        }
    }

    /**
     * active photo を keyset pagination で取得する。
     */
    fun listActive(
        albumId: String?,
        sort: String,
        order: String,
        seed: String?,
        limitPlusOne: Int,
        lastLongKey: Long?,
        lastStringKey: String?,
        lastId: String?,
    ): List<PhotoRecord> = database.withConnection { connection ->
        val fromClause = if (albumId == null) {
            "FROM photos p"
        } else {
            """
            FROM album_photos ap
            JOIN albums a ON a.id = ap.album_id
            JOIN photos p ON p.id = ap.photo_id
            """.trimIndent()
        }
        val whereClause = mutableListOf(activeWhere("p"))
        val parameters = mutableListOf<Any?>()

        if (albumId != null) {
            whereClause += "ap.album_id = ?"
            whereClause += "ap.removed_at_epoch_ms IS NULL"
            whereClause += "a.deleted_at_epoch_ms IS NULL"
            parameters += albumId
        }

        if (sort == "random") {
            return@withConnection listActiveRandom(
                connection = connection,
                fromClause = fromClause,
                whereClause = whereClause,
                parameters = parameters,
                seed = seed ?: "default",
                limitPlusOne = limitPlusOne,
                lastLongKey = lastLongKey,
                lastId = lastId,
            )
        }

        val keyExpression = sortExpression(sort)
        val isDescending = order == "desc"
        val comparison = if (isDescending) "<" else ">"

        if (lastId != null) {
            whereClause += if (sort == "name") {
                parameters += lastStringKey
                parameters += lastStringKey
                parameters += lastId

                "($keyExpression $comparison ? OR ($keyExpression = ? AND p.id > ?))"
            } else {
                parameters += lastLongKey
                parameters += lastLongKey
                parameters += lastId

                "($keyExpression $comparison ? OR ($keyExpression = ? AND p.id > ?))"
            }
        }

        val orderDirection = if (isDescending) "DESC" else "ASC"
        val sql = """
            SELECT p.*
            $fromClause
            WHERE ${whereClause.joinToString(" AND ")}
            ORDER BY $keyExpression $orderDirection, p.id ASC
            LIMIT ?
        """.trimIndent()

        connection.prepareStatement(sql).use { statement ->
            var parameterIndex = 1

            parameterIndex = bindParameters(statement, parameters, parameterIndex)

            statement.setInt(parameterIndex, limitPlusOne)

            statement.executeQuery().use { resultSet ->
                resultSet.toPhotoRecords()
            }
        }
    }

    private fun listActiveRandom(
        connection: Connection,
        fromClause: String,
        whereClause: List<String>,
        parameters: List<Any?>,
        seed: String,
        limitPlusOne: Int,
        lastLongKey: Long?,
        lastId: String?,
    ): List<PhotoRecord> {
        val sql = """
            SELECT p.*
            $fromClause
            WHERE ${whereClause.joinToString(" AND ")}
        """.trimIndent()
        val records = connection.prepareStatement(sql).use { statement ->
            bindParameters(statement, parameters, 1)

            statement.executeQuery().use { resultSet ->
                resultSet.toPhotoRecords()
            }
        }

        return records
            .map { photo ->
                RandomSortedPhoto(
                    photo = photo,
                    randomKey = Hashing.seededRandomKey(seed, photo.id),
                )
            }
            .sortedWith(
                compareBy<RandomSortedPhoto> { sortedPhoto -> sortedPhoto.randomKey }
                    .thenBy { sortedPhoto -> sortedPhoto.photo.id },
            )
            .filter { sortedPhoto -> sortedPhoto.isAfterCursor(lastLongKey, lastId) }
            .take(limitPlusOne)
            .map { sortedPhoto -> sortedPhoto.photo }
    }

    private fun bindParameters(
        statement: PreparedStatement,
        parameters: List<Any?>,
        startIndex: Int,
    ): Int {
        var parameterIndex = startIndex

        parameters.forEach { parameter ->
            when (parameter) {
                is Long -> statement.setLong(parameterIndex++, parameter)
                is String -> statement.setString(parameterIndex++, parameter)
                null -> statement.setObject(parameterIndex++, null)
            }
        }

        return parameterIndex
    }

    /**
     * 除外済み photo を除外日時順で取得する。
     */
    fun listExcluded(limit: Int, cursorExcludedAt: Long?, cursorId: String?): List<PhotoRecord> {
        return database.withConnection { connection ->
            val whereClause = mutableListOf(
                "p.excluded_at_epoch_ms IS NOT NULL",
                "p.missing_since_epoch_ms IS NULL",
            )

            val hasCursor = cursorExcludedAt != null && cursorId != null

            if (hasCursor) {
                whereClause += "(p.excluded_at_epoch_ms < ? OR (p.excluded_at_epoch_ms = ? AND p.id > ?))"
            }

            val sql = """
                SELECT p.*
                FROM photos p
                WHERE ${whereClause.joinToString(" AND ")}
                ORDER BY p.excluded_at_epoch_ms DESC, p.id ASC
                LIMIT ?
            """.trimIndent()

            connection.prepareStatement(sql).use { statement ->
                var parameterIndex = 1

                if (hasCursor) {
                    statement.setLong(parameterIndex++, cursorExcludedAt)
                    statement.setLong(parameterIndex++, cursorExcludedAt)
                    statement.setString(parameterIndex++, cursorId)
                }

                statement.setInt(parameterIndex, limit)

                statement.executeQuery().use { resultSet ->
                    resultSet.toPhotoRecords()
                }
            }
        }
    }

    /**
     * active photo を id で取得する。
     */
    fun findActiveById(photoId: String): PhotoRecord? = database.withConnection { connection ->
        connection.prepareStatement(
            """
            SELECT p.*
            FROM photos p
            WHERE p.id = ?
              AND ${activeWhere("p")}
            """.trimIndent(),
        ).use { statement ->
            statement.setString(1, photoId)

            statement.executeQuery().use { resultSet ->
                if (resultSet.next()) resultSet.toPhotoRecord() else null
            }
        }
    }

    /**
     * active photo を id 群で取得する。
     */
    fun findActiveByIds(photoIds: List<String>): List<PhotoRecord> {
        if (photoIds.isEmpty()) {
            return emptyList()
        }

        return database.withConnection { connection ->
            val placeholders = photoIds.joinToString(",") { "?" }
            val sql = """
                SELECT p.*
                FROM photos p
                WHERE p.id IN ($placeholders)
                  AND ${activeWhere("p")}
            """.trimIndent()

            connection.prepareStatement(sql).use { statement ->
                photoIds.forEachIndexed { index, photoId ->
                    statement.setString(index + 1, photoId)
                }

                statement.executeQuery().use { resultSet ->
                    resultSet.toPhotoRecords()
                }
            }
        }
    }

    /**
     * path で photo を取得する。
     */
    fun findByRelativePath(relativePath: String): PhotoRecord? = database.withConnection { connection ->
        connection.prepareStatement("SELECT p.* FROM photos p WHERE p.relative_path = ?").use { statement ->
            statement.setString(1, relativePath)

            statement.executeQuery().use { resultSet ->
                if (resultSet.next()) resultSet.toPhotoRecord() else null
            }
        }
    }

    /**
     * 指定 id 群の active photo id を返す。
     */
    fun existingActiveIds(photoIds: List<String>): Set<String> = existingIds(photoIds, activeOnly = true)

    /**
     * 指定 id 群の存在する photo id を返す。
     */
    fun existingIds(photoIds: List<String>, activeOnly: Boolean): Set<String> {
        if (photoIds.isEmpty()) {
            return emptySet()
        }

        return database.withConnection { connection ->
            val placeholders = photoIds.joinToString(",") { "?" }
            val activeClause = if (activeOnly) "AND ${activeWhere("p")}" else ""
            val sql = "SELECT p.id FROM photos p WHERE p.id IN ($placeholders) $activeClause"

            connection.prepareStatement(sql).use { statement ->
                photoIds.forEachIndexed { index, photoId ->
                    statement.setString(index + 1, photoId)
                }

                statement.executeQuery().use { resultSet ->
                    buildSet {
                        while (resultSet.next()) {
                            add(resultSet.getString("id"))
                        }
                    }
                }
            }
        }
    }

    /**
     * photo を insert / update する。
     */
    fun upsert(photo: PhotoRecord) {
        database.withConnection { connection ->
            upsert(connection, photo)
        }
    }

    /**
     * last seen のみ更新する。
     */
    fun markSeenUnchanged(photoId: String, now: Long) {
        database.withConnection { connection ->
            connection.prepareStatement(
                """
                UPDATE photos
                SET last_seen_at_epoch_ms = ?,
                    missing_since_epoch_ms = NULL,
                    updated_at_epoch_ms = ?
                WHERE id = ?
                """.trimIndent(),
            ).use { statement ->
                statement.setLong(1, now)
                statement.setLong(2, now)
                statement.setString(3, photoId)
                statement.executeUpdate()
            }
        }
    }

    /**
     * 今回の scan で見つからなかった photo を missing にする。
     */
    fun markMissingNotSeenSince(scanStartedAt: Long, now: Long): Int = database.withConnection { connection ->
        connection.prepareStatement(
            """
            UPDATE photos
            SET missing_since_epoch_ms = ?,
                updated_at_epoch_ms = ?
            WHERE last_seen_at_epoch_ms < ?
              AND missing_since_epoch_ms IS NULL
            """.trimIndent(),
        ).use { statement ->
            statement.setLong(1, now)
            statement.setLong(2, now)
            statement.setLong(3, scanStartedAt)

            statement.executeUpdate()
        }
    }

    /**
     * 写真を論理除外する。
     */
    fun exclude(photoIds: List<String>, now: Long): Pair<Int, List<String>> {
        val existingIds = existingActiveIds(photoIds)
        val targetIds = photoIds.filter(existingIds::contains)

        if (targetIds.isEmpty()) {
            return 0 to photoIds.filterNot(existingIds::contains)
        }

        database.withConnection { connection ->
            val placeholders = targetIds.joinToString(",") { "?" }
            val sql = "UPDATE photos SET excluded_at_epoch_ms = ?, updated_at_epoch_ms = ? WHERE id IN ($placeholders)"

            connection.prepareStatement(sql).use { statement ->
                statement.setLong(1, now)
                statement.setLong(2, now)

                targetIds.forEachIndexed { index, photoId ->
                    statement.setString(index + 3, photoId)
                }

                statement.executeUpdate()
            }
        }

        return targetIds.size to photoIds.filterNot(existingIds::contains)
    }

    /**
     * 除外済み写真を復元する。
     */
    fun restore(photoIds: List<String>, now: Long): Pair<Int, List<String>> {
        if (photoIds.isEmpty()) {
            return 0 to emptyList()
        }

        val restored = database.withConnection { connection ->
            val placeholders = photoIds.joinToString(",") { "?" }
            val sql = """
                UPDATE photos
                SET excluded_at_epoch_ms = NULL,
                    updated_at_epoch_ms = ?
                WHERE id IN ($placeholders)
                  AND excluded_at_epoch_ms IS NOT NULL
                  AND missing_since_epoch_ms IS NULL
            """.trimIndent()

            connection.prepareStatement(sql).use { statement ->
                statement.setLong(1, now)

                photoIds.forEachIndexed { index, photoId ->
                    statement.setString(index + 2, photoId)
                }

                statement.executeUpdate()
            }
        }
        val restoredIds = existingActiveIds(photoIds)

        return restored to photoIds.filterNot(restoredIds::contains)
    }

    private fun upsert(connection: Connection, photo: PhotoRecord) {
        connection.prepareStatement(upsertSql).use { statement ->
            bindPhoto(statement, photo)
            statement.executeUpdate()
        }
    }

    private fun bindPhoto(statement: java.sql.PreparedStatement, photo: PhotoRecord) {
        statement.setString(1, photo.id)
        statement.setString(2, photo.relativePath)
        statement.setString(3, photo.filename)
        statement.setString(4, photo.filenameSortKey)
        statement.setString(5, photo.extension)
        statement.setString(6, photo.mediaType.dbValue)
        statement.setString(7, photo.mimeType)
        statement.setLong(8, photo.fileSizeBytes)
        statement.setLong(9, photo.fileMtimeEpochMs)
        statement.setString(10, photo.sourceFingerprint)
        statement.setLong(11, photo.firstSeenAtEpochMs)
        statement.setLong(12, photo.indexedAtEpochMs)
        statement.setLong(13, photo.lastSeenAtEpochMs)
        statement.setNullableLong(14, photo.missingSinceEpochMs)
        statement.setNullableLong(15, photo.excludedAtEpochMs)
        statement.setNullableInt(16, photo.width)
        statement.setNullableInt(17, photo.height)
        statement.setNullableLong(18, photo.durationMs)
        statement.setNullableInt(19, photo.orientation)
        statement.setLong(20, photo.takenAtEpochMs)
        statement.setString(21, photo.takenAtSource)
        statement.setNullableLong(22, photo.exifTakenAtEpochMs)
        statement.setNullableLong(23, photo.takeoutTakenAtEpochMs)
        statement.setNullableLong(24, photo.videoCreatedAtEpochMs)
        statement.setNullableInt(25, photo.timezoneOffsetMinutes)
        statement.setNullableDouble(26, photo.gpsLat)
        statement.setNullableDouble(27, photo.gpsLng)
        statement.setNullableDouble(28, photo.gpsAlt)
        statement.setNullableString(29, photo.gpsSource)
        statement.setNullableString(30, photo.cameraMake)
        statement.setNullableString(31, photo.cameraModel)
        statement.setNullableString(32, photo.sidecarRelativePath)
        statement.setString(33, photo.metadataStatus)
        statement.setNullableString(34, photo.metadataError)
        statement.setInt(35, photo.metadataVersion)
        statement.setLong(36, photo.createdAtEpochMs)
        statement.setLong(37, photo.updatedAtEpochMs)
    }

    private fun ResultSet.toPhotoRecords(): List<PhotoRecord> {
        val records = mutableListOf<PhotoRecord>()

        while (next()) {
            records += toPhotoRecord()
        }

        return records
    }

    private fun ResultSet.toPhotoRecord(): PhotoRecord = PhotoRecord(
        id = getString("id"),
        relativePath = getString("relative_path"),
        filename = getString("filename"),
        filenameSortKey = getString("filename_sort_key"),
        extension = getString("extension"),
        mediaType = MediaType.fromDb(getString("media_type")),
        mimeType = getString("mime_type"),
        fileSizeBytes = getLong("file_size_bytes"),
        fileMtimeEpochMs = getLong("file_mtime_epoch_ms"),
        sourceFingerprint = getString("source_fingerprint"),
        firstSeenAtEpochMs = getLong("first_seen_at_epoch_ms"),
        indexedAtEpochMs = getLong("indexed_at_epoch_ms"),
        lastSeenAtEpochMs = getLong("last_seen_at_epoch_ms"),
        missingSinceEpochMs = getNullableLong("missing_since_epoch_ms"),
        excludedAtEpochMs = getNullableLong("excluded_at_epoch_ms"),
        width = getNullableInt("width"),
        height = getNullableInt("height"),
        durationMs = getNullableLong("duration_ms"),
        orientation = getNullableInt("orientation"),
        takenAtEpochMs = getLong("taken_at_epoch_ms"),
        takenAtSource = getString("taken_at_source"),
        exifTakenAtEpochMs = getNullableLong("exif_taken_at_epoch_ms"),
        takeoutTakenAtEpochMs = getNullableLong("takeout_taken_at_epoch_ms"),
        videoCreatedAtEpochMs = getNullableLong("video_created_at_epoch_ms"),
        timezoneOffsetMinutes = getNullableInt("timezone_offset_minutes"),
        gpsLat = getNullableDouble("gps_lat"),
        gpsLng = getNullableDouble("gps_lng"),
        gpsAlt = getNullableDouble("gps_alt"),
        gpsSource = getString("gps_source"),
        cameraMake = getString("camera_make"),
        cameraModel = getString("camera_model"),
        sidecarRelativePath = getString("sidecar_relative_path"),
        metadataStatus = getString("metadata_status"),
        metadataError = getString("metadata_error"),
        metadataVersion = getInt("metadata_version"),
        createdAtEpochMs = getLong("created_at_epoch_ms"),
        updatedAtEpochMs = getLong("updated_at_epoch_ms"),
    )

    /**
     * PhotoDao の SQL 補助値。
     */
    private companion object {
        private fun activeWhere(alias: String): String {
            return "$alias.excluded_at_epoch_ms IS NULL AND $alias.missing_since_epoch_ms IS NULL"
        }

        private fun sortExpression(sort: String): String {
            return when (sort) {
                "takenAt" -> "p.taken_at_epoch_ms"
                "name" -> "p.filename_sort_key"
                "indexedAt" -> "p.indexed_at_epoch_ms"
                else -> throw IllegalArgumentException("sort が不正です")
            }
        }

        /**
         * photos table の upsert SQL。
         */
        private val upsertSql = """
            INSERT INTO photos (
              id, relative_path, filename, filename_sort_key, extension, media_type, mime_type,
              file_size_bytes, file_mtime_epoch_ms, source_fingerprint, first_seen_at_epoch_ms,
              indexed_at_epoch_ms, last_seen_at_epoch_ms, missing_since_epoch_ms, excluded_at_epoch_ms,
              width, height, duration_ms, orientation, taken_at_epoch_ms, taken_at_source,
              exif_taken_at_epoch_ms, takeout_taken_at_epoch_ms, video_created_at_epoch_ms,
              timezone_offset_minutes, gps_lat, gps_lng, gps_alt, gps_source, camera_make,
              camera_model, sidecar_relative_path, metadata_status, metadata_error, metadata_version,
              created_at_epoch_ms, updated_at_epoch_ms
            ) VALUES (
              ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?
            )
            ON CONFLICT(id) DO UPDATE SET
              relative_path = excluded.relative_path,
              filename = excluded.filename,
              filename_sort_key = excluded.filename_sort_key,
              extension = excluded.extension,
              media_type = excluded.media_type,
              mime_type = excluded.mime_type,
              file_size_bytes = excluded.file_size_bytes,
              file_mtime_epoch_ms = excluded.file_mtime_epoch_ms,
              source_fingerprint = excluded.source_fingerprint,
              indexed_at_epoch_ms = excluded.indexed_at_epoch_ms,
              last_seen_at_epoch_ms = excluded.last_seen_at_epoch_ms,
              missing_since_epoch_ms = NULL,
              width = excluded.width,
              height = excluded.height,
              duration_ms = excluded.duration_ms,
              orientation = excluded.orientation,
              taken_at_epoch_ms = excluded.taken_at_epoch_ms,
              taken_at_source = excluded.taken_at_source,
              exif_taken_at_epoch_ms = excluded.exif_taken_at_epoch_ms,
              takeout_taken_at_epoch_ms = excluded.takeout_taken_at_epoch_ms,
              video_created_at_epoch_ms = excluded.video_created_at_epoch_ms,
              timezone_offset_minutes = excluded.timezone_offset_minutes,
              gps_lat = excluded.gps_lat,
              gps_lng = excluded.gps_lng,
              gps_alt = excluded.gps_alt,
              gps_source = excluded.gps_source,
              camera_make = excluded.camera_make,
              camera_model = excluded.camera_model,
              sidecar_relative_path = excluded.sidecar_relative_path,
              metadata_status = excluded.metadata_status,
              metadata_error = excluded.metadata_error,
              metadata_version = excluded.metadata_version,
              updated_at_epoch_ms = excluded.updated_at_epoch_ms
        """.trimIndent()
    }
}

/**
 * random sort 用に photo と seed key を束ねた値。
 */
private data class RandomSortedPhoto(
    val photo: PhotoRecord,
    val randomKey: Long,
) {
    /**
     * cursor より後ろの item かどうかを返す。
     */
    fun isAfterCursor(lastLongKey: Long?, lastId: String?): Boolean {
        val hasNoCursor = lastLongKey == null || lastId == null

        if (hasNoCursor) {
            return true
        }

        val hasGreaterKey = randomKey > lastLongKey
        val hasSameKeyAndGreaterId = randomKey == lastLongKey && photo.id > lastId

        return hasGreaterKey || hasSameKeyAndGreaterId
    }
}
