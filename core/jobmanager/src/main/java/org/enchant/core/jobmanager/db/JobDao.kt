package org.enchant.core.jobmanager.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface JobDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertJobs(jobs: List<JobSpecEntity>)

    @Query("SELECT * FROM jobs WHERE id = :id")
    suspend fun getJobSpec(id: String): JobSpecEntity?

    @Query("SELECT * FROM jobs WHERE isRunning = 0 AND (lifespan <= 0 OR (:currentTime - createTime) < lifespan) ORDER BY globalPriority DESC, createTime ASC, id ASC")
    suspend fun getEligibleJobs(currentTime: Long): List<JobSpecEntity>

    @Query("SELECT COUNT(*) FROM jobs WHERE isRunning = 0 AND (lifespan <= 0 OR (:currentTime - createTime) < lifespan)")
    suspend fun getEligibleJobCount(currentTime: Long): Int

    @Query("UPDATE jobs SET isRunning = 1, lastRunAttemptTime = :currentTime WHERE id = :id")
    suspend fun markJobAsRunning(id: String, currentTime: Long)

    @Query("UPDATE jobs SET isRunning = 0, runAttempt = :runAttempt, nextBackoffInterval = :nextBackoffInterval, lastRunAttemptTime = :currentTime, serializedData = :serializedData WHERE id = :id")
    suspend fun updateJobAfterRetry(
        id: String,
        currentTime: Long,
        runAttempt: Int,
        nextBackoffInterval: Long,
        serializedData: ByteArray?
    )

    @Query("UPDATE jobs SET isRunning = 0")
    suspend fun updateAllJobsToBePending()

    @Query("UPDATE jobs SET serializedInputData = :inputData WHERE id = :jobId")
    suspend fun updateJobInputData(jobId: String, inputData: ByteArray)

    @Query("DELETE FROM jobs WHERE id = :id")
    suspend fun deleteJob(id: String)

    @Query("DELETE FROM jobs WHERE id IN (:ids)")
    suspend fun deleteJobs(ids: List<String>)

    @Query("DELETE FROM jobs")
    suspend fun deleteAll()

    @Query("SELECT * FROM jobs")
    suspend fun getAllJobs(): List<JobSpecEntity>
}
