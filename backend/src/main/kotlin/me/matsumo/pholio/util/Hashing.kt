package me.matsumo.pholio.util

import java.nio.charset.StandardCharsets
import java.security.MessageDigest

/**
 * ID や cache busting に使う hash ユーティリティ。
 */
object Hashing {
    /**
     * SHA-256 hex の先頭 32 桁から photo id を作成する。
     */
    fun photoId(relativePath: String): String = sha256(relativePath).take(32)

    /**
     * source fingerprint を作成する。
     */
    fun sourceFingerprint(
        relativePath: String,
        fileSizeBytes: Long,
        fileMtimeEpochMs: Long,
        thumbnailVariantVersion: Int,
    ): String = sha1("$relativePath:$fileSizeBytes:$fileMtimeEpochMs:$thumbnailVariantVersion").take(12)

    /**
     * seed と photo id から deterministic random key を作成する。
     */
    fun seededRandomKey(seed: String, photoId: String): Long {
        val digest = digest("SHA-256", "$seed:$photoId")
        var value = 0L

        for (index in 0 until 8) {
            value = (value shl 8) or (digest[index].toLong() and 0xff)
        }

        return value and Long.MAX_VALUE
    }

    private fun sha1(value: String): String = digest("SHA-1", value).toHex()

    private fun sha256(value: String): String = digest("SHA-256", value).toHex()

    private fun digest(algorithm: String, value: String): ByteArray {
        return MessageDigest.getInstance(algorithm).digest(value.toByteArray(StandardCharsets.UTF_8))
    }

    private fun ByteArray.toHex(): String = joinToString(separator = "") { byte ->
        "%02x".format(byte)
    }
}
