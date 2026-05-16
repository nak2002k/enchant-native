package org.enchant.core.notifications

import android.app.NotificationManager
import android.content.Context
import android.graphics.drawable.Icon
import android.os.Build
import java.time.DayOfWeek
import java.time.ZoneId
import java.time.ZonedDateTime

data class ProfileSchedule(
    val startHour: Int,
    val startMinute: Int,
    val endHour: Int,
    val endMinute: Int,
    val daysOfWeek: List<DayOfWeek> = emptyList(),
    val timezone: ZoneId = ZoneId.systemDefault()
)

object NotificationProfileHelper {
    private const val PREFS_NAME = "notification_profiles"
    private const val PROFILE_COUNT_KEY = "profile_count"

    fun createProfile(
        context: Context,
        name: String,
        schedule: ProfileSchedule,
        allowedContacts: List<String>
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val count = prefs.getInt(PROFILE_COUNT_KEY, 0)
        prefs.edit().putInt(PROFILE_COUNT_KEY, count + 1).apply()
    }

    fun updateProfileSchedule(
        context: Context,
        profileId: String,
        schedule: ProfileSchedule
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putInt("${profileId}_start_h", schedule.startHour)
            .putInt("${profileId}_start_m", schedule.startMinute)
            .putInt("${profileId}_end_h", schedule.endHour)
            .putInt("${profileId}_end_m", schedule.endMinute)
            .putString("${profileId}_tz", schedule.timezone.id)
            .apply()
    }

    fun deleteProfile(context: Context, profileId: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .remove("${profileId}_start_h")
            .remove("${profileId}_start_m")
            .remove("${profileId}_end_h")
            .remove("${profileId}_end_m")
            .remove("${profileId}_tz")
            .apply()
    }

    fun isProfileActive(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return false
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val count = prefs.getInt(PROFILE_COUNT_KEY, 0)
        if (count == 0) return false
        val now = ZonedDateTime.now()
        for (i in 0 until count) {
            val startH = prefs.getInt("profile_${i}_start_h", -1)
            if (startH < 0) continue
            val startM = prefs.getInt("profile_${i}_start_m", 0)
            val endH = prefs.getInt("profile_${i}_end_h", 23)
            val endM = prefs.getInt("profile_${i}_end_m", 59)
            val currentMinutes = now.hour * 60 + now.minute
            val startMinutes = startH * 60 + startM
            val endMinutes = endH * 60 + endM
            if (currentMinutes >= startMinutes && currentMinutes <= endMinutes) {
                return true
            }
        }
        return false
    }
}
