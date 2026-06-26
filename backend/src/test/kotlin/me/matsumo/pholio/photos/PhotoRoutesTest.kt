package me.matsumo.pholio.photos

import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import java.nio.file.Files
import java.util.Base64
import kotlin.io.path.writeBytes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import me.matsumo.pholio.config.AppConfig
import me.matsumo.pholio.module

/**
 * photo routes の integration テスト。
 */
class PhotoRoutesTest {
    @Test
    fun `scan indexes local image and photos api returns it`() = testApplication {
        val photoRoot = Files.createTempDirectory("pholio-photos")
        val dataDir = Files.createTempDirectory("pholio-data")
        val imagePath = photoRoot.resolve("sample.png")
        imagePath.writeBytes(samplePngBytes())

        application {
            module(testConfig(photoRoot, dataDir))
        }

        val scanResponse = client.post("/api/v1/index/scan") {
            contentType(ContentType.Application.Json)
            setBody("""{"mode":"full"}""")
        }

        assertEquals(HttpStatusCode.Accepted, scanResponse.status)

        val photos = waitForPhotos()

        assertEquals(1, photos.items.size)
        assertEquals("sample.png", photos.items.first().filename)
        assertTrue(photos.items.first().thumbnail.gridMd.contains("/api/v1/photos/"))
    }

    private suspend fun io.ktor.server.testing.ApplicationTestBuilder.waitForPhotos(): PhotoListResponse {
        repeat(30) {
            val response = client.get("/api/v1/photos")
            val photos = json.decodeFromString<PhotoListResponse>(response.bodyAsText())

            if (photos.items.isNotEmpty()) {
                return photos
            }

            delay(100)
        }

        error("photos API に scan 結果が反映されませんでした")
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

    private fun samplePngBytes(): ByteArray {
        return Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+/p9sAAAAASUVORK5CYII=",
        )
    }

    private companion object {
        /**
         * JSON parser。
         */
        private val json = Json {
            ignoreUnknownKeys = true
            explicitNulls = false
        }
    }
}
