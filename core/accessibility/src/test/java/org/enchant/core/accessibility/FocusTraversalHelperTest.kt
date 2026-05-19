package org.enchant.core.accessibility

import android.view.View
import android.widget.LinearLayout
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@Config(sdk = [35])
@RunWith(AndroidJUnit4::class)
class FocusTraversalHelperTest {

    private lateinit var parent: LinearLayout
    private lateinit var child1: View
    private lateinit var child2: View
    private lateinit var child3: View

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        parent = LinearLayout(context)
        child1 = View(context).apply { id = 1 }
        child2 = View(context).apply { id = 2 }
        child3 = View(context).apply { id = 3 }
        parent.addView(child1)
        parent.addView(child2)
        parent.addView(child3)
    }

    @Test
    fun `sets order`() {
        FocusTraversalHelper.setTraversalOrder(parent, child3, child1, child2)
        assertNotNull(child1)
        assertNotNull(child2)
        assertNotNull(child3)
    }

    @Test
    fun `single child`() {
        FocusTraversalHelper.setTraversalOrder(parent, child1)
        assertNotNull(child1)
    }

    @Test
    fun `empty order`() {
        FocusTraversalHelper.setTraversalOrder(parent)
    }

    @Test
    fun `correct conversation order`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val avatar = View(context)
        val name = View(context)
        val lastMessage = View(context)
        val timestamp = View(context)
        val unreadBadge = View(context)

        val order = FocusTraversalHelper.getConversationItemTraversal(
            avatar, name, lastMessage, timestamp, unreadBadge
        )

        assertEquals(5, order.size)
        assertEquals(avatar, order[0])
        assertEquals(name, order[1])
        assertEquals(lastMessage, order[2])
        assertEquals(timestamp, order[3])
        assertEquals(unreadBadge, order[4])
    }

    @Test
    fun `incoming with avatar`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val avatar = View(context)
        val senderName = View(context)
        val body = View(context)
        val timestamp = View(context)

        val order = FocusTraversalHelper.getMessageBubbleTraversal(
            isIncoming = true,
            avatar = avatar,
            senderName = senderName,
            messageBody = body,
            mediaAttachment = null,
            timestamp = timestamp,
            deliveryStatus = null,
            reactions = null
        )

        assertEquals(4, order.size)
        assertEquals(avatar, order[0])
        assertEquals(senderName, order[1])
        assertEquals(body, order[2])
        assertEquals(timestamp, order[3])
    }

    @Test
    fun `outgoing no avatar`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val body = View(context)
        val timestamp = View(context)

        val order = FocusTraversalHelper.getMessageBubbleTraversal(
            isIncoming = false,
            avatar = View(context),
            senderName = View(context),
            messageBody = body,
            mediaAttachment = null,
            timestamp = timestamp,
            deliveryStatus = null,
            reactions = null
        )

        assertEquals(2, order.size)
        assertEquals(body, order[0])
        assertEquals(timestamp, order[1])
    }

    @Test
    fun `all views present`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val avatar = View(context)
        val senderName = View(context)
        val body = View(context)
        val media = View(context)
        val timestamp = View(context)
        val status = View(context)
        val reactions = View(context)

        val order = FocusTraversalHelper.getMessageBubbleTraversal(
            isIncoming = true,
            avatar = avatar,
            senderName = senderName,
            messageBody = body,
            mediaAttachment = media,
            timestamp = timestamp,
            deliveryStatus = status,
            reactions = reactions
        )

        assertEquals(7, order.size)
        assertEquals(media, order[3])
        assertEquals(status, order[5])
        assertEquals(reactions, order[6])
    }

    @Test
    fun `incoming no avatar`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val body = View(context)
        val timestamp = View(context)

        val order = FocusTraversalHelper.getMessageBubbleTraversal(
            isIncoming = true,
            avatar = null,
            senderName = null,
            messageBody = body,
            mediaAttachment = null,
            timestamp = timestamp,
            deliveryStatus = null,
            reactions = null
        )

        assertEquals(2, order.size)
        assertEquals(body, order[0])
    }

    @Test
    fun `correct call controls order`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val mute = View(context)
        val video = View(context)
        val speaker = View(context)
        val endCall = View(context)

        val order = FocusTraversalHelper.getCallControlsTraversal(mute, video, speaker, endCall)

        assertEquals(4, order.size)
        assertEquals(mute, order[0])
        assertEquals(video, order[1])
        assertEquals(speaker, order[2])
        assertEquals(endCall, order[3])
    }

    @Test
    fun `skips view`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val view = View(context)
        FocusTraversalHelper.skipForAccessibility(view)
        assertEquals(View.IMPORTANT_FOR_ACCESSIBILITY_NO, view.importantForAccessibility)
    }

    @Test
    fun `condition true`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val view = View(context)
        FocusTraversalHelper.setImportantForAccessibilityIf(view, true)
        assertEquals(View.IMPORTANT_FOR_ACCESSIBILITY_YES, view.importantForAccessibility)
    }

    @Test
    fun `condition false`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val view = View(context)
        FocusTraversalHelper.setImportantForAccessibilityIf(view, false)
        assertEquals(View.IMPORTANT_FOR_ACCESSIBILITY_NO, view.importantForAccessibility)
    }

    @Test
    fun `is heading`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val view = View(context)
        FocusTraversalHelper.setAsHeading(view, true)
    }

    @Test
    fun `not heading`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val view = View(context)
        FocusTraversalHelper.setAsHeading(view, false)
    }
}
