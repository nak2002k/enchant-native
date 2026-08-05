package org.enchant.agent

import java.util.function.Consumer

/** JVM-friendly hooks called from AuthNavDisplay via reflection. */
object AuthBindBridge {
    @JvmStatic
    fun registerPhone(submit: Consumer<String>) {
        AgentScreenState.registerPhoneEntry { submit.accept(it) }
    }

    @JvmStatic
    fun registerOtp(identifier: String, submit: Consumer<String>, resend: Runnable) {
        AgentScreenState.registerOtpVerify(
            identifier = identifier,
            onSubmit = { submit.accept(it) },
            onResend = { resend.run() }
        )
    }

    @JvmStatic
    fun registerAppLock(complete: Consumer<String?>) {
        AgentScreenState.registerAppLock { pin -> complete.accept(pin) }
    }
}
