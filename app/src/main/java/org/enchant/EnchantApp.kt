package org.enchant

import android.app.Application
import android.os.StrictMode
import org.enchant.core.crash.CrashReporter
import org.enchant.core.notifications.NotificationChannels
import org.enchant.core.performance.ImagePipeline

class EnchantApp : Application() {
    override fun onCreate() {
        super.onCreate()
        initDi()
        initCrashReporting()
        initPerformance()
        if (isDebug()) {
            initLeakCanary()
            initStrictMode()
        }
        initNotificationChannels()
    }

    private fun initDi() {
    }

    private fun initCrashReporting() {
        CrashReporter.init()
    }

    private fun initPerformance() {
        ImagePipeline.init(this)
    }

    private fun initLeakCanary() {
    }

    private fun initStrictMode() {
        StrictMode.setThreadPolicy(
            StrictMode.ThreadPolicy.Builder()
                .detectAll()
                .penaltyLog()
                .build()
        )
        StrictMode.setVmPolicy(
            StrictMode.VmPolicy.Builder()
                .detectAll()
                .penaltyLog()
                .build()
        )
    }

    private fun initNotificationChannels() {
        NotificationChannels.createAll(this)
    }

    private fun isDebug(): Boolean {
        return try {
            BuildConfig.DEBUG
        } catch (_: Exception) {
            false
        }
    }
}
