package org.enchant.core.database

import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import net.sqlcipher.database.SQLiteDatabase
import net.sqlcipher.database.SQLiteOpenHelper
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("DatabasePool — Full Coverage")
class DatabasePoolTest {

    @BeforeEach
    fun setUp() {
        mockkStatic(android.util.Log::class)
        every { android.util.Log.w(any(), any<String>()) } returns 0
    }

    @AfterEach
    fun tearDown() {
        unmockkStatic(android.util.Log::class)
    }

    @Nested @DisplayName("Constants")
    inner class ConstantsTest {
        @Test @DisplayName("DB_VERSION is 4")
        fun `db version`() {
            assertEquals(4, DatabasePool.DB_VERSION)
        }
    }

    @Nested @DisplayName("Instance")
    inner class InstanceTest {
        @Test @DisplayName("instance is null by default")
        fun `instance defaults null`() {
            assertNull(DatabasePool.instance)
        }

        @Test @DisplayName("instance can be set and retrieved")
        fun `instance set and get`() {
            val mockPool = mockk<DatabasePool>(relaxed = true)
            DatabasePool.instance = mockPool
            assertSame(mockPool, DatabasePool.instance)
            DatabasePool.instance = null
        }
    }

    @Nested @DisplayName("Create Tables")
    inner class CreateTablesTest {
        @Test @DisplayName("createTables executes all CREATE TABLE statements")
        fun `create tables all statements`() {
            val db = mockk<SQLiteDatabase>(relaxed = true)
            DatabasePool.createTables(db)

            verify(atLeast = 1) {
                db.execSQL(match { it.contains("CREATE TABLE IF NOT EXISTS messages") })
                db.execSQL(match { it.contains("CREATE TABLE IF NOT EXISTS conversations") })
                db.execSQL(match { it.contains("CREATE TABLE IF NOT EXISTS enchant_sessions") })
                db.execSQL(match { it.contains("CREATE TABLE IF NOT EXISTS identities") })
                db.execSQL(match { it.contains("CREATE TABLE IF NOT EXISTS key_material") })
                db.execSQL(match { it.contains("CREATE TABLE IF NOT EXISTS groups_table") })
                db.execSQL(match { it.contains("CREATE TABLE IF NOT EXISTS recipients") })
                db.execSQL(match { it.contains("CREATE TABLE IF NOT EXISTS group_members") })
                db.execSQL(match { it.contains("CREATE TABLE IF NOT EXISTS media_cache") })
                db.execSQL(match { it.contains("CREATE TABLE IF NOT EXISTS profile_cache") })
                db.execSQL(match { it.contains("CREATE TABLE IF NOT EXISTS call_logs") })
                db.execSQL(match { it.contains("CREATE TABLE IF NOT EXISTS status_cache") })
                db.execSQL(match { it.contains("CREATE TABLE IF NOT EXISTS sticker_packs") })
                db.execSQL(match { it.contains("CREATE TABLE IF NOT EXISTS installed_stickers") })
                db.execSQL(match { it.contains("CREATE TABLE IF NOT EXISTS reactions") })
                db.execSQL(match { it.contains("CREATE TABLE IF NOT EXISTS message_mentions") })
            }
        }

        @Test @DisplayName("createTables creates FTS virtual table")
        fun `create tables fts`() {
            val db = mockk<SQLiteDatabase>(relaxed = true)
            DatabasePool.createTables(db)

            verify(atLeast = 1) {
                db.execSQL(match { it.contains("CREATE VIRTUAL TABLE IF NOT EXISTS messages_fts") })
            }
        }

        @Test @DisplayName("createTables creates triggers for FTS sync")
        fun `create tables triggers`() {
            val db = mockk<SQLiteDatabase>(relaxed = true)
            DatabasePool.createTables(db)

            verify(atLeast = 1) {
                db.execSQL(match { it.contains("CREATE TRIGGER IF NOT EXISTS messages_ai") })
                db.execSQL(match { it.contains("CREATE TRIGGER IF NOT EXISTS messages_ad") })
                db.execSQL(match { it.contains("CREATE TRIGGER IF NOT EXISTS messages_au") })
            }
        }

        @Test @DisplayName("createTables creates indexes")
        fun `create tables indexes`() {
            val db = mockk<SQLiteDatabase>(relaxed = true)
            DatabasePool.createTables(db)

            verify(atLeast = 1) {
                db.execSQL(match { it.contains("CREATE INDEX IF NOT EXISTS idx_messages_conversation_ts") })
                db.execSQL(match { it.contains("CREATE UNIQUE INDEX IF NOT EXISTS idx_messages_envelope") })
                db.execSQL(match { it.contains("CREATE INDEX IF NOT EXISTS idx_conversations_pinned") })
                db.execSQL(match { it.contains("CREATE INDEX IF NOT EXISTS idx_recipients_username") })
                db.execSQL(match { it.contains("CREATE INDEX IF NOT EXISTS idx_group_members_group") })
                db.execSQL(match { it.contains("CREATE INDEX IF NOT EXISTS idx_call_logs_remote") })
                db.execSQL(match { it.contains("CREATE INDEX IF NOT EXISTS idx_stickers_pack") })
                db.execSQL(match { it.contains("CREATE INDEX IF NOT EXISTS idx_reactions_msg") })
                db.execSQL(match { it.contains("CREATE INDEX IF NOT EXISTS idx_mentions_msg") })
            }
        }
    }
}

