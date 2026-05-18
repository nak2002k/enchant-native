package org.enchant

import leakcanary.LeakCanary

object LeakCanaryInitializer {
    fun init() {
        LeakCanary.config = LeakCanary.config.copy(retainedVisibleThreshold = 3)
    }
}
