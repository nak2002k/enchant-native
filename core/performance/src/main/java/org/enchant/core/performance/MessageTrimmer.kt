package org.enchant.core.performance

import android.content.Context
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

class MessageTrimmerWorker(
    context: Context,
    params: WorkerParameters
) : Worker(context, params) {
    override fun doWork(): Result {
        val retentionDays = inputData.getLong("retentionDays", 365L)
        val pool = DatabasePool.instance ?: return Result.failure()
        pool.write { db ->
            val cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(retentionDays)
            db.execSQL(
                "DELETE FROM messages WHERE timestamp < ? AND is_starred = 0 AND disappear_at IS NULL",
                arrayOf(cutoff.toString())
            )
        }
        return Result.success()
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
            val pool = DatabasePool.instance ?: return@withContext
            pool.write { db ->
                val cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(retentionDays)
                db.execSQL(
                    "DELETE FROM messages WHERE timestamp < ? AND is_starred = 0 AND disappear_at IS NULL",
                    arrayOf(cutoff.toString())
                )
            }
        }
    }
}
