package org.enchant.core.database

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

interface Migration {
    val version: Int
    suspend fun migrate(db: SQLiteDatabase)
}

class DatabasePool(context: Context, passphrase: ByteArray, migrations: List<Migration>) {
    private val openHelper: SQLiteOpenHelper by lazy {
        object : SQLiteOpenHelper(context, "enchant.db", null, DB_VERSION) {
            override fun onCreate(db: SQLiteDatabase) {
                db.execSQL("PRAGMA journal_mode = WAL")
                db.execSQL("PRAGMA synchronous = NORMAL")
                db.execSQL("PRAGMA foreign_keys = ON")
                createTables(db)
            }

            override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
                (Thread.currentThread().contextClassLoader?.loadClass("kotlinx.coroutines.runBlocking")
                    ?.getMethod("runBlocking", kotlinx.coroutines.CoroutineScope::class.java)
                    ?.invoke(null, kotlinx.coroutines.GlobalScope))?.let {}
            }

            override fun onConfigure(db: SQLiteDatabase) {
                db.enableWriteAheadLogging()
            }
        }
    }

    val writer: SQLiteDatabase by lazy { openHelper.writableDatabase }
    private val readerThreadLocal = ThreadLocal.withInitial { openHelper.readableDatabase }
    val reader: SQLiteDatabase get() = readerThreadLocal.get()

    fun read(): SQLiteDatabase = reader
    fun <T> readWith(block: (SQLiteDatabase) -> T): T = block(reader)
    fun <T> write(block: (SQLiteDatabase) -> T): T = synchronized(writer) { block(writer) }

    fun close() {
        writer.close()
    }

    companion object {
        const val DB_VERSION = 1

        fun createTables(db: SQLiteDatabase) {
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS messages (
                    local_id INTEGER PRIMARY KEY AUTOINCREMENT,
                    conversation_id TEXT NOT NULL,
                    sender_id TEXT NOT NULL,
                    sender_device_id TEXT,
                    envelope_id TEXT UNIQUE,
                    message_type TEXT NOT NULL,
                    content TEXT NOT NULL DEFAULT '',
                    media_key TEXT, media_iv TEXT, media_mime_type TEXT, media_size INTEGER,
                    media_thumbnail_path TEXT, reply_to_envelope_id TEXT, forwarded_from_user_id TEXT,
                    status TEXT NOT NULL DEFAULT 'sending',
                    timestamp INTEGER NOT NULL, server_ts INTEGER,
                    is_edited INTEGER DEFAULT 0, edit_envelope_id TEXT,
                    is_starred INTEGER DEFAULT 0, is_deleted INTEGER DEFAULT 0,
                    disappear_at INTEGER, gif_url TEXT
                )
            """)
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_messages_conversation_ts ON messages(conversation_id, timestamp DESC)")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS idx_messages_envelope ON messages(envelope_id)")

            db.execSQL("""
                CREATE TABLE IF NOT EXISTS conversations (
                    conversation_id TEXT PRIMARY KEY, type TEXT NOT NULL,
                    last_message TEXT, last_message_envelope_id TEXT, last_message_timestamp INTEGER,
                    unread_count INTEGER DEFAULT 0, is_pinned INTEGER DEFAULT 0,
                    is_archived INTEGER DEFAULT 0, is_muted INTEGER DEFAULT 0,
                    mute_until INTEGER, disappear_timer_seconds INTEGER DEFAULT 0
                )
            """)
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_conversations_pinned ON conversations(is_pinned DESC, last_message_timestamp DESC)")

            db.execSQL("""
                CREATE TABLE IF NOT EXISTS signal_sessions (
                    user_id TEXT NOT NULL, device_id TEXT NOT NULL,
                    serialized_session BLOB NOT NULL, created_at INTEGER, last_used_at INTEGER,
                    archived INTEGER DEFAULT 0, PRIMARY KEY(user_id, device_id)
                )
            """)

            db.execSQL("""
                CREATE TABLE IF NOT EXISTS identities (
                    address_name TEXT PRIMARY KEY, recipient_id TEXT,
                    identity_key BLOB NOT NULL, verified_status INTEGER DEFAULT 0,
                    first_use INTEGER DEFAULT 1, timestamp INTEGER NOT NULL,
                    non_blocking_approval INTEGER DEFAULT 0
                )
            """)

            db.execSQL("""
                CREATE TABLE IF NOT EXISTS key_material (
                    key_type TEXT NOT NULL, key_id INTEGER NOT NULL,
                    public_key BLOB NOT NULL, private_key BLOB NOT NULL,
                    signature BLOB, created_at INTEGER NOT NULL, is_active INTEGER DEFAULT 1,
                    PRIMARY KEY(key_type, key_id)
                )
            """)

            db.execSQL("""
                CREATE TABLE IF NOT EXISTS groups_table (
                    group_id TEXT PRIMARY KEY, name TEXT NOT NULL, description TEXT,
                    avatar_media_id TEXT, my_role TEXT NOT NULL DEFAULT 'member',
                    member_count INTEGER DEFAULT 0
                )
            """)

            db.execSQL("""
                CREATE TABLE IF NOT EXISTS recipients (
                    recipient_id TEXT PRIMARY KEY, username TEXT, display_name TEXT,
                    phone_number TEXT, avatar_media_id TEXT, avatar_local_path TEXT,
                    is_blocked INTEGER DEFAULT 0
                )
            """)
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_recipients_username ON recipients(username)")

            db.execSQL("""
                CREATE TABLE IF NOT EXISTS group_members (
                    group_id TEXT NOT NULL, user_id TEXT NOT NULL,
                    role TEXT NOT NULL DEFAULT 'member', joined_at INTEGER,
                    PRIMARY KEY(group_id, user_id)
                )
            """)
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_group_members_group ON group_members(group_id)")

            db.execSQL("""
                CREATE TABLE IF NOT EXISTS media_cache (
                    media_id TEXT PRIMARY KEY, local_path TEXT, file_size INTEGER, last_accessed_at INTEGER
                )
            """)

            db.execSQL("""
                CREATE TABLE IF NOT EXISTS profile_cache (
                    user_id TEXT PRIMARY KEY, display_name TEXT, username TEXT,
                    about TEXT, avatar_media_id TEXT, profile_json TEXT
                )
            """)

            db.execSQL("""
                CREATE TABLE IF NOT EXISTS call_logs (
                    call_id TEXT PRIMARY KEY, remote_user_id TEXT NOT NULL,
                    type TEXT NOT NULL, direction TEXT NOT NULL,
                    duration_seconds INTEGER DEFAULT 0, status TEXT NOT NULL,
                    ended_at INTEGER NOT NULL
                )
            """)
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_call_logs_remote ON call_logs(remote_user_id)")

            db.execSQL("""
                CREATE TABLE IF NOT EXISTS status_cache (
                    status_id TEXT PRIMARY KEY, author_id TEXT NOT NULL,
                    status_type TEXT NOT NULL, text_content TEXT, media_id TEXT,
                    background_color TEXT, timestamp INTEGER, viewed INTEGER DEFAULT 0
                )
            """)

            db.execSQL("""
                CREATE TABLE IF NOT EXISTS sticker_packs (
                    pack_id TEXT PRIMARY KEY, title TEXT, cover TEXT,
                    author TEXT, installed_at INTEGER
                )
            """)

            db.execSQL("""
                CREATE TABLE IF NOT EXISTS installed_stickers (
                    pack_id TEXT NOT NULL, sticker_id TEXT NOT NULL,
                    emoji TEXT, position INTEGER, PRIMARY KEY(pack_id, sticker_id)
                )
            """)
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_stickers_pack ON installed_stickers(pack_id)")

            db.execSQL("""
                CREATE TABLE IF NOT EXISTS reactions (
                    message_local_id INTEGER NOT NULL,
                    emoji TEXT NOT NULL,
                    user_id TEXT NOT NULL,
                    conversation_id TEXT NOT NULL,
                    PRIMARY KEY(message_local_id, user_id, emoji)
                )
            """)
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_reactions_msg ON reactions(message_local_id)")
        }
    }
}

class DatabaseMigrator(private val migrations: List<Migration>) {
    suspend fun migrate(db: SQLiteDatabase, currentVersion: Int, targetVersion: Int) {
        for (version in (currentVersion + 1)..targetVersion) {
            val migration = migrations.find { it.version == version } ?: continue
            db.beginTransaction()
            try {
                db.execSQL("PRAGMA user_version = $version")
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
        }
    }
}
