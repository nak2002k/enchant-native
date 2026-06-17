package org.enchant.core.crypto

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("Prekey Native FFI — Integration Tests")
class PrekeyNativeTest {

    @Test
    fun `prekey_generate_signed produces 32-byte keys and 64-byte signature`() {
        val identityKeyPair = CryptoPrimitives.generateEd25519KeyPair()
        val pub = ByteArray(32)
        val priv = ByteArray(32)
        val sig = ByteArray(64)

        val rc = EnchantCrypto.enchant_prekey_generate_signed(
            1, identityKeyPair.privateKey, pub, priv, sig, sig.size.toLong()
        )

        assertEquals(EnchantCrypto.SUCCESS, rc)
        assertFalse(pub.all { it == 0.toByte() })
        assertFalse(priv.all { it == 0.toByte() })
        assertFalse(sig.all { it == 0.toByte() })
    }

    @Test
    fun `prekey_generate_batch produces count keys in 68-byte records`() {
        val count = 5
        val startId = 10
        val buf = ByteArray(count * 68)
        val len = LongArray(1)

        val rc = EnchantCrypto.enchant_prekey_generate_batch(count, startId, buf, len)

        assertEquals(EnchantCrypto.SUCCESS, rc)
        assertEquals(count * 68L, len[0])

        // Verify first key id
        val id0 = (buf[0].toInt() and 0xFF) or
                ((buf[1].toInt() and 0xFF) shl 8) or
                ((buf[2].toInt() and 0xFF) shl 16) or
                ((buf[3].toInt() and 0xFF) shl 24)
        assertEquals(startId, id0)
    }
}