@DisplayName("DatabaseMigrator — Full Coverage")
class DatabaseMigratorTest {

    @Nested @DisplayName("Migration")
    inner class MigrationTest {
        @Test @DisplayName("migrate applies migrations in order")
        fun `migrate applies in order`() {
            val db = mockk<SQLiteDatabase>(relaxed = true)
            var appliedVersions = mutableListOf<Int>()
            val migrations = listOf(
                object : Migration {
                    override val version = 2
                    override suspend fun migrate(db: SQLiteDatabase) {
                        appliedVersions.add(2)
                    }
                },
                object : Migration {
                    override val version = 3
                    override suspend fun migrate(db: SQLiteDatabase) {
                        appliedVersions.add(3)
                    }
                }
            )
            val migrator = DatabaseMigrator(migrations)
            // Migration runs synchronously in test context
            kotlinx.coroutines.test.runTest {
                migrator.migrate(db, 1, 3)
            }
            assertEquals(listOf(2, 3), appliedVersions)
        }

        @Test @DisplayName("migrate skips versions without migrations")
        fun `migrate skips missing versions`() {
            val db = mockk<SQLiteDatabase>(relaxed = true)
            var appliedVersions = mutableListOf<Int>()
            val migrations = listOf(
                object : Migration {
                    override val version = 4
                    override suspend fun migrate(db: SQLiteDatabase) {
                        appliedVersions.add(4)
                    }
                }
            )
            val migrator = DatabaseMigrator(migrations)
            kotlinx.coroutines.test.runTest {
                migrator.migrate(db, 1, 5)
            }
            assertEquals(listOf(4), appliedVersions)
        }

        @Test @DisplayName("migrate does nothing when current equals target")
        fun `migrate no op when same version`() {
            val db = mockk<SQLiteDatabase>(relaxed = true)
            var appliedVersions = mutableListOf<Int>()
            val migrations = listOf(
                object : Migration {
                    override val version = 2
                    override suspend fun migrate(db: SQLiteDatabase) {
                        appliedVersions.add(2)
                    }
                }
            )
            val migrator = DatabaseMigrator(migrations)
            kotlinx.coroutines.test.runTest {
                migrator.migrate(db, 3, 3)
            }
            assertTrue(appliedVersions.isEmpty())
        }

        @Test @DisplayName("migrate does nothing when target is less than current")
        fun `migrate no op when downgrade`() {
            val db = mockk<SQLiteDatabase>(relaxed = true)
            var appliedVersions = mutableListOf<Int>()
            val migrations = listOf(
                object : Migration {
                    override val version = 2
                    override suspend fun migrate(db: SQLiteDatabase) {
                        appliedVersions.add(2)
                    }
                }
            )
            val migrator = DatabaseMigrator(migrations)
            kotlinx.coroutines.test.runTest {
                migrator.migrate(db, 5, 3)
            }
            assertTrue(appliedVersions.isEmpty())
        }

        @Test @DisplayName("migrate wraps each migration in a transaction")
        fun `migrate uses transactions`() {
            val db = mockk<SQLiteDatabase>(relaxed = true)
            val migrations = listOf(
                object : Migration {
                    override val version = 2
                    override suspend fun migrate(db: SQLiteDatabase) {}
                }
            )
            val migrator = DatabaseMigrator(migrations)
            kotlinx.coroutines.test.runTest {
                migrator.migrate(db, 1, 2)
            }
            verify { db.beginTransaction() }
            verify { db.setTransactionSuccessful() }
            verify { db.endTransaction() }
        }

        @Test @DisplayName("migrate sets user_version after each migration")
        fun `migrate sets user version`() {
            val db = mockk<SQLiteDatabase>(relaxed = true)
            val migrations = listOf(
                object : Migration {
                    override val version = 2
                    override suspend fun migrate(db: SQLiteDatabase) {}
                },
                object : Migration {
                    override val version = 3
                    override suspend fun migrate(db: SQLiteDatabase) {}
                }
            )
            val migrator = DatabaseMigrator(migrations)
            kotlinx.coroutines.test.runTest {
                migrator.migrate(db, 1, 3)
            }
            verify { db.execSQL("PRAGMA user_version = 2") }
            verify { db.execSQL("PRAGMA user_version = 3") }
        }
    }
}
