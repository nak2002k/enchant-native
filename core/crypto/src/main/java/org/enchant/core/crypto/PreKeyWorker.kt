package org.enchant.core.crypto

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/**
 * Android WorkManager periodic worker for prekey maintenance.
 *
 * Runs every 30 days to:
 * 1. Top up one-time prekeys if count < 10
 * 2. Clean stale signed prekeys (older than 30 days)
 * 3. Rotate signed prekey if needed (25+ days since last rotation)
 *
 * Requires network connectivity to communicate with the IKS server.
 */
class PreKeyWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        return try {
            KeyManager.topUpOpks()
            KeyManager.cleanSignedPreKeys()
            if (KeyManager.needsKeyRotation()) {
                val rotationResult = KeyManager.rotateSignedPreKey()
                if (rotationResult.isFailure) {
                    return Result.retry()
                }
            }
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    companion object {
        private const val WORK_NAME = "prekey_rotation"

        /** Schedule the periodic prekey rotation worker. */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<PreKeyWorker>(30, TimeUnit.DAYS)
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
