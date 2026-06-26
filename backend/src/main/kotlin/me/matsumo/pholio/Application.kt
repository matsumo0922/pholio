package me.matsumo.pholio

import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.autohead.AutoHeadResponse
import io.ktor.server.plugins.cachingheaders.CachingHeaders
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.compression.Compression
import io.ktor.server.plugins.compression.gzip
import io.ktor.server.plugins.conditionalheaders.ConditionalHeaders
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.forwardedheaders.XForwardedHeaders
import io.ktor.server.plugins.partialcontent.PartialContent
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import io.ktor.server.response.respondResource
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import kotlinx.serialization.json.Json
import me.matsumo.pholio.api.ApiErrorResponse
import me.matsumo.pholio.api.OpenApiDocumentFactory
import me.matsumo.pholio.api.respondApiError
import me.matsumo.pholio.auth.NoopAuthenticator
import me.matsumo.pholio.config.AppConfig
import me.matsumo.pholio.config.StartupChecks
import me.matsumo.pholio.health.healthRoutes
import me.matsumo.pholio.openapi.swaggerHtml

/**
 * Pholio の Ktor サーバーを起動するエントリーポイント。
 */
fun main() {
    val config = AppConfig.fromEnvironment()

    StartupChecks(config).verify()

    embeddedServer(
        factory = Netty,
        port = config.port,
        module = { module(config) },
    ).start(wait = true)
}

/**
 * Pholio の Ktor application module。
 */
fun Application.module(config: AppConfig = AppConfig.fromEnvironment()) {
    val applicationLog = environment.log

    install(ContentNegotiation) {
        json(
            Json {
                ignoreUnknownKeys = true
                explicitNulls = false
            },
        )
    }

    install(Compression) {
        gzip()
    }

    install(ConditionalHeaders)
    install(CachingHeaders)
    install(PartialContent)
    install(AutoHeadResponse)
    install(CallLogging)

    install(StatusPages) {
        exception<IllegalArgumentException> { call, cause ->
            call.respondApiError(
                status = HttpStatusCode.BadRequest,
                code = "BAD_REQUEST",
                message = cause.message ?: "リクエストが不正です",
            )
        }

        exception<Throwable> { call, cause ->
            applicationLog.error("Unhandled application error", cause)

            call.respond(
                status = HttpStatusCode.InternalServerError,
                message = ApiErrorResponse.internalError(),
            )
        }
    }

    if (config.trustProxyHeaders) {
        install(XForwardedHeaders)
    }

    val authenticator = NoopAuthenticator()
    val openApiDocumentFactory = OpenApiDocumentFactory(config)

    routing {
        route("/api/v1") {
            healthRoutes(config, authenticator)

            get("/openapi.json") {
                call.respond(openApiDocumentFactory.create())
            }
        }

        get("/api/docs") {
            call.respondText(swaggerHtml(), io.ktor.http.ContentType.Text.Html)
        }

        get("{path...}") {
            call.respondSpaFallback()
        }
    }
}

private suspend fun ApplicationCall.respondSpaFallback() {
    val path = parameters.getAll("path").orEmpty()

    if (path.firstOrNull() == "api") {
        respondApiError(
            status = HttpStatusCode.NotFound,
            code = "NOT_FOUND",
            message = "API が見つかりません",
        )

        return
    }

    respondResource("public/index.html")
}
