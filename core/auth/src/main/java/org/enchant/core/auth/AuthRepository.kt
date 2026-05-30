package org.enchant.core.auth

import android.util.Log
import kotlinx.serialization.json.*
import org.enchant.core.base.AppConfig
import org.enchant.core.network.ApiClient
import org.enchant.core.network.models.*

class AuthRepository(private val apiClient: ApiClient) {

    suspend fun requestOtp(identifier: String): Result<OtpResponse> {
        return try {
            val body = buildJsonObject { put("identifier", identifier) }
            val response = apiClient.post(AuthConstants.PATH_REQUEST_OTP, body)
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
            val response = apiClient.post(AuthConstants.PATH_VERIFY_OTP, body)
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
            if (parts.size == 3 && parts.none { it.isEmpty() }) {
                val payload = java.util.Base64.getUrlDecoder().decode(parts[1])
                val payloadStr = payload.decodeToString()
                val json = kotlinx.serialization.json.Json.parseToJsonElement(payloadStr).jsonObject
                json["did"]?.jsonPrimitive?.content ?: ""
            } else {
                Log.w("AuthRepo", "Malformed JWT: ${parts.size} parts, empty=${parts.any { it.isEmpty() }}")
                ""
            }
        } catch (e: Exception) {
            Log.w("AuthRepo", "Failed to extract device ID from JWT: ${e.message}")
            ""
        }
    }

    suspend fun refreshToken(refreshToken: String): Result<RefreshResponse> {
        return try {
            val body = buildJsonObject { put("refresh_token", refreshToken) }
            val response = apiClient.post(AuthConstants.PATH_REFRESH, body)
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
            apiClient.post(AuthConstants.PATH_LOGOUT).map { }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun listDevices(): Result<List<DeviceInfo>> {
        return try {
            val response = apiClient.get(AuthConstants.PATH_DEVICES)
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
            val result = apiClient.del("${AuthConstants.PATH_DEVICES}/$deviceId")
            result.map { }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteAccount(): Result<Unit> {
        return try {
            apiClient.del(AuthConstants.PATH_ACCOUNT).map { }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchJwks(): Result<Map<String, String>> {
        return try {
            val response = apiClient.get(AuthConstants.PATH_JWKS)
            if (response.isSuccess) {
                val json = response.getOrNull()!!
                val keysArray = json["keys"]?.jsonArray ?: return Result.success(emptyMap())
                val map = keysArray.mapNotNull { keyObj ->
                    val obj = keyObj.jsonObject
                    val kid = obj["kid"]?.jsonPrimitive?.content ?: return@mapNotNull null
                    val x = obj["x"]?.jsonPrimitive?.content ?: return@mapNotNull null
                    kid to x
                }.toMap()
                Result.success(map)
            } else {
                Result.failure(Exception("Failed to fetch JWKS: HTTP ${response.exceptionOrNull()?.message}"))
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
            val response = apiClient.post(AuthConstants.PATH_KEYS_REGISTER, body)
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
            apiClient.put(AuthConstants.PATH_KEYS_SPK, body).map { }
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
            val response = apiClient.post(AuthConstants.PATH_KEYS_OPK, body)
            response.map { json ->
                json["opk_count"]?.jsonPrimitive?.int ?: 0
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getOpkCount(): Result<Int> {
        return try {
            val response = apiClient.get(AuthConstants.PATH_KEYS_OPK_COUNT)
            response.map { json ->
                json["opk_count"]?.jsonPrimitive?.int ?: 0
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
