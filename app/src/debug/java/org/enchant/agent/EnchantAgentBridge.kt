package org.enchant.agent

import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.enchant.DI
import org.enchant.MainNavigationDetailLocation
import org.enchant.MainNavigationListLocation
import org.enchant.auth.screens.hashPinArgon2
import org.enchant.auth.screens.verifyPinArgon2
import org.enchant.backup.BackupExporter
import org.enchant.backup.BackupSection
import org.enchant.chat.data.MessageSendPipeline
import org.enchant.chat.data.SendError
import org.enchant.chat.data.SendResult
import org.enchant.contacts.data.ContactsRepository
import org.enchant.contacts.data.ContactResult
import org.enchant.core.auth.AuthConstants
import org.enchant.core.auth.AuthManager
import org.enchant.core.auth.AuthState
import org.enchant.core.auth.RegistrationState
import org.enchant.core.base.AppConfig
import org.enchant.core.base.SecurePreferences
import org.enchant.core.calls.CallManager
import org.enchant.core.crypto.CryptoHelper
import org.enchant.core.crypto.KeyManager
import org.enchant.core.network.ConnectivityMonitor
import org.enchant.core.network.WebSocketManager
import org.enchant.groups.data.GroupResult
import org.enchant.groups.data.GroupsRepository
import org.enchant.status.StatusPrivacy
import java.io.File

/**
 * Full app bridge — every endpoint calls the same managers/repositories the UI uses.
 */
class EnchantAgentBridge : AgentAppBridge {

    private fun ok(data: JsonObject = buildJsonObject {}): JsonObject = buildJsonObject {
        put("ok", true)
        data.forEach { (k, v) -> put(k, v) }
    }

    private fun err(message: String, extra: JsonObject = buildJsonObject {}): JsonObject = buildJsonObject {
        put("ok", false)
        put("error", message)
        extra.forEach { (k, v) -> put(k, v) }
    }

    override suspend fun getState(): JsonObject {
        val auth = AuthManager.authState.value
        val reg = AuthManager.currentState.value
        val ws = WebSocketManager.connectionState.value.name
        val online = ConnectivityMonitor.isOnline.value
        return ok(buildJsonObject {
            put("auth", auth.javaClass.simpleName)
            put("registration", reg.javaClass.simpleName)
            put("user_id", SecurePreferences.getString(AuthConstants.USER_ID_KEY) ?: "")
            put("device_id", SecurePreferences.getString(AuthConstants.DEVICE_ID_KEY) ?: "")
            put("websocket", ws)
            put("network_online", online)
            put("di_initialized", DI.isInitialized)
            put("ui", AgentUiTracker.toJson())
            put("screen", AgentScreenState.toJson())
            put("agent_debug_running", AgentDebug.isRunning())
        })
    }

    override suspend fun getHelp(): JsonObject = ok(buildJsonObject {
        put("endpoints", buildJsonArray {
            listOf(
                "GET /health",
                "GET /help",
                "GET /state",
                "GET /events?since=0&limit=100",
                "GET /ui/current",
                "POST /ui/action {action}",
                "POST /auth/request-otp {identifier}",
                "POST /auth/accept-terms",
                "POST /auth/skip-permissions",
                "POST /auth/skip-pin",
                "POST /auth/skip-applock",
                "POST /auth/verify-otp {otp}",
                "POST /auth/register-keys",
                "POST /auth/profile {username, display_name, about?}",
                "POST /auth/complete",
                "POST /auth/skip-to-main",
                "POST /auth/logout",
                "POST /auth/full-flow {identifier, otp, username, display_name, about?}",
                "POST /nav {target, ...params}",
                "GET /conversations",
                "POST /conversations/open {conversation_id}",
                "GET /conversations/{id}/messages?limit=50",
                "POST /messages/send {recipient_user_id, text, sealed?}",
                "POST /messages/media {recipient_user_id, conversation_id, file_path, mime_type, file_name?, view_once?}",
                "POST /messages/reaction {conversation_id, emoji, envelope_id?, message_local_id?}",
                "POST /messages/sticker {recipient_user_id, conversation_id, pack_id, sticker_id}",
                "GET /groups",
                "POST /groups/create {name, description?, initial_member_ids?, add_members_policy?, join_type?}",
                "POST /groups/{id}/members {user_ids:[]}",
                "POST /groups/join {link_code}",
                "POST /calls/start {remote_user_id, video?}",
                "GET /calls/log?limit=50",
                "GET /status/feed",
                "POST /status/text {text, background_color?, privacy?, selected_contacts?}",
                "POST /status/media {media_id, privacy?, selected_contacts?}",
                "POST /status/{id}/view",
                "GET /stickers/library",
                "GET /stickers/featured",
                "POST /stickers/{packId}/install",
                "POST /backup/cloud/initiate",
                "GET /backup/cloud/latest",
                "POST /backup/cloud/restore {backup_id}",
                "POST /backup/local/export {output_path, backup_key_b64}",
                "POST /backup/local/import {input_path, backup_key_b64, sections:[]}",
                "POST /applock/set {pin}",
                "POST /applock/verify {pin}",
                "POST /applock/disable",
                "GET /contacts",
                "POST /contacts/add {user_id, custom_name?}",
                "POST /contacts/remove {user_id}",
                "GET /contacts/blocked",
                "GET /network/status",
                "POST /network/ws/connect",
                "POST /network/ws/disconnect",
                "GET /crypto/status"
            ).forEach { add(JsonPrimitive(it)) }
        })
        put("adb", "adb forward tcp:19789 tcp:19789")
    })

    override suspend fun setPin(pin: String): JsonObject {
        if (!DI.isInitialized) return err("DI not initialized")
        return DI.apiClient.put("/v1/auth/pin", kotlinx.serialization.json.buildJsonObject {
            put("pin", pin)
        }).fold(
            onSuccess = { ok(buildJsonObject { put("pin_set", true) }) },
            onFailure = { err(it.message ?: "set pin failed") }
        )
    }

    override suspend fun verifyPin(pin: String): JsonObject {
        if (!DI.isInitialized) return err("DI not initialized")
        return DI.apiClient.post("/v1/auth/verify-pin", kotlinx.serialization.json.buildJsonObject {
            put("pin", pin)
        }).fold(
            onSuccess = { ok(buildJsonObject { put("verified", it["verified"]?.jsonPrimitive?.content == "true") }) },
            onFailure = { err(it.message ?: "verify pin failed") }
        )
    }

    override suspend fun requestOtp(identifier: String): JsonObject {
        val result = AuthManager.requestOtp(identifier)
        return result.fold(
            onSuccess = {
                AgentEventLog.emit("auth_request_otp", data = buildJsonObject {
                    put("identifier", identifier)
                    put("challenge_id", it.challengeId)
                })
                ok(buildJsonObject {
                    put("challenge_id", it.challengeId)
                    put("expires_in", it.expiresIn)
                    put("hint", "Read OTP from backend logs, then POST /auth/verify-otp")
                })
            },
            onFailure = {
                AgentEventLog.emit("auth_request_otp", ok = false, data = buildJsonObject {
                    put("error", it.message ?: "failed")
                })
                err(it.message ?: "request otp failed")
            }
        )
    }

    override suspend fun verifyOtp(otp: String, pin: String?): JsonObject {
        if (pin != null) {
            org.enchant.core.auth.AuthManager.setRegistrationPin(pin)
        }
        val result = AuthManager.verifyOtp(otp)
        return result.fold(
            onSuccess = {
                AgentEventLog.emit("auth_verify_otp", data = buildJsonObject {
                    put("user_id", SecurePreferences.getString(AuthConstants.USER_ID_KEY) ?: "")
                })
                ok(buildJsonObject {
                    put("registration_state", AuthManager.currentState.value.javaClass.simpleName)
                })
            },
            onFailure = {
                AgentEventLog.emit("auth_verify_otp", ok = false, data = buildJsonObject {
                    put("error", it.message ?: "failed")
                })
                err(it.message ?: "verify otp failed")
            }
        )
    }

    override suspend fun registerKeys(): JsonObject {
        val result = AuthManager.registerKeys()
        return result.fold(
            onSuccess = {
                AgentEventLog.emit("auth_register_keys")
                ok()
            },
            onFailure = {
                AgentEventLog.emit("auth_register_keys", ok = false, data = buildJsonObject {
                    put("error", it.message ?: "failed")
                })
                err(it.message ?: "register keys failed")
            }
        )
    }

    override suspend fun setProfile(username: String, displayName: String, about: String?): JsonObject {
        val result = AuthManager.updateProfile(username, displayName, about)
        return result.fold(
            onSuccess = {
                AgentEventLog.emit("auth_profile_set", data = buildJsonObject { put("username", username) })
                ok()
            },
            onFailure = { err(it.message ?: "profile failed") }
        )
    }

    override suspend fun completeRegistration(): JsonObject {
        AuthManager.completeRegistration()
        AgentNavigationHooks.onShowMainApp?.invoke()
        AgentEventLog.emit("auth_complete")
        return ok(buildJsonObject { put("registration", "Complete") })
    }

