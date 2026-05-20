package org.enchant.core.accessibility

import android.content.Context
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAccessibilityManager

@Config(sdk = [35])
@RunWith(AndroidJUnit4::class)
class AccessibilityHelperTest {

    private lateinit var context: Context
    private lateinit var shadowAccessibilityManager: ShadowAccessibilityManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        shadowAccessibilityManager = Shadows.shadowOf(
            context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        )
    }

    @Test
    fun `isScreenReaderEnabled returns false when disabled`() {
        shadowAccessibilityManager.setEnabled(false)
        shadowAccessibilityManager.setTouchExplorationEnabled(false)
        assertFalse(AccessibilityHelper.isScreenReaderEnabled(context))
    }

    @Test
    fun `isScreenReaderEnabled returns true when enabled with touch exploration`() {
        shadowAccessibilityManager.setEnabled(true)
        shadowAccessibilityManager.setTouchExplorationEnabled(true)
        assertTrue(AccessibilityHelper.isScreenReaderEnabled(context))
    }

    @Test
    fun `isScreenReaderEnabled returns false when enabled but no touch exploration`() {
        shadowAccessibilityManager.setEnabled(true)
        shadowAccessibilityManager.setTouchExplorationEnabled(false)
        assertFalse(AccessibilityHelper.isScreenReaderEnabled(context))
    }

    @Test
    fun `isAccessibilityServiceEnabled returns false when disabled`() {
        shadowAccessibilityManager.setEnabled(false)
        assertFalse(AccessibilityHelper.isAccessibilityServiceEnabled(context))
    }

    @Test
    fun `isAccessibilityServiceEnabled returns true when enabled`() {
        shadowAccessibilityManager.setEnabled(true)
        assertTrue(AccessibilityHelper.isAccessibilityServiceEnabled(context))
    }

    @Test
    fun `isTouchExplorationEnabled returns false when disabled`() {
        shadowAccessibilityManager.setTouchExplorationEnabled(false)
        assertFalse(AccessibilityHelper.isTouchExplorationEnabled(context))
    }

    @Test
    fun `isTouchExplorationEnabled returns true when enabled`() {
        shadowAccessibilityManager.setTouchExplorationEnabled(true)
        assertTrue(AccessibilityHelper.isTouchExplorationEnabled(context))
    }

    @Test
    fun `areAnimationsDisabled returns false by default`() {
        Settings.Global.putFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1.0f
        )
        assertFalse(AccessibilityHelper.areAnimationsDisabled(context))
    }

    @Test
    fun `areAnimationsDisabled returns true when scale is zero`() {
        Settings.Global.putFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            0f
        )
        assertTrue(AccessibilityHelper.areAnimationsDisabled(context))
    }

    @Test
    fun `areAnimationsDisabled returns false on missing setting`() {
        assertFalse(AccessibilityHelper.areAnimationsDisabled(context))
    }

    @Test
    fun `isReducedMotionPreferred returns true when animations disabled`() {
        Settings.Global.putFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            0f
        )
        assertTrue(AccessibilityHelper.isReducedMotionPreferred(context))
    }

    @Test
    fun `isReducedMotionPreferred returns false when animations enabled and normal font`() {
        Settings.Global.putFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1.0f
        )
        assertFalse(AccessibilityHelper.isReducedMotionPreferred(context))
    }

    @Test
    fun `isLargeFontScale returns false at default scale`() {
        assertFalse(AccessibilityHelper.isLargeFontScale(context))
    }

    @Test
    fun `isLargeFontScale returns true at 1_5x scale`() {
        val config = context.resources.configuration
        config.fontScale = 1.5f
        val updatedContext = context.createConfigurationContext(config)
        assertTrue(AccessibilityHelper.isLargeFontScale(updatedContext))
    }

    @Test
    fun `isLargeFontScale returns false at 1_3x boundary`() {
        val config = context.resources.configuration
        config.fontScale = 1.3f
        val updatedContext = context.createConfigurationContext(config)
        assertFalse(AccessibilityHelper.isLargeFontScale(updatedContext))
    }

    @Test
    fun `isLargeFontScale returns true just above 1_3x`() {
        val config = context.resources.configuration
        config.fontScale = 1.31f
        val updatedContext = context.createConfigurationContext(config)
        assertTrue(AccessibilityHelper.isLargeFontScale(updatedContext))
    }
}
