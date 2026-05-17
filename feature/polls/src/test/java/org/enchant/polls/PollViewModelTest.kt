package org.enchant.polls

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class PollViewModelTest {

    @Test
    fun `initial state has defaults`() {
        val state = PollUiState()
        assertNull(state.currentPoll)
        assertFalse(state.isSubmitting)
        assertNull(state.error)
        assertNull(state.successMessage)
    }

    @Test
    fun `PollData data class holds values`() {
        val poll = PollData(
            pollId = "p1",
            question = "Best color?",
            options = listOf("Red", "Blue"),
            results = mapOf("Red" to 10, "Blue" to 5),
            yourVote = listOf("Red"),
            totalVotes = 15,
            isClosed = false,
            allowMultiple = false
        )
        assertEquals("p1", poll.pollId)
        assertEquals("Best color?", poll.question)
        assertEquals(2, poll.options.size)
        assertEquals(listOf("Red", "Blue"), poll.options)
        assertEquals(10, poll.results["Red"])
        assertEquals(5, poll.results["Blue"])
        assertEquals(listOf("Red"), poll.yourVote)
        assertEquals(15, poll.totalVotes)
        assertFalse(poll.isClosed)
        assertFalse(poll.allowMultiple)
    }
}