    override suspend fun skipToMainIfRegistered(): JsonObject {
        val reg = AuthManager.currentState.value
        val auth = AuthManager.authState.value
        if (auth is AuthState.Authenticated && reg is RegistrationState.Complete) {
            AgentNavigationHooks.onShowMainApp?.invoke()
            return ok(buildJsonObject { put("skipped_to", "main") })
        }
        return ok(buildJsonObject {
            put("skipped_to", "none")
            put("auth", auth.javaClass.simpleName)
            put("registration", reg.javaClass.simpleName)
        })
    }

    override suspend fun logout(): JsonObject {
        AuthManager.logout()
        AgentNavigationHooks.onShowAuthFlow?.invoke()
        AgentEventLog.emit("auth_logout")
        return ok()
    }

    override suspend fun getUiCurrent(): JsonObject = ok(buildJsonObject {
        put("screen", AgentScreenState.toJson())
        put("tracker", AgentUiTracker.toJson())
    })

    override suspend fun performUiAction(action: String): JsonObject = withContext(Dispatchers.Main) {
        val normalized = when (action.lowercase()) {
            "agree", "agree_and_continue" -> "accept_terms"
            "grant", "grant_permissions" -> "grant_permissions"
            "not_now", "skip", "skip_permissions" -> "not_now"
            else -> action
        }
        if (AgentScreenState.runAction(normalized)) {
            ok(buildJsonObject {
                put("action", normalized)
                put("screen", AgentScreenState.toJson())
            })
        } else {
            err(
                "Action '$normalized' not available on screen '${AgentScreenState.screenId}'",
                AgentScreenState.toJson()
            )
        }
    }

    override suspend fun submitPhone(phone: String): JsonObject {
        if (phone.isBlank()) return err("phone is required")
        val normalized = phone.trim()
        return AuthManager.requestOtp(normalized).fold(
            onSuccess = { response ->
                AgentEventLog.emit("auth_request_otp", data = buildJsonObject {
                    put("identifier", normalized)
                    put("challenge_id", response.challengeId)
                    put("via", "ui/phone")
                })
                ok(buildJsonObject {
                    put("phone", normalized)
                    put("challenge_id", response.challengeId)
                    put("screen", AgentScreenState.toJson())
                })
            },
            onFailure = {
                err(it.message ?: "request otp failed", AgentScreenState.toJson())
            }
        )
    }

    override suspend fun submitOtp(otp: String, pin: String?): JsonObject {
        if (otp.isBlank()) return err("otp is required")
        val code = otp.trim()
        if (pin != null) {
            org.enchant.core.auth.AuthManager.setRegistrationPin(pin)
        }
        return AuthManager.verifyOtp(code).fold(
            onSuccess = {
                AgentEventLog.emit("auth_verify_otp", data = buildJsonObject {
                    put("user_id", SecurePreferences.getString(AuthConstants.USER_ID_KEY) ?: "")
                    put("via", "ui/otp")
                })
                ok(buildJsonObject {
                    put("otp", code)
                    put("user_id", SecurePreferences.getString(AuthConstants.USER_ID_KEY) ?: "")
                    put("registration_state", AuthManager.currentState.value.javaClass.simpleName)
                    put("screen", AgentScreenState.toJson())
                })
            },
            onFailure = {
                err(it.message ?: "verify otp failed", AgentScreenState.toJson())
            }
        )
    }

    override suspend fun completeAppLock(pin: String?): JsonObject = withContext(Dispatchers.Main) {
        if (pin != null && (pin.length != 6 || pin.any { !it.isDigit() })) {
            return@withContext err("PIN must be exactly 6 digits")
        }
        if (AgentScreenState.completeAppLock(pin)) {
            ok(buildJsonObject {
                put("pin_set", pin != null)
                put("screen", AgentScreenState.toJson())
            })
        } else {
            err("App lock screen not active", AgentScreenState.toJson())
        }
    }

    override suspend fun acceptTerms(): JsonObject = performUiAction("accept_terms")

    override suspend fun skipPermissions(): JsonObject = performUiAction("not_now")

    override suspend fun skipPin(): JsonObject = withContext(Dispatchers.Main) {
        if (AgentScreenState.runAction("skip_pin")) {
            ok(buildJsonObject { put("action", "skip_pin"); put("screen", AgentScreenState.toJson()) })
        } else {
            AuthManager.completeRegistration()
            ok(buildJsonObject { put("action", "skip_pin"); put("via", "auth_manager") })
        }
    }

    override suspend fun skipAppLock(): JsonObject = performUiAction("skip_applock")

    override suspend fun runRegistrationFlow(
        identifier: String,
        otp: String,
        username: String,
        displayName: String,
        about: String?,
        pin: String?
    ): JsonObject {
        performUiAction("skip_to_phone").let { if (it["ok"]?.jsonPrimitive?.content != "true") return it }
        kotlinx.coroutines.delay(800)
        submitPhone(identifier).let { if (it["ok"]?.jsonPrimitive?.content != "true") return it }
        kotlinx.coroutines.delay(1500)
        submitOtp(otp, pin).let { if (it["ok"]?.jsonPrimitive?.content != "true") return it }
        kotlinx.coroutines.delay(500)
        registerKeys().let { if (it["ok"]?.jsonPrimitive?.content != "true") return it }
        setProfile(username, displayName, about).let { if (it["ok"]?.jsonPrimitive?.content != "true") return it }
        kotlinx.coroutines.delay(1000)
        skipPin().let { if (it["ok"]?.jsonPrimitive?.content != "true") return it }
        kotlinx.coroutines.delay(800)
        completeAppLock("123456").let { if (it["ok"]?.jsonPrimitive?.content != "true") return it }
        kotlinx.coroutines.delay(1000)
        connectWebSocket()
        return ok(buildJsonObject {
            put("registered", true)
            put("user_id", SecurePreferences.getString(AuthConstants.USER_ID_KEY) ?: "")
        })
    }

    override suspend fun navigate(body: JsonObject): JsonObject {
        val target = body["target"]?.jsonPrimitive?.content
            ?: body["route"]?.jsonPrimitive?.content
            ?: return err("Missing target or route")

        return when (target.lowercase()) {
            "main", "home" -> {
                AgentNavigationHooks.emit(AgentNavCommand.ShowMainApp)
                ok(buildJsonObject { put("navigated", "main") })
            }
            "auth", "login" -> {
                AgentNavigationHooks.emit(AgentNavCommand.ShowAuthFlow)
                ok(buildJsonObject { put("navigated", "auth") })
            }
            "accept_terms", "agree" -> performUiAction("accept_terms")
            "skip_to_phone" -> performUiAction("skip_to_phone")
            "skip_permissions", "permissions", "not_now" -> performUiAction("not_now")
            "grant_permissions", "grant" -> performUiAction("grant_permissions")
            "skip_pin" -> skipPin()
            "skip_applock", "skip_app_lock" -> performUiAction("skip_applock")
            "chats", "calls", "stories", "archive" -> {
                AgentNavigationHooks.emit(AgentNavCommand.OpenMainTab(target.uppercase()))
                ok(buildJsonObject { put("tab", target) })
            }
            "conversation", "chat" -> {
                val id = body["conversation_id"]?.jsonPrimitive?.content
                    ?: body["user_id"]?.jsonPrimitive?.content
                    ?: return err("Missing conversation_id or user_id")
                AgentNavigationHooks.emit(
                    AgentNavCommand.OpenMainDetail("conversation", buildJsonObject { put("conversation_id", id) })
                )
                ok(buildJsonObject { put("conversation_id", id) })
            }
            "contacts" -> {
                AgentNavigationHooks.emit(
                    AgentNavCommand.OpenMainDetail("contacts", buildJsonObject {})
                )
                ok(buildJsonObject { put("detail", "contacts") })
            }
            "settings" -> {
                AgentNavigationHooks.emit(AgentNavCommand.OpenMainDetail("settings"))
                ok(buildJsonObject { put("detail", "settings") })
            }
            "contacts" -> {
                AgentNavigationHooks.emit(AgentNavCommand.OpenMainDetail("contacts"))
                ok(buildJsonObject { put("detail", "contacts") })
            }
            "security_settings" -> {
                AgentNavigationHooks.emit(AgentNavCommand.OpenMainDetail("security_settings"))
                ok()
            }
            "groups", "create_group" -> {
                AgentNavigationHooks.emit(
                    AgentNavCommand.OpenMainDetail(if (target == "create_group") "create_group" else "groups")
                )
                ok(buildJsonObject { put("detail", target) })
            }
            "group" -> {
                val groupId = body["group_id"]?.jsonPrimitive?.content ?: return err("Missing group_id")
                AgentNavigationHooks.emit(
                    AgentNavCommand.OpenMainDetail("group_info", buildJsonObject { put("group_id", groupId) })
                )
                ok(buildJsonObject { put("group_id", groupId) })
            }
            "status", "stories", "status_feed" -> {
                AgentNavigationHooks.emit(AgentNavCommand.OpenMainTab("STORIES"))
                AgentNavigationHooks.emit(AgentNavCommand.OpenMainDetail("status_feed"))
                ok(buildJsonObject { put("detail", "status_feed") })
            }
            "status_create" -> {
                AgentNavigationHooks.emit(AgentNavCommand.OpenMainTab("STORIES"))
                AgentNavigationHooks.emit(AgentNavCommand.OpenMainDetail("status_create"))
                ok()
            }
            "stickers" -> {
                AgentNavigationHooks.emit(AgentNavCommand.OpenMainDetail("stickers"))
                ok()
            }
            "backup", "backup_settings" -> {
                AgentNavigationHooks.emit(AgentNavCommand.OpenMainDetail("backup_settings"))
                ok()
            }
            "calls" -> {
                AgentNavigationHooks.emit(AgentNavCommand.OpenMainTab("CALLS"))
                ok(buildJsonObject { put("tab", "calls") })
            }
            "profile" -> {
                val userId = body["user_id"]?.jsonPrimitive?.content
                    ?: SecurePreferences.getString(AuthConstants.USER_ID_KEY)
                    ?: return err("Missing user_id")
                AgentNavigationHooks.emit(
                    AgentNavCommand.OpenMainDetail("profile", buildJsonObject { put("user_id", userId) })
                )
                ok(buildJsonObject { put("user_id", userId) })
            }
            else -> err("Unknown nav target: $target")
        }
    }

