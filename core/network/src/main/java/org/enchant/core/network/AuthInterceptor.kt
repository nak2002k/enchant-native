package org.enchant.core.network

import okhttp3.Interceptor
import okhttp3.Response
import org.enchant.core.base.SecurePreferences

object AuthInterceptor : Interceptor {
    private var refreshing = false
    private var currentToken: String? = null

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        val token = currentToken ?: SecurePreferences.getString("auth.jwt")
        val request = if (token != null) {
            originalRequest.newBuilder()
                .addHeader("Authorization", "Bearer $token")
                .build()
        } else {
            originalRequest
        }

        val response = chain.proceed(request)

        if (response.code == 401 && !refreshing) {
            response.close()
            refreshing = true
            try {
                val newToken = refreshToken()
                if (newToken != null) {
                    currentToken = newToken
                    val retryRequest = originalRequest.newBuilder()
                        .header("Authorization", "Bearer $newToken")
                        .build()
                    return chain.proceed(retryRequest)
                }
            } finally {
                refreshing = false
            }
        }

        return response
    }

    private fun refreshToken(): String? {
        val refreshToken = SecurePreferences.getString("auth.refresh_token") ?: return null
        return try {
            val response = okhttp3.OkHttpClient().newCall(
                okhttp3.Request.Builder()
                    .url("${org.enchant.core.base.AppConfig.gatewayUrl}/v1/auth/refresh")
                    .post(okhttp3.RequestBody.create(null, """{"refresh_token":"$refreshToken"""".toByteArray()))
                    .build()
            ).execute()
            if (response.isSuccessful) {
                val body = response.body?.string()
                val newJwt = body?.let { extractJsonField(it, "access_token") }
                if (newJwt != null) {
                    SecurePreferences.putString("auth.jwt", newJwt)
                }
                newJwt
            } else {
                SecurePreferences.remove("auth.jwt")
                SecurePreferences.remove("auth.refresh_token")
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun extractJsonField(json: String, field: String): String? {
        val pattern = """"$field"\s*:\s*"([^"]+)"""".toRegex()
        return pattern.find(json)?.groupValues?.getOrNull(1)
    }
}
