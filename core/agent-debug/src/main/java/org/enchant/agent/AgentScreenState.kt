package org.enchant.agent

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Readable snapshot of whatever screen is on screen and which agent actions can tap its buttons.
 */
object AgentScreenState {
    @Volatile
    var screenId: String = "unknown"

    @Volatile
    var screenTitle: String = "Unknown screen"

    @Volatile
    var availableActions: List<String> = emptyList()

    private val handlers = mutableMapOf<String, () -> Unit>()
    private var phoneSubmitHandler: ((String) -> Unit)? = null
    private var otpSubmitHandler: ((String) -> Unit)? = null
    private var appLockCompleteHandler: ((String?) -> Unit)? = null

    @Synchronized
    fun register(screenId: String, screenTitle: String, actions: Map<String, () -> Unit>) {
        this.screenId = screenId
        this.screenTitle = screenTitle
        this.availableActions = actions.keys.toList()
        handlers.clear()
        handlers.putAll(actions)
        phoneSubmitHandler = null
        otpSubmitHandler = null
        appLockCompleteHandler = null
        AgentUiTracker.setAuthRoute(screenId)
        AgentEventLog.emit("ui_screen", data = buildJsonObject {
            put("screen", screenId)
            put("title", screenTitle)
            put("actions", buildJsonArray { availableActions.forEach { add(JsonPrimitive(it)) } })
        })
    }

    @Synchronized
    fun registerPhoneEntry(onSubmit: (String) -> Unit) {
        register(
            screenId = "phone_entry",
            screenTitle = "Enter phone number — POST /ui/phone {\"phone\":\"+1...\"}",
            actions = mapOf("submit_phone" to { /* requires phone param via submitPhone() */ })
        )
        phoneSubmitHandler = onSubmit
    }

    @Synchronized
    fun registerOtpVerify(identifier: String, onSubmit: (String) -> Unit, onResend: () -> Unit) {
        register(
            screenId = "otp_verify",
            screenTitle = "OTP for $identifier — POST /ui/otp {\"otp\":\"123456\"}",
            actions = mapOf(
                "submit_otp" to { /* use submitOtp() */ },
                "resend_otp" to onResend
            )
        )
        otpSubmitHandler = onSubmit
    }

    @Synchronized
    fun registerAppLock(onComplete: (pin: String?) -> Unit) {
        register(
            screenId = "app_lock",
            screenTitle = "App lock — POST /ui/applock {\"pin\":\"123456\"} or skip",
            actions = mapOf(
                "skip_applock" to { onComplete(null) },
                "set_applock_pin" to { /* use completeAppLock(pin) */ }
            )
        )
        appLockCompleteHandler = onComplete
    }

    @Synchronized
    fun runAction(action: String): Boolean {
        val handler = handlers[action] ?: return false
        if (action == "submit_phone" || action == "submit_otp" || action == "set_applock_pin") {
            return false
        }
        handler.invoke()
        recordAction(action)
        return true
    }

    @Synchronized
    fun submitPhone(phone: String): Boolean {
        val handler = phoneSubmitHandler ?: return false
        handler.invoke(phone)
        recordAction("submit_phone")
        AgentEventLog.emit("ui_phone_submitted", data = buildJsonObject { put("phone", phone) })
        return true
    }

    @Synchronized
    fun submitOtp(otp: String): Boolean {
        val handler = otpSubmitHandler ?: return false
        handler.invoke(otp)
        recordAction("submit_otp")
        return true
    }

    @Synchronized
    fun completeAppLock(pin: String?): Boolean {
        val handler = appLockCompleteHandler ?: return false
        handler.invoke(pin)
        recordAction(if (pin == null) "skip_applock" else "set_applock_pin")
        return true
    }

    private fun recordAction(action: String) {
        AgentUiTracker.recordAction(action)
        AgentEventLog.emit("ui_action", data = buildJsonObject { put("action", action) })
    }

    fun toJson(): JsonObject = buildJsonObject {
        put("screen_id", screenId)
        put("screen_title", screenTitle)
        put("available_actions", buildJsonArray { availableActions.forEach { add(JsonPrimitive(it)) } })
    }
}
