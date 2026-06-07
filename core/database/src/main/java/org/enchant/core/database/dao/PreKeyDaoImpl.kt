package org.enchant.core.database.dao

import org.enchant.core.database.DatabasePool

class PreKeyDaoImpl(private val pool: DatabasePool) : PreKeyDao {

    override suspend fun storeSignedPreKey(record: SignedPreKeyRecord) {
        pool.write { db ->
            val stmt = db.compileStatement("""
                INSERT OR REPLACE INTO key_material (key_type, key_id, public_key, private_key, signature, created_at, is_active)
                VALUES ('SPK', ?, ?, ?, ?, ?, 1)
            """)
            stmt.bindLong(1, record.id.toLong())
            stmt.bindBlob(2, record.publicKey)
            stmt.bindBlob(3, record.privateKey)
            stmt.bindBlob(4, record.signature)
            stmt.bindLong(5, record.timestamp)
            stmt.executeInsert()
        }
    }

    override suspend fun loadSignedPreKeys(): List<SignedPreKeyRecord> = pool.readWith { db ->
        val list = mutableListOf<SignedPreKeyRecord>()
        db.rawQuery("""
            SELECT key_id, public_key, private_key, signature, created_at
            FROM key_material WHERE key_type = 'SPK' AND is_active = 1
            ORDER BY created_at ASC
        """, null).use { cursor ->
            while (cursor.moveToNext()) {
                list.add(SignedPreKeyRecord(
                    id = cursor.getInt(0),
                    publicKey = cursor.getBlob(1),
                    privateKey = cursor.getBlob(2),
                    signature = cursor.getBlob(3),
                    timestamp = cursor.getLong(4)
                ))
            }
        }
        list
    }

    override suspend fun deleteSignedPreKey(id: Int) {
        pool.write { db ->
            db.execSQL("DELETE FROM key_material WHERE key_type = 'SPK' AND key_id = ?", arrayOf(id.toString()))
        }
    }

    override suspend fun storeOneTimePreKeys(records: List<OneTimePreKeyRecord>) {
        pool.write { db ->
            db.beginTransaction()
            try {
                records.forEach { record ->
                    val keyType = if (record.isLastResort) "OPK_LR" else "OPK"
                    val stmt = db.compileStatement("""
                        INSERT OR REPLACE INTO key_material (key_type, key_id, public_key, private_key, signature, created_at, is_active)
                        VALUES (?, ?, ?, ?, NULL, ?, 1)
                    """)
                    stmt.bindString(1, keyType)
                    stmt.bindLong(2, record.id.toLong())
                    stmt.bindBlob(3, record.publicKey)
                    stmt.bindBlob(4, record.privateKey)
                    stmt.bindLong(5, record.timestamp)
                    stmt.executeInsert()
                }
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
        }
    }

    override suspend fun loadOneTimePreKeys(): List<OneTimePreKeyRecord> = pool.readWith { db ->
        val list = mutableListOf<OneTimePreKeyRecord>()
        db.rawQuery("""
            SELECT key_id, public_key, private_key, created_at, key_type
            FROM key_material WHERE key_type IN ('OPK', 'OPK_LR') AND is_active = 1
            ORDER BY created_at ASC
        """, null).use { cursor ->
            while (cursor.moveToNext()) {
                list.add(OneTimePreKeyRecord(
                    id = cursor.getInt(0),
                    publicKey = cursor.getBlob(1),
                    privateKey = cursor.getBlob(2),
                    timestamp = cursor.getLong(3),
                    isLastResort = cursor.getString(4) == "OPK_LR"
                ))
            }
        }
        list
    }

    override suspend fun deleteOneTimePreKey(id: Int) {
        pool.write { db ->
            db.execSQL("DELETE FROM key_material WHERE key_type IN ('OPK', 'OPK_LR') AND key_id = ?", arrayOf(id.toString()))
        }
    }
}
