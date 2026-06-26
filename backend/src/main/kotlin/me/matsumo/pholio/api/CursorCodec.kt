package me.matsumo.pholio.api

import java.nio.charset.StandardCharsets
import java.util.Base64
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * keyset pagination cursor を encode / decode する。
 */
class CursorCodec(
    private val json: Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    },
) {
    /**
     * cursor payload を Base64URL 文字列へ変換する。
     */
    fun encode(payload: PageCursor): String {
        val jsonText = json.encodeToString(payload)

        return Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(jsonText.toByteArray(StandardCharsets.UTF_8))
    }

    /**
     * Base64URL cursor を payload へ戻す。
     */
    fun decode(cursor: String): PageCursor {
        val jsonText = runCatching {
            String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8)
        }.getOrElse {
            throw IllegalArgumentException("cursor が不正です")
        }

        return runCatching {
            json.decodeFromString<PageCursor>(jsonText)
        }.getOrElse {
            throw IllegalArgumentException("cursor が不正です")
        }
    }
}

/**
 * keyset pagination cursor の中身。
 */
@Serializable
data class PageCursor(
    @SerialName("v")
    val version: Int,
    val scope: String,
    val sort: String,
    val order: String?,
    val seed: String?,
    val lastLongKey: Long?,
    val lastStringKey: String?,
    val lastId: String,
    val libraryRevision: Long,
)
