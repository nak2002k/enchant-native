package org.enchant.core.jobmanager.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [JobSpecEntity::class, ConstraintSpecEntity::class, DependencySpecEntity::class],
    version = 1,
    exportSchema = false
)
abstract class JobDatabase : RoomDatabase() {
    abstract fun jobDao(): JobDao
    abstract fun constraintDao(): ConstraintDao
    abstract fun dependencyDao(): DependencyDao

    companion object {
        @Volatile
        private var INSTANCE: JobDatabase? = null

        fun getInstance(context: Context): JobDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    JobDatabase::class.java,
                    "jobmanager_database"
                ).build().also { INSTANCE = it }
            }
        }

        fun resetInstance() {
            INSTANCE = null
        }

        fun getInMemoryInstance(context: Context): JobDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.inMemoryDatabaseBuilder(
                    context.applicationContext,
                    JobDatabase::class.java
                ).build().also { INSTANCE = it }
            }
        }
    }
}
