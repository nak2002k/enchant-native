package org.enchant.profile

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("ProfileViewModel")
class ProfileViewModelTest {

    @Test
    fun `ProfileUiState has initial defaults`() {
        val state = ProfileUiState()
        assertNull(state.profile)
        assertEquals(false, state.isLoading)
        assertNull(state.error)
        assertEquals(0, state.searchResults.size)
        assertEquals(0, state.blockedUsers.size)
    }

    @Test
    fun `ProfileData data class holds values`() {
        val data = ProfileData("u1", "Alice", "alice", "Hello", "avatar1")
        assertEquals("u1", data.userId)
        assertEquals("Alice", data.displayName)
        assertEquals("alice", data.username)
        assertEquals("Hello", data.about)
        assertEquals("avatar1", data.avatarMediaId)
    }

    @Test
    fun `ProfileData with null fields`() {
        val data = ProfileData("u2", "Bob", null, null, null)
        assertEquals("u2", data.userId)
        assertEquals("Bob", data.displayName)
        assertNull(data.username)
        assertNull(data.about)
        assertNull(data.avatarMediaId)
    }
}
