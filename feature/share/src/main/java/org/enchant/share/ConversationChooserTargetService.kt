package org.enchant.feature.share

import android.app.Service
import android.content.Intent
import android.os.IBinder

class ConversationChooserTargetService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_NOT_STICKY
}
