package org.enchant.core.accessibility

import android.widget.TextView
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
class LiveRegionAnnouncerTest {

    private lateinit var announcer: LiveRegionAnnouncer
    private lateinit var liveRegion: TextView

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        announcer = LiveRegionAnnouncer()
        liveRegion = LiveRegionAnnouncer.createLiveRegionView(context)
        announcer.attach(liveRegion)
    }

    @Test
    fun `sets polite live region`() {
        assertNotNull(liveRegion)
        assertEquals(0f, liveRegion.alpha)
    }

    @Test
    fun `view is invisible`() {
        assertEquals(android.view.View.INVISIBLE, liveRegion.visibility)
        assertEquals(0, liveRegion.layoutParams.width)
        assertEquals(0, liveRegion.layoutParams.height)
    }

    @Test
    fun `not focusable`() {
        assertTrue(!liveRegion.isFocusable)
        assertTrue(!liveRegion.isFocusableInTouchMode)
    }

    @Test
    fun `sets text`() {
        announcer.announce("Hello world")
        assertEquals("Hello world", liveRegion.text.toString())
    }

    @Test
    fun `ignores blank`() {
        announcer.announce("")
        // Blank announcements are ignored, text remains empty
        assertTrue(liveRegion.text.isNullOrEmpty())
    }

    @Test
    fun `overwrites previous`() {
        announcer.announce("First")
        announcer.announce("Second")
        assertEquals("Second", liveRegion.text.toString())
    }

    @Test
    fun `sender and preview`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        announcer.announceIncomingMessage(context, "Alice", "Hey there!")
        val text = liveRegion.text.toString()
        assertTrue(text.contains("Alice"))
        assertTrue(text.contains("Hey there!"))
    }

    @Test
    fun `typing announcement`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        announcer.announceTyping(context, "Bob")
        val text = liveRegion.text.toString()
        assertTrue(text.contains("Bob"))
        assertTrue(text.contains("typing"))
    }

    @Test
    fun `single unread`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        announcer.announceUnreadCount(context, 1)
        val text = liveRegion.text.toString()
        assertTrue(text.contains("1"))
        assertTrue(text.contains("unread"))
    }

    @Test
    fun `multiple unread`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        announcer.announceUnreadCount(context, 7)
        val text = liveRegion.text.toString()
        assertTrue(text.contains("7"))
    }

    @Test
    fun `incoming call`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        announcer.announceCallState(context, CallState.INCOMING, "Alice")
        assertTrue(liveRegion.text.toString().contains("Incoming"))
    }

    @Test
    fun `ended call`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        announcer.announceCallState(context, CallState.ENDED, "Charlie")
        assertTrue(liveRegion.text.toString().contains("ended"))
    }

    @Test
    fun `missed call`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        announcer.announceCallState(context, CallState.MISSED, "Dave")
        assertTrue(liveRegion.text.toString().contains("Missed"))
    }

    @Test
    fun `connected`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        announcer.announceConnectionState(context, ConnectionState.CONNECTED)
        assertTrue(liveRegion.text.toString().contains("Connected"))
    }

    @Test
    fun `disconnected`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        announcer.announceConnectionState(context, ConnectionState.DISCONNECTED)
        assertTrue(liveRegion.text.toString().contains("Disconnected"))
    }

    @Test
    fun `sent announcement`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        announcer.announceDeliveryStatus(context, DeliveryStatus.SENT)
        assertTrue(liveRegion.text.toString().contains("sent"))
    }

    @Test
    fun `delivered announcement`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        announcer.announceDeliveryStatus(context, DeliveryStatus.DELIVERED)
        assertTrue(liveRegion.text.toString().contains("delivered"))
    }

    @Test
    fun `read announcement`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        announcer.announceDeliveryStatus(context, DeliveryStatus.READ)
        assertTrue(liveRegion.text.toString().contains("read"))
    }

    @Test
    fun `none no-op`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        announcer.announce("Initial")
        announcer.announceDeliveryStatus(context, DeliveryStatus.NONE)
        assertEquals("Initial", liveRegion.text.toString())
    }

    @Test
    fun `clears text`() {
        announcer.announce("Something")
        announcer.clear()
        assertEquals("", liveRegion.text.toString())
    }

    @Test
    fun `creates text view`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val view = LiveRegionAnnouncer.createLiveRegionView(context)
        assertTrue(view is TextView)
        assertEquals(android.view.View.INVISIBLE, view.visibility)
    }

    @Test
    fun `zero size`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val view = LiveRegionAnnouncer.createLiveRegionView(context)
        assertEquals(0, view.layoutParams.width)
        assertEquals(0, view.layoutParams.height)
    }

    @Test
    fun `not focusable create`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val view = LiveRegionAnnouncer.createLiveRegionView(context)
        assertTrue(!view.isFocusable)
        assertTrue(!view.isFocusableInTouchMode)
    }
}
