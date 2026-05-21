package org.enchant.core.jobmanager

import kotlinx.coroutines.delay

class DisappearingMessagesWorker(
    id: String = java.util.UUID.randomUUID().toString(),
    parameters: JobParameters = JobParameters.Builder(id).build()
) : Job(id, parameters) {
    override val factoryKey: String = "DisappearingMessagesWorker"

    override suspend fun run(): JobResult {
        delay(100)
        return success()
    }

    override fun onFailure() {}

    override fun serialize(): ByteArray? = null

    object Factory : Job.Factory<DisappearingMessagesWorker> {
        override fun create(id: String, serializedData: ByteArray?): DisappearingMessagesWorker {
            return DisappearingMessagesWorker(id)
        }
    }
}
