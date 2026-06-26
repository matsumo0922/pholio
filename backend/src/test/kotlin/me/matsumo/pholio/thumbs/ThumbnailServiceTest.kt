package me.matsumo.pholio.thumbs

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import me.matsumo.pholio.config.AppConfig
import me.matsumo.pholio.db.Database
import me.matsumo.pholio.files.PhotoPathResolver
import me.matsumo.pholio.media.ThumbnailGenerator
import me.matsumo.pholio.photos.MediaType
import me.matsumo.pholio.photos.PhotoDao
import me.matsumo.pholio.photos.PhotoRecord
import me.matsumo.pholio.photos.TakenAtSource
import me.matsumo.pholio.photos.ThumbnailVariant

/**
 * ThumbnailService のテスト。
 */
class ThumbnailServiceTest {
    @Test
    fun `inactive photo task is skipped and leaves pending queue`() {
        val photoRoot = Files.createTempDirectory("pholio-photos")
        val dataDir = Files.createTempDirectory("pholio-data")
        val config = testConfig(photoRoot, dataDir)
        val database = Database(config)

        try {
            val photoDao = PhotoDao(database)
            val thumbnailDao = ThumbnailDao(database)
            val service = ThumbnailService(
                config = config,
                photoDao = photoDao,
                thumbnailDao = thumbnailDao,
                pathResolver = PhotoPathResolver(config),
                thumbnailGenerator = ThumbnailGenerator(config),
            )
            val photo = samplePhoto()
            val now = System.currentTimeMillis()

            photoDao.upsert(photo)
            thumbnailDao.enqueue(photo.id, ThumbnailVariant.GridMd, photo.sourceFingerprint, now)
            photoDao.exclude(listOf(photo.id), now + 1)

            val task = assertNotNull(thumbnailDao.lockNext(now + 2, now + 3, maxAttempts = 3))
            service.processTask(task)

            val thumbnail = assertNotNull(thumbnailDao.find(photo.id, ThumbnailVariant.GridMd))
            val queue = thumbnailDao.queueSummary()

            assertEquals("skipped", thumbnail.status)
            assertEquals(0L, queue.pending)
        } finally {
            database.close()
        }
    }

    @Test
    fun `old task result does not overwrite requeued fingerprint`() {
        val photoRoot = Files.createTempDirectory("pholio-photos")
        val dataDir = Files.createTempDirectory("pholio-data")
        val config = testConfig(photoRoot, dataDir)
        val database = Database(config)

        try {
            val photoDao = PhotoDao(database)
            val thumbnailDao = ThumbnailDao(database)
            val photo = samplePhoto()

            photoDao.upsert(photo)
            thumbnailDao.enqueue(photo.id, ThumbnailVariant.GridMd, "old-fingerprint", now = 1000L)
            assertNotNull(thumbnailDao.lockNext(now = 1001L, lockedUntil = 2000L, maxAttempts = 3))
            thumbnailDao.enqueue(photo.id, ThumbnailVariant.GridMd, "new-fingerprint", now = 1002L)

            val applied = thumbnailDao.markReady(
                photoId = photo.id,
                variant = ThumbnailVariant.GridMd,
                sourceFingerprint = "old-fingerprint",
                relativeCachePath = "old/cache.webp",
                width = 1,
                height = 1,
                sizeBytes = 1L,
                now = 1003L,
            )
            val thumbnail = assertNotNull(thumbnailDao.find(photo.id, ThumbnailVariant.GridMd))

            assertEquals(false, applied)
            assertEquals("stale", thumbnail.status)
            assertEquals("new-fingerprint", thumbnail.sourceFingerprint)
            assertEquals(null, thumbnail.relativeCachePath)
        } finally {
            database.close()
        }
    }

    @Test
    fun `old inactive task does not skip restored photo task`() {
        val photoRoot = Files.createTempDirectory("pholio-photos")
        val dataDir = Files.createTempDirectory("pholio-data")
        val config = testConfig(photoRoot, dataDir)
        val database = Database(config)

        try {
            val photoDao = PhotoDao(database)
            val thumbnailDao = ThumbnailDao(database)
            val photo = samplePhoto()

            photoDao.upsert(photo)
            thumbnailDao.enqueue(photo.id, ThumbnailVariant.GridMd, photo.sourceFingerprint, now = 1000L)
            assertNotNull(thumbnailDao.lockNext(now = 1001L, lockedUntil = 2000L, maxAttempts = 3))
            photoDao.exclude(listOf(photo.id), now = 1002L)
            photoDao.restore(listOf(photo.id), now = 1003L)
            thumbnailDao.enqueue(photo.id, ThumbnailVariant.GridMd, photo.sourceFingerprint, now = 1004L)

            val applied = thumbnailDao.markSkipped(
                photoId = photo.id,
                variant = ThumbnailVariant.GridMd,
                sourceFingerprint = photo.sourceFingerprint,
                reason = "inactive",
                now = 1005L,
            )
            val thumbnail = assertNotNull(thumbnailDao.find(photo.id, ThumbnailVariant.GridMd))
            val queue = thumbnailDao.queueSummary()

            assertEquals(false, applied)
            assertEquals("pending", thumbnail.status)
            assertEquals(1L, queue.pending)
        } finally {
            database.close()
        }
    }

    private fun testConfig(photoRoot: java.nio.file.Path, dataDir: java.nio.file.Path): AppConfig {
        return AppConfig.fromEnvironment(
            mapOf(
                "PHOTO_ROOT" to photoRoot.toString(),
                "DATA_DIR" to dataDir.toString(),
                "SCAN_ON_STARTUP" to "false",
                "MEDIA_TOOL_CHECK_ENABLED" to "false",
            ),
        )
    }

    private fun samplePhoto(): PhotoRecord {
        return PhotoRecord(
            id = "photo-id",
            relativePath = "sample.png",
            filename = "sample.png",
            filenameSortKey = "sample.png",
            extension = "png",
            mediaType = MediaType.Image,
            mimeType = "image/png",
            fileSizeBytes = 1L,
            fileMtimeEpochMs = 1000L,
            sourceFingerprint = "fingerprint",
            firstSeenAtEpochMs = 1000L,
            indexedAtEpochMs = 1000L,
            lastSeenAtEpochMs = 1000L,
            missingSinceEpochMs = null,
            excludedAtEpochMs = null,
            width = 1,
            height = 1,
            durationMs = null,
            orientation = null,
            takenAtEpochMs = 1000L,
            takenAtSource = TakenAtSource.FileMtime.dbValue,
            exifTakenAtEpochMs = null,
            takeoutTakenAtEpochMs = null,
            videoCreatedAtEpochMs = null,
            timezoneOffsetMinutes = null,
            gpsLat = null,
            gpsLng = null,
            gpsAlt = null,
            gpsSource = null,
            cameraMake = null,
            cameraModel = null,
            sidecarRelativePath = null,
            metadataStatus = "ready",
            metadataError = null,
            metadataVersion = 1,
            createdAtEpochMs = 1000L,
            updatedAtEpochMs = 1000L,
        )
    }
}
