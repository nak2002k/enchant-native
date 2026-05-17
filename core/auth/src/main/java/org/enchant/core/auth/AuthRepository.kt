package org.enchant.core.auth

import kotlinx.serialization.json.*
import org.enchant.core.base.AppConfig
import org.enchant.core.network.ApiClient
import org.enchant.core.network.models.*

class AuthRepository(private val apiClient: ApiClient) {

    suspend fun requestOtp(identifier: String): Result<OtpResponse> {
        return try {
            val body = buildJsonObject { put("identifier", identifier) }
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
            val body = buildJsonObject {
                put("challenge_id", challengeId)
                put("otp", otp)
                if (deviceId != null) {
                    put("device_info", buildJsonObject {
                        put("device_id", deviceId)
                        put("user_agent", "Enchant-Android/${AppConfig.appVersion}")
                    })
                }
            }
            val response = apiClient.post("/v1/auth/verify-otp", body)
            response.map { json ->
                val accessToken = json["access_token"]?.jsonPrimitive?.content ?: ""
                val deviceIdFromJwt = extractDeviceIdFromJwt(accessToken)
                AuthResponse(
                    userId = json["user_id"]?.jsonPrimitive?.content ?: "",
                    accessToken = accessToken,
                    refreshToken = json["refresh_token"]?.jsonPrimitive?.content ?: "",
                    expiresIn = json["expires_in"]?.jsonPrimitive?.int ?: 900,
                    deviceId = deviceIdFromJwt
                )
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun extractDeviceIdFromJwt(jwt: String): String {
        return try {
            val parts = jwt.split(".")
            if (parts.size == 3) {
                val payload = java.util.Base64.getUrlDecoder().decode(parts[1])
                val payloadStr = payload.decodeToString()
                val json = kotlinx.serialization.json.Json.parseToJsonElement(payloadStr).jsonObject
                json["did"]?.jsonPrimitive?.content ?: ""
            } else ""
        } catch (_: Exception) { "" }
    }

    suspend fun refreshToken(refreshToken: String): Result<RefreshResponse> {
        return try {
            val body = buildJsonObject { put("refresh_token", refreshToken) }
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
                json["devices"]?.jsonArray?.map { value ->
                    DeviceInfo(
                        deviceId = value.jsonObject["device_id"]?.jsonPrimitive?.content,
                        userAgent = value.jsonObject["user_agent"]?.jsonPrimitive?.content
                    )
                } ?: emptyList()
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
                val keysArray = json["keys"]?.jsonArray ?: return@map emptyMap()
                keysArray.mapNotNull { keyObj ->
                    val obj = keyObj.jsonObject
                    val kid = obj["kid"]?.jsonPrimitive?.content ?: return@mapNotNull null
                    val x = obj["x"]?.jsonPrimitive?.content ?: return@mapNotNull null
                    kid to x
                }.toMap()
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun registerKeys(request: KeyRegisterRequest): Result<String> {
        return try {
            val body = buildJsonObject {
                put("identity_key", request.identityKey)
                put("signed_prekey", buildJsonObject {
                    put("public_key", request.signedPrekey.publicKey)
                    put("signature", request.signedPrekey.signature)
                })
                put("one_time_prekeys", buildJsonArray {
                    request.oneTimePrekeys.forEach { opk ->
                        add(buildJsonObject { put("public_key", opk.publicKey) })
                    }
                })
            }
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
            val body = buildJsonObject {
                put("public_key", publicKey)
                put("signature", signature)
            }
            apiClient.put("/v1/keys/signed-prekey", body)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun uploadOpks(prekeys: List<OneTimePrekeyData>): Result<Int> {
        return try {
            val body = buildJsonObject {
                put("one_time_prekeys", buildJsonArray {
                    prekeys.forEach { opk ->
                        add(buildJsonObject { put("public_key", opk.publicKey) })
                    }
                })
            }
            val response = apiClient.post("/v1/keys/one-time-prekeys", body)
            response.map { json ->
                json["count"]?.jsonPrimitive?.int ?: 0
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getOpkCount(): Result<Int> {
        return try {
            val response = apiClient.get("/v1/keys/opk-count")
            response.map { json ->
                json["count"]?.jsonPrimitive?.int ?: 0
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
