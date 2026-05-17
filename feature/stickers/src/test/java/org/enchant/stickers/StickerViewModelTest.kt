package org.enchant.stickers

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("StickerViewModel")
class StickerViewModelTest {

    @Test
    fun `StickerPack data class holds values`() {
        val pack = StickerPack(
            packId = "pack-1",
            title = "Fun Stickers",
            cover = "cover.png",
            author = "Author",
            stickerCount = 5
        )
        assertEquals("pack-1", pack.packId)
        assertEquals("Fun Stickers", pack.title)
        assertEquals(5, pack.stickerCount)
    }

    @Test
    fun `StickerPack default values`() {
        val pack = StickerPack(packId = "pack-2", title = "Test", cover = "", author = "", stickerCount = 0)
        assertEquals("pack-2", pack.packId)
        assertEquals("Test", pack.title)
    }

    @Test
    fun `StickerUiState has initial defaults`() {
        val state = StickerUiState()
        assertEquals(0, state.featured.size)
        assertEquals(0, state.searchResults.size)
        assertEquals(false, state.isLoading)
        assertEquals(null, state.error)
    }
}
