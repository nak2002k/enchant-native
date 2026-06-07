package org.enchant.core.jobmanager

import android.util.Log

class DisappearingMessagesWorker(
    id: String = java.util.UUID.randomUUID().toString(),
    parameters: JobParameters = JobParameters.Builder(id).build(),
    private val onRun: (suspend () -> Unit)? = null
) : Job(id, parameters) {
    override val factoryKey: String = "DisappearingMessagesWorker"

    override suspend fun run(): JobResult {
        return try {
            onRun?.invoke()
            success()
        } catch (e: Exception) {
            Log.w("DisappearingMessagesWorker", "Failed: ${e.message}")
            retry(60_000L)
        }
    }

    override fun onFailure() {}

    override fun serialize(): ByteArray? = null

    object Factory : Job.Factory<DisappearingMessagesWorker> {
        override fun create(id: String, serializedData: ByteArray?): DisappearingMessagesWorker {
            return DisappearingMessagesWorker(id)
        }
    }
}
