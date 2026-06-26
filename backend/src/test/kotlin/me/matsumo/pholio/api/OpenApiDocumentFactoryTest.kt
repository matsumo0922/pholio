package me.matsumo.pholio.api

import kotlin.io.path.Path
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlinx.serialization.json.jsonObject
import me.matsumo.pholio.config.AppConfig

/**
 * OpenAPI document のテスト。
 */
class OpenApiDocumentFactoryTest {
    @Test
    fun `document exposes backend media api paths`() {
        val document = OpenApiDocumentFactory(
            AppConfig(
                port = 8080,
                photoRoot = Path("/photos"),
                dataDir = Path("/data"),
                databaseUrl = "jdbc:sqlite::memory:",
                thumbDir = Path("/data/thumbs"),
                defaultTimezone = "Asia/Tokyo",
                scanOnStartup = false,
                thumbnailWorkers = 1,
                thumbnailPreviewLazy = true,
                trustProxyHeaders = false,
                cloudflareAccessEnabled = false,
                ffmpegPath = "ffmpeg",
                ffprobePath = "ffprobe",
                vipsThumbnailPath = "vipsthumbnail",
                mediaToolCheckEnabled = false,
                version = "test",
            ),
        ).create()
        val paths = document["paths"]?.jsonObject.orEmpty()

        assertTrue(paths.containsKey("/api/v1/photos"))
        assertTrue(paths.containsKey("/api/v1/photos/{photoId}/thumbnail/{variant}"))
        assertTrue(paths.containsKey("/api/v1/albums/{albumId}/photos"))
        assertTrue(paths.containsKey("/api/v1/index/scan"))
    }
}
