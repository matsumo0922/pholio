package me.matsumo.pholio.thumbs

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * アプリ内 thumbnail worker。
 */
class ThumbnailWorker(
    private val thumbnailDao: ThumbnailDao,
    private val thumbnailService: ThumbnailService,
    private val workerCount: Int,
    private val maxAttempts: Int = 3,
) {
    /**
     * worker coroutine を開始する。
     */
    fun start(scope: CoroutineScope) {
        repeat(workerCount.coerceAtLeast(1)) {
            scope.launch {
                runLoop()
            }
        }
    }

    private suspend fun runLoop() {
        while (kotlin.coroutines.coroutineContext.isActive) {
            val now = System.currentTimeMillis()
            val task = thumbnailDao.lockNext(
                now = now,
                lockedUntil = now + 5 * 60 * 1000,
                maxAttempts = maxAttempts,
            )

            if (task == null) {
                delay(1000)
            } else {
                thumbnailService.processTask(task)
            }
        }
    }
}
