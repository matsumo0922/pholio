package me.matsumo.pholio.index

import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.io.path.extension
import kotlin.io.path.fileSize
import kotlin.io.path.getLastModifiedTime
import kotlin.io.path.isRegularFile
import kotlin.io.path.name
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import me.matsumo.pholio.config.AppConfig
import me.matsumo.pholio.api.ApiConflictException
import me.matsumo.pholio.media.MediaMetadataExtractor
import me.matsumo.pholio.media.MediaTypes
import me.matsumo.pholio.media.TakeoutSidecar
import me.matsumo.pholio.photos.MediaType
import me.matsumo.pholio.photos.PhotoDao
import me.matsumo.pholio.photos.PhotoRecord
import me.matsumo.pholio.photos.ThumbnailVariant
import me.matsumo.pholio.thumbs.ThumbnailDao
import me.matsumo.pholio.util.Hashing

/**
 * `/photos` 配下を scan して DB と thumbnail queue を更新する。
 */
class IndexScanner(
    private val config: AppConfig,
    private val indexDao: IndexDao,
    private val photoDao: PhotoDao,
    private val thumbnailDao: ThumbnailDao,
    private val metadataExtractor: MediaMetadataExtractor,
) {
    private val running = AtomicBoolean(false)

    /**
     * 起動時 scan を必要に応じて開始する。
     */
    fun startOnStartup(scope: CoroutineScope) {
        if (!config.scanOnStartup) {
            return
        }

        val mode = if (photoDao.countActive() == 0L) ScanMode.Full else ScanMode.Diff

        start(scope, mode)
    }

    /**
     * scan job を開始する。
     */
    fun start(scope: CoroutineScope, mode: ScanMode): ScanStartResponse {
        if (!running.compareAndSet(false, true)) {
            throw ApiConflictException("scan はすでに実行中です")
        }

        val job = indexDao.createJob(mode, System.currentTimeMillis())

        scope.launch {
            runScan(job.id, mode)
        }

        return ScanStartResponse(
            jobId = job.id,
            status = job.status,
        )
    }

    /**
     * 実行中 scan に cancel を要求する。
     */
    fun cancel(jobId: String): Boolean = indexDao.requestCancel(jobId, System.currentTimeMillis())

    private fun runScan(jobId: String, mode: ScanMode) {
        val scanStartedAt = System.currentTimeMillis()
        var progress = ScanProgress()

        try {
            indexDao.markRunning(jobId, scanStartedAt)

            val sidecars = collectSidecars(jobId) { path ->
                progress = progress.copy(
                    filesSeen = progress.filesSeen + 1,
                    sidecarJsonSeen = progress.sidecarJsonSeen + 1,
                    currentRelativePath = relativePath(path),
                )
                indexDao.updateProgress(jobId, progress, System.currentTimeMillis())
            }

            var cancelled = false
            Files.walk(config.photoRoot).use { stream ->
                val iterator = stream
                    .filter { path -> path.isRegularFile() }
                    .filter { path -> !isHiddenPath(path) }
                    .iterator()

                while (iterator.hasNext()) {
                    if (indexDao.isCancelRequested(jobId)) {
                        cancelled = true

                        break
                    }

                    progress = processPath(jobId, iterator.next(), sidecars, progress)
                }
            }

            val shouldCancel = cancelled || indexDao.isCancelRequested(jobId)

            if (shouldCancel) {
                indexDao.markCancelled(jobId, System.currentTimeMillis())
            } else {
                val missingCount = photoDao.markMissingNotSeenSince(scanStartedAt, System.currentTimeMillis())
                progress = progress.copy(photosMarkedMissing = missingCount.toLong())
                indexDao.updateProgress(jobId, progress, System.currentTimeMillis())

                indexDao.incrementLibraryRevision(System.currentTimeMillis())
                indexDao.markCompleted(jobId, System.currentTimeMillis())
            }
        } catch (throwable: Throwable) {
            indexDao.markFailed(jobId, throwable.message ?: throwable::class.java.name, System.currentTimeMillis())
        } finally {
            running.set(false)
        }
    }

    private fun processPath(
        jobId: String,
        path: Path,
        sidecars: SidecarIndex,
        progress: ScanProgress,
    ): ScanProgress {
        val extension = path.extension.lowercase()
        val mediaType = MediaTypes.mediaType(extension) ?: return progress
        val now = System.currentTimeMillis()
        val relativePath = relativePath(path)
        val existing = photoDao.findByRelativePath(relativePath)
        val fileSizeBytes = path.fileSize()
        val fileMtimeEpochMs = path.getLastModifiedTime().toMillis()
        val currentProgress = progress.copy(
            filesSeen = progress.filesSeen + 1,
            mediaFilesSeen = progress.mediaFilesSeen + 1,
            currentRelativePath = relativePath,
        )

        val isUnchangedFileSize = existing?.fileSizeBytes == fileSizeBytes
        val isUnchangedFileMtime = existing?.fileMtimeEpochMs == fileMtimeEpochMs
        val isUnchangedPhoto = existing != null && isUnchangedFileSize && isUnchangedFileMtime

        if (isUnchangedPhoto) {
            photoDao.markSeenUnchanged(existing.id, now)

            return currentProgress.copy(photosUnchanged = currentProgress.photosUnchanged + 1)
        }

        return runCatching {
            val metadata = metadataExtractor.extract(path, mediaType, sidecars.match(path))
            val photoId = Hashing.photoId(relativePath)
            val sourceFingerprint = Hashing.sourceFingerprint(
                relativePath = relativePath,
                fileSizeBytes = fileSizeBytes,
                fileMtimeEpochMs = fileMtimeEpochMs,
                thumbnailVariantVersion = 1,
            )
            val photo = PhotoRecord(
                id = photoId,
                relativePath = relativePath,
                filename = path.name,
                filenameSortKey = path.name.lowercase(),
                extension = extension,
                mediaType = mediaType,
                mimeType = MediaTypes.mimeType(extension) ?: "application/octet-stream",
                fileSizeBytes = fileSizeBytes,
                fileMtimeEpochMs = fileMtimeEpochMs,
                sourceFingerprint = sourceFingerprint,
                firstSeenAtEpochMs = existing?.firstSeenAtEpochMs ?: now,
                indexedAtEpochMs = now,
                lastSeenAtEpochMs = now,
                missingSinceEpochMs = null,
                excludedAtEpochMs = existing?.excludedAtEpochMs,
                width = metadata.width,
                height = metadata.height,
                durationMs = metadata.durationMs,
                orientation = metadata.orientation,
                takenAtEpochMs = metadata.takenAtEpochMs ?: fileMtimeEpochMs,
                takenAtSource = metadata.takenAtSource,
                exifTakenAtEpochMs = metadata.exifTakenAtEpochMs,
                takeoutTakenAtEpochMs = metadata.takeoutTakenAtEpochMs,
                videoCreatedAtEpochMs = metadata.videoCreatedAtEpochMs,
                timezoneOffsetMinutes = null,
                gpsLat = metadata.gps?.lat,
                gpsLng = metadata.gps?.lng,
                gpsAlt = metadata.gps?.alt,
                gpsSource = metadata.gps?.source ?: "none",
                cameraMake = metadata.cameraMake,
                cameraModel = metadata.cameraModel,
                sidecarRelativePath = sidecars.match(path)?.relativePath,
                metadataStatus = "ready",
                metadataError = null,
                metadataVersion = 1,
                createdAtEpochMs = existing?.createdAtEpochMs ?: now,
                updatedAtEpochMs = now,
            )

            photoDao.upsert(photo)

            val enqueuedCount = enqueueThumbnails(photo, now)
            val inserted = if (existing == null) 1 else 0
            val updated = if (existing == null) 0 else 1

            currentProgress.copy(
                photosInserted = currentProgress.photosInserted + inserted,
                photosUpdated = currentProgress.photosUpdated + updated,
                thumbnailTasksEnqueued = currentProgress.thumbnailTasksEnqueued + enqueuedCount,
            )
        }.getOrElse { throwable ->
            indexDao.addError(jobId, relativePath, "metadata", throwable, now)

            if (existing != null) {
                photoDao.markSeenUnchanged(existing.id, now)
            }

            currentProgress.copy(errorsCount = currentProgress.errorsCount + 1)
        }.also { updatedProgress ->
            indexDao.updateProgress(jobId, updatedProgress, now)
        }
    }

    private fun enqueueThumbnails(photo: PhotoRecord, now: Long): Long {
        val variants = if (config.thumbnailPreviewLazy) {
            listOf(ThumbnailVariant.GridSm, ThumbnailVariant.GridMd)
        } else {
            ThumbnailVariant.entries
        }

        return variants.count { variant ->
            thumbnailDao.enqueue(photo.id, variant, photo.sourceFingerprint, now)
        }.toLong()
    }

    private fun collectSidecars(jobId: String, onSidecar: (Path) -> Unit): SidecarIndex {
        val sidecars = mutableListOf<Pair<Path, TakeoutSidecar>>()

        Files.walk(config.photoRoot).use { stream ->
            val iterator = stream
                .filter { path -> path.isRegularFile() }
                .filter { path -> !isHiddenPath(path) }
                .filter { MediaTypes.isSidecar(it.extension) }
                .iterator()

            while (iterator.hasNext()) {
                val path = iterator.next()
                onSidecar(path)

                val relativePath = relativePath(path)
                val sidecar = runCatching {
                    metadataExtractor.readSidecar(path, relativePath)
                }.getOrElse { throwable ->
                    indexDao.addError(jobId, relativePath, "sidecar", throwable, System.currentTimeMillis())

                    null
                }

                if (sidecar != null) {
                    sidecars.add(path to sidecar)
                }
            }
        }

        return SidecarIndex(sidecars)
    }

    private fun relativePath(path: Path): String {
        return config.photoRoot.relativize(path)
            .joinToString("/")
    }

    private fun isHiddenPath(path: Path): Boolean {
        return config.photoRoot.relativize(path).any { segment ->
            segment.toString().startsWith(".")
        }
    }
}

/**
 * sidecar JSON の lookup index。
 */
class SidecarIndex(
    entries: List<Pair<Path, TakeoutSidecar>>,
) {
    private val exact = entries.associate { (path, sidecar) ->
        path.parent.resolve(sidecar.title ?: "").normalize() to sidecar
    }
    private val byCompanionName = entries.associate { (path, sidecar) ->
        path.fileName.toString().removeSuffix(".json").lowercase() to sidecar
    }

    /**
     * media file に対応する sidecar を探す。
     */
    fun match(path: Path): TakeoutSidecar? {
        val exactMatch = exact[path.normalize()]

        if (exactMatch != null) {
            return exactMatch
        }

        val fileName = path.fileName.toString()
        val lowerFileName = fileName.lowercase()
        val stem = lowerFileName.substringBeforeLast('.', lowerFileName)

        return byCompanionName["$lowerFileName.json".removeSuffix(".json")]
            ?: byCompanionName[stem]
    }
}
