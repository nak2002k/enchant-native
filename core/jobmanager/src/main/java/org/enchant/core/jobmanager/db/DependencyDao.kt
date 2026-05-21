package org.enchant.core.jobmanager.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface DependencyDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDependencies(dependencies: List<DependencySpecEntity>)

    @Query("SELECT * FROM dependencies WHERE jobId = :jobId")
    suspend fun getDependenciesForJob(jobId: String): List<DependencySpecEntity>

    @Query("SELECT * FROM dependencies WHERE dependsOnJobId = :jobId")
    suspend fun getDependentsOfJob(jobId: String): List<DependencySpecEntity>

    @Query("SELECT * FROM dependencies")
    suspend fun getAll(): List<DependencySpecEntity>

    @Query("DELETE FROM dependencies WHERE jobId = :jobId")
    suspend fun deleteDependenciesForJob(jobId: String)

    @Query("DELETE FROM dependencies WHERE dependsOnJobId = :jobId")
    suspend fun deleteDependentsOfJob(jobId: String)

    @Query("DELETE FROM dependencies")
    suspend fun deleteAll()
}
