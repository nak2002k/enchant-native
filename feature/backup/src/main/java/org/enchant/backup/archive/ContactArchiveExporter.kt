package org.enchant.backup.archive

import android.content.ContentValues
import org.enchant.core.database.DatabasePool

data class ContactArchive(
    val userId: String,
    val username: String? = null,
    val displayName: String? = null,
    val phoneNumber: String? = null,
    val customName: String? = null
)

class ContactArchiveExporter(private val pool: DatabasePool) {

    suspend fun exportContacts(): List<ContactArchive> {
        val db = pool.readWith { db -> db }
        val contacts = mutableListOf<ContactArchive>()
        val cursor = db.rawQuery(
            "SELECT recipient_id, username, display_name, phone_number FROM recipients",
            null
        )
        while (cursor.moveToNext()) {
            contacts.add(
                ContactArchive(
                    userId = cursor.getString(0) ?: "",
                    username = cursor.getString(1),
                    displayName = cursor.getString(2),
                    phoneNumber = cursor.getString(3),
                    customName = null
                )
            )
        }
        cursor.close()
        return contacts
    }

    suspend fun importContacts(archives: List<ContactArchive>) {
        val db = pool.write { db -> db }
        val existingIds = mutableSetOf<String>()
        val cursor = db.rawQuery("SELECT recipient_id FROM recipients", null)
        while (cursor.moveToNext()) {
            existingIds.add(cursor.getString(0))
        }
        cursor.close()

        db.beginTransaction()
        try {
            archives.forEach { contact ->
                if (contact.userId !in existingIds) {
                    val values = ContentValues().apply {
                        put("recipient_id", contact.userId)
                        put("username", contact.username)
                        put("display_name", contact.displayName)
                        put("phone_number", contact.phoneNumber)
                        put("custom_name", contact.customName)
                    }
                    db.insert("recipients", null, values)
                }
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }
}
