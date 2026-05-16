package org.enchant.chat.components

import org.enchant.core.model.Message

enum class ClusterPosition { SINGLE, START, MIDDLE, END }

object V2ConversationItemShape {
    private const val CLUSTER_TIME_THRESHOLD_MS = 5 * 60 * 1000L

    fun calculateClusterPosition(messages: List<Message>, index: Int): ClusterPosition {
        if (messages.isEmpty()) return ClusterPosition.SINGLE
        val current = messages[index]
        val prev = if (index > 0) messages[index - 1] else null
        val next = if (index < messages.size - 1) messages[index + 1] else null

        val sameSenderAsPrev = prev != null && prev.senderId == current.senderId &&
            (current.timestamp - prev.timestamp) < CLUSTER_TIME_THRESHOLD_MS
        val sameSenderAsNext = next != null && next.senderId == current.senderId &&
            (next.timestamp - current.timestamp) < CLUSTER_TIME_THRESHOLD_MS

        return when {
            !sameSenderAsPrev && !sameSenderAsNext -> ClusterPosition.SINGLE
            sameSenderAsPrev && sameSenderAsNext -> ClusterPosition.MIDDLE
            sameSenderAsPrev -> ClusterPosition.END
            sameSenderAsNext -> ClusterPosition.START
            else -> ClusterPosition.SINGLE
        }
    }

    fun shouldShowSenderName(messages: List<Message>, index: Int, isGroup: Boolean): Boolean {
        if (!isGroup) return false
        if (index < 0 || index >= messages.size) return false
        val current = messages[index]
        val prev = if (index > 0) messages[index - 1] else null
        return prev == null || prev.senderId != current.senderId ||
            (current.timestamp - prev.timestamp) >= CLUSTER_TIME_THRESHOLD_MS
    }
}
