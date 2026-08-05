package org.enchant.agent

/**
 * Binds auth/onboarding screens to agent-readable actions (debug APK only).
 * Called from AuthNavDisplay via reflection so feature modules stay clean.
 */
object AuthScreenAgent {

    @JvmStatic
    fun bindWelcome(onAccept: Runnable, onRestore: Runnable, onSkipToPhone: Runnable) {
        AgentScreenState.register(
            screenId = "welcome",
            screenTitle = "Welcome — Agree & Continue",
            actions = mapOf(
                "accept_terms" to { onAccept.run() },
                "restore_account" to { onRestore.run() },
                "skip_to_phone" to { onSkipToPhone.run() }
            )
        )
    }

    @JvmStatic
    fun bindPermissions(onGrant: Runnable, onNotNow: Runnable) {
        AgentScreenState.register(
            screenId = "permissions",
            screenTitle = "Permissions — Grant Permissions / Not now",
            actions = mapOf(
                "grant_permissions" to { onGrant.run() },
                "not_now" to { onNotNow.run() },
                "skip_permissions" to { onNotNow.run() }
            )
        )
    }

    @JvmStatic
    fun bindPhoneEntry(onSubmit: Runnable) {
        AgentScreenState.register(
            screenId = "phone_entry",
            screenTitle = "Enter phone number — use POST /auth/request-otp {identifier}",
            actions = emptyMap()
        )
    }

    @JvmStatic
    fun bindOtpVerify(identifier: String, onSubmit: Runnable, onResend: Runnable) {
        AgentScreenState.register(
            screenId = "otp_verify",
            screenTitle = "Enter OTP for $identifier — use POST /auth/verify-otp {otp}",
            actions = mapOf(
                "resend_otp" to { onResend.run() }
            )
        )
    }

    @JvmStatic
    fun bindProfileSetup(onContinue: Runnable) {
        AgentScreenState.register(
            screenId = "profile_setup",
            screenTitle = "Profile setup — use POST /auth/profile after OTP",
            actions = emptyMap()
        )
    }

    @JvmStatic
    fun bindUsernamePicker(onSkip: Runnable) {
        AgentScreenState.register(
            screenId = "username_picker",
            screenTitle = "Pick username — use POST /auth/profile",
            actions = mapOf(
                "skip_username" to { onSkip.run() }
            )
        )
    }

    @JvmStatic
    fun bindKeyGeneration(onRetry: Runnable) {
        AgentScreenState.register(
            screenId = "key_generation",
            screenTitle = "Generating encryption keys",
            actions = mapOf(
                "retry_keys" to { onRetry.run() }
            )
        )
    }

    @JvmStatic
    fun bindTwoStepPin(onComplete: Runnable) {
        AgentScreenState.register(
            screenId = "two_step_pin",
            screenTitle = "Create PIN — or POST /ui/action {action:skip_pin}",
            actions = mapOf(
                "skip_pin" to { onComplete.run() }
            )
        )
    }

    @JvmStatic
    fun bindAppLock(onVerified: Runnable) {
        AgentScreenState.register(
            screenId = "app_lock",
            screenTitle = "App lock — or POST /ui/action {action:skip_applock}",
            actions = mapOf(
                "skip_applock" to { onVerified.run() }
            )
        )
    }

    @JvmStatic
    fun bindMain(tab: String, detail: String?) {
        AgentScreenState.register(
            screenId = "main",
            screenTitle = "Main app — tab=$tab${detail?.let { ", detail=$it" } ?: ""}",
            actions = mapOf(
                "open_chats" to { AgentNavigationHooks.onOpenMainTab?.invoke("CHATS") },
                "open_calls" to { AgentNavigationHooks.onOpenMainTab?.invoke("CALLS") },
                "open_stories" to { AgentNavigationHooks.onOpenMainTab?.invoke("STORIES") }
            )
        )
    }
}