    override suspend fun listConversations(): JsonObject {
        if (!DI.isInitialized) return err("DI not initialized")
        val list = DI.conversationRepository.getConversations().first()
        return ok(buildJsonObject {
            put("conversations", buildJsonArray {
                list.forEach { c ->
                    add(buildJsonObject {
                        put("conversation_id", c.id)
                        put("type", c.type.name)
                        put("unread_count", c.unreadCount)
                        put("last_message", c.lastMessage ?: "")
                    })
                }
            })
        })
    }

    override suspend fun markConversationRead(conversationId: String): JsonObject {
        if (!DI.isInitialized) return err("DI not initialized")
        val result = DI.apiClient.post("/v1/messages/read", kotlinx.serialization.json.buildJsonObject {
            put("conversation_id", kotlinx.serialization.json.JsonPrimitive(conversationId))
        })
        return result.fold(
            onSuccess = { ok(buildJsonObject { put("conversation_id", conversationId); put("read", true) }) },
            onFailure = { err(it.message ?: "mark read failed") }
        )
    }

    override suspend fun openConversation(conversationId: String): JsonObject {
        AgentNavigationHooks.emit(
            AgentNavCommand.OpenMainDetail("conversation", buildJsonObject { put("conversation_id", conversationId) })
        )
        AgentNavigationHooks.emit(AgentNavCommand.OpenMainTab("CHATS"))
        return ok(buildJsonObject { put("conversation_id", conversationId) })
    }

    override suspend fun listMessages(conversationId: String, limit: Int): JsonObject {
        if (!DI.isInitialized) return err("DI not initialized")
        val messages = DI.conversationRepository.getMessages(conversationId, limit).first()
        return ok(buildJsonObject {
            put("messages", buildJsonArray {
                messages.forEach { m ->
                    add(buildJsonObject {
                        put("envelope_id", m.envelopeId ?: "")
                        put("sender_id", m.senderId)
                        put("content", m.content ?: "")
                        put("status", m.status.name)
                        put("timestamp", m.timestamp)
                        put("media_id", m.mediaId ?: "")
                        put("media_key", m.mediaKey ?: "")
                        put("media_mime_type", m.mediaMimeType ?: "")
                        put("media_size", m.mediaSize?.toString() ?: "")
                        put("is_view_once", m.isViewOnce)
                    })
                }
            })
        })
    }

    override suspend fun setAvatar(): JsonObject {
        if (!DI.isInitialized) return err("DI not initialized")
        return try {
            // 128x128 purple PNG
            val bmp = android.graphics.Bitmap.createBitmap(128, 128, android.graphics.Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(bmp)
            canvas.drawColor(android.graphics.Color.rgb(0x3A, 0x0D, 0x6E))
            val paint = android.graphics.Paint().apply { color = android.graphics.Color.WHITE; textSize = 60f; isAntiAlias = true }
            canvas.drawText("E", 40f, 88f, paint)
            val stream = java.io.ByteArrayOutputStream()
            bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, stream)
            val bytes = stream.toByteArray()
            android.util.Log.w("AVATAR", "png bytes: " + bytes.take(8).joinToString { "%02x".format(it) } + " size=" + bytes.size)
            val set = DI.apiClient.postRaw("/v1/profile/avatar", bytes, "image/png")
            set.fold(
                onSuccess = {
                    val mid = it["avatar_media_id"]?.jsonPrimitive?.content ?: ""
                    ok(buildJsonObject { put("avatar_media_id", mid) })
                },
                onFailure = { err(it.message ?: "set avatar failed") }
            )
        } catch (e: Exception) {
            err(e.message ?: "set avatar failed")
        }
    }

    override suspend fun sendTyping(recipientUserId: String, start: Boolean): JsonObject {
        org.enchant.chat.data.MessageSendPipeline.sendTypingIndicator(recipientUserId, start)
        return ok(buildJsonObject { put("recipient_user_id", recipientUserId); put("typing", start) })
    }

    override suspend fun sendMessage(recipientUserId: String, text: String, sealed: Boolean): JsonObject {
        if (!DI.isInitialized) return err("DI not initialized")
        val conversationId = recipientUserId
        val result = MessageSendPipeline.sendMessage(
            conversationId = conversationId,
            recipientUserId = recipientUserId,
            plaintext = text.encodeToByteArray(),
            useVeil = sealed
        )
        return when (result) {
            is SendResult.Success -> {
                AgentEventLog.emit("message_sent", data = buildJsonObject {
                    put("recipient", recipientUserId)
                    put("envelope_id", result.envelopeId)
                    put("sealed", sealed)
                })
                ok(buildJsonObject { put("envelope_id", result.envelopeId) })
            }
            is SendResult.Queued -> {
                AgentEventLog.emit("message_queued", data = buildJsonObject {
                    put("recipient", recipientUserId)
                    put("message_id", result.messageId)
                })
                ok(buildJsonObject { put("queued", true); put("message_id", result.messageId) })
            }
            is SendResult.Failed -> {
                AgentEventLog.emit("message_failed", ok = false, data = buildJsonObject {
                    put("recipient", recipientUserId)
                    put("error", result.error.name)
                })
                err(result.error.name)
            }
        }
    }

    override suspend fun sendFriendRequest(userId: String): JsonObject {
        if (!DI.isInitialized) return err("DI not initialized")
        val repo = org.enchant.contacts.data.ContactsRepository(DI.apiClient, DI.databasePool)
        return when (val r = repo.sendFriendRequest(userId)) {
            is org.enchant.contacts.data.ContactResult.RequestSent ->
                ok(buildJsonObject { put("status", "request_sent"); put("friend_request_id", r.friendRequestId) })
            is org.enchant.contacts.data.ContactResult.Added ->
                ok(buildJsonObject { put("status", "connected"); put("added", true) })
            is org.enchant.contacts.data.ContactResult.Failed -> err(r.error)
            else -> err("unexpected result")
        }
    }

    override suspend fun acceptFriendRequest(requestId: String): JsonObject {
        if (!DI.isInitialized) return err("DI not initialized")
        val repo = org.enchant.contacts.data.ContactsRepository(DI.apiClient, DI.databasePool)
        return when (val r = repo.acceptFriendRequest(requestId)) {
            is org.enchant.contacts.data.ContactResult.RequestAccepted ->
                ok(buildJsonObject { put("status", "accepted"); put("friend_user_id", r.friendUserId) })
            is org.enchant.contacts.data.ContactResult.Failed -> err(r.error)
            else -> err("unexpected result")
        }
    }

    override suspend fun declineFriendRequest(requestId: String): JsonObject {
        if (!DI.isInitialized) return err("DI not initialized")
        val repo = org.enchant.contacts.data.ContactsRepository(DI.apiClient, DI.databasePool)
        return when (val r = repo.declineFriendRequest(requestId)) {
            is org.enchant.contacts.data.ContactResult.Removed -> ok(buildJsonObject { put("status", "declined") })
            is org.enchant.contacts.data.ContactResult.Failed -> err(r.error)
            else -> err("unexpected result")
        }
    }

    override suspend fun listFriendRequests(): JsonObject {
        if (!DI.isInitialized) return err("DI not initialized")
        val repo = org.enchant.contacts.data.ContactsRepository(DI.apiClient, DI.databasePool)
        val items = repo.listFriendRequests()
        return ok(buildJsonObject {
            put("count", items.size)
            put("requests", kotlinx.serialization.json.buildJsonArray {
                items.forEach { add(kotlinx.serialization.json.buildJsonObject {
                    put("id", it.id)
                    put("from_user_id", it.userId)
                    put("display_name", it.displayName ?: "")
                    put("username", it.username ?: "")
                }) }
            })
        })
    }

    override suspend fun searchByUsername(q: String): JsonObject {
        if (!DI.isInitialized) return err("DI not initialized")
        val result = DI.apiClient.get("/v1/profile/search", mapOf("username" to q))
        return result.fold(
            onSuccess = { ok(it) },
            onFailure = { err(it.message ?: "search failed") }
        )
    }

