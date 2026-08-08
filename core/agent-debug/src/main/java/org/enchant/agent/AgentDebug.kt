package org.enchant.agent

import android.content.Context
import android.util.Log
import fi.iki.elonen.NanoHTTPD
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.net.InetSocketAddress

/**
 * Entry point for the debug-only agent control plane. Not present in release builds.
 */
object AgentDebug {
    private const val TAG = "AgentDebug"

    @Volatile
    private var server: AgentDebugServer? = null

    @Volatile
    private var ready = false

    fun start(context: Context, bridge: AgentAppBridge, port: Int = AgentDebugServer.DEFAULT_PORT, authToken: String = "") {
        synchronized(this) {
            if (ready && server != null) return
            Thread({
                try {
                    startServer(bridge, port, authToken)
                    ready = true
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to start agent server", e)
                }
            }, "enchant-agent-debug").start()
        }
    }

    private fun startServer(bridge: AgentAppBridge, port: Int, authToken: String) {
        server?.stop()
        server = null
        val s = AgentDebugServer(port = port, bridge = bridge, authToken = authToken)
        s.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false)
        repeat(20) {
            if (portOpen(port)) {
                server = s
                AgentEventLog.emit(
                    "agent_debug_started",
                    data = buildJsonObject {
                        put("port", port)
                        put("bind", "127.0.0.1")
                        put("hint", "adb forward tcp:$port tcp:$port")
                        put("auth_token", authToken)
                    }
                )
                return
            }
            Thread.sleep(100)
        }
        s.stop()
        throw IllegalStateException("Agent server did not bind to port $port")
    }

    private fun portOpen(port: Int): Boolean = runCatching {
        java.net.Socket().use { socket ->
            socket.connect(java.net.InetSocketAddress("127.0.0.1", port), 200)
            true
        }
    }.getOrDefault(false)

    fun stop() {
        synchronized(this) {
            server?.stop()
            server = null
            ready = false
        }
    }

    fun isRunning(): Boolean = ready && server != null
}
