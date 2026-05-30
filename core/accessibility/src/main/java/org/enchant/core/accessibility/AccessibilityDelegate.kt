package org.enchant.core.accessibility

import android.content.Context
import androidx.annotation.StringRes

/**
 * Generates accessibility content-description strings for UI elements.
 *
 * All strings reference [R.string] resources for full localization support.
 * Callers pass a [Context] to resolve the final string.
 *
 * NOTE: Requires core:model:MessageRecord, core:model:DeliveryStatus,
 *       core:model:Reaction, and core:model:Recipient types for full integration.
 *       String resources must be defined in app/res/values/strings.xml.
 */
object AccessibilityDelegate {

    /**
     * Content description for a chat message.
     *
     * @param direction "outgoing" or "incoming"
     * @param content   message body text
     * @param status    delivery status label (e.g. "Sent", "Delivered")
     * @param timestamp formatted time string
     * @param hasMedia  whether the message contains an attachment
     * @param isEdited  whether the message was edited after sending
     */
    fun getMessageDescription(
        context: Context,
        direction: MessageDirection,
        content: String,
        status: String,
        timestamp: String,
        hasMedia: Boolean = false,
        isEdited: Boolean = false
    ): String {
        val safeContent = content.ifBlank { "Empty message" }
        val suffixRes = if (isEdited) R.string.a11y_message_edited_suffix else R.string.a11y_message_plain_suffix
        val suffix = context.getString(suffixRes)

        val resId = when {
            direction == MessageDirection.OUTGOING && hasMedia -> R.string.a11y_message_outgoing_media
            direction == MessageDirection.OUTGOING -> R.string.a11y_message_outgoing
            hasMedia -> R.string.a11y_message_incoming_media
            else -> R.string.a11y_message_incoming
        }

        return context.getString(resId, safeContent, status, timestamp, suffix)
    }

    /**
     * Content description for a user avatar.
     */
    fun getAvatarDescription(
        context: Context,
        userName: String,
        isOnline: Boolean
    ): String {
        val name = userName.ifBlank { return context.getString(R.string.a11y_avatar_unknown) }
        val resId = if (isOnline) R.string.a11y_avatar_online else R.string.a11y_avatar_offline
        return context.getString(resId, name)
    }

    /**
     * Content description for a group avatar.
     */
    fun getGroupAvatarDescription(context: Context, groupName: String): String {
        val name = groupName.ifBlank { return context.getString(R.string.a11y_avatar_group_unknown) }
        return context.getString(R.string.a11y_avatar_group, name)
    }

    /**
     * Content description for an action button, optionally including state.
     */
    fun getButtonDescription(
        context: Context,
        @StringRes buttonLabelRes: Int,
        state: String? = null
    ): String {
        val buttonName = context.getString(buttonLabelRes)
        return when {
            state == "on" -> context.getString(R.string.a11y_button_toggled_on, buttonName)
            state == "off" -> context.getString(R.string.a11y_button_toggled_off, buttonName)
            state != null -> context.getString(R.string.a11y_button_with_state, buttonName, state)
            else -> context.getString(R.string.a11y_button_plain, buttonName)
        }
    }

    /**
     * Content description for a button identified by an action key.
     * Resolves the action key to a string resource, then delegates to
     * [getButtonDescription].
     */
    fun getButtonDescriptionByKey(
        context: Context,
        actionKey: String,
        state: String? = null
    ): String {
        val resId = resolveButtonActionKey(actionKey)
        return getButtonDescription(context, resId, state)
    }

    /**
     * Content description for message reactions.
     *
     * @param emoji the reaction emoji
     * @param count number of users who reacted
     */
    fun getReactionDescription(context: Context, emoji: String, count: Int): String {
        val countLabel = when {
            count <= 0 -> context.getString(R.string.a11y_reactions_no)
            count == 1 -> "1"
            else -> count.toString()
        }
        val emojiNameRes = resolveReactionEmoji(emoji)
        val emojiName = context.getString(emojiNameRes)
        return context.getString(R.string.a11y_reactions_count, countLabel, emojiName)
    }

    /**
     * Content description for a delivery status icon.
     */
    fun getDeliveryStatusDescription(
        context: Context,
        status: DeliveryStatus
    ): String {
        val resId = when (status) {
            DeliveryStatus.NONE -> R.string.a11y_delivery_none
            DeliveryStatus.PENDING -> R.string.a11y_delivery_pending
            DeliveryStatus.SENT -> R.string.a11y_delivery_sent
            DeliveryStatus.DELIVERED -> R.string.a11y_delivery_delivered
            DeliveryStatus.READ -> R.string.a11y_delivery_read
        }
        val text = context.getString(resId)
        return text
    }

