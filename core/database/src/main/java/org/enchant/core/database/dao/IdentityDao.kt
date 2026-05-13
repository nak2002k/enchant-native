package org.enchant.core.database.dao

import org.enchant.core.database.DatabasePool
import org.enchant.core.database.entity.IdentityEntity
import org.enchant.core.database.util.CursorMapper

class IdentityDao(private val pool: DatabasePool) {
    suspend fun save(entity: IdentityEntity) = pool.write { db ->
        db.execSQL("""
            INSERT OR REPLACE INTO identities
                (address_name, recipient_id, identity_key, verified_status, first_use, timestamp, non_blocking_approval)
            VALUES (?, ?, ?, ?, ?, ?, ?)
        """, arrayOf(
            entity.addressName,
            entity.recipientId,
            entity.identityKey,
            entity.verifiedStatus.toString(),
            if (entity.firstUse) "1" else "0",
            entity.timestamp.toString(),
            if (entity.nonBlockingApproval) "1" else "0"
        ))
    }

    suspend fun getByAddress(addressName: String): IdentityEntity? = pool.read { db ->
        db.query("SELECT * FROM identities WHERE address_name = ?", arrayOf(addressName))
            .use { CursorMapper.mapTo<IdentityEntity>(it) }
    }

    suspend fun setVerified(addressName: String, status: Int) = pool.write { db ->
        db.execSQL("UPDATE identities SET verified_status = ? WHERE address_name = ?", arrayOf(status.toString(), addressName))
    }

    suspend fun delete(addressName: String) = pool.write { db ->
        db.execSQL("DELETE FROM identities WHERE address_name = ?", arrayOf(addressName))
    }
}