    override suspend fun listContacts(): JsonObject {
        if (!DI.isInitialized) return err("DI not initialized")
        val repo = ContactsRepository(DI.apiClient, DI.databasePool)
        val contacts = repo.getContacts()
        return ok(buildJsonObject {
            put("contacts", buildJsonArray {
                contacts.forEach { c ->
                    add(buildJsonObject {
                        put("user_id", c.userId)
                        put("username", c.username ?: "")
                        put("display_name", c.displayName ?: "")
                    })
                }
            })
        })
    }

    override suspend fun addContact(userId: String, customName: String?): JsonObject {
        if (!DI.isInitialized) return err("DI not initialized")
        val repo = ContactsRepository(DI.apiClient, DI.databasePool)
        return when (val r = repo.addContact(userId, customName)) {
            is ContactResult.Added -> ok(buildJsonObject { put("added", r.added); put("status", "connected") })
            is ContactResult.RequestSent -> ok(buildJsonObject {
                put("added", false); put("status", "request_sent")
                put("friend_request_id", r.friendRequestId)
            })
            is ContactResult.RequestPending -> ok(buildJsonObject {
                put("added", false); put("status", "request_pending")
                put("friend_request_id", r.friendRequestId)
            })
            is ContactResult.Failed -> err(r.error)
            else -> err("unexpected result")
        }
    }

    override suspend fun removeContact(userId: String): JsonObject {
        if (!DI.isInitialized) return err("DI not initialized")
        val repo = ContactsRepository(DI.apiClient, DI.databasePool)
        return when (val r = repo.removeContact(userId)) {
            is ContactResult.Removed -> ok(buildJsonObject { put("removed", r.removed) })
            is ContactResult.Failed -> err(r.error)
            else -> err("unexpected result")
        }
    }

    override suspend fun listBlockedUsers(): JsonObject {
        if (!DI.isInitialized) return err("DI not initialized")
        val repo = ContactsRepository(DI.apiClient, DI.databasePool)
        val blocked = repo.getBlockedUsers()
        return ok(buildJsonObject {
            put("blocked", buildJsonArray {
                blocked.forEach { c ->
                    add(buildJsonObject {
                        put("user_id", c.userId)
                        put("username", c.username ?: "")
                    })
                }
            })
        })
    }

    override suspend fun getNetworkStatus(): JsonObject = ok(buildJsonObject {
        put("online", ConnectivityMonitor.isOnline.value)
        put("websocket", WebSocketManager.connectionState.value.name)
    })

    override suspend fun connectWebSocket(): JsonObject {
        WebSocketManager.connect()
        AgentEventLog.emit("ws_connect_requested")
        return ok(buildJsonObject { put("websocket", WebSocketManager.connectionState.value.name) })
    }

    override suspend fun disconnectWebSocket(): JsonObject {
        WebSocketManager.disconnect()
        AgentEventLog.emit("ws_disconnect")
        return ok()
    }

    /**
     * Debug-only: run the exact JNI sequence used by key registration step by
     * step, returning each result so a crash can be pinned to one call.
     */
    override suspend fun mlsCreate(groupIdB64: String, epochSecretB64: String): JsonObject {
        return try {
            val groupId = org.enchant.core.crypto.CryptoPrimitives.base64UrlDecode(groupIdB64)
            val secret = org.enchant.core.crypto.CryptoPrimitives.base64UrlDecode(epochSecretB64)
            val state = org.enchant.core.crypto.CryptoPrimitives.mlsGroupCreate(groupId, secret)
            ok(buildJsonObject { put("state_b64", org.enchant.core.crypto.CryptoPrimitives.base64UrlEncode(state)) })
        } catch (e: Exception) {
            err(e.message ?: "mls create failed")
        }
    }

    override suspend fun mlsEncrypt(stateB64: String, plaintextB64: String): JsonObject {
        return try {
            val state = org.enchant.core.crypto.CryptoPrimitives.base64UrlDecode(stateB64)
            val plain = org.enchant.core.crypto.CryptoPrimitives.base64UrlDecode(plaintextB64)
            val cipher = org.enchant.core.crypto.CryptoPrimitives.mlsEncrypt(state, plain)
            ok(buildJsonObject { put("ciphertext_b64", org.enchant.core.crypto.CryptoPrimitives.base64UrlEncode(cipher)) })
        } catch (e: Exception) {
            err(e.message ?: "mls encrypt failed")
        }
    }

    override suspend fun mlsDecrypt(stateB64: String, ciphertextB64: String): JsonObject {
        return try {
            val state = org.enchant.core.crypto.CryptoPrimitives.base64UrlDecode(stateB64)
            val cipher = org.enchant.core.crypto.CryptoPrimitives.base64UrlDecode(ciphertextB64)
            val plain = org.enchant.core.crypto.CryptoPrimitives.mlsDecrypt(state, cipher)
                ?: return err("mls decrypt failed: authentication")
            ok(buildJsonObject {
                put("plaintext_b64", org.enchant.core.crypto.CryptoPrimitives.base64UrlEncode(plain))
                put("plaintext", plain.toString(Charsets.UTF_8))
            })
        } catch (e: Exception) {
            err(e.message ?: "mls decrypt failed")
        }
    }

    override suspend fun resetSession(userId: String): JsonObject {
        if (userId.isBlank()) return err("user_id is required")
        return try {
            org.enchant.core.crypto.VeilSession.get().deleteSession(userId)
            org.enchant.chat.data.MessageSendPipeline.markSessionReset(userId)
            ok(buildJsonObject { put("session_reset", true) })
        } catch (e: Throwable) {
            err(e.message ?: "reset failed")
        }
    }

    override suspend fun debugIdentity(): JsonObject {
        return try {
            val pair = org.enchant.core.crypto.KeyManager.getIdentityKeyPair()
            if (pair == null) return err("no identity key")
            val did = org.enchant.core.base.SecurePreferences.getString("auth.device_id") ?: ""
            ok(buildJsonObject {
                put("identity_public_b64", org.enchant.core.crypto.CryptoHelper.base64UrlEncode(pair.publicKey))
                put("device_id", did)
                put("user_id", org.enchant.core.base.SecurePreferences.getString(org.enchant.core.auth.AuthConstants.USER_ID_KEY) ?: "")
            })
        } catch (e: Exception) {
            err(e.message ?: "debug identity failed")
        }
    }

    override suspend fun testJniSequence(): JsonObject {
        val steps = mutableListOf<kotlinx.serialization.json.JsonObject>()
        try {
            android.util.Log.e("JNITEST", "step: identity")
            val ik = org.enchant.core.crypto.CryptoPrimitives.generateX25519KeyPair()
            steps.add(buildJsonObject { put("step", "identity"); put("ok", true) })

            android.util.Log.e("JNITEST", "step: batch")
            val batch = ByteArray(100 * 68)
            val batchLen = longArrayOf(batch.size.toLong())
            val rc2 = org.enchant.core.crypto.EnchantCrypto.enchant_prekey_generate_batch(100, 1, batch, batchLen)
            steps.add(buildJsonObject { put("step", "generate_batch"); put("rc", rc2); put("len", batchLen[0]) })
            if (rc2 != 0) return ok(buildJsonObject { put("steps", buildJsonArray { steps.forEach { add(it) } }) })

            android.util.Log.e("JNITEST", "step: secure_zero")
            org.enchant.core.crypto.EnchantCrypto.enchant_secure_zero(batch, batch.size.toLong())
            steps.add(buildJsonObject { put("step", "secure_zero"); put("ok", true) })

            android.util.Log.e("JNITEST", "step: generate_signed")
            val spkPub = ByteArray(32); val spkPriv = ByteArray(32); val spkSig = ByteArray(64)
            val sigLen = longArrayOf(64)
            val rc1 = org.enchant.core.crypto.EnchantCrypto.enchant_prekey_generate_signed(
                1, ik.privateKey, spkPub, spkPriv, spkSig, sigLen
            )
            steps.add(buildJsonObject { put("step", "generate_signed"); put("rc", rc1) })
            android.util.Log.e("JNITEST", "step: done")
        } catch (e: Throwable) {
            android.util.Log.e("JNITEST", "step: EXCEPTION " + e)
            steps.add(buildJsonObject { put("step", "EXCEPTION"); put("error", e.toString()) })
        }
        return ok(buildJsonObject { put("steps", buildJsonArray { steps.forEach { add(it) } }) })
    }

