package org.enchant.core.network

import android.app.Service
import android.content.Intent
import android.os.IBinder

class WebSocketService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY
}
