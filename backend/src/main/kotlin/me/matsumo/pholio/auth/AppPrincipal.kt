package me.matsumo.pholio.auth

import io.ktor.server.application.ApplicationCall

/**
 * Pholio が扱う認証済みユーザー情報。
 */
data class AppPrincipal(
    val subject: String,
    val email: String?,
    val name: String?,
    val groups: List<String>,
)

/**
 * リクエストからユーザー情報を取得する認証フック。
 */
interface RequestAuthenticator {
    /**
     * リクエストを認証し、認証済みユーザー情報を返す。
     */
    suspend fun authenticate(call: ApplicationCall): AppPrincipal?
}

/**
 * LAN 内 v1 用の認証なしフック。
 */
class NoopAuthenticator : RequestAuthenticator {
    override suspend fun authenticate(call: ApplicationCall): AppPrincipal = AppPrincipal(
        subject = "local-user",
        email = null,
        name = "Local User",
        groups = emptyList(),
    )
}