    /**
     * Content description for a timestamp, with optional "edited" indicator.
     */
    fun getTimestampDescription(
        context: Context,
        label: String,
        relative: String,
        isEdited: Boolean = false,
        isNow: Boolean = false
    ): String {
        return when {
            isNow && isEdited -> context.getString(R.string.a11y_timestamp_edited_now)
            isNow -> context.getString(R.string.a11y_timestamp_now)
            isEdited -> context.getString(R.string.a11y_timestamp_edited_relative, label, relative)
            else -> context.getString(R.string.a11y_timestamp_relative, label, relative)
        }
    }

    /**
     * Content description for a chat list item in the conversation list.
     */
    fun getChatListItemDescription(
        context: Context,
        name: String,
        lastMessage: String,
        timestamp: String,
        unreadCount: Int = 0,
        isMuted: Boolean = false,
        isPinned: Boolean = false,
        hasDraft: String? = null
    ): String {
        val unreadSuffix = when (unreadCount) {
            0 -> ""
            1 -> " " + context.getString(R.string.a11y_chat_list_unread_badge_single)
            else -> " " + context.getString(R.string.a11y_chat_list_unread_badge, unreadCount)
        }
        val muteSuffix = if (isMuted) " " + context.getString(R.string.a11y_chat_list_muted) else ""
        val pinSuffix = if (isPinned) " " + context.getString(R.string.a11y_chat_list_pinned) else ""
        val draftSuffix = hasDraft?.let { " " + context.getString(R.string.a11y_chat_list_draft, it) } ?: ""

        return context.getString(
            R.string.a11y_chat_list_item,
            name, lastMessage, timestamp, "$unreadSuffix$muteSuffix$pinSuffix$draftSuffix"
        )
    }

    /**
     * Content description for a media attachment type.
     */
    fun getMediaDescription(context: Context, mediaType: MediaType, durationLabel: String? = null): String {
        val resId = when (mediaType) {
            MediaType.PHOTO -> R.string.a11y_media_photo
            MediaType.VIDEO -> R.string.a11y_media_video
            MediaType.AUDIO -> R.string.a11y_media_audio
            MediaType.DOCUMENT -> R.string.a11y_media_document
            MediaType.GIF -> R.string.a11y_media_gif
            MediaType.STICKER -> R.string.a11y_media_sticker
            MediaType.CONTACT -> R.string.a11y_media_contact
            MediaType.LOCATION -> R.string.a11y_media_location
            MediaType.POLL -> R.string.a11y_media_poll
            MediaType.PAYMENT -> R.string.a11y_media_payment
            MediaType.VOICE_NOTE -> {
                return durationLabel?.let {
                    context.getString(R.string.a11y_media_voice_note, it)
                } ?: context.getString(R.string.a11y_media_audio)
            }
            MediaType.VIDEO_NOTE -> {
                return durationLabel?.let {
                    context.getString(R.string.a11y_media_video_note, it)
                } ?: context.getString(R.string.a11y_media_video)
            }
        }
        return context.getString(resId)
    }

    /**
     * Content description for a call state indicator.
     */
    fun getCallStateDescription(context: Context, state: CallState, participantName: String): String {
        return when (state) {
            CallState.INCOMING -> context.getString(R.string.a11y_announce_call_incoming, participantName)
            CallState.ONGOING -> context.getString(R.string.a11y_announce_call_ongoing, participantName)
            CallState.ENDED -> context.getString(R.string.a11y_announce_call_ended, participantName)
            CallState.MISSED -> context.getString(R.string.a11y_announce_call_missed, participantName)
        }
    }

    /**
     * Content description for a security indicator.
     */
    fun getSecurityDescription(context: Context, securityState: SecurityState, contactName: String? = null): String {
        return when (securityState) {
            SecurityState.ENCRYPTED -> context.getString(R.string.a11y_security_encrypted)
            SecurityState.VERIFIED -> context.getString(R.string.a11y_security_verified)
            SecurityState.UNVERIFIED -> context.getString(R.string.a11y_security_unverified)
            SecurityState.BLOCKED -> context.getString(R.string.a11y_security_blocked)
            SecurityState.SAFETY_NUMBER_CHANGED -> {
                contactName?.let {
                    context.getString(R.string.a11y_security_safety_number, it)
                } ?: context.getString(R.string.a11y_security_unverified)
            }
        }
    }

    // -- Private resolvers ------------------------------------------------------------------

