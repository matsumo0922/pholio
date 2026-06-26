package me.matsumo.pholio.config

import kotlin.io.path.createDirectories
import kotlin.io.path.isDirectory
import kotlin.io.path.isReadable
import kotlin.io.path.isWritable

/**
 * 起動時に必要なディレクトリと media tool を検証する。
 */
class StartupChecks(
    private val config: AppConfig,
) {
    /**
     * 起動に必要な前提を検証する。
     */
    fun verify() {
        val canReadPhotoRoot = config.photoRoot.isDirectory() && config.photoRoot.isReadable()

        require(canReadPhotoRoot) {
            "PHOTO_ROOT は読み取り可能なディレクトリである必要があります: ${config.photoRoot}"
        }

        config.dataDir.createDirectories()
        config.thumbDir.createDirectories()

        val canWriteDataDir = config.dataDir.isDirectory() && config.dataDir.isWritable()
        val canWriteThumbDir = config.thumbDir.isDirectory() && config.thumbDir.isWritable()

        require(canWriteDataDir) {
            "DATA_DIR は書き込み可能なディレクトリである必要があります: ${config.dataDir}"
        }

        require(canWriteThumbDir) {
            "THUMB_DIR は書き込み可能なディレクトリである必要があります: ${config.thumbDir}"
        }

        if (config.mediaToolCheckEnabled) {
            verifyCommand(config.ffmpegPath, listOf("-version"))
            verifyCommand(config.ffprobePath, listOf("-version"))
            verifyCommand(config.vipsThumbnailPath, listOf("--help"))
        }
    }

    private fun verifyCommand(command: String, arguments: List<String>) {
        val process = ProcessBuilder(listOf(command) + arguments)
            .redirectError(ProcessBuilder.Redirect.DISCARD)
            .redirectOutput(ProcessBuilder.Redirect.DISCARD)
            .start()

        val exitCode = process.waitFor()

        require(exitCode == 0) {
            "media tool が見つからない、または実行できません: $command"
        }
    }
}
