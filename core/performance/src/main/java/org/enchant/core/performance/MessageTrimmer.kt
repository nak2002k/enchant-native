package org.enchant.core.performance

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.enchant.core.database.DatabasePool
import java.util.concurrent.TimeUnit

const val TRIMMER_WORK_NAME = "message_trimmer"
private const val TAG = "MessageTrimmer"

class MessageTrimmerWorker(
    context: Context,
    params: WorkerParameters
) : Worker(context, params) {
    override fun doWork(): Result {
        return try {
            val retentionDays = inputData.getLong("retentionDays", 365L)
            val pool = DatabasePool.instance ?: return Result.failure()
            pool.write { db ->
                val cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(retentionDays)
                db.execSQL(
                    "DELETE FROM messages WHERE timestamp < ? AND is_starred = 0 AND disappear_at IS NULL",
                    arrayOf(cutoff.toString())
                )
            }
            Result.success()
        } catch (e: Exception) {
            Log.w(TAG, "Trim work failed: ${e.message}")
            Result.retry()
        }
    }
}

object MessageTrimmer {
    fun scheduleTrimming(context: Context, retentionDays: Long = 365) {
        val constraints = Constraints.Builder()
            .setRequiresBatteryNotLow(true)
            .build()
        val request = PeriodicWorkRequestBuilder<MessageTrimmerWorker>(1, TimeUnit.DAYS)
            .setConstraints(constraints)
            .setInputData(
                androidx.work.Data.Builder()
                    .putLong("retentionDays", retentionDays)
                    .build()
            )
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            TRIMMER_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    suspend fun trimOldMessages(retentionDays: Long) {
        withContext(Dispatchers.IO) {
            try {
                val pool = DatabasePool.instance ?: return@withContext
                pool.write { db ->
                    val cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(retentionDays)
                    db.execSQL(
                        "DELETE FROM messages WHERE timestamp < ? AND is_starred = 0 AND disappear_at IS NULL",
                        arrayOf(cutoff.toString())
                    )
                }
            } catch (e: Exception) {
                Log.w(TAG, "trimOldMessages failed: ${e.message}")
            }
        }
    }
}
