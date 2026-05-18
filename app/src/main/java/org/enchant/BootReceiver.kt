package org.enchant

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import org.enchant.core.network.WebSocketService

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED || intent?.action == "android.intent.action.QUICKBOOT_POWERON") {
            val jwt = org.enchant.core.base.SecurePreferences.getString("auth.jwt")
            if (jwt == null) {
                android.util.Log.d("BootReceiver", "No auth token, skipping WebSocketService")
                return
            }
            val wsIntent = Intent(context, WebSocketService::class.java).apply {
                action = WebSocketService.ACTION_CONNECT
            }
            try {
                context.startForegroundService(wsIntent)
            } catch (e: IllegalStateException) {
                android.util.Log.w("BootReceiver", "Foreground service start failed: ${e.message}")
                try { context.startService(wsIntent) } catch (_: Exception) {}
            } catch (e: Exception) {
                android.util.Log.w("BootReceiver", "Service start failed: ${e.message}")
            }
        }
    }
}
