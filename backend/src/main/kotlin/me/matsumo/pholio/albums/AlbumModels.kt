package me.matsumo.pholio.albums

import kotlinx.serialization.Serializable
import me.matsumo.pholio.photos.PhotoRecord
import me.matsumo.pholio.photos.ThumbnailVariant
import me.matsumo.pholio.photos.thumbnailUrl
import me.matsumo.pholio.time.TimeFormats

/**
 * albums table の 1 行。
 */
data class AlbumRecord(
    val id: String,
    val name: String,
    val nameSortKey: String,
    val coverPhotoId: String?,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
    val deletedAtEpochMs: Long?,
)

/**
 * album 一覧 item。
 */
@Serializable
data class AlbumSummaryResponse(
    val id: String,
    val name: String,
    val photoCount: Long,
    val coverPhoto: AlbumCoverPhotoResponse?,
    val createdAt: String,
    val updatedAt: String,
)

/**
 * album cover photo。
 */
@Serializable
data class AlbumCoverPhotoResponse(
    val id: String,
    val thumbnailUrl: String,
)

/**
 * album 一覧 response。
 */
@Serializable
data class AlbumListResponse(
    val items: List<AlbumSummaryResponse>,
)

/**
 * album 詳細 response。
 */
@Serializable
data class AlbumDetailResponse(
    val id: String,
    val name: String,
    val photoCount: Long,
    val createdAt: String,
    val updatedAt: String,
)

/**
 * album 作成 request。
 */
@Serializable
data class CreateAlbumRequest(
    val name: String,
)

/**
 * album 更新 request。
 */
@Serializable
data class UpdateAlbumRequest(
    val name: String,
)

/**
 * album 写真追加 response。
 */
@Serializable
data class AddAlbumPhotosResponse(
    val added: Int,
    val alreadyPresent: Int,
    val notFound: List<String>,
)

/**
 * album 写真除去 response。
 */
@Serializable
data class RemoveAlbumPhotosResponse(
    val removed: Int,
    val notPresent: Int,
    val notFound: List<String>,
)

/**
 * AlbumRecord から summary response を作成する。
 */
fun AlbumRecord.toSummaryResponse(
    photoCount: Long,
    coverPhoto: PhotoRecord?,
): AlbumSummaryResponse = AlbumSummaryResponse(
    id = id,
    name = name,
    photoCount = photoCount,
    coverPhoto = coverPhoto?.let {
        AlbumCoverPhotoResponse(
            id = it.id,
            thumbnailUrl = it.thumbnailUrl(ThumbnailVariant.GridMd),
        )
    },
    createdAt = TimeFormats.toIsoUtc(createdAtEpochMs),
    updatedAt = TimeFormats.toIsoUtc(updatedAtEpochMs),
)

/**
 * AlbumRecord から detail response を作成する。
 */
fun AlbumRecord.toDetailResponse(photoCount: Long): AlbumDetailResponse = AlbumDetailResponse(
    id = id,
    name = name,
    photoCount = photoCount,
    createdAt = TimeFormats.toIsoUtc(createdAtEpochMs),
    updatedAt = TimeFormats.toIsoUtc(updatedAtEpochMs),
)
