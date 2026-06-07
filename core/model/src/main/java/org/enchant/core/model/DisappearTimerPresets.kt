package org.enchant.core.model

data class DisappearTimerOption(val seconds: Int, val label: String)

object DisappearTimerPresets {
    val CONVERSATION_OPTIONS = listOf(
        DisappearTimerOption(0, "Off"),
        DisappearTimerOption(5, "5 seconds"),
        DisappearTimerOption(30, "30 seconds"),
        DisappearTimerOption(60, "1 minute"),
        DisappearTimerOption(3600, "1 hour"),
        DisappearTimerOption(86400, "1 day"),
        DisappearTimerOption(604800, "1 week")
    )

    val GROUP_OPTIONS = listOf(
        DisappearTimerOption(0, "Off"),
        DisappearTimerOption(5, "5 seconds"),
        DisappearTimerOption(30, "30 seconds"),
        DisappearTimerOption(3600, "1 hour"),
        DisappearTimerOption(86400, "24 hours"),
        DisappearTimerOption(604800, "7 days")
    )

    val SETTINGS_OPTIONS = listOf(
        DisappearTimerOption(0, "Off"),
        DisappearTimerOption(86400, "1 day"),
        DisappearTimerOption(604800, "1 week"),
        DisappearTimerOption(2592000, "1 month"),
        DisappearTimerOption(31536000, "1 year")
    )

    val MAX_TIMER_SECONDS = 7776000

    fun formatDuration(seconds: Int): String = when {
        seconds <= 0 -> "Off"
        seconds < 60 -> "${seconds}s"
        seconds < 3600 -> "${seconds / 60}m"
        seconds < 86400 -> "${seconds / 3600}h"
        else -> "${seconds / 86400}d"
    }

    fun formatTimeRemaining(expireAt: Long): String {
        val remaining = expireAt - System.currentTimeMillis()
        if (remaining <= 0) return "Expired"
        val secs = (remaining / 1000).toInt()
        return when {
            secs < 60 -> "${secs}s"
            secs < 3600 -> "${secs / 60}m"
            secs < 86400 -> "${secs / 3600}h ${secs % 3600 / 60}m"
            else -> "${secs / 86400}d"
        }
    }
}
