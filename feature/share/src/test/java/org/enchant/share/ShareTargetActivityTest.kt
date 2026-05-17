package org.enchant.share

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("ShareTargetActivity")
class ShareTargetActivityTest {

    @Test
    fun `action constants match expected`() {
        assertEquals("org.enchant.action.SHARE_TEXT", ShareTargetActivity.ACTION_SHARE_TEXT)
    }
}
