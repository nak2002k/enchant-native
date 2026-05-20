package org.enchant.core.calls.webrtc

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.enchant.core.calls.model.CallQualityStats
import org.webrtc.PeerConnection
import java.util.concurrent.atomic.AtomicBoolean

class StatsCollector(
    private val peerConnection: PeerConnection
) {
    private val _stats = MutableStateFlow(CallQualityStats())
    val stats: StateFlow<CallQualityStats> = _stats.asStateFlow()

    private val isRunning = AtomicBoolean(false)

    suspend fun startCollecting(intervalMs: Long = 2000) {
        if (isRunning.getAndSet(true)) return
        try {
            while (isRunning.get()) {
                delay(intervalMs)
                collectStats()
            }
        } finally {
            isRunning.set(false)
        }
    }

    fun stopCollecting() {
        isRunning.set(false)
    }

    private suspend fun collectStats() {
        withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                var rttMs = 0L
                var packetsLost = 0
                var jitterMs = 0L
                var bytesReceived = 0L
                var bytesSent = 0L

                peerConnection.getStats { report ->
                    for (stat in report.statsMap.values) {
                        when (stat.type) {
                            "candidate-pair" -> {
                                val rtt = stat.members["currentRoundTripTime"]
                                if (rtt != null) rttMs = (((rtt as? Number)?.toDouble() ?: 0.0) * 1000).toLong()
                            }
                            "inbound-rtp" -> {
                                val lost = stat.members["packetsLost"]
                                if (lost != null) packetsLost += (lost as? Number)?.toInt() ?: 0
                                val jitter = stat.members["jitter"]
                                if (jitter != null) jitterMs = (((jitter as? Number)?.toDouble() ?: 0.0) * 1000).toLong()
                                val received = stat.members["bytesReceived"]
                                if (received != null) bytesReceived += (received as? Number)?.toLong() ?: 0L
                            }
                            "outbound-rtp" -> {
                                val sent = stat.members["bytesSent"]
                                if (sent != null) bytesSent += (sent as? Number)?.toLong() ?: 0L
                            }
                        }
                    }
                }

                _stats.value = CallQualityStats(
                    rttMs = rttMs.toLong(),
                    packetsLost = packetsLost,
                    jitterMs = jitterMs.toLong(),
                    bytesReceived = bytesReceived,
                    bytesSent = bytesSent
                )
            } catch (e: Exception) {
            }
        }
    }
}