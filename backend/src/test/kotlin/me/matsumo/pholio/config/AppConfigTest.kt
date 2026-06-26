package me.matsumo.pholio.config

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * AppConfig のテスト。
 */
class AppConfigTest {
    @Test
    fun `required paths are enforced`() {
        assertFailsWith<IllegalArgumentException> {
            AppConfig.fromEnvironment(emptyMap())
        }
    }

    @Test
    fun `defaults are applied`() {
        val config = AppConfig.fromEnvironment(
            mapOf(
                "PHOTO_ROOT" to "/photos",
                "DATA_DIR" to "/data",
            ),
        )

        assertEquals(8080, config.port)
        assertEquals("jdbc:sqlite:/data/pholio.sqlite3", config.databaseUrl)
        assertEquals("Asia/Tokyo", config.defaultTimezone)
        assertEquals(2, config.thumbnailWorkers)
    }
}
