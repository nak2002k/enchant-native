package org.enchant

import android.app.Application
import android.os.StrictMode
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.enchant.core.base.SecurePreferences
import org.enchant.core.base.logging.Scrubber
import org.enchant.core.crash.CrashHandler
import org.enchant.core.crash.ScrubbedCrashlyticsTree
import org.enchant.core.notifications.NotificationChannels
import org.enchant.core.performance.ImagePipeline
import timber.log.Timber

class EnchantApp : Application() {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate() {
        super.onCreate()
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            val scrubbed = Scrubber.scrub(throwable.message)
            android.util.Log.e("EnchantApp", "Uncaught crash on ${thread.name}: $scrubbed", throwable)
            CrashHandler.recordException(throwable)
            defaultHandler?.uncaughtException(thread, throwable)
        }
        Timber.plant(ScrubbedCrashlyticsTree())
        appScope.launch {
            initDi()
            initCrashReporting()
        }
        initPerformance()
        if (isDebug()) {
            initLeakCanary()
            initStrictMode()
        }
        initNotificationChannels()
    }

    private suspend fun initDi() {
        try {
            DI.init(this@EnchantApp)
        } catch (e: Throwable) {
            android.util.Log.e("EnchantApp", "DI init failed", e as? Exception ?: Exception(e))
        }
    }

    private fun initCrashReporting() {
        try {
            CrashHandler.install(DI.databasePool)
        } catch (e: Exception) {
            android.util.Log.w("EnchantApp", "CrashHandler init failed: ${e.message}")
        }
    }

    private fun initPerformance() {
        ImagePipeline.init(this)
    }

    private fun initLeakCanary() {
        LeakCanaryInitializer.init()
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