    override suspend fun getCryptoStatus(): JsonObject {
        val hasKeys = KeyManager.hasKeys()
        val pub = KeyManager.getIdentityPublicKeyBase64()
        val preKeyStore = if (DI.isInitialized) DI.preKeyStore else null
        val veil = runCatching { org.enchant.core.crypto.VeilSession.get() }.getOrNull()
        val nativeOpks = buildJsonArray {
            if (preKeyStore != null) {
                preKeyStore.getAllOneTimePreKeyIds().sorted().forEach { id ->
                    val nativePriv = veil?.getNativeOneTimePrekeyPrivate(id)
                    add(buildJsonObject {
                        put("id", id)
                        put("kotlin_fp", preKeyStore.getOneTimePreKey(id)?.let {
                            org.enchant.core.crypto.CryptoPrimitives.sha256(it.privateKey)
                                .take(8).joinToString("") { "%02x".format(it) }
                        } ?: "")
                        put("native_fp", nativePriv?.let {
                            org.enchant.core.crypto.CryptoPrimitives.sha256(it)
                                .take(8).joinToString("") { "%02x".format(it) }
                        } ?: "MISSING")
                    })
                }
            }
        }
        val nativeSpkFp = veil?.let { v ->
            preKeyStore?.getCurrentSignedPreKey()?.let { spk ->
                v.getNativeSignedPrekeyPrivate(spk.id)?.let {
                    org.enchant.core.crypto.CryptoPrimitives.sha256(it)
                        .take(8).joinToString("") { "%02x".format(it) }
                }
            }
        } ?: ""
        val nativeIdentity = runCatching {
            org.enchant.core.crypto.VeilSession.get().getLocalIdentityPublicKey()
                ?.let { CryptoHelper.base64UrlEncode(it) }
        }.getOrNull()
        val peerBundle = runCatching {
            KeyManager.fetchKeyBundle("793dbb3b-5008-4eea-80f8-5b57c8fb00bb")
        }.getOrNull()
        return ok(buildJsonObject {
            put("has_identity_key", hasKeys)
            put("identity_public_b64", pub ?: "")
            put("native_identity_public_b64", nativeIdentity ?: "")
            put("native_signed_prekey_fp", nativeSpkFp)
            put("native_one_time_prekeys", nativeOpks)
            if (peerBundle != null) {
                put("fetched_bundle", buildJsonObject {
                    put("device_id", peerBundle.deviceId)
                    put("ik_pub", CryptoHelper.base64UrlEncode(peerBundle.identityKey))
                    put("spk_id", peerBundle.signedPrekeyId)
                    put("spk_pub", CryptoHelper.base64UrlEncode(peerBundle.signedPrekey.publicKey))
                    peerBundle.oneTimePrekey?.let { put("opk_pub", CryptoHelper.base64UrlEncode(it)) }
                    put("opk_id", peerBundle.oneTimePrekeyId)
                })
            }
            KeyManager.getIdentityKeyPair()?.let { ik ->
                put("identity_private_fp", org.enchant.core.crypto.CryptoPrimitives.sha256(ik.privateKey)
                    .take(8).joinToString("") { "%02x".format(it) })
            }
            if (preKeyStore != null) {
                preKeyStore.getCurrentSignedPreKey()?.let { record ->
                    put("signed_prekey", buildJsonObject {
                        put("id", record.id)
                        put("public_key", CryptoHelper.base64UrlEncode(record.publicKey))
                        put("private_fp", org.enchant.core.crypto.CryptoPrimitives.sha256(record.privateKey)
                            .take(8).joinToString("") { "%02x".format(it) })
                    })
                }
                put("one_time_prekeys", buildJsonArray {
                    preKeyStore.getAllOneTimePreKeyIds().sorted().forEach { id ->
                        preKeyStore.getOneTimePreKey(id)?.let { record ->
                            add(buildJsonObject {
                                put("id", id)
                                put("public_key", CryptoHelper.base64UrlEncode(record.publicKey))
                                put("private_fp", org.enchant.core.crypto.CryptoPrimitives.sha256(record.privateKey)
                                    .take(8).joinToString("") { "%02x".format(it) })
                            })
                        }
                    }
                })
            }
        })
    }

    override suspend fun sendMediaMessage(
        recipientUserId: String,
        conversationId: String,
        filePath: String,
        mimeType: String,
        fileName: String?,
        isViewOnce: Boolean
    ): JsonObject {
        if (!DI.isInitialized) return err("DI not initialized")
        val ctx = AppConfig.applicationContext ?: return err("App context not available")
        val file = File(filePath)
        if (!file.exists()) return err("File not found: $filePath")
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", file)
        val name = fileName ?: file.name
        val result = MessageSendPipeline.sendFileMessage(
            conversationId = conversationId,
            recipientUserId = recipientUserId,
            fileUri = uri,
            fileName = name,
            mimeType = mimeType,
            isViewOnce = isViewOnce
        )
        return sendResultToJson(result, recipientUserId, "media")
    }

    override suspend fun sendReaction(
        conversationId: String,
        emoji: String,
        envelopeId: String?,
        messageLocalId: Long?
    ): JsonObject {
        if (!DI.isInitialized) return err("DI not initialized")
        val messageId = envelopeId ?: messageLocalId?.let { localId ->
            DI.conversationRepository.getMessageByLocalId(localId)?.envelopeId
        } ?: return err("Provide envelope_id or message_local_id")
        return MessageSendPipeline.sendReaction(messageId, emoji, conversationId).fold(
            onSuccess = {
                AgentEventLog.emit("reaction_sent", data = buildJsonObject {
                    put("conversation_id", conversationId)
                    put("emoji", emoji)
                    put("envelope_id", messageId)
                })
                ok(buildJsonObject { put("envelope_id", messageId) })
            },
            onFailure = { err(it.message ?: "reaction failed") }
        )
    }

    override suspend fun sendSticker(
        recipientUserId: String,
        conversationId: String,
        packId: String,
        stickerId: String
    ): JsonObject {
        if (!DI.isInitialized) return err("DI not initialized")
        DI.apiClient.post("/v1/stickers/recent/$stickerId", buildJsonObject { put("pack_id", packId) })
        val stickerPayload = "STICKER_JSON:$packId:$stickerId"
        val result = MessageSendPipeline.sendMessage(
            conversationId = conversationId,
            recipientUserId = recipientUserId,
            plaintext = stickerPayload.encodeToByteArray(),
            useVeil = false
        )
        return sendResultToJson(result, recipientUserId, "sticker")
    }

    override suspend fun listGroups(): JsonObject {
        if (!DI.isInitialized) return err("DI not initialized")
        val repo = GroupsRepository(DI.apiClient, DI.databasePool)
        val groups = repo.getGroups()
        return ok(buildJsonObject {
            put("groups", buildJsonArray {
                groups.forEach { g ->
                    add(buildJsonObject {
                        put("group_id", g.groupId)
                        put("name", g.name)
                        put("role", g.myRole.value)
                        put("member_count", g.memberCount)
                    })
                }
            })
        })
    }

    override suspend fun createGroup(
        name: String,
        description: String?,
        initialMemberIds: List<String>?,
        addMembersPolicy: String,
        joinType: String
    ): JsonObject {
        if (!DI.isInitialized) return err("DI not initialized")
        val repo = GroupsRepository(DI.apiClient, DI.databasePool)
        return groupResultToJson(repo.createGroup(name, description, initialMemberIds, addMembersPolicy, joinType))
    }

    override suspend fun addGroupMembers(groupId: String, userIds: List<String>): JsonObject {
        if (!DI.isInitialized) return err("DI not initialized")
        val repo = GroupsRepository(DI.apiClient, DI.databasePool)
        return groupResultToJson(repo.addMembers(groupId, userIds))
    }

    override suspend fun updateGroupSettings(groupId: String, name: String?, description: String?): JsonObject {
        if (!DI.isInitialized) return err("DI not initialized")
        val repo = GroupsRepository(DI.apiClient, DI.databasePool)
        return groupResultToJson(repo.updateGroup(groupId, name, description))
    }

    override suspend fun getGroupInfo(groupId: String): JsonObject {
        if (!DI.isInitialized) return err("DI not initialized")
        val repo = GroupsRepository(DI.apiClient, DI.databasePool)
        return groupResultToJson(repo.getGroupInfo(groupId))
    }

    override suspend fun removeGroupMember(groupId: String, userId: String): JsonObject {
        if (!DI.isInitialized) return err("DI not initialized")
        val repo = GroupsRepository(DI.apiClient, DI.databasePool)
        return groupResultToJson(repo.removeMember(groupId, userId))
    }

    override suspend fun listGroupMembers(groupId: String): JsonObject {
        if (!DI.isInitialized) return err("DI not initialized")
        val repo = GroupsRepository(DI.apiClient, DI.databasePool)
        val members = repo.getMembers(groupId)
        return ok(buildJsonObject {
            put("members", buildJsonArray {
                members.forEach { m ->
                    add(buildJsonObject {
                        put("user_id", m.userId)
                        put("role", m.role.value)
                    })
                }
            })
        })
    }

    override suspend fun sendGroupMessage(groupId: String, text: String): JsonObject {
        if (!DI.isInitialized) return err("DI not initialized")
        val repo = GroupsRepository(DI.apiClient, DI.databasePool)
        val members = repo.getMembers(groupId).map { it.userId }
        val result = org.enchant.chat.data.MessageSendPipeline.sendGroupMessage(
            groupId, members, text.encodeToByteArray()
        )
        return if (result is org.enchant.chat.data.SendResult.Success ||
            result is org.enchant.chat.data.SendResult.Queued) {
            AgentEventLog.emit("group_message_sent", data = buildJsonObject {
                put("group_id", groupId); put("members", members.size)
            })
            ok(buildJsonObject { put("sent", true); put("members", members.size) })
        } else {
            err("group message send failed")
        }
    }

