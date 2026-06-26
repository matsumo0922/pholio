package me.matsumo.pholio.photos

import io.ktor.http.ContentDisposition
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.PartData
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondFile
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import me.matsumo.pholio.api.respondApiError
import me.matsumo.pholio.files.PhotoPathResolver
import me.matsumo.pholio.thumbs.ThumbnailResponse
import me.matsumo.pholio.thumbs.ThumbnailService

/**
 * photo API routes を登録する。
 */
fun Route.photoRoutes(
    photoService: PhotoService,
    thumbnailService: ThumbnailService,
    pathResolver: PhotoPathResolver,
) {
    get("/photos") {
        val sort = call.request.queryParameters["sort"] ?: "takenAt"
        val order = call.request.queryParameters["order"] ?: "desc"
        val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 120
        val cursor = call.request.queryParameters["cursor"]
        val seed = call.request.queryParameters["seed"]

        call.respond(photoService.listPhotos(null, sort, order, seed, limit, cursor))
    }

    get("/photos/excluded") {
        val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 120
        val cursor = call.request.queryParameters["cursor"]

        call.respond(photoService.listExcluded(limit, cursor))
    }

    post("/photos:exclude") {
        call.respond(photoService.exclude(call.receive<PhotoIdsRequest>().photoIds))
    }

    post("/photos:restore") {
        call.respond(photoService.restore(call.receive<PhotoIdsRequest>().photoIds))
    }

    route("/photos/{photoId}") {
        get {
            val photoId = call.parameters["photoId"] ?: return@get call.respondApiError(
                status = HttpStatusCode.BadRequest,
                code = "BAD_REQUEST",
                message = "写真 ID が必要です",
            )

            call.respond(photoService.getDetail(photoId))
        }

        get("/neighbors") {
            val photoId = call.parameters["photoId"] ?: return@get call.respondApiError(
                status = HttpStatusCode.BadRequest,
                code = "BAD_REQUEST",
                message = "写真 ID が必要です",
            )
            val scope = call.request.queryParameters["scope"] ?: "home"
            val albumId = call.request.queryParameters["albumId"]?.takeIf { scope == "album" }
            val sort = call.request.queryParameters["sort"] ?: "takenAt"
            val order = call.request.queryParameters["order"] ?: "desc"
            val seed = call.request.queryParameters["seed"]

            call.respond(photoService.neighbors(photoId, albumId, sort, order, seed))
        }

        get("/thumbnail/{variant}") {
            val photoId = call.parameters["photoId"] ?: return@get call.respondApiError(
                status = HttpStatusCode.BadRequest,
                code = "BAD_REQUEST",
                message = "写真 ID が必要です",
            )
            val variant = ThumbnailVariant.fromPath(call.parameters["variant"].orEmpty())
            val photo = photoService.getActiveRecord(photoId)

            call.response.header(
                HttpHeaders.CacheControl,
                "public, max-age=31536000, immutable",
            )
            call.response.header(HttpHeaders.ETag, "\"thumb-$photoId-${variant.dbValue}-${photo.sourceFingerprint}\"")

            when (val thumbnail = thumbnailService.resolveThumbnail(photo, variant)) {
                is ThumbnailResponse.File -> call.respondFile(thumbnail.path.toFile())
                is ThumbnailResponse.Placeholder -> {
                    if (thumbnail.failed) {
                        call.response.header("X-Thumbnail-Status", "failed")
                    }

                    call.respondBytes(
                        bytes = ThumbnailResponse.placeholderBytes,
                        contentType = ContentType.parse("image/webp"),
                    )
                }
            }
        }

        get("/original") {
            val photoId = call.parameters["photoId"] ?: return@get call.respondApiError(
                status = HttpStatusCode.BadRequest,
                code = "BAD_REQUEST",
                message = "写真 ID が必要です",
            )
            val photo = photoService.getActiveRecord(photoId)
            val path = pathResolver.resolve(photo)

            call.response.header(HttpHeaders.CacheControl, "private, max-age=3600")
            call.response.header(HttpHeaders.ETag, "\"orig-$photoId-${photo.sourceFingerprint}\"")
            call.response.header(HttpHeaders.AcceptRanges, "bytes")
            call.response.header(
                HttpHeaders.ContentDisposition,
                ContentDisposition.Inline.withParameter(
                    key = ContentDisposition.Parameters.FileName,
                    value = photo.filename,
                ).toString(),
            )
            call.respondFile(path.toFile())
        }
    }
}
