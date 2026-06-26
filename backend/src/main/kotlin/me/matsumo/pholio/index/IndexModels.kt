package me.matsumo.pholio.index

import kotlinx.serialization.Serializable

/**
 * scan mode。
 */
enum class ScanMode(
    val dbValue: String,
) {
    Full("full"),
    Diff("diff"),
    ;

    /**
     * ScanMode の生成ヘルパー。
     */
    companion object {
        /**
         * request 値から scan mode を復元する。
         */
        fun fromRequest(value: String): ScanMode = entries.firstOrNull { it.dbValue == value }
            ?: throw IllegalArgumentException("scan mode が不正です")
    }
}

/**
 * scan status。
 */
enum class ScanStatus(
    val dbValue: String,
) {
    Queued("queued"),
    Running("running"),
    Completed("completed"),
    Failed("failed"),
    Cancelled("cancelled"),
    ;
}

/**
 * scan_jobs table の 1 行。
 */
data class ScanJobRecord(
    val id: String,
    val mode: String,
    val status: String,
    val filesSeen: Long,
    val mediaFilesSeen: Long,
    val sidecarJsonSeen: Long,
    val photosInserted: Long,
    val photosUpdated: Long,
    val photosUnchanged: Long,
    val photosMarkedMissing: Long,
    val thumbnailTasksEnqueued: Long,
    val errorsCount: Long,
    val currentRelativePath: String?,
    val cancelRequested: Boolean,
    val errorSummary: String?,
    val startedAtEpochMs: Long?,
    val finishedAtEpochMs: Long?,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
)

/**
 * scan start request。
 */
@Serializable
data class ScanStartRequest(
    val mode: String,
)

/**
 * scan start response。
 */
@Serializable
data class ScanStartResponse(
    val jobId: String,
    val status: String,
)

/**
 * index status response。
 */
@Serializable
data class IndexStatusResponse(
    val status: String,
    val currentJob: ScanJobResponse?,
    val thumbnailQueue: ThumbnailQueueResponse,
    val libraryRevision: Long,
)

/**
 * scan job response。
 */
@Serializable
data class ScanJobResponse(
    val id: String,
    val mode: String,
    val filesSeen: Long,
    val mediaFilesSeen: Long,
    val sidecarJsonSeen: Long,
    val photosInserted: Long,
    val photosUpdated: Long,
    val photosUnchanged: Long,
    val photosMarkedMissing: Long,
    val thumbnailTasksEnqueued: Long,
    val errorsCount: Long,
    val currentRelativePath: String?,
    val startedAt: String?,
)

/**
 * thumbnail queue summary。
 */
@Serializable
data class ThumbnailQueueResponse(
    val pending: Long,
    val ready: Long,
    val failed: Long,
)
