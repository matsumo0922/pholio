package me.matsumo.pholio

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import me.matsumo.pholio.albums.AlbumDao
import me.matsumo.pholio.api.CursorCodec
import me.matsumo.pholio.config.AppConfig
import me.matsumo.pholio.db.Database
import me.matsumo.pholio.files.PhotoPathResolver
import me.matsumo.pholio.index.IndexDao
import me.matsumo.pholio.index.IndexScanner
import me.matsumo.pholio.media.MediaMetadataExtractor
import me.matsumo.pholio.media.ThumbnailGenerator
import me.matsumo.pholio.photos.PhotoDao
import me.matsumo.pholio.photos.PhotoService
import me.matsumo.pholio.thumbs.ThumbnailDao
import me.matsumo.pholio.thumbs.ThumbnailService
import me.matsumo.pholio.thumbs.ThumbnailWorker

/**
 * Ktor routes から使う application services。
 */
class AppServices(
    val database: Database,
    val photoDao: PhotoDao,
    val albumDao: AlbumDao,
    val indexDao: IndexDao,
    val thumbnailDao: ThumbnailDao,
    val photoService: PhotoService,
    val thumbnailService: ThumbnailService,
    val indexScanner: IndexScanner,
    val pathResolver: PhotoPathResolver,
    val scope: CoroutineScope,
    private val thumbnailWorker: ThumbnailWorker,
) : AutoCloseable {
    /**
     * background jobs を開始する。
     */
    fun startBackgroundJobs() {
        thumbnailWorker.start(scope)
        indexScanner.startOnStartup(scope)
    }

    override fun close() {
        scope.cancel()
        database.close()
    }

    companion object {
        /**
         * AppServices を作成する。
         */
        fun create(config: AppConfig): AppServices {
            val database = Database(config)
            val photoDao = PhotoDao(database)
            val indexDao = IndexDao(database)
            val thumbnailDao = ThumbnailDao(database)
            val pathResolver = PhotoPathResolver(config)
            val metadataExtractor = MediaMetadataExtractor(config)
            val thumbnailGenerator = ThumbnailGenerator(config)
            val thumbnailService = ThumbnailService(
                config = config,
                photoDao = photoDao,
                thumbnailDao = thumbnailDao,
                pathResolver = pathResolver,
                thumbnailGenerator = thumbnailGenerator,
            )
            val albumDao = AlbumDao(database, photoDao, indexDao)
            val photoService = PhotoService(
                photoDao = photoDao,
                indexDao = indexDao,
                thumbnailDao = thumbnailDao,
                cursorCodec = CursorCodec(),
            )
            val indexScanner = IndexScanner(
                config = config,
                indexDao = indexDao,
                photoDao = photoDao,
                thumbnailDao = thumbnailDao,
                metadataExtractor = metadataExtractor,
            )
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            val thumbnailWorker = ThumbnailWorker(
                thumbnailDao = thumbnailDao,
                thumbnailService = thumbnailService,
                workerCount = config.thumbnailWorkers,
            )

            return AppServices(
                database = database,
                photoDao = photoDao,
                albumDao = albumDao,
                indexDao = indexDao,
                thumbnailDao = thumbnailDao,
                photoService = photoService,
                thumbnailService = thumbnailService,
                indexScanner = indexScanner,
                pathResolver = pathResolver,
                scope = scope,
                thumbnailWorker = thumbnailWorker,
            )
        }
    }
}
