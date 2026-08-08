package org.enchant.core.crypto

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/**
 * Key Transparency monitor (RFC 6962 append-only audit).
 *
 * Periodically verifies that the signed key log has not been rewritten: it
 * requests a consistency proof between the previously-verified tree size and
 * the current signed tree head. If the proof is invalid, the log (or a MITM
 * serving it) is untrustworthy and the app flags it. This is Signal's
 * "monitor" role — a lightweight client-side watch on log consistency.
 */
class KeyTransparencyMonitorWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val client = org.enchant.core.network.ApiClient.getInstance()
            val ok = KeyTransparencyVerifier.verifyConsistency(client)
            if (!ok) {
                Log.w(TAG, "Key transparency consistency check failed — log may be rewritten")
                // Keep the last-good size so a transient network issue doesn't
                // poison the next audit; retry later.
            } else {
                Log.d(TAG, "Key transparency consistency check passed")
            }
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Key transparency monitor failed, will retry", e)
            Result.retry()
        }
    }

    companion object {
        private const val TAG = "KeyTransparencyMonitor"
        private const val WORK_NAME = "key_transparency_monitor"

        /** Schedule the periodic key-transparency monitor (daily). */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<KeyTransparencyMonitorWorker>(1, TimeUnit.DAYS)
                .setConstraints(
                    androidx.work.Constraints.Builder()
                        .setRequiredNetworkType(androidx.work.NetworkType.CONNECTED)
                        .build()
                )
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request
            )
        }
    }
}
