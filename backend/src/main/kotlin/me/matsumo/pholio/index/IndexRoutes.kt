package me.matsumo.pholio.index

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import kotlinx.coroutines.CoroutineScope
import me.matsumo.pholio.thumbs.ThumbnailDao
import me.matsumo.pholio.time.TimeFormats

/**
 * index / scan API routes を登録する。
 */
fun Route.indexRoutes(
    indexDao: IndexDao,
    thumbnailDao: ThumbnailDao,
    indexScanner: IndexScanner,
    scope: CoroutineScope,
) {
    get("/index/status") {
        val latestJob = indexDao.latestJob()
        val runningJob = indexDao.runningJob()
        val displayJob = runningJob ?: latestJob

        call.respond(
            IndexStatusResponse(
                status = displayJob?.status ?: "idle",
                currentJob = displayJob?.toResponse(),
                thumbnailQueue = thumbnailDao.queueSummary(),
                libraryRevision = indexDao.libraryRevision(),
            ),
        )
    }

    post("/index/scan") {
        val request = call.receive<ScanStartRequest>()
        val response = indexScanner.start(scope, ScanMode.fromRequest(request.mode))

        call.respond(
            status = HttpStatusCode.Accepted,
            message = response,
        )
    }

    post("/index/scan/{jobId}:cancel") {
        val jobId = call.parameters["jobId"].orEmpty()

        if (!indexScanner.cancel(jobId)) {
            throw NoSuchElementException("実行中の scan job が見つかりません")
        }

        call.respond(HttpStatusCode.Accepted)
    }
}

private fun ScanJobRecord.toResponse(): ScanJobResponse = ScanJobResponse(
    id = id,
    mode = mode,
    status = status,
    filesSeen = filesSeen,
    mediaFilesSeen = mediaFilesSeen,
    sidecarJsonSeen = sidecarJsonSeen,
    photosInserted = photosInserted,
    photosUpdated = photosUpdated,
    photosUnchanged = photosUnchanged,
    photosMarkedMissing = photosMarkedMissing,
    thumbnailTasksEnqueued = thumbnailTasksEnqueued,
    errorsCount = errorsCount,
    currentRelativePath = currentRelativePath,
    startedAt = startedAtEpochMs?.let(TimeFormats::toIsoUtc),
)
