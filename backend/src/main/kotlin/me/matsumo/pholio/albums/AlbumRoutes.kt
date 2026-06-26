package me.matsumo.pholio.albums

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import me.matsumo.pholio.photos.PhotoIdsRequest
import me.matsumo.pholio.photos.PhotoService

/**
 * album API routes を登録する。
 */
fun Route.albumRoutes(albumDao: AlbumDao, photoService: PhotoService) {
    get("/albums") {
        call.respond(
            AlbumListResponse(
                items = albumDao.listAlbums().map { stats ->
                    stats.album.toSummaryResponse(
                        photoCount = stats.photoCount,
                        coverPhoto = stats.coverPhoto,
                    )
                },
            ),
        )
    }

    post("/albums") {
        val album = albumDao.create(call.receive<CreateAlbumRequest>().name, System.currentTimeMillis())

        call.respond(
            status = HttpStatusCode.Created,
            message = album.toSummaryResponse(
                photoCount = 0,
                coverPhoto = null,
            ),
        )
    }

    route("/albums/{albumId}") {
        get {
            val albumId = call.parameters["albumId"].orEmpty()
            val album = albumDao.findActive(albumId) ?: throw NoSuchElementException("アルバムが見つかりません")

            call.respond(album.toDetailResponse(albumDao.countPhotos(albumId)))
        }

        patch {
            val albumId = call.parameters["albumId"].orEmpty()
            val album = albumDao.updateName(
                albumId = albumId,
                name = call.receive<UpdateAlbumRequest>().name,
                now = System.currentTimeMillis(),
            )

            call.respond(album.toDetailResponse(albumDao.countPhotos(albumId)))
        }

        delete {
            val albumId = call.parameters["albumId"].orEmpty()

            if (!albumDao.delete(albumId, System.currentTimeMillis())) {
                throw NoSuchElementException("アルバムが見つかりません")
            }

            call.respond(HttpStatusCode.NoContent)
        }

        get("/photos") {
            val albumId = call.parameters["albumId"].orEmpty()
            val sort = call.request.queryParameters["sort"] ?: "takenAt"
            val order = call.request.queryParameters["order"] ?: "desc"
            val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 120
            val cursor = call.request.queryParameters["cursor"]
            val seed = call.request.queryParameters["seed"]

            require(albumDao.findActive(albumId) != null) {
                "アルバムが見つかりません"
            }

            call.respond(photoService.listPhotos(albumId, sort, order, seed, limit, cursor))
        }

        post("/photos") {
            val albumId = call.parameters["albumId"].orEmpty()
            val request = call.receive<PhotoIdsRequest>()

            require(request.photoIds.size <= 1000) {
                "一度に追加できる写真は 1000 件までです"
            }

            call.respond(albumDao.addPhotos(albumId, request.photoIds.distinct(), System.currentTimeMillis()))
        }

        post("/photos:remove") {
            val albumId = call.parameters["albumId"].orEmpty()
            val request = call.receive<PhotoIdsRequest>()

            require(request.photoIds.size <= 1000) {
                "一度に除去できる写真は 1000 件までです"
            }

            call.respond(albumDao.removePhotos(albumId, request.photoIds.distinct(), System.currentTimeMillis()))
        }
    }
}
