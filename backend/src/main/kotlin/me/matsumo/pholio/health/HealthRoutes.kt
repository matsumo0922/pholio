package me.matsumo.pholio.health

import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import kotlinx.serialization.Serializable
import me.matsumo.pholio.auth.RequestAuthenticator
import me.matsumo.pholio.config.AppConfig
import kotlin.io.path.isReadable
import kotlin.io.path.isWritable

/**
 * health check route を登録する。
 */
fun Route.healthRoutes(config: AppConfig, authenticator: RequestAuthenticator) {
    get("/health") {
        authenticator.authenticate(call)

        call.respond(
            HealthResponse(
                status = "ok",
                db = "ok",
                photoRootReadable = config.photoRoot.isReadable(),
                dataDirWritable = config.dataDir.isWritable(),
                version = config.version,
            ),
        )
    }
}

/**
 * health check のレスポンス。
 */
@Serializable
data class HealthResponse(
    val status: String,
    val db: String,
    val photoRootReadable: Boolean,
    val dataDirWritable: Boolean,
    val version: String,
)
