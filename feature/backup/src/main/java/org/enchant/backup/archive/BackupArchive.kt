package org.enchant.backup.archive

import java.io.File
import java.io.RandomAccessFile
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

object BackupArchive {
    val BACKUP_MAGIC: ByteArray = "ENCHBKP".encodeToByteArray()
    const val VERSION = 1
    private const val GCM_TAG_LENGTH = 128
    private const val GCM_IV_LENGTH = 12

    suspend fun encryptSection(data: ByteArray, key: ByteArray, nonce: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val spec = GCMParameterSpec(GCM_TAG_LENGTH, nonce)
        val keySpec = SecretKeySpec(key, "AES")
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, spec)
        return cipher.doFinal(data)
    }

    suspend fun decryptSection(ciphertext: ByteArray, key: ByteArray, nonce: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val spec = GCMParameterSpec(GCM_TAG_LENGTH, nonce)
        val keySpec = SecretKeySpec(key, "AES")
        cipher.init(Cipher.DECRYPT_MODE, keySpec, spec)
        return cipher.doFinal(ciphertext)
    }

    suspend fun verifyIntegrity(file: File, backupKey: ByteArray): Boolean {
        return try {
            if (!file.exists() || file.length() < BACKUP_MAGIC.size + 4 + 12 + 16) return false
            RandomAccessFile(file, "r").use { raf ->
                val magic = ByteArray(BACKUP_MAGIC.size)
                raf.readFully(magic)
                if (!magic.contentEquals(BACKUP_MAGIC)) return false
                val version = raf.readInt()
                if (version != VERSION) return false
                val nonce = ByteArray(GCM_IV_LENGTH)
                raf.readFully(nonce)
                val remaining = raf.length() - raf.filePointer
                if (remaining <= GCM_TAG_LENGTH / 8) return false
                val encryptedBody = ByteArray(remaining.toInt())
                raf.readFully(encryptedBody)
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                val spec = GCMParameterSpec(GCM_TAG_LENGTH, nonce)
                val keySpec = SecretKeySpec(backupKey, "AES")
                cipher.init(Cipher.DECRYPT_MODE, keySpec, spec)
                cipher.doFinal(encryptedBody)
                true
            }
        } catch (_: Exception) {
            false
        }
    }
}
