package org.enchant.core.push

import org.enchant.core.base.SecurePreferences
import org.enchant.core.network.ApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object PushTokenRegistrar {
    private const val PUSH_TOKEN_KEY = "push.fcm_token"

    suspend fun registerWithBackend(token: String) {
        if (token == SecurePreferences.getString(PUSH_TOKEN_KEY)) return
        withContext(Dispatchers.IO) {
            try {
                val apiClient = ApiClient()
                val body = kotlinx.serialization.json.JsonObject(mapOf(
                    "token" to kotlinx.serialization.json.JsonPrimitive(token),
                    "platform" to kotlinx.serialization.json.JsonPrimitive("ANDROID")
                ))
                apiClient.post("/v1/push/register", body)
                SecurePreferences.putString(PUSH_TOKEN_KEY, token)
            } catch (_: Exception) {
            }
        }
    }

    suspend fun deregisterFromBackend() {
        withContext(Dispatchers.IO) {
            try {
                val apiClient = ApiClient()
                apiClient.del("/v1/push/register")
            } catch (_: Exception) {
            }
            SecurePreferences.remove(PUSH_TOKEN_KEY)
        }
    }

    suspend fun getFcmToken(): String? {
        return withContext(Dispatchers.IO) {
            try {
                com.google.firebase.messaging.FirebaseMessaging.getInstance().token
                    .addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                            SecurePreferences.putString(PUSH_TOKEN_KEY, task.result)
                        }
                    }
                SecurePreferences.getString(PUSH_TOKEN_KEY)
            } catch (_: Exception) {
                SecurePreferences.getString(PUSH_TOKEN_KEY)
            }
        }
    }

    fun isPlayServicesAvailable(context: android.content.Context): Boolean {
        return try {
            com.google.android.gms.common.GoogleApiAvailability.getInstance()
                .isGooglePlayServicesAvailable(context) == com.google.android.gms.common.ConnectionResult.SUCCESS
        } catch (_: Exception) {
            false
        }
    }
}
