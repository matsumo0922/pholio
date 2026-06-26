package me.matsumo.pholio.albums

import com.github.f4b6a3.ulid.UlidCreator
import java.sql.ResultSet
import me.matsumo.pholio.db.Database
import me.matsumo.pholio.db.Jdbc.getNullableLong
import me.matsumo.pholio.index.IndexDao
import me.matsumo.pholio.photos.PhotoDao
import me.matsumo.pholio.photos.PhotoRecord

/**
 * アルバムとアルバム所属の DAO。
 */
class AlbumDao(
    private val database: Database,
    private val photoDao: PhotoDao,
    private val indexDao: IndexDao,
) {
    /**
     * active album を作成日時順で取得する。
     */
    fun listAlbums(): List<AlbumWithStats> = database.withConnection { connection ->
        connection.prepareStatement(
            """
            SELECT *
            FROM albums
            WHERE deleted_at_epoch_ms IS NULL
            ORDER BY created_at_epoch_ms DESC, id ASC
            """.trimIndent(),
        ).use { statement ->
            statement.executeQuery().use { resultSet ->
                buildList {
                    while (resultSet.next()) {
                        val album = resultSet.toAlbumRecord()

                        add(
                            AlbumWithStats(
                                album = album,
                                photoCount = countPhotos(album.id),
                                coverPhoto = coverPhoto(album),
                            ),
                        )
                    }
                }
            }
        }
    }

    /**
     * active album を取得する。
     */
    fun findActive(albumId: String): AlbumRecord? = database.withConnection { connection ->
        connection.prepareStatement(
            """
            SELECT *
            FROM albums
            WHERE id = ?
              AND deleted_at_epoch_ms IS NULL
            """.trimIndent(),
        ).use { statement ->
            statement.setString(1, albumId)

            statement.executeQuery().use { resultSet ->
                if (resultSet.next()) resultSet.toAlbumRecord() else null
            }
        }
    }

    /**
     * album を作成する。
     */
    fun create(name: String, now: Long): AlbumRecord {
        val trimmedName = name.trim()
        require(trimmedName.isNotEmpty()) {
            "アルバム名を入力してください"
        }

        val albumId = UlidCreator.getUlid().toString()

        database.withConnection { connection ->
            connection.prepareStatement(
                """
                INSERT INTO albums (
                  id, name, name_sort_key, created_at_epoch_ms, updated_at_epoch_ms
                ) VALUES (?, ?, ?, ?, ?)
                """.trimIndent(),
            ).use { statement ->
                statement.setString(1, albumId)
                statement.setString(2, trimmedName)
                statement.setString(3, trimmedName.lowercase())
                statement.setLong(4, now)
                statement.setLong(5, now)
                statement.executeUpdate()
            }
        }
        indexDao.incrementLibraryRevision(now)

        return findActive(albumId) ?: error("作成したアルバムが見つかりません")
    }

    /**
     * album 名を変更する。
     */
    fun updateName(albumId: String, name: String, now: Long): AlbumRecord {
        val trimmedName = name.trim()
        require(trimmedName.isNotEmpty()) {
            "アルバム名を入力してください"
        }

        val updated = database.withConnection { connection ->
            connection.prepareStatement(
                """
                UPDATE albums
                SET name = ?,
                    name_sort_key = ?,
                    updated_at_epoch_ms = ?
                WHERE id = ?
                  AND deleted_at_epoch_ms IS NULL
                """.trimIndent(),
            ).use { statement ->
                statement.setString(1, trimmedName)
                statement.setString(2, trimmedName.lowercase())
                statement.setLong(3, now)
                statement.setString(4, albumId)

                statement.executeUpdate()
            }
        }

        require(updated > 0) {
            "アルバムが見つかりません"
        }
        indexDao.incrementLibraryRevision(now)

        return findActive(albumId) ?: error("更新したアルバムが見つかりません")
    }

    /**
     * album を論理削除する。
     */
    fun delete(albumId: String, now: Long): Boolean {
        val deleted = database.withConnection { connection ->
            connection.prepareStatement(
                """
                UPDATE albums
                SET deleted_at_epoch_ms = ?,
                    updated_at_epoch_ms = ?
                WHERE id = ?
                  AND deleted_at_epoch_ms IS NULL
                """.trimIndent(),
            ).use { statement ->
                statement.setLong(1, now)
                statement.setLong(2, now)
                statement.setString(3, albumId)

                statement.executeUpdate() > 0
            }
        }

        if (deleted) {
            indexDao.incrementLibraryRevision(now)
        }

        return deleted
    }

    /**
     * album に写真を追加する。
     */
    fun addPhotos(albumId: String, photoIds: List<String>, now: Long): AddAlbumPhotosResponse {
        require(photoIds.size <= 1000) {
            "一度に追加できる写真は 1000 件までです"
        }
        require(findActive(albumId) != null) {
            "アルバムが見つかりません"
        }

        val existingIds = photoDao.existingActiveIds(photoIds)
        val notFound = photoIds.filterNot(existingIds::contains)
        var added = 0
        var alreadyPresent = 0

        database.withConnection { connection ->
            existingIds.forEach { photoId ->
                val changed = connection.prepareStatement(
                    """
                    INSERT INTO album_photos (
                      album_id, photo_id, added_at_epoch_ms, removed_at_epoch_ms
                    ) VALUES (?, ?, ?, NULL)
                    ON CONFLICT(album_id, photo_id) DO UPDATE SET
                      removed_at_epoch_ms = NULL,
                      added_at_epoch_ms = excluded.added_at_epoch_ms
                    WHERE album_photos.removed_at_epoch_ms IS NOT NULL
                    """.trimIndent(),
                ).use { statement ->
                    statement.setString(1, albumId)
                    statement.setString(2, photoId)
                    statement.setLong(3, now)
                    statement.executeUpdate()
                }

                if (changed > 0) {
                    added += 1
                } else {
                    alreadyPresent += 1
                }
            }
        }

        if (added > 0) {
            indexDao.incrementLibraryRevision(now)
        }

        return AddAlbumPhotosResponse(
            added = added,
            alreadyPresent = alreadyPresent,
            notFound = notFound,
        )
    }

    /**
     * album から写真を論理除去する。
     */
    fun removePhotos(albumId: String, photoIds: List<String>, now: Long): RemoveAlbumPhotosResponse {
        require(photoIds.size <= 1000) {
            "一度に除去できる写真は 1000 件までです"
        }
        require(findActive(albumId) != null) {
            "アルバムが見つかりません"
        }

        val existingIds = photoDao.existingActiveIds(photoIds)
        val notFound = photoIds.filterNot(existingIds::contains)
        val removed = database.withConnection { connection ->
            val targetIds = photoIds.filter(existingIds::contains)

            if (targetIds.isEmpty()) {
                return@withConnection 0
            }

            val placeholders = targetIds.joinToString(",") { "?" }
            val sql = """
                UPDATE album_photos
                SET removed_at_epoch_ms = ?
                WHERE album_id = ?
                  AND photo_id IN ($placeholders)
                  AND removed_at_epoch_ms IS NULL
            """.trimIndent()

            connection.prepareStatement(sql).use { statement ->
                statement.setLong(1, now)
                statement.setString(2, albumId)

                targetIds.forEachIndexed { index, photoId ->
                    statement.setString(index + 3, photoId)
                }

                statement.executeUpdate()
            }
        }
        val notPresent = existingIds.size - removed

        if (removed > 0) {
            indexDao.incrementLibraryRevision(now)
        }

        return RemoveAlbumPhotosResponse(
            removed = removed,
            notPresent = notPresent,
            notFound = notFound,
        )
    }

    /**
     * album 内 active photo 件数を返す。
     */
    fun countPhotos(albumId: String): Long = photoDao.countActive(albumId)

    /**
     * album cover photo を返す。
     */
    fun coverPhoto(album: AlbumRecord): PhotoRecord? {
        val configuredCover = album.coverPhotoId?.let(photoDao::findActiveById)

        if (configuredCover != null) {
            return configuredCover
        }

        return database.withConnection { connection ->
            connection.prepareStatement(
                """
                SELECT p.*
                FROM album_photos ap
                JOIN photos p ON p.id = ap.photo_id
                WHERE ap.album_id = ?
                  AND ap.removed_at_epoch_ms IS NULL
                  AND p.excluded_at_epoch_ms IS NULL
                  AND p.missing_since_epoch_ms IS NULL
                ORDER BY ap.added_at_epoch_ms DESC, p.id ASC
                LIMIT 1
                """.trimIndent(),
            ).use { statement ->
                statement.setString(1, album.id)

                statement.executeQuery().use { resultSet ->
                    if (resultSet.next()) {
                        // PhotoDao の mapper を外へ出さないため、id で取り直す。
                        photoDao.findActiveById(resultSet.getString("id"))
                    } else {
                        null
                    }
                }
            }
        }
    }

    private fun ResultSet.toAlbumRecord(): AlbumRecord = AlbumRecord(
        id = getString("id"),
        name = getString("name"),
        nameSortKey = getString("name_sort_key"),
        coverPhotoId = getString("cover_photo_id"),
        createdAtEpochMs = getLong("created_at_epoch_ms"),
        updatedAtEpochMs = getLong("updated_at_epoch_ms"),
        deletedAtEpochMs = getNullableLong("deleted_at_epoch_ms"),
    )
}

/**
 * album と集計情報。
 */
data class AlbumWithStats(
    val album: AlbumRecord,
    val photoCount: Long,
    val coverPhoto: PhotoRecord?,
)
