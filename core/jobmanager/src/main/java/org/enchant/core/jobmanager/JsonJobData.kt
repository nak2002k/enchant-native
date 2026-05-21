package org.enchant.core.jobmanager

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.Base64

@Serializable
private data class JsonJobDataDto(
    val strings: Map<String, String> = emptyMap(),
    val longs: Map<String, Long> = emptyMap(),
    val ints: Map<String, Int> = emptyMap(),
    val booleans: Map<String, Boolean> = emptyMap(),
    val blobs: Map<String, String> = emptyMap()
)

class JsonJobData private constructor(
    private val strings: Map<String, String>,
    private val longs: Map<String, Long>,
    private val ints: Map<String, Int>,
    private val booleans: Map<String, Boolean>,
    private val blobs: Map<String, String>
) {
    fun getString(key: String): String? = strings[key]
    fun getLong(key: String): Long? = longs[key]
    fun getInt(key: String): Int? = ints[key]
    fun getBoolean(key: String): Boolean? = booleans[key]
    fun getBlob(key: String): ByteArray? = blobs[key]?.let { Base64.getDecoder().decode(it) }

    fun serialize(): ByteArray? {
        if (strings.isEmpty() && longs.isEmpty() && ints.isEmpty() && booleans.isEmpty() && blobs.isEmpty()) return null
        val dto = JsonJobDataDto(strings, longs, ints, booleans, blobs)
        return Json.encodeToString(dto).toByteArray(Charsets.UTF_8)
    }

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun deserialize(data: ByteArray?): JsonJobData {
            if (data == null) return JsonJobData(emptyMap(), emptyMap(), emptyMap(), emptyMap(), emptyMap())
            val dto = json.decodeFromString<JsonJobDataDto>(data.decodeToString())
            return JsonJobData(dto.strings, dto.longs, dto.ints, dto.booleans, dto.blobs)
        }
    }

    class Builder {
        private val strings = mutableMapOf<String, String>()
        private val longs = mutableMapOf<String, Long>()
        private val ints = mutableMapOf<String, Int>()
        private val booleans = mutableMapOf<String, Boolean>()
        private val blobs = mutableMapOf<String, String>()

        fun putString(key: String, value: String) = apply { strings[key] = value }
        fun putLong(key: String, value: Long) = apply { longs[key] = value }
        fun putInt(key: String, value: Int) = apply { ints[key] = value }
        fun putBoolean(key: String, value: Boolean) = apply { booleans[key] = value }
        fun putBlob(key: String, value: ByteArray) = apply { blobs[key] = Base64.getEncoder().encodeToString(value) }

        fun build(): JsonJobData = JsonJobData(strings, longs, ints, booleans, blobs)
    }
}
