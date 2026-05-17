package org.enchant.stickers.screens

import org.enchant.stickers.StickerPack
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("StickerStoreScreen")
class StickerStoreScreenTest {

    @Nested
    @DisplayName("StickerPack data class")
    inner class StickerPackDataClass {
        @Test
        fun `holds all values when constructed`() {
            val pack = StickerPack(
                packId = "pack1",
                title = "Cool Stickers",
                cover = "https://example.com/cover.png",
                stickerCount = 20,
                author = "StickerMaster",
                stickers = listOf("s1", "s2", "s3"),
                isInstalled = true
            )
            assertEquals("pack1", pack.packId)
            assertEquals("Cool Stickers", pack.title)
            assertEquals("https://example.com/cover.png", pack.cover)
            assertEquals(20, pack.stickerCount)
            assertEquals("StickerMaster", pack.author)
            assertEquals(3, pack.stickers.size)
            assertTrue(pack.isInstalled)
        }

        @Test
        fun `uses default values`() {
            val pack = StickerPack(packId = "p1", title = "Mini Pack")
            assertEquals("p1", pack.packId)
            assertEquals("Mini Pack", pack.title)
            assertNull(pack.cover)
            assertEquals(0, pack.stickerCount)
            assertNull(pack.author)
            assertTrue(pack.stickers.isEmpty())
            assertFalse(pack.isInstalled)
        }
    }
}
