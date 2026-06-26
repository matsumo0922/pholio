package me.matsumo.pholio.thumbs

import java.nio.file.Files
import java.nio.file.Path
import java.util.Base64
import kotlin.io.path.createDirectories
import me.matsumo.pholio.config.AppConfig
import me.matsumo.pholio.files.PhotoPathResolver
import me.matsumo.pholio.media.ThumbnailGenerator
import me.matsumo.pholio.photos.PhotoDao
import me.matsumo.pholio.photos.PhotoRecord
import me.matsumo.pholio.photos.ThumbnailVariant

/**
 * thumbnail 生成・配信を扱う service。
 */
class ThumbnailService(
    private val config: AppConfig,
    private val photoDao: PhotoDao,
    private val thumbnailDao: ThumbnailDao,
    private val pathResolver: PhotoPathResolver,
    private val thumbnailGenerator: ThumbnailGenerator,
) {
    /**
     * thumbnail response を取得する。
     */
    fun resolveThumbnail(photo: PhotoRecord, variant: ThumbnailVariant): ThumbnailResponse {
        val now = System.currentTimeMillis()

        if (variant == ThumbnailVariant.PreviewLg) {
            thumbnailDao.enqueue(photo.id, variant, photo.sourceFingerprint, now)
        }

        val thumbnail = thumbnailDao.find(photo.id, variant)
        val hasReadyThumbnail = thumbnail?.status == "ready" && thumbnail.relativeCachePath != null

        if (hasReadyThumbnail) {
            val cachePath = config.thumbDir.resolve(thumbnail.relativeCachePath).normalize()

            if (Files.exists(cachePath)) {
                return ThumbnailResponse.File(cachePath)
            }
        }

        if (variant == ThumbnailVariant.PreviewLg) {
            val gridMd = thumbnailDao.find(photo.id, ThumbnailVariant.GridMd)
            val hasReadyGridMd = gridMd?.status == "ready" && gridMd.relativeCachePath != null

            if (hasReadyGridMd) {
                val cachePath = config.thumbDir.resolve(gridMd.relativeCachePath).normalize()

                if (Files.exists(cachePath)) {
                    return ThumbnailResponse.File(cachePath)
                }
            }
        }

        return ThumbnailResponse.Placeholder(failed = thumbnail?.status == "failed")
    }

    /**
     * worker が 1 task を処理する。
     */
    fun processTask(task: ThumbnailTask) {
        val photo = photoDao.findActiveById(task.photoId)

        if (photo == null) {
            thumbnailDao.markSkipped(
                photoId = task.photoId,
                variant = task.variant,
                sourceFingerprint = task.sourceFingerprint,
                reason = "写真が active ではないため thumbnail 生成を skip しました",
                now = System.currentTimeMillis(),
            )

            return
        }

        val sourcePath = pathResolver.resolve(photo)
        val relativeCachePath = relativeCachePath(photo.id, task.variant, task.sourceFingerprint)
        val outputPath = config.thumbDir.resolve(relativeCachePath)
        val tmpPath = config.thumbDir.resolve("tmp/${photo.id}-${task.variant.dbValue}-${System.nanoTime()}.webp")

        tmpPath.parent.createDirectories()
        outputPath.parent.createDirectories()

        try {
            thumbnailGenerator.generate(sourcePath, photo, task.variant, tmpPath)
            Files.move(
                tmpPath,
                outputPath,
                java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                java.nio.file.StandardCopyOption.ATOMIC_MOVE,
            )

            thumbnailDao.markReady(
                photoId = photo.id,
                variant = task.variant,
                sourceFingerprint = task.sourceFingerprint,
                relativeCachePath = relativeCachePath,
                width = null,
                height = null,
                sizeBytes = Files.size(outputPath),
                now = System.currentTimeMillis(),
            )
        } catch (throwable: Throwable) {
            Files.deleteIfExists(tmpPath)
            thumbnailDao.markFailed(
                photoId = task.photoId,
                variant = task.variant,
                sourceFingerprint = task.sourceFingerprint,
                error = throwable.message ?: throwable::class.java.name,
                now = System.currentTimeMillis(),
                maxAttempts = 3,
            )
        }
    }

    private fun relativeCachePath(photoId: String, variant: ThumbnailVariant, fingerprint: String): String {
        val first = photoId.take(2)
        val second = photoId.drop(2).take(2)

        return "$first/$second/$photoId/${variant.dbValue}.$fingerprint.webp"
    }
}

/**
 * thumbnail API が返す内容。
 */
sealed class ThumbnailResponse {
    /**
     * cache file を返す。
     */
    data class File(
        val path: Path,
    ) : ThumbnailResponse()

    /**
     * placeholder を返す。
     */
    data class Placeholder(
        val failed: Boolean,
    ) : ThumbnailResponse()

    companion object {
        /**
         * 1x1 WebP placeholder。
         */
        val placeholderBytes: ByteArray = Base64.getDecoder().decode(
            "UklGRiIAAABXRUJQVlA4IBYAAAAwAQCdASoBAAEADsD+JaQAA3AAAAAA",
        )
    }
}
