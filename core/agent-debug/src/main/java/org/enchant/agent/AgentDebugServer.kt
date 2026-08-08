package org.enchant.agent

import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
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
 *
 * F-C4: every endpoint except /health and /events requires a per-install
 * bearer token. This prevents a co-installed app (or any process that can
 * reach the loopback socket) from driving the agent — e.g. submitting an OTP,
 * reading a conversation, or extracting key material — without the token.
 */
class AgentDebugServer(
    port: Int = DEFAULT_PORT,
    private val bridge: AgentAppBridge,
    authToken: String = ""
) : NanoHTTPD("127.0.0.1", port) {

    private val listenPort = port
    private val json = Json { ignoreUnknownKeys = true }

    // Per-install token generated on first start and persisted by the caller,
    // so it survives restarts and is only known to the app (and whoever reads
    // it from the agent's authenticated /token endpoint once).
    private val authToken: String = authToken.ifEmpty { generateToken() }

    private fun generateToken(): String {
        val bytes = ByteArray(32)
        java.security.SecureRandom().nextBytes(bytes)
        return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    override fun serve(session: IHTTPSession): Response {
        val path = session.uri.removePrefix("/").ifEmpty { "health" }
        if (session.method == Method.OPTIONS) {
            return cors(newFixedLengthResponse(Response.Status.OK, MIME_PLAINTEXT, ""))
        }
        // /health and /events are safe to expose unauthenticated (loopback-only
        // socket, no sensitive data); everything else requires the bearer token.
        val unauthenticated = path == "health" || path.startsWith("events")
        if (!unauthenticated && !isAuthorized(session)) {
            return cors(jsonResponse(buildJsonObject {
                put("ok", false)
                put("error", "Unauthorized")
            }, status = Response.Status.FORBIDDEN))
        }
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

    private fun isAuthorized(session: IHTTPSession): Boolean {
        val auth = session.headers["authorization"]
            ?: session.headers["Authorization"]
            ?: return false
        return auth == "Bearer $authToken"
    }

    /**
     * Returns the per-install token. Protected by the same bearer check as all
     * other authenticated endpoints (you must already know the token to read
     * it); automation retrieves it the first time via `adb reverse`.
     */
    private fun getToken(): JsonObject = buildJsonObject {
        put("ok", true)
        put("token", authToken)
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
            root == "token" && method == Method.GET -> getToken()
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
                bridge.submitOtp(body.string("otp"), body.optString("pin"))
            root == "ui" && method == Method.POST && segments.getOrNull(1) == "applock" ->
                bridge.completeAppLock(body.optString("pin"))
            root == "auth" && method == Method.POST -> when (segments.getOrNull(1)) {
                "request-otp" -> bridge.requestOtp(body.string("identifier"))
                "set-pin" -> bridge.setPin(body.string("pin"))
                "verify-pin" -> bridge.verifyPin(body.string("pin"))
                "verify-otp" -> bridge.verifyOtp(body.string("otp"), body.optString("pin"))
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
                    about = body.optString("about"),
                    pin = body.optString("pin")
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
            root == "conversations" && method == Method.POST && segments.size == 3 &&
                segments[2] == "read" -> bridge.markConversationRead(segments[1])
            root == "profile" && method == Method.POST && segments.getOrNull(1) == "set-avatar" ->
                bridge.setAvatar()
            root == "messages" && method == Method.POST && segments.getOrNull(1) == "typing" ->
                bridge.sendTyping(body.string("recipient_user_id"), body.bool("start", true))
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
            root == "keys" && method == Method.GET && segments.getOrNull(1) == "bundle" && segments.size == 3 ->
                bridge.keyBundle(segments[2])
            root == "kt" && method == Method.GET && segments.getOrNull(1) == "sth" && segments.size == 3 ->
                bridge.ktTreeHeadPublicKey()
            root == "kt" && method == Method.GET && segments.getOrNull(1) == "sth" ->
                bridge.ktTreeHead()
            root == "kt" && method == Method.GET && segments.getOrNull(1) == "verify" && segments.size == 3 ->
                bridge.ktVerifyIdentity(segments[2], "")
            root == "channels" && method == Method.GET && segments.getOrNull(1) == "discover" ->
                bridge.discoverChannels()
            root == "channels" && method == Method.POST && segments.size == 1 ->
                bridge.createChannel(body.string("name"), body.optString("description"))
            root == "channels" && method == Method.POST && segments.size == 3 &&
                segments[2] == "subscribe" -> bridge.subscribeChannel(segments[1])
            root == "channels" && method == Method.GET && segments.size == 3 &&
                segments[2] == "feed" -> bridge.channelFeed(segments[1])
            root == "contacts" && method == Method.GET && segments.size == 1 ->
                bridge.listContacts()
            root == "contacts" && method == Method.POST && segments.getOrNull(1) == "add" ->
                bridge.addContact(body.string("user_id"), body.optString("custom_name"))
            root == "contacts" && method == Method.POST && segments.getOrNull(1) == "remove" ->
                bridge.removeContact(body.string("user_id"))
            root == "contacts" && method == Method.POST && segments.size == 2 && segments.getOrNull(1) == "friend-request" ->
                bridge.sendFriendRequest(body.string("user_id"))
            root == "contacts" && method == Method.POST && segments.getOrNull(1) == "friend-request" &&
                segments.size == 4 && segments[3] == "accept" -> bridge.acceptFriendRequest(segments[2])
            root == "contacts" && method == Method.POST && segments.getOrNull(1) == "friend-request" &&
                segments.size == 4 && segments[3] == "decline" -> bridge.declineFriendRequest(segments[2])
            root == "contacts" && method == Method.GET && segments.getOrNull(1) == "friend-requests" ->
                bridge.listFriendRequests()
            root == "contacts" && method == Method.POST && segments.getOrNull(1) == "search" ->
                bridge.searchByUsername(body.string("q"))
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
            root == "crypto" && method == Method.POST && segments.getOrNull(1) == "mls" && segments.getOrNull(2) == "create" ->
                bridge.mlsCreate(body.string("group_id_b64"), body.string("epoch_secret_b64"))
            root == "crypto" && method == Method.POST && segments.getOrNull(1) == "mls" && segments.getOrNull(2) == "encrypt" ->
                bridge.mlsEncrypt(body.string("state_b64"), body.string("plaintext_b64"))
            root == "crypto" && method == Method.POST && segments.getOrNull(1) == "mls" && segments.getOrNull(2) == "decrypt" ->
                bridge.mlsDecrypt(body.string("state_b64"), body.string("ciphertext_b64"))
            root == "crypto" && method == Method.GET && segments.getOrNull(1) == "identity" -> bridge.debugIdentity()
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
            root == "groups" && method == Method.POST && segments.size == 3 &&
                segments[2] == "credential" -> bridge.groupCredential(segments[1])
            root == "groups" && method == Method.GET && segments.size == 4 &&
                segments[2] == "credential" && segments[3] == "present" ->
                bridge.groupCredentialPresent(segments[1])
            root == "groups" && method == Method.POST && segments.size == 4 &&
                segments[2] == "credential" && segments[3] == "verify" ->
                bridge.verifyGroupCredential(segments[1], body.string("presentation"))
            root == "groups" && method == Method.GET && segments.size == 2 ->
                bridge.getGroupInfo(segments[1])
            root == "groups" && method == Method.GET && segments.size == 3 &&
                segments[2] == "members" -> bridge.listGroupMembers(segments[1])
            root == "groups" && method == Method.PUT && segments.size == 3 &&
                segments[2] == "settings" ->
                bridge.updateGroupSettings(
                    segments[1],
                    body.optString("name"),
                    body.optString("description")
                )
            root == "groups" && method == Method.DELETE && segments.size == 4 &&
                segments[2] == "members" -> bridge.removeGroupMember(segments[1], segments[3])
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
            root == "keys" && method == Method.GET && segments.getOrNull(1) == "bundle" && segments.size == 3 ->
                bridge.keyBundle(segments[2])
            root == "kt" && method == Method.GET && segments.getOrNull(1) == "sth" && segments.size == 3 ->
                bridge.ktTreeHeadPublicKey()
            root == "kt" && method == Method.GET && segments.getOrNull(1) == "sth" ->
                bridge.ktTreeHead()
            root == "kt" && method == Method.GET && segments.getOrNull(1) == "verify" && segments.size == 3 ->
                bridge.ktVerifyIdentity(segments[2], "")
            root == "channels" && method == Method.GET && segments.getOrNull(1) == "discover" ->
                bridge.discoverChannels()
            root == "channels" && method == Method.POST && segments.size == 1 ->
                bridge.createChannel(body.string("name"), body.optString("description"))
            root == "channels" && method == Method.POST && segments.size == 3 &&
                segments[2] == "subscribe" -> bridge.subscribeChannel(segments[1])
            root == "channels" && method == Method.GET && segments.size == 3 &&
                segments[2] == "feed" -> bridge.channelFeed(segments[1])
            root == "contacts" && method == Method.GET && segments.size == 1 ->
                bridge.listContacts()
            root == "contacts" && method == Method.POST && segments.getOrNull(1) == "sync" ->
                bridge.syncDeviceContacts()
            root == "contacts" && method == Method.GET && segments.getOrNull(1) == "discovery-salt" ->
                bridge.discoverySalt()
            root == "contacts" && method == Method.POST && segments.getOrNull(1) == "discover" ->
                bridge.discoverContacts(body.stringList("phone_numbers") ?: emptyList())
            root == "polls" && method == Method.POST && segments.size == 1 ->
                bridge.createPoll(
                    conversationId = body.string("conversation_id"),
                    question = body.string("question"),
                    optionTexts = body.objectList("options")?.mapNotNull { entry ->
                        entry["text"]?.jsonPrimitive?.content
                    } ?: emptyList()
                )
            root == "polls" && method == Method.POST && segments.size == 3 &&
                segments[2] == "vote" ->
                bridge.votePoll(segments[1], body.stringListRequired("option_ids"))
            root == "blocks" && method == Method.POST && segments.size == 2 ->
                bridge.blockUser(segments[1])
            root == "blocks" && method == Method.DELETE && segments.size == 2 ->
                bridge.unblockUser(segments[1])
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
                    backupKeyB64 = body.optString("backup_key_b64") ?: "",
                    pin = body.optString("pin")
                )
            root == "backup" && method == Method.POST && segments.getOrNull(1) == "local" &&
                segments.getOrNull(2) == "import" ->
                bridge.backupLocalImport(
                    inputPath = body.string("input_path"),
                    backupKeyB64 = body.optString("backup_key_b64") ?: "",
                    sections = body.stringListRequired("sections"),
                    pin = body.optString("pin")
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
        // F-C4: no wildcard CORS. The control plane is reached via `adb
        // reverse` (no Origin) or from the app's own webview (file://). Refuse
        // cross-origin browser fetches entirely; a request with an arbitrary
        // Origin must not be able to drive the agent.
        response.addHeader("Access-Control-Allow-Origin", "null")
        response.addHeader("Vary", "Origin")
        response.addHeader("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
        response.addHeader("Access-Control-Allow-Headers", "Content-Type, Authorization")
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

private fun JsonObject.objectList(key: String): List<JsonObject>? =
    this[key]?.let { el ->
        when (el) {
            is kotlinx.serialization.json.JsonArray ->
                el.mapNotNull { it.jsonObjectOrNull() }
            else -> null
        }
    }

private fun JsonElement.jsonObjectOrNull(): JsonObject? =
    if (this is JsonObject) this else null

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
