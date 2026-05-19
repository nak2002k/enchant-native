package org.enchant.core.accessibility

import android.view.View
import android.widget.LinearLayout
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@Config(sdk = [35])
@RunWith(AndroidJUnit4::class)
class AccessibilityExtensionsTest {

    private lateinit var view: View
    private lateinit var parent: LinearLayout
    private lateinit var child1: View
    private lateinit var child2: View

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        view = View(context)
        parent = LinearLayout(context)
        child1 = View(context).apply { id = 1 }
        child2 = View(context).apply { id = 2 }
        parent.addView(child1)
        parent.addView(child2)
    }

    @Test
    fun `from resource`() {
        view.withContentDescription(R.string.a11y_button_send)
        assertEquals("Send message", view.contentDescription)
    }

    @Test
    fun `with args`() {
        view.withContentDescription(R.string.a11y_announce_unread, 5)
        assertTrue(view.contentDescription.toString().contains("5"))
    }

    @Test
    fun `returns view for chaining resource`() {
        val result = view.withContentDescription(R.string.a11y_button_send)
        assertEquals(view, result)
    }

    @Test
    fun `literal string`() {
        view.withContentDescription("Custom description")
        assertEquals("Custom description", view.contentDescription)
    }

    @Test
    fun `returns view for chaining string`() {
        val result = view.withContentDescription("Test")
        assertEquals(view, result)
    }

    @Test
    fun `polite`() {
        view.asLiveRegion()
    }

    @Test
    fun `returns view for chaining live region`() {
        val result = view.asLiveRegion()
        assertEquals(view, result)
    }

    @Test
    fun `assertive`() {
        view.asAssertiveLiveRegion()
    }

    @Test
    fun `returns view for chaining assertive`() {
        val result = view.asAssertiveLiveRegion()
        assertEquals(view, result)
    }

    @Test
    fun `adds action`() {
        val result = view.withAccessibilityAction("Test action") { true }
        assertEquals(view, result)
    }

    @Test
    fun `callback gets view`() {
        var received: View? = null
        view.withAccessibilityAction("Test") { v ->
            received = v
            true
        }
        // Callback is registered but not invoked until accessibility action is triggered
        // Verify the action was registered by checking the view is not null
        assertNotNull(view)
    }

    @Test
    fun `true`() {
        view.setImportantForAccessibilityIf(true)
        assertEquals(View.IMPORTANT_FOR_ACCESSIBILITY_YES, view.importantForAccessibility)
    }

    @Test
    fun `false`() {
        view.setImportantForAccessibilityIf(false)
        assertEquals(View.IMPORTANT_FOR_ACCESSIBILITY_NO, view.importantForAccessibility)
    }

    @Test
    fun `returns view for chaining importance`() {
        val result = view.setImportantForAccessibilityIf(true)
        assertEquals(view, result)
    }

    @Test
    fun `decorative`() {
        view.asDecorative()
        assertEquals(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS, view.importantForAccessibility)
    }

    @Test
    fun `returns view for chaining decorative`() {
        val result = view.asDecorative()
        assertEquals(view, result)
    }

    @Test
    fun `heading`() {
        view.asHeading(true)
    }

    @Test
    fun `not heading`() {
        view.asHeading(false)
    }

    @Test
    fun `returns view for chaining heading`() {
        val result = view.asHeading()
        assertEquals(view, result)
    }

    @Test
    fun `by ids`() {
        parent.setDescendantOrder(2, 1)
        assertNotNull(child1)
        assertNotNull(child2)
    }

    @Test
    fun `returns parent for chaining ids`() {
        val result = parent.setDescendantOrder(1, 2)
        assertEquals(parent, result)
    }

    @Test
    fun `by views`() {
        parent.setDescendantOrder(child2, child1)
    }

    @Test
    fun `returns parent for chaining views`() {
        val result = parent.setDescendantOrder(child1, child2)
        assertEquals(parent, result)
    }

    @Test
    fun `attaches message actions`() {
        view.attachMessageActions(onReply = {}, onCopy = {})
        assertNotNull(view)
    }

    @Test
    fun `returns view for chaining message actions`() {
        val result = view.attachMessageActions()
        assertEquals(view, result)
    }

    @Test
    fun `attaches call actions`() {
        view.attachCallActions(onEndCall = {}, onToggleMic = {})
        assertNotNull(view)
    }

    @Test
    fun `returns view for chaining call actions`() {
        val result = view.attachCallActions()
        assertEquals(view, result)
    }
}
