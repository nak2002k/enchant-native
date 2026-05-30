package org.enchant.core.push

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings

object BatteryOptimizationHelper {

    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    fun requestDisableBatteryOptimization(context: Context) {
        if (!isIgnoringBatteryOptimizations(context)) {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:${context.packageName}")
            }
            context.startActivity(intent)
        }
    }

    fun isXiaomi(): Boolean = Build.MANUFACTURER.equals("Xiaomi", ignoreCase = true)
    fun isHuawei(): Boolean = Build.MANUFACTURER.equals("Huawei", ignoreCase = true)
    fun isOnePlus(): Boolean = Build.MANUFACTURER.equals("OnePlus", ignoreCase = true)

    fun showAutoStartSettings(context: Context) {
        val intents = listOfNotNull(
            Intent().setClassName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"),
            Intent().setClassName("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"),
            Intent().setClassName("com.oneplus.security", "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity")
        ).filterNotNull()

        for (intent in intents) {
            try {
                context.startActivity(intent)
                return
            } catch (e: Exception) { }
        }
        requestDisableBatteryOptimization(context)
    }
}