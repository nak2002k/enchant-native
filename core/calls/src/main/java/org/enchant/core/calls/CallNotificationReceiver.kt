package org.enchant.core.calls

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class CallNotificationReceiver : BroadcastReceiver() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_MUTE -> scope.launch { CallManager.toggleMute() }
            ACTION_SPEAKER -> scope.launch { CallManager.toggleSpeaker() }
            ACTION_HANGUP -> scope.launch { CallManager.endCall() }
        }
    }

    companion object {
        const val ACTION_MUTE = "org.enchant.action.CALL_MUTE"
        const val ACTION_SPEAKER = "org.enchant.action.CALL_SPEAKER"
        const val ACTION_HANGUP = "org.enchant.action.CALL_HANGUP"
    }
}
