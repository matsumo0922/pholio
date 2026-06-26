package me.matsumo.pholio.files

import java.nio.file.Files
import java.nio.file.Path
import me.matsumo.pholio.config.AppConfig
import me.matsumo.pholio.photos.PhotoRecord

/**
 * DB の相対 path から安全に実ファイル path を解決する。
 */
class PhotoPathResolver(
    config: AppConfig,
) {
    private val photoRoot = config.photoRoot.toAbsolutePath().normalize()

    /**
     * photo の原本 path を返す。
     */
    fun resolve(photo: PhotoRecord): Path {
        val resolvedPath = photoRoot.resolve(photo.relativePath).normalize()

        require(resolvedPath.startsWith(photoRoot)) {
            "写真 path が不正です"
        }
        require(Files.exists(resolvedPath)) {
            "写真ファイルが見つかりません"
        }

        return resolvedPath
    }
}
