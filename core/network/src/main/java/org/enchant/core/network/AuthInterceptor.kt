package org.enchant.core.network

import android.util.Log
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.enchant.core.base.AppConfig
import org.enchant.core.base.SecurePreferences

object AuthInterceptor : Interceptor {
    @Volatile private var refreshing = false
    private var currentToken: String? = null
    private val refreshClient = OkHttpClient.Builder()
        .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
        .build()
    private val json = Json { ignoreUnknownKeys = true }
    private val lock = Any()

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        val token = synchronized(lock) { currentToken ?: SecurePreferences.getString("auth.jwt") }
        val request = if (token != null) {
            originalRequest.newBuilder()
                .addHeader("Authorization", "Bearer $token")
                .build()
        } else {
            originalRequest
        }

        val response = chain.proceed(request)

        if (response.code == 401) {
            val shouldRefresh = synchronized(lock) {
                if (refreshing) false
                else { refreshing = true; true }
            }
            if (shouldRefresh) {
                response.close()
                try {
                    val newToken = refreshToken()
                    if (newToken != null) {
                        synchronized(lock) { currentToken = newToken }
                        val retryRequest = originalRequest.newBuilder()
                            .header("Authorization", "Bearer $newToken")
                            .build()
                        return chain.proceed(retryRequest)
                    }
                } finally {
                    synchronized(lock) { refreshing = false }
                }
            } else {
                var waited = 0
                while (refreshing && waited < 10000) {
                    Thread.sleep(500)
                    waited += 500
                }
                val refreshedToken = synchronized(lock) { currentToken }
                if (refreshedToken != null && !refreshing) {
                    response.close()
                    val retryRequest = originalRequest.newBuilder()
                        .header("Authorization", "Bearer $refreshedToken")
                        .build()
                    return chain.proceed(retryRequest)
                }
            }
            return response
        }

        return response
    }

    private fun refreshToken(): String? {
        val refreshToken = SecurePreferences.getString("auth.refresh_token") ?: return null
        return try {
            val bodyJson = """{"refresh_token":"$refreshToken"}"""
            val request = Request.Builder()
                .url("${AppConfig.gatewayUrl}/v1/auth/refresh")
                .post(bodyJson.toRequestBody("application/json".toMediaType()))
                .build()
            val response = refreshClient.newCall(request).execute()
            if (response.isSuccessful) {
                val body = response.body?.string()
                if (body != null) {
                    val parsed = json.parseToJsonElement(body).jsonObject
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
            Log.w("AuthInterceptor", "Refresh failed: ${e.message}")
            null
        }
    }
}
