package org.enchant.core.ui

import android.app.Activity
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

object ScreenSecurity {
    fun enableSecureFlag(activity: Activity) {
        activity.window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }

    fun disableSecureFlag(activity: Activity) {
        activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }
}

@Composable
fun SecureScreen() {
    val view = LocalView.current
    DisposableEffect(view) {
        val activity = view.context as? Activity
        activity?.let { ScreenSecurity.enableSecureFlag(it) }
        onDispose {
            activity?.let { ScreenSecurity.disableSecureFlag(it) }
        }
    }
}
