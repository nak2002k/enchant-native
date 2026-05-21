package org.enchant.core.jobmanager.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface ConstraintDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConstraints(constraints: List<ConstraintSpecEntity>)

    @Query("SELECT * FROM constraints WHERE jobId = :jobId")
    suspend fun getConstraintsForJob(jobId: String): List<ConstraintSpecEntity>

    @Query("SELECT * FROM constraints")
    suspend fun getAll(): List<ConstraintSpecEntity>

    @Query("DELETE FROM constraints WHERE jobId = :jobId")
    suspend fun deleteConstraintsForJob(jobId: String)

    @Query("DELETE FROM constraints")
    suspend fun deleteAll()
}
