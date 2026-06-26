package me.matsumo.pholio.api

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * CursorCodec のテスト。
 */
class CursorCodecTest {
    @Test
    fun `cursor keeps compact version field on wire`() {
        val codec = CursorCodec()
        val cursor = PageCursor(
            version = 1,
            scope = "home",
            sort = "takenAt",
            order = "desc",
            seed = null,
            lastLongKey = 1000L,
            lastStringKey = null,
            lastId = "photo-id",
            libraryRevision = 2L,
        )

        val encoded = codec.encode(cursor)
        val decoded = codec.decode(encoded)
        val jsonText = String(java.util.Base64.getUrlDecoder().decode(encoded))
        val jsonObject = Json.parseToJsonElement(jsonText).jsonObject

        assertEquals(cursor, decoded)
        assertEquals("1", jsonObject["v"]?.jsonPrimitive?.content)
    }
}