    @StringRes
    private fun resolveButtonActionKey(actionKey: String): Int {
        return when (actionKey) {
            "send" -> R.string.a11y_button_send
            "attach" -> R.string.a11y_button_attach
            "emoji" -> R.string.a11y_button_emoji
            "mic" -> R.string.a11y_button_mic
            "back" -> R.string.a11y_button_back
            "call" -> R.string.a11y_button_call
            "video_call" -> R.string.a11y_button_video_call
            "mute" -> R.string.a11y_button_mute
            "archive" -> R.string.a11y_button_archive
            "delete" -> R.string.a11y_button_delete
            "reply" -> R.string.a11y_button_reply
            "forward" -> R.string.a11y_button_forward
            "star" -> R.string.a11y_button_star
            "search" -> R.string.a11y_button_search
            "more" -> R.string.a11y_button_more
            "camera" -> R.string.a11y_button_camera
            "gallery" -> R.string.a11y_button_gallery
            "document" -> R.string.a11y_button_document
            "location" -> R.string.a11y_button_location
            "contact" -> R.string.a11y_button_contact
            "poll" -> R.string.a11y_button_poll
            "sticker" -> R.string.a11y_button_sticker
            "gif" -> R.string.a11y_button_gif
            "payment" -> R.string.a11y_button_payment
            "schedule" -> R.string.a11y_button_schedule
            "note" -> R.string.a11y_button_note
            "edit" -> R.string.a11y_button_edit
            "copy" -> R.string.a11y_button_copy
            "select" -> R.string.a11y_button_select
            "pin" -> R.string.a11y_button_pin
            "unpin" -> R.string.a11y_button_unpin
            "resend" -> R.string.a11y_button_resend
            "cancel" -> R.string.a11y_button_cancel
            "confirm" -> R.string.a11y_button_confirm
            "close" -> R.string.a11y_button_close
            "open" -> R.string.a11y_button_open
            "download" -> R.string.a11y_button_download
            "pause" -> R.string.a11y_button_pause
            "end_call" -> R.string.a11y_button_end_call
            "accept_call" -> R.string.a11y_button_accept_call
            "decline_call" -> R.string.a11y_button_decline_call
            "toggle_video" -> R.string.a11y_button_toggle_video
            "toggle_mic" -> R.string.a11y_button_toggle_mic
            "toggle_speaker" -> R.string.a11y_button_toggle_speaker
            else -> R.string.a11y_button_plain
        }
    }

    @StringRes
    private fun resolveReactionEmoji(emoji: String): Int {
        return when (emoji.trim()) {
            "❤️", "\u2764\uFE0F" -> R.string.a11y_reactions_emoji_heart
            "😂", "\uD83D\uDE02" -> R.string.a11y_reactions_emoji_laugh
            "😮", "\uD83D\uDE2E" -> R.string.a11y_reactions_emoji_surprised
            "😢", "\uD83D\uDE22" -> R.string.a11y_reactions_emoji_crying
            "😡", "\uD83D\uDE21" -> R.string.a11y_reactions_emoji_angry
            "👍", "\uD83D\uDC4D" -> R.string.a11y_reactions_emoji_thumbs_up
            "👎", "\uD83D\uDC4E" -> R.string.a11y_reactions_emoji_thumbs_down
            "👏", "\uD83D\uDC4F" -> R.string.a11y_reactions_emoji_clapping
            else -> R.string.a11y_reactions_emoji_default
        }
    }
}

/**
 * Delivery status values used by [AccessibilityDelegate.getDeliveryStatusDescription].
 */
enum class DeliveryStatus {
    NONE, PENDING, SENT, DELIVERED, READ
}

/**
 * Direction of a chat message, used by [AccessibilityDelegate.getMessageDescription].
 */
enum class MessageDirection {
    INCOMING, OUTGOING
}

/**
 * Call state values used by [AccessibilityDelegate.getCallStateDescription].
 */
enum class CallState {
    INCOMING, ONGOING, ENDED, MISSED
}

/**
 * Security state values used by [AccessibilityDelegate.getSecurityDescription].
 */
enum class SecurityState {
    ENCRYPTED, VERIFIED, UNVERIFIED, BLOCKED, SAFETY_NUMBER_CHANGED
}

/**
 * Media type values used by [AccessibilityDelegate.getMediaDescription].
 */
enum class MediaType {
    PHOTO, VIDEO, AUDIO, DOCUMENT, GIF, STICKER, CONTACT, LOCATION, POLL, PAYMENT, VOICE_NOTE, VIDEO_NOTE
}
