package me.matsumo.pholio.photos

import java.security.SecureRandom
import me.matsumo.pholio.api.CursorCodec
import me.matsumo.pholio.api.PageCursor
import me.matsumo.pholio.index.IndexDao

/**
 * 写真一覧・詳細 API の application service。
 */
class PhotoService(
    private val photoDao: PhotoDao,
    private val indexDao: IndexDao,
    private val cursorCodec: CursorCodec = CursorCodec(),
) {
    /**
     * active photo 一覧を取得する。
     */
    fun listPhotos(
        albumId: String?,
        sort: String,
        order: String,
        seed: String?,
        limit: Int,
        cursor: String?,
    ): PhotoListResponse {
        val normalizedSort = normalizeSort(sort)
        val normalizedOrder = if (normalizedSort == "random") "asc" else normalizeOrder(order)
        val normalizedSeed = if (normalizedSort == "random") seed ?: createSeed() else null
        val boundedLimit = limit.coerceIn(1, 500)
        val libraryRevision = indexDao.libraryRevision()
        val scope = albumId?.let { "album:$it" } ?: "home"
        val decodedCursor = cursor?.let(cursorCodec::decode)

        validateCursor(decodedCursor, scope, normalizedSort, normalizedOrder, normalizedSeed)

        val records = photoDao.listActive(
            albumId = albumId,
            sort = normalizedSort,
            order = normalizedOrder,
            seed = normalizedSeed,
            limitPlusOne = boundedLimit + 1,
            lastLongKey = decodedCursor?.lastLongKey,
            lastStringKey = decodedCursor?.lastStringKey,
            lastId = decodedCursor?.lastId,
        )
        val hasMore = records.size > boundedLimit
        val items = records.take(boundedLimit)
        val nextCursor = if (hasMore) {
            items.lastOrNull()?.let { last ->
                cursorCodec.encode(
                    PageCursor(
                        version = 1,
                        scope = scope,
                        sort = normalizedSort,
                        order = normalizedOrder,
                        seed = normalizedSeed,
                        lastLongKey = last.longSortKey(normalizedSort, normalizedSeed),
                        lastStringKey = last.stringSortKey(normalizedSort),
                        lastId = last.id,
                        libraryRevision = libraryRevision,
                    ),
                )
            }
        } else {
            null
        }

        return PhotoListResponse(
            items = items.map(PhotoRecord::toSummaryResponse),
            pageInfo = PageInfoResponse(
                hasMore = hasMore,
                nextCursor = nextCursor,
                sort = normalizedSort,
                order = normalizedOrder,
                seed = normalizedSeed,
                limit = boundedLimit,
                totalCount = photoDao.countActive(albumId),
                libraryRevision = libraryRevision,
            ),
        )
    }

    /**
     * 除外済み photo 一覧を取得する。
     */
    fun listExcluded(limit: Int, cursor: String?): PhotoListResponse {
        val boundedLimit = limit.coerceIn(1, 500)
        val libraryRevision = indexDao.libraryRevision()
        val decodedCursor = cursor?.let(cursorCodec::decode)

        validateCursor(decodedCursor, "excluded", "excludedAt", "desc", null)

        val records = photoDao.listExcluded(
            limit = boundedLimit + 1,
            cursorExcludedAt = decodedCursor?.lastLongKey,
            cursorId = decodedCursor?.lastId,
        )
        val hasMore = records.size > boundedLimit
        val items = records.take(boundedLimit)
        val nextCursor = if (hasMore) {
            items.lastOrNull()?.let { last ->
                cursorCodec.encode(
                    PageCursor(
                        version = 1,
                        scope = "excluded",
                        sort = "excludedAt",
                        order = "desc",
                        seed = null,
                        lastLongKey = last.excludedAtEpochMs,
                        lastStringKey = null,
                        lastId = last.id,
                        libraryRevision = libraryRevision,
                    ),
                )
            }
        } else {
            null
        }

        return PhotoListResponse(
            items = items.map(PhotoRecord::toSummaryResponse),
            pageInfo = PageInfoResponse(
                hasMore = hasMore,
                nextCursor = nextCursor,
                sort = "excludedAt",
                order = "desc",
                seed = null,
                limit = boundedLimit,
                totalCount = photoDao.countExcluded(),
                libraryRevision = libraryRevision,
            ),
        )
    }

    /**
     * 詳細を取得する。
     */
    fun getDetail(photoId: String): PhotoDetailResponse {
        val photo = photoDao.findActiveById(photoId) ?: throw NoSuchElementException("写真が見つかりません")

        return photo.toDetailResponse()
    }

    /**
     * active photo record を取得する。
     */
    fun getActiveRecord(photoId: String): PhotoRecord {
        return photoDao.findActiveById(photoId) ?: throw NoSuchElementException("写真が見つかりません")
    }

    /**
     * 前後写真を取得する。
     */
    fun neighbors(
        photoId: String,
        albumId: String?,
        sort: String,
        order: String,
        seed: String?,
    ): NeighborsResponse {
        val normalizedSort = normalizeSort(sort)
        val normalizedOrder = if (normalizedSort == "random") "asc" else normalizeOrder(order)
        val records = photoDao.listActive(
            albumId = albumId,
            sort = normalizedSort,
            order = normalizedOrder,
            seed = seed,
            limitPlusOne = 100_000,
            lastLongKey = null,
            lastStringKey = null,
            lastId = null,
        )
        val index = records.indexOfFirst { it.id == photoId }

        require(index >= 0) {
            "写真が見つかりません"
        }

        return NeighborsResponse(
            previous = records.getOrNull(index - 1)?.toNeighborResponse(),
            current = NeighborCurrentResponse(id = photoId),
            next = records.getOrNull(index + 1)?.toNeighborResponse(),
        )
    }

    /**
     * 写真を論理除外する。
     */
    fun exclude(photoIds: List<String>): ExcludePhotosResponse {
        require(photoIds.size <= 1000) {
            "一度に除外できる写真は 1000 件までです"
        }

        val now = System.currentTimeMillis()
        val (excluded, notFound) = photoDao.exclude(photoIds.distinct(), now)

        if (excluded > 0) {
            indexDao.incrementLibraryRevision(now)
        }

        return ExcludePhotosResponse(
            excluded = excluded,
            notFound = notFound,
        )
    }

    /**
     * 写真を復元する。
     */
    fun restore(photoIds: List<String>): RestorePhotosResponse {
        require(photoIds.size <= 1000) {
            "一度に復元できる写真は 1000 件までです"
        }

        val now = System.currentTimeMillis()
        val (restored, notFound) = photoDao.restore(photoIds.distinct(), now)

        if (restored > 0) {
            indexDao.incrementLibraryRevision(now)
        }

        return RestorePhotosResponse(
            restored = restored,
            notFound = notFound,
        )
    }

    private fun validateCursor(
        cursor: PageCursor?,
        scope: String,
        sort: String,
        order: String,
        seed: String?,
    ) {
        if (cursor == null) {
            return
        }

        val hasExpectedVersion = cursor.version == 1
        val hasExpectedScope = cursor.scope == scope
        val hasExpectedSort = cursor.sort == sort
        val hasExpectedOrder = cursor.order == order
        val hasExpectedSeed = cursor.seed == seed
        val isValidCursor = hasExpectedVersion && hasExpectedScope && hasExpectedSort && hasExpectedOrder && hasExpectedSeed

        require(isValidCursor) {
            "cursor がリクエスト条件と一致しません"
        }
    }

    private fun normalizeSort(sort: String): String {
        return when (sort) {
            "takenAt", "name", "indexedAt", "random" -> sort
            else -> "takenAt"
        }
    }

    private fun normalizeOrder(order: String): String {
        return when (order) {
            "asc", "desc" -> order
            else -> "desc"
        }
    }

    private fun createSeed(): String {
        val bytes = ByteArray(8)
        random.nextBytes(bytes)

        return bytes.joinToString(separator = "") { "%02x".format(it) }
    }

    /**
     * PhotoService の共有値。
     */
    private companion object {
        /**
         * random sort seed 生成用の乱数。
         */
        private val random = SecureRandom()
    }
}

private fun PhotoRecord.longSortKey(sort: String, seed: String?): Long? {
    return when (sort) {
        "takenAt" -> takenAtEpochMs
        "indexedAt" -> indexedAtEpochMs
        "random" -> me.matsumo.pholio.util.Hashing.seededRandomKey(seed ?: "default", id)
        else -> null
    }
}

private fun PhotoRecord.stringSortKey(sort: String): String? {
    return when (sort) {
        "name" -> filenameSortKey
        else -> null
    }
}

private fun PhotoRecord.toNeighborResponse(): NeighborPhotoResponse = NeighborPhotoResponse(
    id = id,
    thumbnail = DetailThumbnailResponse(previewLg = thumbnailUrl(ThumbnailVariant.PreviewLg)),
)
