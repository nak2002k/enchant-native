package org.enchant.core.jobmanager

interface JobStorage {
    fun init()
    fun insertJobs(fullSpecs: List<FullSpec>)
    fun getJobSpec(id: String): JobSpec?
    fun getNextEligibleJob(currentTime: Long, filter: (MinimalJobSpec) -> Boolean): JobSpec?
    fun getEligibleJobCount(currentTime: Long): Int
    fun markJobAsRunning(id: String, currentTime: Long)
    fun updateJobAfterRetry(
        id: String,
        currentTime: Long,
        runAttempt: Int,
        nextBackoffInterval: Long,
        serializedData: ByteArray?
    )
    fun updateAllJobsToBePending()
    fun updateJobInputData(jobId: String, inputData: ByteArray)
    fun deleteJob(id: String)
    fun deleteJobs(ids: List<String>)
    fun deleteAll()
    fun getConstraintSpecs(jobId: String): List<ConstraintSpec>
    fun getDependencySpecsThatDependOnJob(jobId: String): List<DependencySpec>
}
