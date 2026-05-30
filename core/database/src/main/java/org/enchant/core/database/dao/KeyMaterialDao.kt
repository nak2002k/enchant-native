package org.enchant.core.database.dao

import org.enchant.core.database.DatabasePool

class KeyMaterialDao(private val pool: DatabasePool) {
    suspend fun store(keyType: String, keyId: Int, publicKey: ByteArray, privateKey: ByteArray, signature: ByteArray? = null, createdAt: Long = System.currentTimeMillis()) = pool.write { db ->
        val stmt = db.compileStatement("""
            INSERT OR REPLACE INTO key_material (key_type, key_id, public_key, private_key, signature, created_at)
            VALUES (?, ?, ?, ?, ?, ?)
        """)
        stmt.bindString(1, keyType)
        stmt.bindLong(2, keyId.toLong())
        stmt.bindBlob(3, publicKey)
        stmt.bindBlob(4, privateKey)
        signature?.let { stmt.bindBlob(5, it) } ?: stmt.bindNull(5)
        stmt.bindLong(6, createdAt)
        stmt.executeInsert()
    }

    suspend fun load(keyType: String, keyId: Int): Triple<ByteArray, ByteArray, ByteArray?>? = pool.readWith { db ->
        db.rawQuery("SELECT public_key, private_key, signature FROM key_material WHERE key_type = ? AND key_id = ?", arrayOf(keyType, keyId.toString()))
            .use { if (it.moveToFirst()) Triple(it.getBlob(0), it.getBlob(1), it.getBlob(2)) else null }
    }

    suspend fun getActive(keyType: String): Map<Int, Triple<ByteArray, ByteArray, ByteArray?>> = pool.readWith { db ->
        val result = mutableMapOf<Int, Triple<ByteArray, ByteArray, ByteArray?>>()
        db.rawQuery("SELECT key_id, public_key, private_key, signature FROM key_material WHERE key_type = ? AND is_active = 1", arrayOf(keyType))
            .use { while (it.moveToNext()) { result[it.getInt(0)] = Triple(it.getBlob(1), it.getBlob(2), it.getBlob(3)) } }
        result
    }

    suspend fun delete(keyType: String, keyId: Int) = pool.write { db ->
        db.execSQL("DELETE FROM key_material WHERE key_type = ? AND key_id = ?", arrayOf(keyType, keyId.toString()))
    }

    suspend fun deleteAll(keyType: String) = pool.write { db ->
        db.execSQL("DELETE FROM key_material WHERE key_type = ?", arrayOf(keyType))
    }
}
