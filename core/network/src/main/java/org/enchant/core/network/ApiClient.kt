package org.enchant.core.network

import kotlinx.coroutines.Dispatchers
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
    companion object {
        @Volatile
        private var _instance: ApiClient? = null
        fun getInstance(): ApiClient = _instance ?: error("ApiClient not initialized")
        fun setInstance(client: ApiClient) { _instance = client }
    }
    private val initLock = Any()
    @Volatile
    private var initialized = false
    private lateinit var client: OkHttpClient
    private val json = Json { ignoreUnknownKeys = true }

    fun init() {
        if (initialized) return
        synchronized(initLock) {
            if (initialized) return
            client = OkHttpClient.Builder()
                .addInterceptor(AuthInterceptor)
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .build()
            initialized = true
        }
    }

    suspend fun get(path: String, queryParams: Map<String, String>? = null): Result<JsonObject> =
        request("GET", path, queryParams = queryParams)

    suspend fun post(path: String, body: JsonObject? = null): Result<JsonObject> =
        request("POST", path, body)

    private val anonymousClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    suspend fun postAnonymous(path: String, body: JsonObject): Result<JsonObject> {
        return withContext(Dispatchers.IO) {
            try {
                val jsonBody = json.encodeToString(JsonObject.serializer(), body)
                    .toRequestBody("application/json".toMediaType())
                val request = Request.Builder()
                    .url("${AppConfig.gatewayUrl}$path")
                    .post(jsonBody)
                    .build()
                val response = anonymousClient.newCall(request).execute()
                if (response.isSuccessful) {
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
                } else {
                    Result.failure(Exception("HTTP ${response.code}: ${response.message}"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

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

    private val maxRetries = 2
    private val max429Retries = 1
    private val max5xxRetries = 1

    private suspend fun request(
        method: String,
        path: String,
        body: JsonObject? = null,
        queryParams: Map<String, String>? = null,
        rawBody: ByteArray? = null,
        mimeType: String? = null,
        depth: Int = 0
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
                        response.body?.close()
                        val retryAfter = headers["Retry-After"]?.toLongOrNull()
                        if (retryAfter != null && depth < max429Retries) {
                            RateLimitTracker.updateFromHeaders(path, headers)
                            kotlinx.coroutines.delay(retryAfter * 1000)
                            request(method, path, body, queryParams, rawBody, mimeType, depth + 1)
                        } else {
                            Result.failure(Exception("Rate limited"))
                        }
                    }
                    response.code in 500..599 -> {
                        response.body?.close()
                        if (depth < max5xxRetries) {
                            kotlinx.coroutines.delay(2000)
                            request(method, path, body, queryParams, rawBody, mimeType, depth + 1)
                        } else {
                            Result.failure(Exception("Server error: HTTP ${response.code}"))
                        }
                    }
                    else -> {
                        val errorBody = response.body?.string() ?: response.message
                        Result.failure(Exception("HTTP ${response.code}: $errorBody"))
                    }
                }
            } catch (e: Exception) {
                if (depth < maxRetries) {
                    RateLimitTracker.waitIfNeeded(path)
                    kotlinx.coroutines.delay(1000L * (depth + 1))
                    request(method, path, body, queryParams, rawBody, mimeType, depth + 1)
                } else {
                    Result.failure(e)
                }
            }
        }
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
        val params = queryParams.entries.joinToString("&") {
            "${java.net.URLEncoder.encode(it.key, "UTF-8")}=${java.net.URLEncoder.encode(it.value, "UTF-8")}"
        }
        return "$base?$params"
    }
}
