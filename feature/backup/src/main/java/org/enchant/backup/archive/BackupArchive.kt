package org.enchant.backup.archive

import org.enchant.core.crypto.CryptoHelper
import java.io.File
import java.io.RandomAccessFile

object BackupArchive {
    val BACKUP_MAGIC: ByteArray = "ENCHBKP".encodeToByteArray()
    const val VERSION = 1
    const val XCHACHA_NONCE_SIZE = 24
    const val XCHACHA_TAG_SIZE = 16

    suspend fun encryptSection(data: ByteArray, key: ByteArray, nonce: ByteArray): ByteArray {
        return CryptoHelper.encryptXChaCha20Poly1305(data, key, nonce)
    }

    suspend fun decryptSection(ciphertext: ByteArray, key: ByteArray, nonce: ByteArray): ByteArray {
        val fullData = ByteArray(nonce.size + ciphertext.size)
        nonce.copyInto(fullData, 0)
        ciphertext.copyInto(fullData, nonce.size)
        return CryptoHelper.decryptXChaCha20Poly1305(fullData, key)
    }

    suspend fun verifyIntegrity(file: File, backupKey: ByteArray): Boolean {
        return try {
            if (!file.exists() || file.length() < BACKUP_MAGIC.size + 4 + XCHACHA_NONCE_SIZE + XCHACHA_TAG_SIZE) return false
            RandomAccessFile(file, "r").use { raf ->
                val magic = ByteArray(BACKUP_MAGIC.size)
                raf.readFully(magic)
                if (!magic.contentEquals(BACKUP_MAGIC)) return false
                val version = raf.readInt()
                if (version != VERSION) return false
                val nonce = ByteArray(XCHACHA_NONCE_SIZE)
                raf.readFully(nonce)
                val remaining = raf.length() - raf.filePointer
                if (remaining <= XCHACHA_TAG_SIZE) return false
                val encryptedBody = ByteArray(remaining.toInt())
                raf.readFully(encryptedBody)
                val fullData = ByteArray(nonce.size + encryptedBody.size)
                nonce.copyInto(fullData, 0)
                encryptedBody.copyInto(fullData, nonce.size)
                CryptoHelper.decryptXChaCha20Poly1305(fullData, backupKey)
                true
            }
        } catch (e: javax.crypto.AEADBadTagException) {
            android.util.Log.w("BackupArchive", "Integrity check failed: bad authentication tag (possible tampering)")
            false
        } catch (e: Exception) {
            android.util.Log.w("BackupArchive", "Integrity check failed: ${e.message}")
            false
        }
    }
}
