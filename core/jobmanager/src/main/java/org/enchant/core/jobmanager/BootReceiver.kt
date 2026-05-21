package org.enchant.core.jobmanager

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            try {
                JobManager.wakeUp()
            } catch (e: IllegalStateException) {
                JobLogger.w("JobManager not initialized on boot")
            }
        }
    }
}
