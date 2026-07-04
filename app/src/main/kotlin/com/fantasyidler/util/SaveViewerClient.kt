package com.fantasyidler.util

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.URI
import java.util.concurrent.TimeUnit

data class ViewerTarget(
    val baseUrl: String,
    val viewerId: String,
    val viewerUrl: String,
)

data class UploadResponse(
    val imported: Boolean,
    val snapshotId: Int?,
)

sealed class SaveViewerError(message: String) : Exception(message) {
    class InvalidUrl(detail: String) : SaveViewerError(detail)
    class Network(detail: String) : SaveViewerError(detail)
    object NotFound : SaveViewerError("Viewer not found")
    class ParseError(detail: String) : SaveViewerError(detail)
    object RateLimit : SaveViewerError("Rate limit exceeded")
    class ServerError(val code: Int, detail: String) : SaveViewerError(detail)
}

object SaveViewerClient {

    private val VIEWER_PATH_RE = Regex("/v/([A-Za-z0-9_-]{16,64})(?:/|$|\\?)")
    private val VIEWER_ID_RE = Regex("^[A-Za-z0-9_-]{16,64}$")

    private val http = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    fun parseViewerUrl(input: String): Result<ViewerTarget> {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) {
            return Result.failure(SaveViewerError.InvalidUrl("Empty URL"))
        }

        val normalized = when {
            trimmed.startsWith("http://") || trimmed.startsWith("https://") -> trimmed
            else -> "https://$trimmed"
        }

        val viewerId = VIEWER_PATH_RE.find(normalized)?.groupValues?.get(1)
            ?: return Result.failure(SaveViewerError.InvalidUrl("No viewer ID in URL"))

        if (!VIEWER_ID_RE.matches(viewerId)) {
            return Result.failure(SaveViewerError.InvalidUrl("Invalid viewer ID"))
        }

        return try {
            val uri = URI(normalized)
            val host = uri.host ?: return Result.failure(SaveViewerError.InvalidUrl("Invalid URL"))
            val port = uri.port
            val authority = if (port != -1) "$host:$port" else host
            val baseUrl = "${uri.scheme}://$authority"
            Result.success(
                ViewerTarget(
                    baseUrl = baseUrl,
                    viewerId = viewerId,
                    viewerUrl = "$baseUrl/v/$viewerId/",
                ),
            )
        } catch (_: Exception) {
            Result.failure(SaveViewerError.InvalidUrl("Invalid URL format"))
        }
    }

    fun uploadSave(target: ViewerTarget, jsonString: String): Result<UploadResponse> {
        val url = "${target.baseUrl}/v/${target.viewerId}/api/import"
        val fileBody = jsonString.toRequestBody("application/json".toMediaType())
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("file", "fantasyidler_save.json", fileBody)
            .build()

        val request = Request.Builder().url(url).post(body).build()

        return try {
            http.newCall(request).execute().use { response ->
                val responseBody = response.body?.string().orEmpty()
                when (response.code) {
                    200 -> parseSuccess(responseBody)
                    404 -> Result.failure(SaveViewerError.NotFound)
                    422 -> parse422(responseBody)
                    429 -> Result.failure(SaveViewerError.RateLimit)
                    else -> Result.failure(
                        SaveViewerError.ServerError(response.code, responseBody.ifBlank { "HTTP ${response.code}" }),
                    )
                }
            }
        } catch (e: IOException) {
            Result.failure(SaveViewerError.Network(e.message ?: "Network error"))
        }
    }

    private fun parseSuccess(body: String): Result<UploadResponse> = try {
        val obj = json.parseToJsonElement(body).jsonObject
        Result.success(
            UploadResponse(
                imported = obj["imported"]?.jsonPrimitive?.boolean ?: false,
                snapshotId = obj["snapshot_id"]?.jsonPrimitive?.int,
            ),
        )
    } catch (_: Exception) {
        Result.failure(SaveViewerError.ParseError("Invalid server response"))
    }

    private fun parse422(body: String): Result<UploadResponse> {
        val detail = try {
            json.parseToJsonElement(body).jsonObject["error"]?.jsonPrimitive?.content
        } catch (_: Exception) {
            null
        }
        return Result.failure(SaveViewerError.ParseError(detail ?: "Could not parse save"))
    }
}
