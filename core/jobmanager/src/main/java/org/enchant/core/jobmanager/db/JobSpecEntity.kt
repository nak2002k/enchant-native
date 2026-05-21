package org.enchant.core.jobmanager.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import androidx.room.TypeConverters

object ByteArrayConverter {
    @TypeConverter
    fun fromByteArray(value: ByteArray?): String? {
        return value?.let { bytes ->
            bytes.joinToString("") { "%02x".format(it) }
        }
    }

    @TypeConverter
    fun toByteArray(value: String?): ByteArray? {
        return value?.let { hex ->
            hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        }
    }
}

@Entity(tableName = "jobs")
@TypeConverters(ByteArrayConverter::class)
data class JobSpecEntity(
    @PrimaryKey val id: String,
    val factoryKey: String,
    val queueKey: String?,
    val createTime: Long,
    val lastRunAttemptTime: Long,
    val nextBackoffInterval: Long,
    val runAttempt: Int,
    val maxAttempts: Int,
    val lifespan: Long,
    val serializedData: ByteArray?,
    val serializedInputData: ByteArray?,
    val isRunning: Boolean,
    val isMemoryOnly: Boolean,
    val globalPriority: Int,
    val queuePriority: Int,
    val initialDelay: Long
)

@Entity(tableName = "constraints", primaryKeys = ["jobId", "factoryKey"])
data class ConstraintSpecEntity(
    val jobId: String,
    val factoryKey: String,
    val isMemoryOnly: Boolean
)

@Entity(tableName = "dependencies", primaryKeys = ["jobId", "dependsOnJobId"])
data class DependencySpecEntity(
    val jobId: String,
    val dependsOnJobId: String,
    val isMemoryOnly: Boolean
)
