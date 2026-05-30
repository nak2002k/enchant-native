package org.enchant.core.network

import android.util.Log
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.enchant.core.base.AppConfig
import org.enchant.core.base.SecurePreferences
import java.util.concurrent.TimeUnit

object AuthInterceptor : Interceptor {
    @Volatile private var refreshing = false
    private var currentToken: String? = null
    private var refreshFailCount = 0
    private val maxRefreshFails = 1
    private val refreshClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()
    private val lock = java.lang.Object()

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        val token = synchronized(lock) { currentToken ?: SecurePreferences.getString("auth.jwt") }
        val request = if (token != null) {
            originalRequest.newBuilder()
                .addHeader("Authorization", "Bearer $token")
                .build()
        } else originalRequest

        val response = chain.proceed(request)

        if (response.code == 401 && originalRequest.header("X-Enchant-Retry") == null) {
            val shouldRefresh = synchronized(lock) {
                if (refreshing || refreshFailCount >= maxRefreshFails) false
                else { refreshing = true; true }
            }
            if (shouldRefresh) {
                response.close()
                try {
                    val newToken = doRefresh()
                    synchronized(lock) {
                        if (newToken != null) {
                            currentToken = newToken
                            refreshFailCount = 0
                        } else {
                            refreshFailCount++
                        }
                        refreshing = false
                        lock.notifyAll()
                    }
                    if (newToken != null) {
                        val retryRequest = originalRequest.newBuilder()
                            .header("Authorization", "Bearer $newToken")
                            .header("X-Enchant-Retry", "true")
                            .build()
                        return chain.proceed(retryRequest)
                    }
                } catch (e: Exception) {
                    synchronized(lock) {
                        refreshFailCount++
                        refreshing = false
                        lock.notifyAll()
                    }
                }
            } else {
                synchronized(lock) {
                    var waited = 0L
                    val deadline = System.currentTimeMillis() + 10000
                    while (refreshing && System.currentTimeMillis() < deadline) {
                        lock.wait(500)
                    }
                }
                val refreshedToken = synchronized(lock) { currentToken }
                if (refreshedToken != null) {
                    response.close()
                    return chain.proceed(
                        originalRequest.newBuilder()
                            .header("Authorization", "Bearer $refreshedToken")
                            .header("X-Enchant-Retry", "true")
                            .build()
                    )
                }
            }
        }

        return response
    }

    private fun doRefresh(): String? {
        val refreshToken = SecurePreferences.getString("auth.refresh_token") ?: return null
        return try {
            val bodyJson = Json.encodeToString(
                JsonObject.serializer(),
                buildJsonObject { put("refresh_token", JsonPrimitive(refreshToken)) }
            )
            val request = Request.Builder()
                .url("${AppConfig.gatewayUrl}/v1/auth/refresh")
                .post(bodyJson.toRequestBody("application/json".toMediaType()))
                .build()
            val response = refreshClient.newCall(request).execute()
            if (response.isSuccessful) {
                val body = response.body?.string()
                if (body != null) {
                    val parsed = Json.parseToJsonElement(body).jsonObject
                    val newJwt = parsed["access_token"]?.jsonPrimitive?.content
                    val newRefreshToken = parsed["refresh_token"]?.jsonPrimitive?.content
                    if (newJwt != null) {
                        SecurePreferences.putString("auth.jwt", newJwt)
                    }
                    if (newRefreshToken != null) {
                        SecurePreferences.putString("auth.refresh_token", newRefreshToken)
                    }
                    newJwt
                } else null
            } else {
                SecurePreferences.remove("auth.jwt")
                SecurePreferences.remove("auth.refresh_token")
                null
            }
        } catch (e: Exception) {
            Log.w("AuthInterceptor", "Token refresh failed")
            null
        }
    }
}
