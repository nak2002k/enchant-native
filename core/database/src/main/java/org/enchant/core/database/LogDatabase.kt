package org.enchant.core.database

import org.enchant.core.database.dao.CrashEntity
import org.enchant.core.database.dao.CrashLogDao

object LogDatabase {
    private val database: DatabasePool
        get() = DatabasePool.instance ?: throw IllegalStateException("DatabasePool not initialized")

    val crashes: CrashLogDao by lazy { CrashLogDao(database) }

    fun saveCrash(timestamp: Long, exceptionName: String, message: String?, stackTrace: String) {
        crashes.insert(timestamp, exceptionName, message, stackTrace, isFatal = true)
    }

    fun getAllCrashes(limit: Int = 100): List<CrashEntity> = crashes.getAll(limit)

    fun getUnreportedCrashes(limit: Int = 50): List<CrashEntity> = crashes.getUnreported(limit)

    fun markCrashesReported(ids: List<Long>) = crashes.markReported(ids)

    fun deleteCrash(id: Long) = crashes.delete(id)

    fun deleteCrashesOlderThan(timestamp: Long) = crashes.deleteOlderThan(timestamp)

    fun getCrashCount(): Int = crashes.getCount()

    fun getUnreportedCrashCount(): Int = crashes.getUnreportedCount()
}