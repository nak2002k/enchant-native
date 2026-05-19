package org.enchant.core.accessibility

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityViewCommand

/**
 * Announces dynamic events to screen readers via an accessibility live region.
 *
 * Equivalent to ARIA `aria-live="polite"` on the web. A hidden [TextView] is
 * used as the live region; each call to [announce] sets its text, which TalkBack
 * reads aloud without requiring the user to navigate to it.
 *
 * NOTE: Requires app-level integration — a live-region [TextView] must be placed
 *       in the root layout of each Activity/Fragment. See [createLiveRegionView].
 *       Requires core:notifications:NotificationManager for call-state announcements.
 */
class LiveRegionAnnouncer {

    private var liveRegion: View? = null

    /**
     * Binds this announcer to a live-region view (typically a hidden TextView
     * in the root layout).
     */
    fun attach(liveRegionView: View) {
        ViewCompat.setAccessibilityLiveRegion(liveRegionView, ViewCompat.ACCESSIBILITY_LIVE_REGION_POLITE)
        liveRegionView.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
        this.liveRegion = liveRegionView
    }

    /**
     * Announces a message to the screen reader.
     * No-op if no live region has been attached.
     */
    fun announce(text: String) {
        val view = liveRegion ?: return
        if (text.isBlank()) return
        (view as? TextView)?.text = text
        view.contentDescription = text
    }

    /**
     * Announces an incoming message preview.
     */
    fun announceIncomingMessage(context: Context, sender: String, preview: String) {
        announce(context.getString(R.string.a11y_announce_incoming_message, sender, preview))
    }

    /**
     * Announces a typing indicator.
     */
    fun announceTyping(context: Context, userName: String) {
        announce(context.getString(R.string.a11y_announce_typing, userName))
    }

    /**
     * Announces the unread message count.
     */
    fun announceUnreadCount(context: Context, count: Int) {
        val resId = if (count == 1) R.string.a11y_announce_unread_single else R.string.a11y_announce_unread
        announce(context.getString(resId, count))
    }

    /**
     * Announces a call state change.
     */
    fun announceCallState(context: Context, state: CallState, participantName: String) {
        val resId = when (state) {
            CallState.INCOMING -> R.string.a11y_announce_call_incoming
            CallState.ONGOING -> R.string.a11y_announce_call_ongoing
            CallState.ENDED -> R.string.a11y_announce_call_ended
            CallState.MISSED -> R.string.a11y_announce_call_missed
        }
        announce(context.getString(resId, participantName))
    }

    /**
     * Announces a connection state change.
     */
    fun announceConnectionState(context: Context, state: ConnectionState) {
        val resId = when (state) {
            ConnectionState.CONNECTED -> R.string.a11y_announce_connected
            ConnectionState.RECONNECTING -> R.string.a11y_announce_reconnecting
            ConnectionState.DISCONNECTED -> R.string.a11y_announce_disconnected
        }
        announce(context.getString(resId))
    }

    /**
     * Announces a message delivery status change.
     */
    fun announceDeliveryStatus(context: Context, status: DeliveryStatus) {
        val resId = when (status) {
            DeliveryStatus.SENT -> R.string.a11y_announce_message_sent
            DeliveryStatus.DELIVERED -> R.string.a11y_announce_message_delivered
            DeliveryStatus.READ -> R.string.a11y_announce_message_read
            DeliveryStatus.PENDING -> R.string.a11y_message_status_pending
            DeliveryStatus.NONE -> return
        }
        announce(context.getString(resId))
    }

    /**
     * Announces voice recording state.
     */
    fun announceRecordingStarted(context: Context) {
        announce(context.getString(R.string.a11y_announce_recording_started))
    }

    fun announceRecordingStopped(context: Context) {
        announce(context.getString(R.string.a11y_announce_recording_stopped))
    }

    /**
     * Announces upload/download progress (percentage).
     */
    fun announceUploadProgress(context: Context, percent: Int) {
        announce(context.getString(R.string.a11y_announce_upload_progress, percent))
    }

    fun announceDownloadProgress(context: Context, percent: Int) {
        announce(context.getString(R.string.a11y_announce_download_progress, percent))
    }

    fun announceUploadComplete(context: Context) {
        announce(context.getString(R.string.a11y_announce_upload_complete))
    }

    fun announceDownloadComplete(context: Context) {
        announce(context.getString(R.string.a11y_announce_download_complete))
    }

    /**
     * Clears the current announcement text.
     */
    fun clear() {
        liveRegion?.let {
            (it as? TextView)?.text = ""
            it.contentDescription = ""
        }
    }

    companion object {
        /**
         * Creates a hidden [TextView] suitable for use as a live region.
         * Add this view to your root layout (invisible, zero-size).
         */
        fun createLiveRegionView(context: Context): TextView {
            return TextView(context).apply {
                visibility = View.INVISIBLE
                alpha = 0f
                isFocusable = false
                isFocusableInTouchMode = false
                layoutParams = ViewGroup.LayoutParams(0, 0)
                ViewCompat.setAccessibilityLiveRegion(this, ViewCompat.ACCESSIBILITY_LIVE_REGION_POLITE)
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
            }
        }
    }
}

/**
 * Connection state values used by [LiveRegionAnnouncer.announceConnectionState].
 */
enum class ConnectionState {
    CONNECTED, RECONNECTING, DISCONNECTED
}