    override suspend fun clearConversation(conversationId: String): JsonObject {
        if (!DI.isInitialized) return err("DI not initialized")
        val pool = DI.databasePool
        pool.write { db ->
            db.execSQL("DELETE FROM messages WHERE conversation_id = ?", arrayOf(conversationId))
            db.execSQL("DELETE FROM conversations WHERE conversation_id = ?", arrayOf(conversationId))
        }
        return ok(buildJsonObject { put("cleared", true) })
    }

    override suspend fun broadcastGroupSenderKey(groupId: String): JsonObject {
        if (!DI.isInitialized) return err("DI not initialized")
        val repo = GroupsRepository(DI.apiClient, DI.databasePool)
        val members = repo.getMembers(groupId).map { it.userId }
        val result = org.enchant.chat.data.MessageSendPipeline.sendGroupSenderKeyDistribution(groupId, members)
        return if (result is org.enchant.chat.data.SendResult.Success) {
            AgentEventLog.emit("group_sender_key_broadcast", data = buildJsonObject {
                put("group_id", groupId)
                put("members", members.size)
            })
            ok(buildJsonObject { put("broadcast", true); put("members", members.size) })
        } else {
            err("sender key broadcast failed")
        }
    }

    override suspend fun joinGroupViaLink(linkCode: String): JsonObject {
        if (!DI.isInitialized) return err("DI not initialized")
        val repo = GroupsRepository(DI.apiClient, DI.databasePool)
        return groupResultToJson(repo.joinViaLink(linkCode))
    }

