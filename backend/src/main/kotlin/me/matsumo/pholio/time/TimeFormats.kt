package me.matsumo.pholio.time

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Pholio で扱う時刻変換ユーティリティ。
 */
object TimeFormats {
    /**
     * epoch milliseconds を UTC ISO-8601 文字列へ変換する。
     */
    fun toIsoUtc(epochMs: Long): String = DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochMilli(epochMs))

    /**
     * offset なし日時を既定 timezone で epoch milliseconds へ変換する。
     */
    fun fromLocalDateTime(text: String, zoneId: ZoneId): Long? {
        val normalizedText = text.trim().replace(':', '-', ignoreCase = false)
        val parts = normalizedText.split(' ', 'T')

        if (parts.size < 2) {
            return null
        }

        val dateParts = parts[0].split('-').mapNotNull(String::toIntOrNull)
        val timeParts = parts[1].split('-').mapNotNull(String::toIntOrNull)
        val hasInvalidDateParts = dateParts.size != 3
        val hasInvalidTimeParts = timeParts.size < 3
        val hasInvalidLocalDateTime = hasInvalidDateParts || hasInvalidTimeParts

        if (hasInvalidLocalDateTime) {
            return null
        }

        return runCatching {
            java.time.LocalDateTime.of(
                dateParts[0],
                dateParts[1],
                dateParts[2],
                timeParts[0],
                timeParts[1],
                timeParts[2],
            ).atZone(zoneId).toInstant().toEpochMilli()
        }.getOrNull()
    }
}
