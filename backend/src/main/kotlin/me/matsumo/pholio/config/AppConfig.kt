package me.matsumo.pholio.config

import java.nio.file.Path
import kotlin.io.path.Path

/**
 * Pholio の起動設定。
 */
data class AppConfig(
    val port: Int,
    val photoRoot: Path,
    val dataDir: Path,
    val databaseUrl: String,
    val thumbDir: Path,
    val defaultTimezone: String,
    val scanOnStartup: Boolean,
    val thumbnailWorkers: Int,
    val thumbnailPreviewLazy: Boolean,
    val trustProxyHeaders: Boolean,
    val cloudflareAccessEnabled: Boolean,
    val ffmpegPath: String,
    val ffprobePath: String,
    val vipsThumbnailPath: String,
    val mediaToolCheckEnabled: Boolean,
    val version: String,
) {
    /**
     * AppConfig の生成ヘルパー。
     */
    companion object {
        /**
         * 環境変数から起動設定を作成する。
         */
        fun fromEnvironment(
            environment: Map<String, String> = System.getenv(),
        ): AppConfig {
            val photoRoot = requiredPath(environment, "PHOTO_ROOT")
            val dataDir = requiredPath(environment, "DATA_DIR")

            return AppConfig(
                port = environment["PORT"]?.toIntOrNull() ?: 8080,
                photoRoot = photoRoot,
                dataDir = dataDir,
                databaseUrl = environment["DATABASE_URL"] ?: "jdbc:sqlite:${dataDir.resolve("pholio.sqlite3")}",
                thumbDir = environment["THUMB_DIR"]?.let(::Path) ?: dataDir.resolve("thumbs"),
                defaultTimezone = environment["APP_DEFAULT_TIMEZONE"] ?: "Asia/Tokyo",
                scanOnStartup = environment["SCAN_ON_STARTUP"]?.toBooleanStrictOrNull() ?: true,
                thumbnailWorkers = environment["THUMBNAIL_WORKERS"]?.toIntOrNull() ?: 2,
                thumbnailPreviewLazy = environment["THUMBNAIL_PREVIEW_LAZY"]?.toBooleanStrictOrNull() ?: true,
                trustProxyHeaders = environment["TRUST_PROXY_HEADERS"]?.toBooleanStrictOrNull() ?: false,
                cloudflareAccessEnabled = environment["CLOUDFLARE_ACCESS_ENABLED"]?.toBooleanStrictOrNull() ?: false,
                ffmpegPath = environment["FFMPEG_PATH"] ?: "ffmpeg",
                ffprobePath = environment["FFPROBE_PATH"] ?: "ffprobe",
                vipsThumbnailPath = environment["VIPS_THUMBNAIL_PATH"] ?: "vipsthumbnail",
                mediaToolCheckEnabled = environment["MEDIA_TOOL_CHECK_ENABLED"]?.toBooleanStrictOrNull() ?: true,
                version = environment["APP_VERSION"] ?: "0.1.0",
            )
        }

        private fun requiredPath(environment: Map<String, String>, name: String): Path {
            val value = requireNotNull(environment[name]?.takeIf(String::isNotBlank)) {
                "$name が設定されていません"
            }

            return Path(value)
        }
    }
}
