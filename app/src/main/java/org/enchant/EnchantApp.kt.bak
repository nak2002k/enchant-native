package org.enchant

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.enchant.core.base.DI

class EnchantApp : Application() {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate() {
        super.onCreate()
        appScope.launch {
            try {
                DI.init(this@EnchantApp)
            } catch (e: Exception) {
                android.util.Log.e("EnchantApp", "DI init failed", e)
            }
        }
    }
}
