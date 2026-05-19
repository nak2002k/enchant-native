package org.enchant.core.accessibility

import android.content.Context
import android.os.Build
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.annotation.ChecksSdkIntAtLeast

/**
 * Detects device-level accessibility and UX settings.
 *
 * Used by UI components to adapt behavior when screen readers,
 * reduced motion, or touch exploration are active.
 */
object AccessibilityHelper {

    /**
     * Returns true when a screen reader (TalkBack) is enabled.
     */
    fun isScreenReaderEnabled(context: Context): Boolean {
        val manager = getAccessibilityManager(context) ?: return false
        return manager.isEnabled && manager.isTouchExplorationEnabled
    }

    /**
     * Returns true when the system accessibility service is enabled,
     * regardless of whether touch exploration is active.
     */
    fun isAccessibilityServiceEnabled(context: Context): Boolean {
        return getAccessibilityManager(context)?.isEnabled == true
    }

    /**
     * Returns true when touch exploration is active (screen reader is actively
     * exploring the screen by touch).
     */
    fun isTouchExplorationEnabled(context: Context): Boolean {
        return getAccessibilityManager(context)?.isTouchExplorationEnabled == true
    }

    /**
     * Returns true when system-wide animations are disabled.
     * Mirrors Signal's [AccessibilityUtil.areAnimationsDisabled].
     */
    fun areAnimationsDisabled(context: Context): Boolean {
        return try {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE
            ) == 0f
        } catch (e: Settings.SettingNotFoundException) {
            false
        }
    }

    /**
     * Returns true when the device prefers reduced motion.
     * Checks animation scale and font scale as a proxy on older Android versions.
     */
    fun isReducedMotionPreferred(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return areAnimationsDisabled(context)
        }
        return areAnimationsDisabled(context)
    }

    /**
     * Returns true when the system font scale is significantly larger than default (> 1.3x).
     */
    fun isLargeFontScale(context: Context): Boolean {
        return context.resources.configuration.fontScale > 1.3f
    }

    // -- Internal ---------------------------------------------------------------------------

    private fun getAccessibilityManager(context: Context): AccessibilityManager? {
        return context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
    }
}
