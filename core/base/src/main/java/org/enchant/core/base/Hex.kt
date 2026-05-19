package org.enchant.core.base

object Hex {

    private val HEX_CHARS = "0123456789ABCDEF".toCharArray()

    fun encode(bytes: ByteArray): String {
        val result = CharArray(bytes.size * 2)
        for (i in bytes.indices) {
            val b = bytes[i].toInt() and 0xFF
            result[i * 2] = HEX_CHARS[b ushr 4]
            result[i * 2 + 1] = HEX_CHARS[b and 0x0F]
        }
        return String(result)
    }

    fun encodeLower(bytes: ByteArray): String {
        return encode(bytes).lowercase()
    }

    fun decode(hex: String): ByteArray {
        val cleaned = hex.replace(" ", "").replace("\n", "")
        require(cleaned.length % 2 == 0) { "Hex string must have even length" }
        val len = cleaned.length / 2
        val result = ByteArray(len)
        for (i in 0 until len) {
            val pos = i * 2
            result[i] = ((Character.digit(cleaned[pos], 16) shl 4) + Character.digit(cleaned[pos + 1], 16)).toByte()
        }
        return result
    }
}
