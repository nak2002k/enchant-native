package org.enchant.agent

import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

/**
 * Localhost-only HTTP control plane for automated agents.
 * Bind: 127.0.0.1:19789 — use `adb reverse tcp:19789 tcp:19789`.
 */
class AgentDebugServer(
    port: Int = DEFAULT_PORT,
    private val bridge: AgentAppBridge
) : NanoHTTPD("127.0.0.1", port) {

    private val listenPort = port
    private val json = Json { ignoreUnknownKeys = true }

    override fun serve(session: IHTTPSession): Response {
        if (session.method == Method.OPTIONS) {
            return cors(newFixedLengthResponse(Response.Status.OK, MIME_PLAINTEXT, ""))
        }
        val path = session.uri.removePrefix("/").ifEmpty { "health" }
        val segments = path.split("/").filter { it.isNotEmpty() }
        return try {
            val response = runBlocking { dispatch(session.method, segments, session) }
            cors(jsonResponse(response))
        } catch (e: Exception) {
            AgentEventLog.emit("api_error", ok = false, data = buildJsonObject {
                put("path", path)
                put("error", e.message ?: "unknown")
            })
            cors(jsonResponse(buildJsonObject {
                put("ok", false)
                put("error", e.message ?: "unknown")
            }, status = Response.Status.INTERNAL_ERROR))
        }
    }

    private suspend fun dispatch(
        method: Method,
        segments: List<String>,
        session: IHTTPSession
    ): JsonObject {
        val root = segments.firstOrNull() ?: "health"
        val body = parseBody(session)

        return when {
            root == "health" && method == Method.GET -> buildJsonObject {
                put("ok", true)
                put("service", "enchant-agent-debug")
                put("port", listenPort)
            }
            root == "help" && method == Method.GET -> bridge.getHelp()
            root == "state" && method == Method.GET -> bridge.getState()
            root == "events" && method == Method.GET -> {
                val since = session.parameters["since"]?.firstOrNull()?.toLongOrNull() ?: 0L
                val limit = session.parameters["limit"]?.firstOrNull()?.toIntOrNull() ?: 100
                buildJsonObject {
                    put("ok", true)
                    put("events", buildJsonArray {
                        AgentEventLog.getEvents(since, limit).forEach { e ->
                            add(buildJsonObject {
                                put("id", e.id)
                                put("t", e.timestampMs)
                                put("type", e.type)
                                put("ok", e.ok)
                                e.data.forEach { (k, v) -> put(k, v) }
                            })
                        }
                    })
                }
            }
            root == "ui" && segments.getOrNull(1) == "current" && method == Method.GET ->
                bridge.getUiCurrent()
            root == "ui" && method == Method.POST && segments.getOrNull(1) == "action" ->
                bridge.performUiAction(body.string("action"))
            root == "ui" && method == Method.POST && segments.getOrNull(1) == "phone" ->
                bridge.submitPhone(body.string("phone"))
            root == "ui" && method == Method.POST && segments.getOrNull(1) == "otp" ->
                bridge.submitOtp(body.string("otp"))
            root == "ui" && method == Method.POST && segments.getOrNull(1) == "applock" ->
                bridge.completeAppLock(body.optString("pin"))
            root == "auth" && method == Method.POST -> when (segments.getOrNull(1)) {
                "request-otp" -> bridge.requestOtp(body.string("identifier"))
                "verify-otp" -> bridge.verifyOtp(body.string("otp"))
                "register-keys" -> bridge.registerKeys()
                "profile" -> bridge.setProfile(
                    body.string("username"),
                    body.string("display_name"),
                    body.optString("about")
                )
                "complete" -> bridge.completeRegistration()
                "skip-to-main" -> bridge.skipToMainIfRegistered()
                "logout" -> bridge.logout()
                "accept-terms" -> bridge.acceptTerms()
                "skip-permissions" -> bridge.skipPermissions()
                "skip-pin" -> bridge.skipPin()
                "skip-applock" -> bridge.skipAppLock()
                "full-flow" -> bridge.runRegistrationFlow(
                    identifier = body.string("identifier"),
                    otp = body.string("otp"),
                    username = body.string("username"),
                    displayName = body.string("display_name"),
                    about = body.optString("about")
                )
                else -> notFound("auth/${segments.getOrNull(1)}")
            }
            root == "nav" && method == Method.POST -> bridge.navigate(body)
            root == "conversations" && method == Method.GET && segments.size == 1 ->
                bridge.listConversations()
            root == "conversations" && method == Method.GET && segments.size == 3 &&
                segments[2] == "messages" -> bridge.listMessages(
                segments[1],
                session.parameters["limit"]?.firstOrNull()?.toIntOrNull() ?: 50
            )
            root == "conversations" && method == Method.POST && segments.size == 2 &&
                segments[1] == "open" -> bridge.openConversation(body.string("conversation_id"))
            root == "messages" && method == Method.POST && segments.getOrNull(1) == "send" ->
                bridge.sendMessage(
                    recipientUserId = body.string("recipient_user_id"),
                    text = body.string("text"),
                    sealed = body.bool("sealed", false)
                )
            root == "messages" && method == Method.POST && segments.getOrNull(1) == "media" ->
                bridge.sendMediaMessage(
                    recipientUserId = body.string("recipient_user_id"),
                    conversationId = body.string("conversation_id"),
                    filePath = body.string("file_path"),
                    mimeType = body.string("mime_type"),
                    fileName = body.optString("file_name"),
                    isViewOnce = body.bool("view_once", false)
                )
            root == "messages" && method == Method.POST && segments.getOrNull(1) == "reaction" ->
                bridge.sendReaction(
                    conversationId = body.string("conversation_id"),
                    emoji = body.string("emoji"),
                    envelopeId = body.optString("envelope_id"),
                    messageLocalId = body.optLong("message_local_id")
                )
            root == "messages" && method == Method.POST && segments.getOrNull(1) == "sticker" ->
                bridge.sendSticker(
                    recipientUserId = body.string("recipient_user_id"),
                    conversationId = body.string("conversation_id"),
                    packId = body.string("pack_id"),
                    stickerId = body.string("sticker_id")
                )
            root == "contacts" && method == Method.GET && segments.size == 1 ->
                bridge.listContacts()
            root == "contacts" && method == Method.POST && segments.getOrNull(1) == "add" ->
                bridge.addContact(body.string("user_id"), body.optString("custom_name"))
            root == "contacts" && method == Method.POST && segments.getOrNull(1) == "remove" ->
                bridge.removeContact(body.string("user_id"))
            root == "contacts" && method == Method.GET && segments.getOrNull(1) == "blocked" ->
                bridge.listBlockedUsers()
            root == "network" && method == Method.GET && segments.getOrNull(1) == "status" ->
                bridge.getNetworkStatus()
            root == "network" && method == Method.POST && segments.getOrNull(1) == "ws" &&
                segments.getOrNull(2) == "connect" -> bridge.connectWebSocket()
            root == "network" && method == Method.POST && segments.getOrNull(1) == "ws" &&
                segments.getOrNull(2) == "disconnect" -> bridge.disconnectWebSocket()
            root == "crypto" && method == Method.GET && segments.getOrNull(1) == "status" ->
                bridge.getCryptoStatus()
            root == "crypto" && method == Method.GET && segments.getOrNull(1) == "test-jni" ->
                bridge.testJniSequence()
            root == "crypto" && method == Method.POST && segments.getOrNull(1) == "reset-session" ->
                bridge.resetSession(body.string("user_id"))
            root == "groups" && method == Method.GET && segments.size == 1 ->
                bridge.listGroups()
            root == "groups" && method == Method.POST && segments.getOrNull(1) == "create" ->
                bridge.createGroup(
                    name = body.string("name"),
                    description = body.optString("description"),
                    initialMemberIds = body.stringList("initial_member_ids"),
                    addMembersPolicy = body.optString("add_members_policy") ?: "ALL_MEMBERS",
                    joinType = body.optString("join_type") ?: "INVITE_ONLY"
                )
            root == "groups" && method == Method.POST && segments.size == 3 &&
                segments[2] == "members" ->
                bridge.addGroupMembers(segments[1], body.stringListRequired("user_ids"))
            root == "debug" && method == Method.POST && segments.getOrNull(1) == "clear-conversation" ->
                bridge.clearConversation(body.string("conversation_id"))
            root == "groups" && method == Method.POST && segments.getOrNull(1) == "message" ->
                bridge.sendGroupMessage(body.string("group_id"), body.string("text"))
            root == "groups" && method == Method.POST && segments.getOrNull(1) == "sender-key" ->
                bridge.broadcastGroupSenderKey(body.string("group_id"))
            root == "groups" && method == Method.POST && segments.getOrNull(1) == "join" ->
                bridge.joinGroupViaLink(body.string("link_code"))
            root == "calls" && method == Method.POST && segments.getOrNull(1) == "start" ->
                bridge.startCall(
                    remoteUserId = body.string("remote_user_id"),
                    isVideo = body.bool("video", false)
                )
            root == "calls" && method == Method.GET && segments.getOrNull(1) == "manager-status" ->
                bridge.getCallManagerStatus()
            root == "calls" && method == Method.POST && segments.getOrNull(1) == "accept" ->
                bridge.acceptCall()
            root == "calls" && method == Method.POST && segments.getOrNull(1) == "deny" ->
                bridge.denyCall()
            root == "calls" && method == Method.POST && segments.getOrNull(1) == "hangup" ->
                bridge.hangupCall()
            root == "calls" && method == Method.GET && segments.getOrNull(1) == "log" ->
                bridge.listCallLog(session.parameters["limit"]?.firstOrNull()?.toIntOrNull() ?: 50)
            root == "status" && method == Method.GET && segments.getOrNull(1) == "feed" ->
                bridge.listStatusFeed()
            root == "status" && method == Method.POST && segments.getOrNull(1) == "text" ->
                bridge.createTextStatus(
                    text = body.string("text"),
                    backgroundColor = body.optString("background_color") ?: "#000000",
                    privacy = body.optString("privacy") ?: "ALL_CONTACTS",
                    selectedContacts = body.stringList("selected_contacts")
                )
            root == "status" && method == Method.POST && segments.getOrNull(1) == "media" ->
                bridge.createMediaStatus(
                    mediaId = body.string("media_id"),
                    privacy = body.optString("privacy") ?: "ALL_CONTACTS",
                    selectedContacts = body.stringList("selected_contacts")
                )
            root == "status" && method == Method.GET && segments.size == 2 ->
                bridge.viewStatus(segments[1])
            root == "status" && method == Method.POST && segments.size == 3 &&
                segments[2] == "view" ->
                bridge.viewStatus(segments[1])
            root == "stickers" && method == Method.GET && segments.getOrNull(1) == "library" ->
                bridge.listStickerLibrary()
            root == "stickers" && method == Method.GET && segments.getOrNull(1) == "featured" ->
                bridge.listFeaturedStickers()
            root == "stickers" && method == Method.POST && segments.size == 3 &&
                segments[2] == "install" ->
                bridge.installStickerPack(segments[1])
            root == "backup" && method == Method.POST && segments.getOrNull(1) == "cloud" &&
                segments.getOrNull(2) == "initiate" ->
                bridge.backupCloudInitiate()
            root == "backup" && method == Method.GET && segments.getOrNull(1) == "cloud" &&
                segments.getOrNull(2) == "latest" ->
                bridge.backupCloudLatest()
            root == "backup" && method == Method.POST && segments.getOrNull(1) == "cloud" &&
                segments.getOrNull(2) == "restore" ->
                bridge.backupCloudRestore(body.string("backup_id"))
            root == "backup" && method == Method.POST && segments.getOrNull(1) == "local" &&
                segments.getOrNull(2) == "export" ->
                bridge.backupLocalExport(
                    outputPath = body.string("output_path"),
                    backupKeyB64 = body.string("backup_key_b64")
                )
            root == "backup" && method == Method.POST && segments.getOrNull(1) == "local" &&
                segments.getOrNull(2) == "import" ->
                bridge.backupLocalImport(
                    inputPath = body.string("input_path"),
                    backupKeyB64 = body.string("backup_key_b64"),
                    sections = body.stringListRequired("sections")
                )
            root == "applock" && method == Method.POST && segments.getOrNull(1) == "set" ->
                bridge.appLockSetPin(body.string("pin"))
            root == "applock" && method == Method.POST && segments.getOrNull(1) == "verify" ->
                bridge.appLockVerifyPin(body.string("pin"))
            root == "applock" && method == Method.POST && segments.getOrNull(1) == "disable" ->
                bridge.appLockDisable()
            else -> notFound(segments.joinToString("/"))
        }
    }

    private fun parseBody(session: IHTTPSession): JsonObject {
        if (session.method != Method.POST && session.method != Method.PUT) return buildJsonObject {}
        val map = HashMap<String, String>()
        session.parseBody(map)
        val raw = map["postData"] ?: return buildJsonObject {}
        if (raw.isBlank()) return buildJsonObject {}
        return runCatching { json.parseToJsonElement(raw).jsonObject }.getOrElse { buildJsonObject {} }
    }

    private fun notFound(path: String?): JsonObject = buildJsonObject {
        put("ok", false)
        put("error", "not found: $path")
    }

    private fun jsonResponse(body: JsonObject, status: Response.Status = Response.Status.OK): Response {
        return newFixedLengthResponse(status, "application/json", body.toString())
    }

    private fun cors(response: Response): Response {
        response.addHeader("Access-Control-Allow-Origin", "*")
        response.addHeader("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
        response.addHeader("Access-Control-Allow-Headers", "Content-Type")
        return response
    }

    companion object {
        const val DEFAULT_PORT = 19789
    }
}

private fun JsonObject.string(key: String): String =
    this[key]?.jsonPrimitive?.content
        ?: throw IllegalArgumentException("Missing required field: $key")

private fun JsonObject.optString(key: String): String? =
    this[key]?.jsonPrimitive?.content

private fun JsonObject.bool(key: String, default: Boolean): Boolean =
    this[key]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: default

private fun JsonObject.optLong(key: String): Long? =
    this[key]?.jsonPrimitive?.longOrNull

private fun JsonObject.stringList(key: String): List<String>? =
    this[key]?.let { el ->
        when (el) {
            is kotlinx.serialization.json.JsonArray ->
                el.map { it.jsonPrimitive.content }
            else -> null
        }
    }

private fun JsonObject.stringListRequired(key: String): List<String> =
    stringList(key) ?: throw IllegalArgumentException("Missing required array field: $key")
