package org.enchant.core.jobmanager

import android.os.Handler
import android.os.Looper

internal class InAppScheduler(private val jobManager: JobManager) : Scheduler {
    private val handler = Handler(Looper.getMainLooper())

    override fun schedule(delayMs: Long, constraints: List<Constraint>) {
        if (delayMs > 0 && constraints.all { it.isMet() }) {
            handler.postDelayed({ jobManager.wakeUp() }, delayMs)
        }
    }
}
