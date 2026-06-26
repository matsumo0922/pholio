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
        put("components", components())
    }

    private fun paths(): JsonObject = buildJsonObject {
        put(
            "/api/v1/health",
            path("get" to operation("getHealth", "ヘルスチェックを取得する")),
        )
        put(
            "/api/v1/openapi.json",
            path("get" to operation("getOpenApiDocument", "OpenAPI document を取得する")),
        )
        put(
            "/api/v1/photos",
            path(
                "get" to operation(
                    operationId = "listPhotos",
                    summary = "写真一覧を取得する",
                    parameters = parameters(
                        queryParameter("sort", "takenAt / name / indexedAt / random"),
                        queryParameter("order", "asc / desc"),
                        queryParameter("limit", "1 から 500"),
                        queryParameter("cursor", "前ページの nextCursor"),
                        queryParameter("seed", "random sort の seed"),
                    ),
                ),
            ),
        )
        put(
            "/api/v1/photos/excluded",
            path(
                "get" to operation(
                    operationId = "listExcludedPhotos",
                    summary = "除外済み写真一覧を取得する",
                    parameters = parameters(
                        queryParameter("limit", "1 から 500"),
                        queryParameter("cursor", "前ページの nextCursor"),
                    ),
                ),
            ),
        )
        put(
            "/api/v1/photos:exclude",
            path(
                "post" to operation(
                    operationId = "excludePhotos",
                    summary = "写真をライブラリから除外する",
                    requestBody = jsonRequestBody("PhotoIdsRequest"),
                ),
            ),
        )
        put(
            "/api/v1/photos:restore",
            path(
                "post" to operation(
                    operationId = "restorePhotos",
                    summary = "除外済み写真を復元する",
                    requestBody = jsonRequestBody("PhotoIdsRequest"),
                ),
            ),
        )
        put(
            "/api/v1/photos/{photoId}",
            path(
                "get" to operation(
                    operationId = "getPhoto",
                    summary = "写真詳細を取得する",
                    parameters = parameters(pathParameter("photoId", "写真 ID")),
                ),
            ),
        )
        put(
            "/api/v1/photos/{photoId}/neighbors",
            path(
                "get" to operation(
                    operationId = "getPhotoNeighbors",
                    summary = "写真詳細の前後を取得する",
                    parameters = parameters(
                        pathParameter("photoId", "写真 ID"),
                        queryParameter("scope", "home / album"),
                        queryParameter("albumId", "scope=album の album ID"),
                        queryParameter("sort", "takenAt / name / indexedAt / random"),
                        queryParameter("order", "asc / desc"),
                        queryParameter("seed", "random sort の seed"),
                    ),
                ),
            ),
        )
        put(
            "/api/v1/photos/{photoId}/thumbnail/{variant}",
            path(
                "get" to operation(
                    operationId = "getPhotoThumbnail",
                    summary = "写真 thumbnail を取得する",
                    parameters = parameters(
                        pathParameter("photoId", "写真 ID"),
                        pathParameter("variant", "grid_sm / grid_md / preview_lg"),
                        queryParameter("v", "source fingerprint"),
                    ),
                ),
            ),
        )
        put(
            "/api/v1/photos/{photoId}/original",
            path(
                "get" to operation(
                    operationId = "getPhotoOriginal",
                    summary = "写真原本を取得する",
                    parameters = parameters(
                        pathParameter("photoId", "写真 ID"),
                        queryParameter("v", "source fingerprint"),
                    ),
                ),
            ),
        )
        put(
            "/api/v1/albums",
            path(
                "get" to operation("listAlbums", "アルバム一覧を取得する"),
                "post" to operation(
                    operationId = "createAlbum",
                    summary = "アルバムを作成する",
                    responseCode = "201",
                    responseDescription = "作成しました",
                    requestBody = jsonRequestBody("CreateAlbumRequest"),
                ),
            ),
        )
        put(
            "/api/v1/albums/{albumId}",
            path(
                "get" to operation(
                    operationId = "getAlbum",
                    summary = "アルバム詳細を取得する",
                    parameters = parameters(pathParameter("albumId", "アルバム ID")),
                ),
                "patch" to operation(
                    operationId = "updateAlbum",
                    summary = "アルバム名を更新する",
                    parameters = parameters(pathParameter("albumId", "アルバム ID")),
                    requestBody = jsonRequestBody("UpdateAlbumRequest"),
                ),
                "delete" to operation(
                    operationId = "deleteAlbum",
                    summary = "アルバムを削除する",
                    responseCode = "204",
                    responseDescription = "削除しました",
                    parameters = parameters(pathParameter("albumId", "アルバム ID")),
                ),
            ),
        )
        put(
            "/api/v1/albums/{albumId}/photos",
            path(
                "get" to operation(
                    operationId = "listAlbumPhotos",
                    summary = "アルバム内の写真一覧を取得する",
                    parameters = parameters(
                        pathParameter("albumId", "アルバム ID"),
                        queryParameter("sort", "takenAt / name / indexedAt / random"),
                        queryParameter("order", "asc / desc"),
                        queryParameter("limit", "1 から 500"),
                        queryParameter("cursor", "前ページの nextCursor"),
                        queryParameter("seed", "random sort の seed"),
                    ),
                ),
                "post" to operation(
                    operationId = "addAlbumPhotos",
                    summary = "アルバムへ写真を追加する",
                    parameters = parameters(pathParameter("albumId", "アルバム ID")),
                    requestBody = jsonRequestBody("PhotoIdsRequest"),
                ),
            ),
        )
        put(
            "/api/v1/albums/{albumId}/photos:remove",
            path(
                "post" to operation(
                    operationId = "removeAlbumPhotos",
                    summary = "アルバムから写真を除去する",
                    parameters = parameters(pathParameter("albumId", "アルバム ID")),
                    requestBody = jsonRequestBody("PhotoIdsRequest"),
                ),
            ),
        )
        put(
            "/api/v1/index/status",
            path("get" to operation("getIndexStatus", "index 状態を取得する")),
        )
        put(
            "/api/v1/index/scan",
            path(
                "post" to operation(
                    operationId = "startIndexScan",
                    summary = "index scan を開始する",
                    responseCode = "202",
                    responseDescription = "scan を受け付けました",
                    requestBody = jsonRequestBody("ScanStartRequest"),
                ),
            ),
        )
        put(
            "/api/v1/index/scan/{jobId}:cancel",
            path(
                "post" to operation(
                    operationId = "cancelIndexScan",
                    summary = "index scan の cancel を要求する",
                    responseCode = "202",
                    responseDescription = "cancel を受け付けました",
                    parameters = parameters(pathParameter("jobId", "scan job ID")),
                ),
            ),
        )
    }

    private fun components(): JsonObject = buildJsonObject {
        put(
            "schemas",
            buildJsonObject {
                put("PhotoIdsRequest", objectSchema("photoIds" to arraySchema("string")))
                put("CreateAlbumRequest", objectSchema("name" to primitiveSchema("string")))
                put("UpdateAlbumRequest", objectSchema("name" to primitiveSchema("string")))
                put("ScanStartRequest", objectSchema("mode" to primitiveSchema("string")))
            },
        )
    }

    private fun path(vararg methods: Pair<String, JsonObject>): JsonObject = buildJsonObject {
        methods.forEach { methodEntry ->
            put(methodEntry.first, methodEntry.second)
        }
    }

    private fun operation(
        operationId: String,
        summary: String,
        responseCode: String = "200",
        responseDescription: String = "成功",
        parameters: JsonArray = emptyJsonArray(),
        requestBody: JsonObject? = null,
    ): JsonObject = buildJsonObject {
        put("operationId", operationId)
        put("summary", summary)

        if (parameters.isNotEmpty()) {
            put("parameters", parameters)
        }

        if (requestBody != null) {
            put("requestBody", requestBody)
        }

        put(
            "responses",
            buildJsonObject {
                put(
                    responseCode,
                    buildJsonObject {
                        put("description", JsonPrimitive(responseDescription))
                    },
                )
            },
        )
    }

    private fun parameters(vararg values: JsonObject): JsonArray = JsonArray(values.toList())

    private fun pathParameter(name: String, description: String): JsonObject = parameter(
        name = name,
        location = "path",
        description = description,
        required = true,
    )

    private fun queryParameter(name: String, description: String): JsonObject = parameter(
        name = name,
        location = "query",
        description = description,
        required = false,
    )

    private fun parameter(
        name: String,
        location: String,
        description: String,
        required: Boolean,
    ): JsonObject = buildJsonObject {
        put("name", name)
        put("in", location)
        put("description", description)
        put("required", required)
        put("schema", primitiveSchema("string"))
    }

    private fun jsonRequestBody(schemaName: String): JsonObject = buildJsonObject {
        put("required", true)
        put(
            "content",
            buildJsonObject {
                put(
                    "application/json",
                    buildJsonObject {
                        put("schema", schemaRef(schemaName))
                    },
                )
            },
        )
    }

    private fun schemaRef(schemaName: String): JsonObject = buildJsonObject {
        put("${'$'}ref", "#/components/schemas/$schemaName")
    }

    private fun objectSchema(vararg properties: Pair<String, JsonObject>): JsonObject = buildJsonObject {
        put("type", "object")
        put(
            "properties",
            buildJsonObject {
                properties.forEach { property ->
                    put(property.first, property.second)
                }
            },
        )
        put("required", JsonArray(properties.map { property -> JsonPrimitive(property.first) }))
    }

    private fun arraySchema(itemType: String): JsonObject = buildJsonObject {
        put("type", "array")
        put("items", primitiveSchema(itemType))
    }

    private fun primitiveSchema(type: String): JsonObject = buildJsonObject {
        put("type", type)
    }

    private fun emptyJsonArray(): JsonArray = JsonArray(emptyList())
}
