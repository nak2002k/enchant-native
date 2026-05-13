package org.enchant.core.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.enchant.core.base.AppConfig
import java.util.concurrent.TimeUnit

class ApiClient {
    private var initialized = false
    private lateinit var client: OkHttpClient
    private val json = Json { ignoreUnknownKeys = true }

    fun init() {
        if (initialized) return
        client = OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()
        initialized = true
    }

    suspend fun get(path: String, queryParams: Map<String, String>? = null): Result<JsonObject> =
        request("GET", path, queryParams = queryParams)

    suspend fun post(path: String, body: JsonObject? = null): Result<JsonObject> =
        request("POST", path, body)

    suspend fun put(path: String, body: JsonObject? = null): Result<JsonObject> =
        request("PUT", path, body)

    suspend fun del(path: String): Result<JsonObject> =
        request("DELETE", path)

    suspend fun postRaw(path: String, body: ByteArray, mimeType: String = "application/octet-stream"): Result<JsonObject> {
        if (body.size > 128 * 1024 * 1024) {
            return Result.failure(IllegalArgumentException("Body exceeds 128MB limit"))
        }
        return request("POST", path, rawBody = body, mimeType = mimeType)
    }

    suspend fun getBinary(path: String): Result<ByteArray> {
        return withContext(Dispatchers.IO) {
            try {
                val request = buildRequest("GET", path)
                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    Result.success(response.body?.bytes() ?: ByteArray(0))
                } else {
                    Result.failure(Exception("HTTP ${response.code}: ${response.message}"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun uploadFile(path: String, fileBytes: ByteArray, mimeType: String): Result<JsonObject> =
        postRaw(path, fileBytes, mimeType)

    private suspend fun request(
        method: String,
        path: String,
        body: JsonObject? = null,
        queryParams: Map<String, String>? = null,
        rawBody: ByteArray? = null,
        mimeType: String? = null
    ): Result<JsonObject> {
        return withContext(Dispatchers.IO) {
            try {
                RateLimitTracker.waitIfNeeded(path)
                RateLimitTracker.recordCall(path)
                val request = buildRequest(method, path, body, queryParams, rawBody, mimeType)
                val response = client.newCall(request).execute()
                val headers = response.headers.toMap()
                RateLimitTracker.updateFromHeaders(path, headers)

                when {
                    response.isSuccessful -> {
                        val responseBody = response.body?.string()
                        if (responseBody.isNullOrEmpty()) {
                            Result.success(JsonObject(emptyMap()))
                        } else {
                            try {
                                Result.success(json.parseToJsonElement(responseBody).jsonObject)
                            } catch (e: Exception) {
                                Result.failure(Exception("Non-JSON response: $responseBody"))
                            }
                        }
                    }
                    response.code == 429 -> {
                        val retryAfter = headers["Retry-After"]?.toLongOrNull()
                        if (retryAfter != null) {
                            RateLimitTracker.updateFromHeaders(path, headers)
                            kotlinx.coroutines.delay(retryAfter * 1000)
                            request(method, path, body, queryParams, rawBody, mimeType)
                        } else {
                            Result.failure(Exception("Rate limited"))
                        }
                    }
                    response.code in 500..599 -> {
                        kotlinx.coroutines.delay(2000)
                        request(method, path, body, queryParams, rawBody, mimeType)
                    }
                    else -> {
                        val errorBody = response.body?.string() ?: response.message
                        Result.failure(Exception("HTTP ${response.code}: $errorBody"))
                    }
                }
            } catch (e: Exception) {
                retryOnNetworkError(method, path, body, queryParams, rawBody, mimeType, e)
            }
        }
    }

    private suspend fun retryOnNetworkError(
        method: String, path: String, body: JsonObject?,
        queryParams: Map<String, String>?, rawBody: ByteArray?, mimeType: String?,
        originalError: Exception, attempt: Int = 1
    ): Result<JsonObject> {
        if (attempt > 2) return Result.failure(originalError)
        kotlinx.coroutines.delay(1000L * attempt)
        return request(method, path, body, queryParams, rawBody, mimeType)
    }

    private fun buildRequest(
        method: String,
        path: String,
        body: JsonObject? = null,
        queryParams: Map<String, String>? = null,
        rawBody: ByteArray? = null,
        mimeType: String? = null
    ): Request {
        val url = buildUrl(path, queryParams)
        val requestBody = when {
            rawBody != null -> rawBody.toRequestBody(mimeType?.toMediaType() ?: "application/octet-stream".toMediaType())
            body != null -> json.encodeToString(JsonObject.serializer(), body).toRequestBody("application/json".toMediaType())
            method == "POST" || method == "PUT" -> "".toRequestBody(null)
            else -> null
        }
        return Request.Builder()
            .url(url)
            .method(method, requestBody)
            .build()
    }

    private fun buildUrl(path: String, queryParams: Map<String, String>?): String {
        val base = "${AppConfig.gatewayUrl}$path"
        if (queryParams.isNullOrEmpty()) return base
        val params = queryParams.entries.joinToString("&") { "${it.key}=${it.value}" }
        return "$base?$params"
    }
}
