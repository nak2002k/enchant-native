package org.enchant.core.notifications

import android.app.Notification
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@Config(sdk = [Build.VERSION_CODES.TIRAMISU])
@RunWith(RobolectricTestRunner::class)
class NotificationBuilderPreviewTest {

    @Before
    fun setUp() {
        NotificationChannels.createAll(ApplicationProvider.getApplicationContext())
    }

    private fun notification(
        showPreview: Boolean,
        messagePreview: String = "top-secret-message-body",
        senderName: String? = "Alice",
        messageCount: Int = 1
    ): Notification {
        return NotificationBuilder.buildMessageNotification(
            context = ApplicationProvider.getApplicationContext(),
            conversationDisplayName = "Bob",
            messagePreview = messagePreview,
            senderName = senderName,
            conversationId = "conv-1",
            messageCount = messageCount,
            showPreview = showPreview
        )
    }

    @Test
    fun `preview shown includes message text`() {
        val n = notification(showPreview = true)
        val collapsed = n.extrasText(Notification.EXTRA_TEXT).orEmpty()
        assertTrue(collapsed.contains("top-secret-message-body"))
        assertTrue(collapsed.contains("Alice"))
    }

    @Test
    fun `preview hidden omits message text and sender`() {
        val n = notification(showPreview = false)
        val collapsed = n.extrasText(Notification.EXTRA_TEXT).orEmpty()
        assertFalse(collapsed.contains("top-secret-message-body"))
        assertFalse(collapsed.contains("Alice"))
        assertTrue(collapsed.contains("New message"))
    }

    @Test
    fun `preview hidden omits body from inbox lines`() {
        val n = notification(showPreview = false)
        val lines = n.extrasTextArray(Notification.EXTRA_TEXT_LINES).orEmpty()
        assertTrue(lines.isNotEmpty())
        lines.forEach { line ->
            assertFalse("inbox line leaked body: $line", line.contains("top-secret-message-body"))
        }
        assertTrue(lines.any { it.contains("New message") })
    }

    @Test
    fun `preview hidden multi message shows count but no content`() {
        val n = notification(showPreview = false, messageCount = 3)
        val collapsed = n.extrasText(Notification.EXTRA_TEXT).orEmpty()
        assertFalse(collapsed.contains("top-secret-message-body"))
        assertEquals("3 messages", n.extras?.getCharSequence(Notification.EXTRA_TITLE_BIG)?.toString())
    }

    private fun Notification.extrasText(key: String): String? =
        extras?.getCharSequence(key)?.toString()

    private fun Notification.extrasTextArray(key: String): Array<String>? {
        val value = extras?.getCharSequenceArray(key) ?: return null
        return value.map { it.toString() }.toTypedArray()
    }
}
