package me.matsumo.pholio.media

import me.matsumo.pholio.photos.MediaType

/**
 * v1 が扱う media 拡張子と MIME type。
 */
object MediaTypes {
    /**
     * v1 が画像として扱う拡張子と MIME type。
     */
    private val imageMimeTypes = mapOf(
        "jpg" to "image/jpeg",
        "jpeg" to "image/jpeg",
        "png" to "image/png",
        "gif" to "image/gif",
        "webp" to "image/webp",
    )

    /**
     * 拡張子から media type を返す。
     */
    fun mediaType(extension: String): MediaType? {
        val normalizedExtension = extension.lowercase()

        return when {
            normalizedExtension in imageMimeTypes -> MediaType.Image
            normalizedExtension == "mp4" -> MediaType.Video
            else -> null
        }
    }

    /**
     * 拡張子から MIME type を返す。
     */
    fun mimeType(extension: String): String? {
        val normalizedExtension = extension.lowercase()

        return imageMimeTypes[normalizedExtension] ?: if (normalizedExtension == "mp4") "video/mp4" else null
    }

    /**
     * sidecar JSON 拡張子か返す。
     */
    fun isSidecar(extension: String): Boolean = extension.equals("json", ignoreCase = true)
}
