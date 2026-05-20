package org.enchant.core.calls.notification

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
            ACTION_MUTE -> scope.launch { org.enchant.core.calls.CallManager.toggleMute() }
            ACTION_SPEAKER -> scope.launch { org.enchant.core.calls.CallManager.toggleSpeaker() }
            ACTION_HANGUP -> scope.launch { org.enchant.core.calls.CallManager.endCall() }
            ACTION_ANSWER -> scope.launch { org.enchant.core.calls.CallManager.acceptCall(intent.getBooleanExtra("is_video", false)) }
            ACTION_DENY -> scope.launch { org.enchant.core.calls.CallManager.denyCall() }
        }
    }

    companion object {
        const val ACTION_MUTE = "org.enchant.action.CALL_MUTE"
        const val ACTION_SPEAKER = "org.enchant.action.CALL_SPEAKER"
        const val ACTION_HANGUP = "org.enchant.action.CALL_HANGUP"
        const val ACTION_ANSWER = "org.enchant.action.CALL_ANSWER"
        const val ACTION_DENY = "org.enchant.action.CALL_DENY"
    }
}