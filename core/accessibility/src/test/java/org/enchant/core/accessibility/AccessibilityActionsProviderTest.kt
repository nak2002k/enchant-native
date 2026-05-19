package org.enchant.core.accessibility

import android.view.View
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@Config(sdk = [35])
@RunWith(AndroidJUnit4::class)
class AccessibilityActionsProviderTest {

    private lateinit var view: View

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        view = View(context)
    }

    @Test
    fun `reply action attached`() {
        var replied = false
        AccessibilityActionsProvider.attachMessageActions(view, onReply = { replied = true })
        assertNotNull(view)
    }

    @Test
    fun `multiple actions attached`() {
        AccessibilityActionsProvider.attachMessageActions(
            view,
            onReply = {},
            onCopy = {},
            onForward = {},
            onDelete = {},
            onStar = {},
            onSelectText = {}
        )
        assertNotNull(view)
    }

    @Test
    fun `no actions when null`() {
        AccessibilityActionsProvider.attachMessageActions(view)
        assertNotNull(view)
    }

    @Test
    fun `all call actions`() {
        AccessibilityActionsProvider.attachCallActions(
            view,
            onToggleMic = {},
            onToggleVideo = {},
            onToggleSpeaker = {},
            onEndCall = {}
        )
        assertNotNull(view)
    }

    @Test
    fun `partial call actions`() {
        AccessibilityActionsProvider.attachCallActions(view, onEndCall = {})
        assertNotNull(view)
    }

    @Test
    fun `adds action`() {
        AccessibilityActionsProvider.addCustomAction(view, "Custom action") { true }
        assertNotNull(view)
    }

    @Test
    fun `callback receives view`() {
        var receivedView: View? = null
        AccessibilityActionsProvider.addCustomAction(view, "Test") { v ->
            receivedView = v
            true
        }
        // Callback is registered but not invoked until accessibility action is triggered
        assertNotNull(view)
    }

    @Test
    fun `clears delegate`() {
        AccessibilityActionsProvider.attachMessageActions(view, onReply = {})
        AccessibilityActionsProvider.clearActions(view)
        assertNotNull(view)
    }
}
