package me.matsumo.pholio.photos

import kotlinx.serialization.Serializable
import me.matsumo.pholio.time.TimeFormats

/**
 * media type の DB 値。
 */
enum class MediaType(
    val dbValue: String,
) {
    Image("image"),
    Video("video"),
    ;

    /**
     * MediaType の生成ヘルパー。
     */
    companion object {
        /**
         * DB 値から media type を復元する。
         */
        fun fromDb(value: String): MediaType = entries.first { it.dbValue == value }
    }
}

/**
 * 撮影日時 source の DB 値。
 */
enum class TakenAtSource(
    val dbValue: String,
) {
    TakeoutJson("takeout_json"),
    Exif("exif"),
    VideoMetadata("video_metadata"),
    FileMtime("file_mtime"),
    Unknown("unknown"),
    ;
}

/**
 * GPS source の DB 値。
 */
enum class GpsSource(
    val dbValue: String,
) {
    TakeoutGeoData("takeout_geoData"),
    TakeoutGeoDataExif("takeout_geoDataExif"),
    Exif("exif"),
    None("none"),
    ;
}

/**
 * thumbnail variant の DB 値。
 */
enum class ThumbnailVariant(
    val dbValue: String,
    val longEdge: Int,
) {
    GridSm("grid_sm", 320),
    GridMd("grid_md", 640),
    PreviewLg("preview_lg", 1920),
    ;

    /**
     * ThumbnailVariant の生成ヘルパー。
     */
    companion object {
        /**
         * API path の値から variant を復元する。
         */
        fun fromPath(value: String): ThumbnailVariant = entries.firstOrNull { it.dbValue == value }
            ?: throw IllegalArgumentException("thumbnail variant が不正です")
    }
}

/**
 * photos table の 1 行。
 */
data class PhotoRecord(
    val id: String,
    val relativePath: String,
    val filename: String,
    val filenameSortKey: String,
    val extension: String,
    val mediaType: MediaType,
    val mimeType: String,
    val fileSizeBytes: Long,
    val fileMtimeEpochMs: Long,
    val sourceFingerprint: String,
    val firstSeenAtEpochMs: Long,
    val indexedAtEpochMs: Long,
    val lastSeenAtEpochMs: Long,
    val missingSinceEpochMs: Long?,
    val excludedAtEpochMs: Long?,
    val width: Int?,
    val height: Int?,
    val durationMs: Long?,
    val orientation: Int?,
    val takenAtEpochMs: Long,
    val takenAtSource: String,
    val exifTakenAtEpochMs: Long?,
    val takeoutTakenAtEpochMs: Long?,
    val videoCreatedAtEpochMs: Long?,
    val timezoneOffsetMinutes: Int?,
    val gpsLat: Double?,
    val gpsLng: Double?,
    val gpsAlt: Double?,
    val gpsSource: String?,
    val cameraMake: String?,
    val cameraModel: String?,
    val sidecarRelativePath: String?,
    val metadataStatus: String,
    val metadataError: String?,
    val metadataVersion: Int,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
)

/**
 * Photo list API のレスポンス。
 */
@Serializable
data class PhotoListResponse(
    val items: List<PhotoSummaryResponse>,
    val pageInfo: PageInfoResponse,
)

/**
 * Photo list のページ情報。
 */
@Serializable
data class PageInfoResponse(
    val hasMore: Boolean,
    val nextCursor: String?,
    val sort: String,
    val order: String,
    val seed: String?,
    val limit: Int,
    val totalCount: Long,
    val libraryRevision: Long,
)

/**
 * 一覧用の写真・動画 summary。
 */
@Serializable
data class PhotoSummaryResponse(
    val id: String,
    val mediaType: String,
    val filename: String,
    val takenAt: String,
    val takenAtEpochMs: Long,
    val takenAtSource: String,
    val indexedAt: String,
    val indexedAtEpochMs: Long,
    val width: Int?,
    val height: Int?,
    val durationMs: Long?,
    val thumbnail: ThumbnailUrlsResponse,
)

/**
 * 詳細用の写真・動画 response。
 */
@Serializable
data class PhotoDetailResponse(
    val id: String,
    val mediaType: String,
    val filename: String,
    val takenAt: String,
    val takenAtEpochMs: Long,
    val takenAtSource: String,
    val width: Int?,
    val height: Int?,
    val durationMs: Long?,
    val gps: GpsResponse?,
    val camera: CameraResponse?,
    val thumbnail: DetailThumbnailResponse,
    val originalUrl: String,
)

