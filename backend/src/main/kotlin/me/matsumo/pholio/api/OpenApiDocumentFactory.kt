package me.matsumo.pholio.api

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import me.matsumo.pholio.config.AppConfig

/**
 * 実装済み route 情報から OpenAPI JSON を生成するファクトリ。
 */
class OpenApiDocumentFactory(
    private val config: AppConfig,
) {
    /**
     * 現在の実装に対応した OpenAPI 3.1 document を作成する。
     */
    fun create(): JsonObject = buildJsonObject {
        put("openapi", "3.1.0")
        put(
            "info",
            buildJsonObject {
                put("title", "Pholio API")
                put("version", config.version)
            },
        )
        put(
            "servers",
            JsonArray(
                listOf(
                    buildJsonObject {
                        put("url", "/")
                    },
                ),
            ),
        )
        put("paths", paths())
    }

    private fun paths(): JsonObject = buildJsonObject {
        put(
            "/api/v1/health",
            buildJsonObject {
                put(
                    "get",
                    operation(
                        operationId = "getHealth",
                        summary = "ヘルスチェックを取得する",
                    ),
                )
            },
        )
        put(
            "/api/v1/openapi.json",
            buildJsonObject {
                put(
                    "get",
                    operation(
                        operationId = "getOpenApiDocument",
                        summary = "OpenAPI document を取得する",
                    ),
                )
            },
        )
    }

    private fun operation(operationId: String, summary: String): JsonObject = buildJsonObject {
        put("operationId", operationId)
        put("summary", summary)
        put(
            "responses",
            buildJsonObject {
                put(
                    "200",
                    buildJsonObject {
                        put("description", JsonPrimitive("成功"))
                    },
                )
            },
        )
    }
}