    override suspend fun startCall(remoteUserId: String, isVideo: Boolean): JsonObject {
        // The calls module is wired late in DI.init (after the WebRTC engine
        // boots); wait for it so the first call doesn't race initialization.
        var attempts = 0
        while (!DI.isInitialized && attempts < 40) {
            attempts++
            kotlinx.coroutines.delay(500)
        }
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            var ready = false
            for (i in 0 until 40) {
                if (runCatching { org.enchant.core.calls.CallsModule.getCallManager() }.isSuccess) {
                    ready = true
                    break
                }
                kotlinx.coroutines.delay(500)
            }
            if (!ready) return@withContext err("CallManager not ready")
            CallManager.startOutgoingCall(remoteUserId, isVideo)
        }
        AgentEventLog.emit("call_started", data = buildJsonObject {
            put("remote_user_id", remoteUserId)
            put("video", isVideo)
        })
        return ok(buildJsonObject {
            put("remote_user_id", remoteUserId)
            put("video", isVideo)
            put("call_state", CallManager.callState.value.status.name)
        })
    }

    override suspend fun groupCredential(groupId: String): JsonObject {
        if (!DI.isInitialized) return err("DI not initialized")
        val result = runCatching {
            DI.apiClient.post("/v1/groups/$groupId/credential", kotlinx.serialization.json.buildJsonObject { })
        }.getOrNull()?.getOrNull() ?: return err("credential fetch failed")
        val credHex = result["credential"]?.jsonPrimitive?.content ?: return err("no credential")
        // Store locally (keyed by group) for later presentations.
        org.enchant.core.base.SecurePreferences.putString("group_cred_$groupId", credHex)
        return ok(buildJsonObject { put("credential", credHex) })
    }

    override suspend fun groupCredentialPresent(groupId: String): JsonObject {
        if (!DI.isInitialized) return err("DI not initialized")
        val credHex = org.enchant.core.base.SecurePreferences.getString("group_cred_$groupId")
            ?: return err("no credential stored — fetch one first")
        val cred = runCatching {
            val hex = credHex
            ByteArray(hex.length / 2) { i -> hex.substring(i * 2, i * 2 + 2).toInt(16).toByte() }
        }.getOrNull() ?: return err("bad stored credential")
        if (cred.size != 32) return err("bad credential length")
        val presentation = ByteArray(96)
        val len = longArrayOf(96)
        val rc = org.enchant.core.crypto.EnchantCrypto.enchant_group_credential_present(
            cred, 32, cred, presentation, len
        )
        if (rc != 0) return err("present failed rc=$rc")
        return ok(buildJsonObject {
            put("presentation", org.enchant.core.crypto.CryptoPrimitives.base64UrlEncode(presentation.copyOf(len[0].toInt())))
        })
    }

    override suspend fun verifyGroupCredential(groupId: String, presentation: String): JsonObject {
        if (!DI.isInitialized) return err("DI not initialized")
        val body = kotlinx.serialization.json.buildJsonObject {
            put("presentation", presentation)
        }
        val result = runCatching {
            DI.apiClient.post("/v1/groups/$groupId/credential/verify", body)
        }.getOrNull()?.getOrNull() ?: return err("verify failed")
        return ok(buildJsonObject { put("valid", result["valid"]?.jsonPrimitive?.content == "true") })
    }

    override suspend fun keyBundle(userId: String): JsonObject {        if (!DI.isInitialized) return err("DI not initialized")
        val json = runCatching { DI.apiClient.get("/v1/keys/bundle/$userId").getOrThrow() }.getOrNull()
            ?: return err("bundle fetch failed")
        return ok(json)
    }

    override suspend fun ktTreeHeadPublicKey(): JsonObject {
        if (!DI.isInitialized) return err("DI not initialized")
        val json = runCatching { DI.apiClient.get("/v1/keys/sth/public-key").getOrThrow() }.getOrNull()
            ?: return err("public key fetch failed")
        return ok(json)
    }

    override suspend fun ktTreeHead(): JsonObject {
        if (!DI.isInitialized) return err("DI not initialized")
        val result = org.enchant.core.crypto.KeyTransparencyVerifier.fetchLatestTreeHead(DI.apiClient)
        return result.fold(
            onSuccess = { head ->
                val sigOk = kotlinx.coroutines.runBlocking {
                    org.enchant.core.crypto.KeyTransparencyVerifier.verifyTreeHeadSignature(DI.apiClient, head)
                }
                ok(buildJsonObject {
                    put("tree_size", head.treeSize)
                    put("root_hash", org.enchant.core.crypto.CryptoPrimitives.base64UrlEncode(head.rootHash))
                    put("signature_valid", sigOk)
                })
            },
            onFailure = { err(it.message ?: "sth failed") }
        )
    }

    override suspend fun ktVerifyIdentity(userId: String, deviceId: String): JsonObject {
        if (!DI.isInitialized) return err("DI not initialized")
        val key = org.enchant.core.crypto.NativeSessionManager.getIdentityKey(userId)
            ?: return err("no identity key for $userId")
        val bundleOk = org.enchant.core.crypto.KeyTransparencyVerifier.verifyIdentityViaBundle(
            DI.apiClient, userId, key
        )
        val actualDevice = if (deviceId.isBlank()) {
            org.enchant.core.crypto.NativeSessionManager.getPeerDeviceId(userId) ?: ""
        } else deviceId
        val consistent = org.enchant.core.crypto.KeyTransparencyVerifier.verifyServerConsistency(
            DI.apiClient, userId, actualDevice
        )
        return ok(buildJsonObject {
            put("verified", bundleOk)
            put("bundle_matches", bundleOk)
            put("server_consistent", consistent)
            put("user_id", userId)
        })
    }

    override suspend fun discoverChannels(): JsonObject {
        if (!DI.isInitialized) return err("DI not initialized")
        val vm = org.enchant.channels.ChannelViewModel(DI.apiClient)
        kotlinx.coroutines.runBlocking { vm.discoverChannels() }
        val channels = vm.uiState.value.discoverResults
        return ok(buildJsonObject {
            put("channels", buildJsonArray {
                channels.forEach { c ->
                    add(buildJsonObject {
                        put("channel_id", c.channelId)
                        put("name", c.name)
                        put("description", c.description ?: "")
                    })
                }
            })
        })
    }

    override suspend fun createChannel(name: String, description: String?): JsonObject {
        if (!DI.isInitialized) return err("DI not initialized")
        val vm = org.enchant.channels.ChannelViewModel(DI.apiClient)
        return runCatching {
            kotlinx.coroutines.runBlocking { vm.createChannel(name, description) }
            ok(buildJsonObject { put("created", name) })
        }.getOrElse { err(it.message ?: "channel create failed") }
    }

    override suspend fun subscribeChannel(channelId: String): JsonObject {
        if (!DI.isInitialized) return err("DI not initialized")
        val vm = org.enchant.channels.ChannelViewModel(DI.apiClient)
        return runCatching {
            kotlinx.coroutines.runBlocking { vm.subscribe(channelId) }
            ok(buildJsonObject { put("subscribed", channelId) })
        }.getOrElse { err(it.message ?: "subscribe failed") }
    }

    override suspend fun channelFeed(channelId: String): JsonObject {
        if (!DI.isInitialized) return err("DI not initialized")
        val vm = org.enchant.channels.ChannelViewModel(DI.apiClient)
        return runCatching {
            kotlinx.coroutines.runBlocking { vm.loadFeed(channelId) }
            ok(buildJsonObject { put("posts", vm.uiState.value.feed.size) })
        }.getOrElse { err(it.message ?: "feed failed") }
    }

    override suspend fun syncDeviceContacts(): JsonObject {
        if (!DI.isInitialized) return err("DI not initialized")
        val ctx = AppConfig.applicationContext ?: return err("no context")
        val service = org.enchant.contacts.ContactSyncService(DI.apiClient, ctx.contentResolver)
        val result = service.syncContacts()
        return result.fold(
            onSuccess = { matched ->
                ok(buildJsonObject {
                    put("matched", matched.size)
                    put("contacts", buildJsonArray {
                        matched.forEach { m ->
                            add(buildJsonObject {
                                put("user_id", m.userId)
                                put("display_name", m.displayName ?: "")
                                put("username", m.username ?: "")
                            })
                        }
                    })
                })
            },
            onFailure = { err(it.message ?: "sync failed") }
        )
    }

    override suspend fun discoverySalt(): JsonObject {
        if (!DI.isInitialized) return err("DI not initialized")
        val result = org.enchant.contacts.ContactSyncService.fetchDiscoverySalt(DI.apiClient)
        return result.fold(
            onSuccess = { salt ->
                ok(buildJsonObject {
                    put("discovery_salt", salt.joinToString("") { b -> "%02x".format(b) })
                })
            },
            onFailure = { err(it.message ?: "salt fetch failed") }
        )
    }

    override suspend fun discoverContacts(phoneNumbers: List<String>): JsonObject {
        if (!DI.isInitialized) return err("DI not initialized")
        val discovery = org.enchant.contacts.ContactDiscovery(DI.apiClient)
        val result = discovery.discoverContacts(phoneNumbers)
        return result.fold(
            onSuccess = { found ->
                ok(buildJsonObject {
                    put("found", found.size)
                    put("contacts", buildJsonArray {
                        found.forEach { c ->
                            add(buildJsonObject {
                                put("user_id", c.userId ?: "")
                                put("display_name", c.displayName ?: "")
                                put("is_registered", c.isRegistered)
                            })
                        }
                    })
                })
            },
            onFailure = { err(it.message ?: "discover failed") }
        )
    }

    override suspend fun createPoll(
        conversationId: String, question: String, optionTexts: List<String>
    ): JsonObject {
        if (!DI.isInitialized) return err("DI not initialized")
        val body = kotlinx.serialization.json.buildJsonObject {
            put("conversation_id", conversationId)
            put("question", question)
            put("options", kotlinx.serialization.json.JsonArray(
                optionTexts.map { kotlinx.serialization.json.buildJsonObject { put("text", it) } }
            ))
            put("allow_multiple", false)
            put("anonymous", false)
        }
        return DI.apiClient.post("/v1/polls", body).fold(
            onSuccess = { ok(buildJsonObject { put("poll_id", it["poll_id"]?.jsonPrimitive?.content ?: "") }) },
            onFailure = { err(it.message ?: "poll create failed") }
        )
    }

    override suspend fun votePoll(pollId: String, optionIds: List<String>): JsonObject {
        if (!DI.isInitialized) return err("DI not initialized")
        val body = kotlinx.serialization.json.buildJsonObject {
            put("option_ids", kotlinx.serialization.json.JsonArray(optionIds.map { kotlinx.serialization.json.JsonPrimitive(it) }))
        }
        return DI.apiClient.post("/v1/polls/$pollId/vote", body).fold(
            onSuccess = { ok(buildJsonObject { put("voted", pollId) }) },
            onFailure = { err(it.message ?: "poll vote failed") }
        )
    }

    override suspend fun blockUser(userId: String): JsonObject {
        if (!DI.isInitialized) return err("DI not initialized")
        val result = DI.apiClient.post("/v1/blocks/$userId", kotlinx.serialization.json.buildJsonObject { })
        return result.fold(
            onSuccess = { ok(buildJsonObject { put("blocked", userId) }) },
            onFailure = { err(it.message ?: "block failed") }
        )
    }

    override suspend fun unblockUser(userId: String): JsonObject {
        if (!DI.isInitialized) return err("DI not initialized")
        val result = DI.apiClient.del("/v1/blocks/$userId")
        return result.fold(
            onSuccess = { ok(buildJsonObject { put("unblocked", userId) }) },
            onFailure = { err(it.message ?: "unblock failed") }
        )
    }

    override suspend fun getCallManagerStatus(): JsonObject {
        val managerOk = runCatching { org.enchant.core.calls.CallsModule.getCallManager() }.isSuccess
        return ok(buildJsonObject {
            put("di_initialized", DI.isInitialized)
            put("call_manager_ready", managerOk)
            put("call_state", runCatching { CallManager.callState.value.status.name }.getOrDefault("ERR"))
        })
    }

    override suspend fun acceptCall(): JsonObject {
        val ok = runCatching { org.enchant.core.calls.CallManager.acceptCall(false) }.isSuccess
        return ok(buildJsonObject { put("accepted", ok) })
    }

    override suspend fun hangupCall(): JsonObject {
        val ok = runCatching { org.enchant.core.calls.CallManager.endCall() }.isSuccess
        return ok(buildJsonObject { put("ended", ok) })
    }

    override suspend fun denyCall(): JsonObject {
        val ok = runCatching { org.enchant.core.calls.CallManager.denyCall() }.isSuccess
        return ok(buildJsonObject { put("denied", ok) })
    }

    override suspend fun listCallLog(limit: Int): JsonObject {
        val logs = CallManager.getCallLogs(limit)
        return ok(buildJsonObject {
            put("calls", buildJsonArray {
                logs.forEach { entry ->
                    add(buildJsonObject {
                        put("call_id", entry.callId)
                        put("remote_user_id", entry.remoteUserId)
                        put("remote_name", entry.remoteName ?: "")
                        put("type", entry.type.name)
                        put("direction", entry.direction.name)
                        put("status", entry.status.name)
                        put("duration_seconds", entry.durationSeconds)
                        put("timestamp", entry.timestamp)
                    })
                }
            })
        })
    }

    override suspend fun listStatusFeed(): JsonObject {
        if (!DI.isInitialized) return err("DI not initialized")
        return DI.apiClient.get("/v1/status/feed").fold(
            onSuccess = { json ->
                ok(buildJsonObject {
                    json["feed"]?.let { put("feed", it) }
                    json["my_status"]?.let { put("my_status", it) }
                })
            },
            onFailure = { err(it.message ?: "status feed failed") }
        )
    }

    override suspend fun createTextStatus(
        text: String,
        backgroundColor: String,
        privacy: String,
        selectedContacts: List<String>?
    ): JsonObject {
        if (!DI.isInitialized) return err("DI not initialized")
        if (text.length > 700) return err("Status text exceeds 700 characters")
        val privacyObj = parseStatusPrivacy(privacy, selectedContacts)
        val body = buildJsonObject {
            put("status_type", "TEXT")
            put("text_content", text)
            put("text_background", backgroundColor)
            put("privacy_setting", statusPrivacyToStr(privacyObj))
            if (privacyObj is StatusPrivacy.Selected && selectedContacts != null) {
                put("selected_contacts", JsonArray(selectedContacts.map { JsonPrimitive(it) }))
            }
        }
        return DI.apiClient.post("/v1/status", body).fold(
            onSuccess = {
                AgentEventLog.emit("status_created", data = buildJsonObject { put("type", "text") })
                ok(buildJsonObject { put("type", "text") })
            },
            onFailure = { err(it.message ?: "create status failed") }
        )
    }

    override suspend fun createMediaStatus(
        mediaId: String,
        privacy: String,
        selectedContacts: List<String>?
    ): JsonObject {
        if (!DI.isInitialized) return err("DI not initialized")
        val privacyObj = parseStatusPrivacy(privacy, selectedContacts)
        val body = buildJsonObject {
            put("status_type", "IMAGE")
            put("media_id", mediaId)
            put("privacy_setting", statusPrivacyToStr(privacyObj))
            if (privacyObj is StatusPrivacy.Selected && selectedContacts != null) {
                put("selected_contacts", JsonArray(selectedContacts.map { JsonPrimitive(it) }))
            }
        }
        return DI.apiClient.post("/v1/status", body).fold(
            onSuccess = {
                AgentEventLog.emit("status_created", data = buildJsonObject { put("type", "media") })
                ok(buildJsonObject { put("type", "media"); put("media_id", mediaId) })
            },
            onFailure = { err(it.message ?: "create status failed") }
        )
    }

    override suspend fun viewStatus(statusId: String): JsonObject {
        if (!DI.isInitialized) return err("DI not initialized")
        return DI.apiClient.get("/v1/status/$statusId").fold(
            onSuccess = { ok(buildJsonObject { put("status_id", statusId) }) },
            onFailure = { err(it.message ?: "view status failed") }
        )
    }

    override suspend fun listStickerLibrary(): JsonObject {
        if (!DI.isInitialized) return err("DI not initialized")
        return DI.apiClient.get("/v1/stickers/library").fold(
            onSuccess = { json -> ok(buildJsonObject { json["packs"]?.let { put("packs", it) } }) },
            onFailure = { err(it.message ?: "sticker library failed") }
        )
    }

    override suspend fun listFeaturedStickers(): JsonObject {
        if (!DI.isInitialized) return err("DI not initialized")
        return DI.apiClient.get("/v1/stickers/packs/featured").fold(
            onSuccess = { json -> ok(buildJsonObject { json["packs"]?.let { put("packs", it) } }) },
            onFailure = { err(it.message ?: "featured stickers failed") }
        )
    }

    override suspend fun installStickerPack(packId: String): JsonObject {
        if (!DI.isInitialized) return err("DI not initialized")
        return DI.apiClient.post("/v1/stickers/library/$packId").fold(
            onSuccess = { ok(buildJsonObject { put("pack_id", packId); put("installed", true) }) },
            onFailure = { err(it.message ?: "install sticker pack failed") }
        )
    }

    override suspend fun backupCloudInitiate(): JsonObject {
        if (!DI.isInitialized) return err("DI not initialized")
        return DI.apiClient.post("/v1/backup", buildJsonObject { put("action", "initiate") }).fold(
            onSuccess = { json ->
                ok(buildJsonObject {
                    put("backup_id", json["backup_id"]?.jsonPrimitive?.content ?: "")
                })
            },
            onFailure = { err(it.message ?: "backup initiate failed") }
        )
    }

    override suspend fun backupCloudLatest(): JsonObject {
        if (!DI.isInitialized) return err("DI not initialized")
        return DI.apiClient.get("/v1/backup/latest").fold(
            onSuccess = { json ->
                ok(buildJsonObject {
                    put("backup_id", json["backup_id"]?.jsonPrimitive?.content ?: "")
                    put("size_bytes", json["size_bytes"]?.jsonPrimitive?.content ?: "0")
                    put("section_count", json["section_count"]?.jsonPrimitive?.content ?: "0")
                    put("created_at", json["created_at"]?.jsonPrimitive?.content ?: "")
                    put("sections", json["sections"]?.jsonArray ?: buildJsonArray {})
                })
            },
            onFailure = { err(it.message ?: "backup latest failed") }
        )
    }

    override suspend fun backupCloudRestore(backupId: String): JsonObject {
        if (!DI.isInitialized) return err("DI not initialized")
        return DI.apiClient.post("/v1/backup/$backupId/restore").fold(
            onSuccess = { ok(buildJsonObject { put("backup_id", backupId); put("restored", true) }) },
            onFailure = { err(it.message ?: "backup restore failed") }
        )
    }

    /** SVR-style: derive the backup key from the registration PIN via
     *  Argon2id with a fixed salt, so the same PIN always yields the same
     *  key and the backup is recoverable with only the PIN. */
    private fun deriveBackupKeyFromPin(pin: String): ByteArray? {
        // Fixed per-account salt: the backup salt is stored alongside the
        // backup, but for local backups we derive it from the PIN domain.
        val salt = java.security.MessageDigest.getInstance("SHA-256")
            .digest((pin + ":enchant-backup").toByteArray(Charsets.UTF_8))
            .copyOf(16)
        val key = ByteArray(32)
        val rc = org.enchant.core.crypto.EnchantCrypto.enchant_argon2id_hash_with_params(
            pin.toByteArray(Charsets.UTF_8), pin.length.toLong(),
            salt, salt.size.toLong(),
            3, 64 * 1024, 1,
            key, key.size.toLong()
        )
        return if (rc == 0) key else null
    }

    override suspend fun backupLocalExport(outputPath: String, backupKeyB64: String, pin: String?): JsonObject {
        if (!DI.isInitialized) return err("DI not initialized")
        val ctx = AppConfig.applicationContext ?: return err("App context not available")
        return try {
            val keyBytes = if (pin != null) {
                deriveBackupKeyFromPin(pin) ?: return err("pin key derivation failed")
            } else {
                CryptoHelper.base64UrlDecode(backupKeyB64)
            }
            BackupExporter(DI.databasePool, ctx).exportFullBackup(outputPath, keyBytes).fold(
                onSuccess = {
                    AgentEventLog.emit("backup_local_export", data = buildJsonObject { put("path", outputPath) })
                    ok(buildJsonObject { put("output_path", outputPath) })
                },
                onFailure = { err(it.message ?: "local export failed") }
            )
        } catch (e: Exception) {
            err(e.message ?: "invalid backup_key_b64")
        }
    }

    override suspend fun backupLocalImport(
        inputPath: String,
        backupKeyB64: String,
        sections: List<String>,
        pin: String?
    ): JsonObject {
        if (!DI.isInitialized) return err("DI not initialized")
        val parsed = sections.mapNotNull { name ->
            runCatching { BackupSection.valueOf(name.uppercase()) }.getOrNull()
        }.toSet()
        val importKey = if (pin != null) {
            deriveBackupKeyFromPin(pin) ?: return err("pin key derivation failed")
        } else {
            CryptoHelper.base64UrlDecode(backupKeyB64)
        }
        if (parsed.isEmpty()) return err("No valid sections (CHATS, CONTACTS, GROUPS, CALLS, SETTINGS)")
        return try {
            val keyBytes = importKey
            BackupExporter(DI.databasePool, AppConfig.applicationContext!!).importFullBackup(
                inputPath, keyBytes, parsed
            ).fold(
                onSuccess = {
                    AgentEventLog.emit("backup_local_import", data = buildJsonObject { put("path", inputPath) })
                    ok(buildJsonObject { put("input_path", inputPath); put("sections", sections.joinToString(",")) })
                },
                onFailure = { err(it.message ?: "local import failed") }
            )
        } catch (e: Exception) {
            err(e.message ?: "invalid backup_key_b64")
        }
    }

    override suspend fun appLockSetPin(pin: String): JsonObject {
        if (pin.length != 6 || pin.any { !it.isDigit() }) {
            return err("PIN must be exactly 6 digits")
        }
        return try {
            val hash = hashPinArgon2(pin)
            SecurePreferences.putString("applock.pin_hash", hash)
            SecurePreferences.putBoolean("applock.enabled", true)
            AgentEventLog.emit("applock_set")
            ok(buildJsonObject { put("enabled", true) })
        } catch (e: Exception) {
            err(e.message ?: "set PIN failed")
        }
    }

    override suspend fun appLockVerifyPin(pin: String): JsonObject {
        val storedHash = SecurePreferences.getString("applock.pin_hash")
            ?: return err("App lock PIN not set")
        val valid = verifyPinArgon2(pin, storedHash)
        AgentEventLog.emit("applock_verify", ok = valid)
        return if (valid) ok(buildJsonObject { put("verified", true) })
        else err("Invalid PIN")
    }

    override suspend fun appLockDisable(): JsonObject {
        SecurePreferences.putBoolean("applock.enabled", false)
        SecurePreferences.remove("applock.pin_hash")
        SecurePreferences.putBoolean("applock.biometric", false)
        AgentEventLog.emit("applock_disabled")
        return ok(buildJsonObject { put("enabled", false) })
    }

    private fun sendResultToJson(result: SendResult, recipient: String, kind: String): JsonObject = when (result) {
        is SendResult.Success -> {
            AgentEventLog.emit("${kind}_sent", data = buildJsonObject {
                put("recipient", recipient)
                put("envelope_id", result.envelopeId)
            })
            ok(buildJsonObject { put("envelope_id", result.envelopeId) })
        }
        is SendResult.Queued -> {
            AgentEventLog.emit("${kind}_queued", data = buildJsonObject {
                put("recipient", recipient)
                put("message_id", result.messageId)
            })
            ok(buildJsonObject { put("queued", true); put("message_id", result.messageId) })
        }
        is SendResult.Failed -> err(result.error.name)
    }

    private fun groupResultToJson(result: GroupResult): JsonObject = when (result) {
        is GroupResult.Success -> ok(buildJsonObject {
            put("group_id", result.groupId)
            put("name", result.name)
            put("member_count", result.memberCount)
            put("role", result.role.value)
        })
        is GroupResult.MemberAdded -> ok(buildJsonObject { put("added", result.added) })
        is GroupResult.Joined -> ok(buildJsonObject {
            put("group_id", result.groupId)
            put("name", result.name)
        })
        is GroupResult.Failed -> err(result.error)
        else -> ok(buildJsonObject { put("result", result.javaClass.simpleName) })
    }

    private fun parseStatusPrivacy(privacy: String, selectedContacts: List<String>?): StatusPrivacy =
        when (privacy.uppercase()) {
            "SELECTED" -> StatusPrivacy.Selected(selectedContacts ?: emptyList())
            "CLOSE_FRIENDS" -> StatusPrivacy.CloseFriends
            else -> StatusPrivacy.AllContacts
        }

    private fun statusPrivacyToStr(privacy: StatusPrivacy): String = when (privacy) {
        StatusPrivacy.AllContacts -> "ALL_CONTACTS"
        is StatusPrivacy.Selected -> "SELECTED_CONTACTS"
        StatusPrivacy.CloseFriends -> "ALL_CONTACTS"
    }
}
