package org.enchant.core.jobmanager

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

internal class AlarmManagerScheduler(private val context: Context) : Scheduler {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    override fun schedule(delayMs: Long, constraints: List<Constraint>) {
        if (delayMs > 0 && constraints.all { it.isMet() }) {
            val intent = Intent(context, RetryReceiver::class.java)
            intent.action = "org.enchant.jobmanager.RETRY"
            val pending = PendingIntent.getBroadcast(
                context,
                0,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            alarmManager?.setExact(
                AlarmManager.RTC_WAKEUP,
                System.currentTimeMillis() + delayMs,
                pending
            )
        }
    }

    class RetryReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            try {
                JobManager.wakeUp()
            } catch (e: IllegalStateException) {
                JobLogger.w("JobManager not initialized on retry alarm")
            }
        }
    }
}
