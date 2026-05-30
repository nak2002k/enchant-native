package org.enchant.core.crash

import com.google.firebase.crashlytics.FirebaseCrashlytics
import org.enchant.core.base.logging.Scrubber
import timber.log.Timber

class ScrubbedCrashlyticsTree : Timber.Tree() {
    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        if (priority > android.util.Log.INFO) {
            val scrubbed = Scrubber.scrub(message) ?: message
            FirebaseCrashlytics.getInstance().log("[$tag] $scrubbed")
        }
    }
}