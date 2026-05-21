package org.enchant.core.database

import kotlinx.coroutines.runBlocking
import org.enchant.core.database.dao.CrashEntity
import org.enchant.core.database.dao.CrashLogDao

object LogDatabase {
    private val database: DatabasePool
        get() = DatabasePool.instance ?: throw IllegalStateException("DatabasePool not initialized")

    val crashes: CrashLogDao by lazy { CrashLogDao(database) }

    fun saveCrash(timestamp: Long, exceptionName: String, message: String?, stackTrace: String) {
        runBlocking { crashes.insert(timestamp, exceptionName, message, stackTrace, isFatal = true) }
    }

    fun getAllCrashes(limit: Int = 100): List<CrashEntity> = runBlocking {
        crashes.getAll(limit)
    }

    fun getUnreportedCrashes(limit: Int = 50): List<CrashEntity> = runBlocking {
        crashes.getUnreported(limit)
    }

    fun markCrashesReported(ids: List<Long>) {
        runBlocking { crashes.markReported(ids) }
    }

    fun deleteCrash(id: Long) {
        runBlocking { crashes.delete(id) }
    }

    fun deleteCrashesOlderThan(timestamp: Long) {
        runBlocking { crashes.deleteOlderThan(timestamp) }
    }

    fun getCrashCount(): Int = runBlocking {
        crashes.getCount()
    }

    fun getUnreportedCrashCount(): Int = runBlocking {
        crashes.getUnreportedCount()
    }
}