package me.matsumo.pholio.health

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import java.nio.file.Files
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import me.matsumo.pholio.module
import me.matsumo.pholio.config.AppConfig

/**
 * health route のテスト。
 */
class HealthRoutesTest {
    @Test
    fun `health returns ok`() = testApplication {
        val photoRoot = Files.createTempDirectory("pholio-photos")
        val dataDir = Files.createTempDirectory("pholio-data")

        application {
            module(
                AppConfig.fromEnvironment(
                    mapOf(
                        "PHOTO_ROOT" to photoRoot.toString(),
                        "DATA_DIR" to dataDir.toString(),
                        "MEDIA_TOOL_CHECK_ENABLED" to "false",
                    ),
                ),
            )
        }

        val response = client.get("/api/v1/health")

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("ok", Json.decodeFromString<HealthResponse>(response.bodyAsText()).status)
    }
}
