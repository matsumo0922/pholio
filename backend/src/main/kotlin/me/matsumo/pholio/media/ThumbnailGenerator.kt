package me.matsumo.pholio.media

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import me.matsumo.pholio.config.AppConfig
import me.matsumo.pholio.photos.MediaType
import me.matsumo.pholio.photos.PhotoRecord
import me.matsumo.pholio.photos.ThumbnailVariant

/**
 * libvips / ffmpeg を使って thumbnail を生成する。
 */
class ThumbnailGenerator(
    private val config: AppConfig,
) {
    /**
     * thumbnail を一時ファイルへ生成する。
     */
    fun generate(sourcePath: Path, photo: PhotoRecord, variant: ThumbnailVariant, outputPath: Path) {
        outputPath.parent.createDirectories()

        if (photo.mediaType == MediaType.Video) {
            generateVideo(sourcePath, photo, variant, outputPath)
        } else {
            generateImage(sourcePath, variant, outputPath)
        }
    }

    private fun generateImage(sourcePath: Path, variant: ThumbnailVariant, outputPath: Path) {
        val process = ProcessBuilder(
            config.vipsThumbnailPath,
            sourcePath.toString(),
            "--size",
            "${variant.longEdge}x${variant.longEdge}",
            "--rotate",
            "--output",
            "${outputPath}[Q=76]",
        ).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().readText()
        val exitCode = process.waitFor()
        val hasGeneratedFile = Files.exists(outputPath)
        val isSuccessful = exitCode == 0 && hasGeneratedFile

        require(isSuccessful) {
            "thumbnail 生成に失敗しました: $output"
        }
    }

    private fun generateVideo(sourcePath: Path, photo: PhotoRecord, variant: ThumbnailVariant, outputPath: Path) {
        val seekSeconds = seekSeconds(photo.durationMs)
        val process = ProcessBuilder(
            config.ffmpegPath,
            "-hide_banner",
            "-loglevel",
            "error",
            "-ss",
            seekSeconds.toString(),
            "-i",
            sourcePath.toString(),
            "-frames:v",
            "1",
            "-vf",
            "scale='min(${variant.longEdge},iw)':-2",
            "-y",
            outputPath.toString(),
        ).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().readText()
        val exitCode = process.waitFor()
        val hasGeneratedFile = Files.exists(outputPath)
        val isSuccessful = exitCode == 0 && hasGeneratedFile

        require(isSuccessful) {
            "動画 thumbnail 生成に失敗しました: $output"
        }
    }

    private fun seekSeconds(durationMs: Long?): Double {
        val durationSeconds = (durationMs ?: 0L) / 1000.0

        return when {
            durationSeconds >= 10.0 -> minOf(durationSeconds * 0.1, 30.0)
            durationSeconds >= 1.0 -> 1.0
            else -> 0.0
        }
    }
}
