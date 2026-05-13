package org.enchant.core.auth

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import org.enchant.core.base.AppConfig
import org.enchant.core.base.SecurePreferences
import org.enchant.core.network.ApiClient
import org.enchant.core.network.models.*

class AuthRepository(private val apiClient: ApiClient) {

    suspend fun requestOtp(identifier: String): Result<OtpResponse> {
        return try {
            val body = JsonObject(mapOf("identifier" to kotlinx.serialization.json.JsonPrimitive(identifier)))
            val response = apiClient.post("/v1/auth/request-otp", body)
            response.map { json ->
                OtpResponse(
                    challengeId = json["challenge_id"]?.jsonPrimitive?.content ?: "",
                    expiresIn = json["expires_in"]?.jsonPrimitive?.int ?: 600
                )
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun verifyOtp(challengeId: String, otp: String, deviceId: String? = null): Result<AuthResponse> {
        return try {
            val deviceInfo = deviceId?.let {
                """{"device_id":"$it","user_agent":"Enchant-Android/${AppConfig.appVersion}"}"""
            }
            val bodyMap = mutableMapOf(
                "challenge_id" to kotlinx.serialization.json.JsonPrimitive(challengeId),
                "otp" to kotlinx.serialization.json.JsonPrimitive(otp)
            )
            if (deviceInfo != null) {
                bodyMap["device_info"] = kotlinx.serialization.json.JsonPrimitive(deviceInfo)
            }
            val body = JsonObject(bodyMap)
            val response = apiClient.post("/v1/auth/verify-otp", body)
            response.map { json ->
                AuthResponse(
                    userId = json["user_id"]?.jsonPrimitive?.content ?: "",
                    accessToken = json["access_token"]?.jsonPrimitive?.content ?: "",
                    refreshToken = json["refresh_token"]?.jsonPrimitive?.content ?: "",
                    expiresIn = json["expires_in"]?.jsonPrimitive?.int ?: 900
                )
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun refreshToken(refreshToken: String): Result<RefreshResponse> {
        return try {
            val body = JsonObject(mapOf("refresh_token" to kotlinx.serialization.json.JsonPrimitive(refreshToken)))
            val response = apiClient.post("/v1/auth/refresh", body)
            response.map { json ->
                RefreshResponse(
                    accessToken = json["access_token"]?.jsonPrimitive?.content ?: "",
                    refreshToken = json["refresh_token"]?.jsonPrimitive?.content ?: "",
                    expiresIn = json["expires_in"]?.jsonPrimitive?.int ?: 900
                )
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun logout(): Result<Unit> {
        return try {
            apiClient.post("/v1/auth/logout")
            Result.success(Unit)
        } catch (_: Exception) {
            Result.success(Unit)
        }
    }

    suspend fun listDevices(): Result<List<DeviceInfo>> {
        return try {
            val response = apiClient.get("/v1/auth/devices")
            response.map { json ->
                val devices = json["devices"]?.jsonObject?.let { obj ->
                    obj.entries.map { (key, value) ->
                        DeviceInfo(
                            deviceId = value.jsonObject["device_id"]?.jsonPrimitive?.content,
                            userAgent = value.jsonObject["user_agent"]?.jsonPrimitive?.content
                        )
                    }
                } ?: emptyList()
                devices
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun revokeDevice(deviceId: String): Result<Unit> {
        return try {
            apiClient.del("/v1/auth/devices/$deviceId")
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteAccount(): Result<Unit> {
        return try {
            apiClient.del("/v1/auth/account")
            Result.success(Unit)
        } catch (_: Exception) {
            Result.success(Unit)
        }
    }

    suspend fun fetchJwks(): Result<Map<String, String>> {
        return try {
            val response = apiClient.get("/v1/auth/.well-known/jwks.json")
            response.map { json ->
                val keys = json["keys"]?.jsonObject ?: JsonObject(emptyMap())
                keys.entries.mapNotNull { (_, value) ->
                    val keyObj = value.jsonObject
                    val kid = keyObj["kid"]?.jsonPrimitive?.content ?: return@mapNotNull null
                    val x = keyObj["x"]?.jsonPrimitive?.content ?: return@mapNotNull null
                    kid to x
                }.toMap()
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun registerKeys(request: KeyRegisterRequest): Result<String> {
        return try {
            val body = JsonObject(mapOf(
                "identity_key" to kotlinx.serialization.json.JsonPrimitive(request.identityKey),
                "signed_prekey" to kotlinx.serialization.json.JsonPrimitive(
                    """{"public_key":"${request.signedPrekey.publicKey}","signature":"${request.signedPrekey.signature}"}"""
                ),
                "one_time_prekeys" to kotlinx.serialization.json.JsonPrimitive(
                    request.oneTimePrekeys.joinToString(",") { """{"public_key":"${it.publicKey}"}""" }
                )
            ))
            val response = apiClient.post("/v1/keys/register", body)
            response.map { json ->
                json["device_id"]?.jsonPrimitive?.content ?: ""
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun rotateSignedPreKey(publicKey: String, signature: String): Result<Unit> {
        return try {
            val body = JsonObject(mapOf(
                "public_key" to kotlinx.serialization.json.JsonPrimitive(publicKey),
                "signature" to kotlinx.serialization.json.JsonPrimitive(signature)
            ))
            apiClient.put("/v1/keys/signed-prekey", body)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun uploadOpks(prekeys: List<OneTimePrekeyData>): Result<Int> {
        return try {
            val prekeysJson = prekeys.joinToString(",") { """{"public_key":"${it.publicKey}"}""" }
            val body = JsonObject(mapOf(
                "one_time_prekeys" to kotlinx.serialization.json.JsonPrimitive("[$prekeysJson]")
            ))
            val response = apiClient.post("/v1/keys/one-time-prekeys", body)
            response.map { json ->
                json["total_opks"]?.jsonPrimitive?.int ?: 0
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getOpkCount(): Result<Int> {
        return try {
            val response = apiClient.get("/v1/keys/opk-count")
            response.map { json ->
                json["remaining"]?.jsonPrimitive?.int ?: 0
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