/**
 * GPS 情報。
 */
@Serializable
data class GpsResponse(
    val lat: Double,
    val lng: Double,
    val alt: Double?,
    val source: String,
)

/**
 * カメラ情報。
 */
@Serializable
data class CameraResponse(
    val make: String?,
    val model: String?,
)

/**
 * 一覧 thumbnail URL 群。
 */
@Serializable
data class ThumbnailUrlsResponse(
    val gridSm: String,
    val gridMd: String,
    val previewLg: String,
)

/**
 * 詳細 thumbnail URL。
 */
@Serializable
data class DetailThumbnailResponse(
    val previewLg: String,
)

/**
 * 前後写真 response。
 */
@Serializable
data class NeighborsResponse(
    val previous: NeighborPhotoResponse?,
    val current: NeighborCurrentResponse,
    val next: NeighborPhotoResponse?,
)

/**
 * 前後写真の簡易情報。
 */
@Serializable
data class NeighborPhotoResponse(
    val id: String,
    val thumbnail: DetailThumbnailResponse,
)

/**
 * 現在写真の簡易情報。
 */
@Serializable
data class NeighborCurrentResponse(
    val id: String,
)

/**
 * 写真一括除外 request。
 */
@Serializable
data class PhotoIdsRequest(
    val photoIds: List<String>,
)

/**
 * 写真一括除外 response。
 */
@Serializable
data class ExcludePhotosResponse(
    val excluded: Int,
    val notFound: List<String>,
)

/**
 * 写真一括復元 response。
 */
@Serializable
data class RestorePhotosResponse(
    val restored: Int,
    val notFound: List<String>,
)

/**
 * PhotoRecord から summary response を作成する。
 */
fun PhotoRecord.toSummaryResponse(): PhotoSummaryResponse = PhotoSummaryResponse(
    id = id,
    mediaType = mediaType.dbValue,
    filename = filename,
    takenAt = TimeFormats.toIsoUtc(takenAtEpochMs),
    takenAtEpochMs = takenAtEpochMs,
    takenAtSource = takenAtSource,
    indexedAt = TimeFormats.toIsoUtc(indexedAtEpochMs),
    indexedAtEpochMs = indexedAtEpochMs,
    width = width,
    height = height,
    durationMs = durationMs,
    thumbnail = thumbnailUrls(),
)

/**
 * PhotoRecord から detail response を作成する。
 */
fun PhotoRecord.toDetailResponse(): PhotoDetailResponse = PhotoDetailResponse(
    id = id,
    mediaType = mediaType.dbValue,
    filename = filename,
    takenAt = TimeFormats.toIsoUtc(takenAtEpochMs),
    takenAtEpochMs = takenAtEpochMs,
    takenAtSource = takenAtSource,
    width = width,
    height = height,
    durationMs = durationMs,
    gps = gpsResponse(),
    camera = cameraResponse(),
    thumbnail = DetailThumbnailResponse(previewLg = thumbnailUrl(ThumbnailVariant.PreviewLg)),
    originalUrl = "/api/v1/photos/$id/original?v=$sourceFingerprint",
)

/**
 * PhotoRecord から thumbnail URL を作成する。
 */
fun PhotoRecord.thumbnailUrl(variant: ThumbnailVariant): String {
    return "/api/v1/photos/$id/thumbnail/${variant.dbValue}?v=$sourceFingerprint"
}

private fun PhotoRecord.thumbnailUrls(): ThumbnailUrlsResponse = ThumbnailUrlsResponse(
    gridSm = thumbnailUrl(ThumbnailVariant.GridSm),
    gridMd = thumbnailUrl(ThumbnailVariant.GridMd),
    previewLg = thumbnailUrl(ThumbnailVariant.PreviewLg),
)

private fun PhotoRecord.gpsResponse(): GpsResponse? {
    val lat = gpsLat ?: return null
    val lng = gpsLng ?: return null
    val source = gpsSource ?: return null

    return GpsResponse(
        lat = lat,
        lng = lng,
        alt = gpsAlt,
        source = source,
    )
}

private fun PhotoRecord.cameraResponse(): CameraResponse? {
    val hasNoCameraInfo = cameraMake == null && cameraModel == null

    if (hasNoCameraInfo) {
        return null
    }

    return CameraResponse(
        make = cameraMake,
        model = cameraModel,
    )
}
