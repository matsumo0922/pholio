package me.matsumo.pholio.api

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond
import kotlinx.serialization.Serializable

/**
 * API エラーの共通レスポンス。
 */
@Serializable
data class ApiErrorResponse(
    val error: ApiError,
) {
    /**
     * ApiErrorResponse の生成ヘルパー。
     */
    companion object {
        /**
         * 予期しない内部エラー用のレスポンスを作成する。
         */
        fun internalError(): ApiErrorResponse = ApiErrorResponse(
            error = ApiError(
                code = "INTERNAL_ERROR",
                message = "内部エラーが発生しました",
                details = emptyMap(),
                requestId = null,
            ),
        )
    }
}

/**
 * API エラーの詳細。
 */
@Serializable
data class ApiError(
    val code: String,
    val message: String,
    val details: Map<String, String>,
    val requestId: String?,
)

/**
 * 共通形式で API エラーを返す。
 */
suspend fun ApplicationCall.respondApiError(
    status: HttpStatusCode,
    code: String,
    message: String,
    details: Map<String, String> = emptyMap(),
) {
    respond(
        status = status,
        message = ApiErrorResponse(
            error = ApiError(
                code = code,
                message = message,
                details = details,
                requestId = request.headers["X-Request-Id"],
            ),
        ),
    )
}
