package org.enchant.core.push

import android.util.Log
import org.enchant.core.base.SecurePreferences
import org.enchant.core.network.ApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

object PushTokenRegistrar {
    private const val PUSH_TOKEN_KEY = "push.fcm_token"
    private const val TAG = "PushTokenRegistrar"

    suspend fun registerWithBackend(token: String) {
        if (token == SecurePreferences.getString(PUSH_TOKEN_KEY)) return
        withContext(Dispatchers.IO) {
            try {
                val apiClient = ApiClient()
                apiClient.init()
                val body = buildJsonObject {
                    put("token", token)
                    put("platform", "ANDROID")
                }
                apiClient.post("/v1/push/register", body)
                SecurePreferences.putString(PUSH_TOKEN_KEY, token)
            } catch (e: Exception) {
                Log.w(TAG, "registerWithBackend failed: ${e.message}")
            }
        }
    }

    suspend fun deregisterFromBackend() {
        withContext(Dispatchers.IO) {
            try {
                val apiClient = ApiClient()
                apiClient.init()
                apiClient.del("/v1/push/register")
            } catch (e: Exception) {
                Log.w(TAG, "deregisterFromBackend failed: ${e.message}")
            }
            SecurePreferences.remove(PUSH_TOKEN_KEY)
        }
    }

    suspend fun getFcmToken(): String? {
        return withContext(Dispatchers.IO) {
            try {
                val token = FirebaseMessaging.getInstance().token.await()
                SecurePreferences.putString(PUSH_TOKEN_KEY, token)
                token
            } catch (e: Exception) {
                Log.w(TAG, "getFcmToken failed: ${e.message}")
                SecurePreferences.getString(PUSH_TOKEN_KEY)
            }
        }
    }

    fun isPlayServicesAvailable(context: android.content.Context): Boolean {
        return try {
            com.google.android.gms.common.GoogleApiAvailability.getInstance()
                .isGooglePlayServicesAvailable(context) == com.google.android.gms.common.ConnectionResult.SUCCESS
        } catch (e: Exception) {
            Log.w(TAG, "isPlayServicesAvailable check failed: ${e.message}")
            false
        }
    }
}
