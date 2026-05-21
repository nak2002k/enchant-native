# Phase B & C Implementation Guide: Signal-Style Action Processor

## Overview

This document defines the implementation for Phase B (foreground service, notifications, StatsCollector wiring) and Phase C (Signal-style action processor state machine). Phase C is the core rewrite — replacing the current ad-hoc `CallManager` with a proper action processor pattern inspired by Signal-Android.

**Reference:** `/home/nsk/project/Signal-Android-main/app/src/main/java/org/thoughtcrime/securesms/call/` and `/home/nsk/project/Signal-Android-main/webrtc/` directories.

---

## Phase B: Incremental Improvements

### B1: Create `CallForegroundService.kt`

**File:** `core/calls/src/main/java/org/enchant/core/calls/notification/CallForegroundService.kt`

**Purpose:** Android foreground service that keeps the call alive when app is backgrounded. Uses `START_STICKY` for crash recovery and `foregroundServiceType="phoneCall"`.

**Implementation:**

```kotlin
package org.enchant.core.calls.notification

import android.app.Notification
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import org.enchant.core.calls.CallManager
import org.enchant.core.calls.CallState
import org.enchant.core.calls.CallStatus

class CallForegroundService : Service() {

    companion object {
        const val ACTION_START = "org.enchant.calls.START_FOREGROUND"
        const val ACTION_STOP = "org.enchant.calls.STOP_FOREGROUND"
        private const val NOTIFICATION_ID = 3000
    }

    private val notificationManager by lazy {
        org.enchant.core.calls.notification.CallNotificationManager(this)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            else -> {
                val notification = buildForegroundNotification()
                ServiceCompat.startForeground(
                    this,
                    NOTIFICATION_ID,
                    notification,
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL
                    } else {
                        0
                    }
                )
                return START_STICKY
            }
        }
    }

    private fun buildForegroundNotification(): Notification {
        val callState = CallManager.callState.value
        val remoteUserId = callState.remoteUserId ?: "Unknown"
        val isVideo = callState.isVideoCall

        return notificationManager.buildForegroundNotification(remoteUserId, isVideo)
    }

    override fun onDestroy() {
        super.onDestroy()
    }
}
```

**AndroidManifest.xml addition:**
```xml
<service
    android:name="org.enchant.core.calls.notification.CallForegroundService"
    android:enabled="true"
    android:exported="false"
    android:foregroundServiceType="phoneCall" />
```

**Rules:**
- Use `START_STICKY` for crash recovery
- Always include `foregroundServiceType="phoneCall"` on Android 14+
- Handle `ACTION_STOP` to gracefully shutdown
- Never call `stopForeground(STOP_FOREGROUND_REMOVE)` except on explicit stop

---

### B2: Enhance `CallNotificationManager` with Active Call Updates

**File:** `core/calls/src/main/java/org/enchant/core/calls/notification/CallNotificationManager.kt`

**Purpose:** Update active call notification with duration timer. Currently the notification shows static duration — needs to update periodically.

**Changes to `CallNotificationManager`:**

1. Add a method to update active call duration:
```kotlin
fun updateActiveCallDuration(remoteUserId: String, isVideo: Boolean, durationSeconds: Int) {
    val pendingFlags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

    val endIntent = PendingIntent.getBroadcast(
        context, 200,
        Intent(CallNotificationReceiver.ACTION_HANGUP).setClass(context, CallNotificationReceiver::class.java),
        pendingFlags
    )
    val muteIntent = PendingIntent.getBroadcast(
        context, 201,
        Intent(CallNotificationReceiver.ACTION_MUTE).setClass(context, CallNotificationReceiver::class.java),
        pendingFlags
    )
    val speakerIntent = PendingIntent.getBroadcast(
        context, 202,
        Intent(CallNotificationReceiver.ACTION_SPEAKER).setClass(context, CallNotificationReceiver::class.java),
        pendingFlags
    )

    val notification = NotificationCompat.Builder(context, CHANNEL_ID)
        .setContentTitle(if (isVideo) "Video call" else "Voice call")
        .setContentText("$remoteUserId • ${formatDuration(durationSeconds)}")
        .setSmallIcon(android.R.drawable.ic_menu_call)
        .setOngoing(true)
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setCategory(NotificationCompat.CATEGORY_CALL)
        .addAction(android.R.drawable.ic_menu_close_clear_cancel, "End", endIntent)
        .addAction(android.R.drawable.ic_btn_speak_now, "Mute", muteIntent)
        .addAction(android.R.drawable.ic_btn_speak_now, "Speaker", speakerIntent)
        .setOnlyAlertOnce(true)
        .build()

    NotificationManagerCompat.from(context).notify(ACTIVE_CALL_ID, notification)
}
```

2. **Wiring in `DefaultCallManager`:** Add a `callScope.launch` periodic task that calls `notificationManager.updateActiveCallDuration()` every second when call is CONNECTED.

**Rules:**
- Use `setOnlyAlertOnce(true)` to prevent notification sound on every update
- Format duration as `MM:SS` or `HH:MM:SS` for calls > 1 hour
- Only update notification when duration changes (throttle if needed)

---

### B3: Wire `StatsCollector` → `observerRegistry.notifyQuality()`

**File:** `core/calls/src/main/java/org/enchant/core/calls/webrtc/StatsCollector.kt`

**Purpose:** Collect WebRTC stats (RTT, packet loss, jitter) and emit them to observers for UI display.

**Current state:** `StatsCollector` exists but `notifyQuality()` is not wired in `DefaultCallManager`.

**Changes:**

1. In `StatsCollector`, ensure `startMonitoring()` collects stats periodically and calls the callback:
```kotlin
fun startMonitoring(pc: PeerConnection, intervalMs: Long = 5000) {
    monitoringJob?.cancel()
    monitoringJob = scope.launch {
        while (isActive) {
            delay(intervalMs)
            pc.getStats { stats ->
                val rtt = extractRtt(stats)
                val packetsLost = extractPacketsLost(stats)
                val jitter = extractJitter(stats)
                val bytesReceived = extractBytesReceived(stats)
                val bytesSent = extractBytesSent(stats)

                callback.onStats Collected(
                    CallQualityStats(rtt, packetsLost, jitter, bytesReceived, bytesSent)
                )
            }
        }
    }
}
```

2. In `DefaultCallManager`, when `PeerConnection` is created, wire up stats collection:
```kotlin
private fun setupStatsCollector(pc: PeerConnection) {
    statsCollector = StatsCollector(callScope)
    statsCollector.startMonitoring(pc) { stats ->
        observerRegistry.notifyQuality(stats)
    }
}
```

3. In `PeerConnectionObserver.onIceConnectionChange()` when state is CONNECTED, start stats collection.

**Rules:**
- Stats collection should not block the main thread
- Use exponential backoff if stats collection fails
- Log errors but don't crash the call

---

## Phase C: Signal-Style Action Processor State Machine

### Architecture Overview

Signal's pattern uses:
1. **Immutable State** (`WebRtcServiceState`) — holds all call state
2. **Action Processor** (`WebRtcActionProcessor`) — state machine that handles all actions for current state
3. **Single-threaded execution** — all actions processed on single executor
4. **Delegation** — shared logic extracted to delegate classes

### New File Structure

```
core/calls/src/main/java/org/enchant/core/calls/
├── action/
│   ├── CallAction.kt              # Sealed class for all call actions
│   ├── ActionProcessor.kt         # Base action processor interface
│   ├── processors/
│   │   ├── IdleActionProcessor.kt
│   │   ├── OutgoingCallActionProcessor.kt
│   │   ├── IncomingCallActionProcessor.kt
│   │   └── ConnectedCallActionProcessor.kt
│   └── delegates/
│       └── ActiveCallDelegate.kt  # Shared hangup/hold logic
├── state/
│   ├── CallServiceState.kt       # Immutable state container
│   └── CallServiceStateBuilder.kt # Builder for state modifications
```

### C1: Create `CallAction.kt` — All Call Actions

**File:** `core/calls/src/main/java/org/enchant/core/calls/action/CallAction.kt`

**Purpose:** Sealed class hierarchy for all possible call actions. Each action is a data class that carries necessary metadata.

```kotlin
package org.enchant.core.calls.action

import org.enchant.core.calls.model.CallQualityStats

sealed class CallAction {
    // ── Outgoing Call Actions ──
    data class StartOutgoingCall(
        val remoteUserId: String,
        val isVideo: Boolean
    ) : CallAction()

    data class CancelOutgoingCall(
        val reason: String? = null
    ) : CallAction()

    // ── Incoming Call Actions ──
    data class ReceiveIncomingOffer(
        val remoteUserId: String,
        val sdp: String,
        val callId: String,
        val isVideo: Boolean
    ) : CallAction()

    data class AcceptIncomingCall(
        val withVideo: Boolean
    ) : CallAction()

    data class DenyIncomingCall(
        val reason: String? = null
    ) : CallAction()

    // ── Signaling Actions ──
    data class ReceiveAnswer(
        val sdp: String
    ) : CallAction()

    data class ReceiveIceCandidate(
        val candidate: String
    ) : CallAction()

    data class ReceiveHangup(
        val reason: String? = null
    ) : CallAction()

    // ── Call Control Actions ──
    data object ToggleMute : CallAction()
    data object ToggleSpeaker : CallAction()
    data object ToggleVideo : CallAction()
    data object FlipCamera : CallAction()
    data class SetOnHold(val hold: Boolean) : CallAction()
    data class RaiseHand(val raised: Boolean) : CallAction()

    // ── System Actions ──
    data object CallConnected : CallAction()
    data object CallReconnecting : CallAction()
    data object CallReconnected : CallAction()
    data object CallEnded : CallAction()
    data class CallFailed(val error: String) : CallAction()
    data class QualityUpdate(val stats: CallQualityStats) : CallAction()

    // ── Timeout Actions ──
    data object IncomingCallTimeout : CallAction()
    data object SignalingTimeout : CallAction()
}
```

**Rules:**
- All actions are immutable data classes
- Use sealed class so `when` is exhaustive
- Each action carries all context needed to process it
- No action references another action

---

### C2: Create `ActionProcessor.kt` — Base Interface

**File:** `core/calls/src/main/java/org/enchant/core/calls/action/ActionProcessor.kt`

**Purpose:** Interface for action processors. Each processor handles all actions in its context (idle, outgoing call, incoming call, connected call).

```kotlin
package org.enchant.core.calls.action

import org.enchant.core.calls.state.CallServiceState

interface ActionProcessor {
    /**
     * Process an action and return the new state.
     * If the processor doesn't handle this action type, return current state unchanged.
     */
    fun process(state: CallServiceState, action: CallAction): CallServiceState

    /**
     * Get the current call state this processor handles.
     */
    val currentPhase: CallPhase

    /**
     * Get a tag for logging.
     */
    val tag: String
}

enum class CallPhase {
    IDLE,
    OUTGOING_CALL,
    INCOMING_CALL,
    CONNECTED,
    RECONNECTING
}
```

**Rules:**
- Processors are single-purpose and stateless (except for configuration like `remoteUserId`)
- Processors do NOT store state — state is always in `CallServiceState`
- Return same state if action not handled (default no-op)

---

### C3: Create `CallServiceState.kt` — Immutable State Container

**File:** `core/calls/src/main/java/org/enchant/core/calls/state/CallServiceState.kt`

**Purpose:** Immutable state container holding all call state. Uses builder pattern for modifications.

```kotlin
package org.enchant.core.calls.state

import org.enchant.core.calls.CallLogger
import org.enchant.core.calls.action.ActionProcessor
import org.enchant.core.calls.action.CallPhase
import org.enchant.core.calls.model.CallQualityStats
import org.enchant.core.calls.model.CallState
import org.enchant.core.calls.observer.CallObserverRegistry

data class CallServiceState(
    val actionProcessor: ActionProcessor,
    val callState: CallState = CallState.idle(),
    val callSetupData: CallSetupData? = null,
    val localDeviceState: LocalDeviceState = LocalDeviceState(),
    val qualityStats: CallQualityStats = CallQualityStats(),
    val callLogger: CallLogger? = null,
    val observerRegistry: CallObserverRegistry? = null
) {
    val phase: CallPhase get() = actionProcessor.currentPhase

    fun builder(): CallServiceStateBuilder = CallServiceStateBuilder(this)
}

data class CallSetupData(
    val remoteUserId: String,
    val callId: String,
    val isVideo: Boolean,
    val offerSdp: String? = null,
    val answerSdp: String? = null,
    val receivedAt: Long = System.currentTimeMillis()
)

data class LocalDeviceState(
    val isMuted: Boolean = false,
    val isSpeakerOn: Boolean = false,
    val isVideoEnabled: Boolean = false,
    val isCameraFlipped: Boolean = false,
    val isOnHold: Boolean = false,
    val isHandRaised: Boolean = false
)

class CallServiceStateBuilder(private val current: CallServiceState) {
    private var processor: ActionProcessor? = current.actionProcessor
    private var state: CallState = current.callState
    private var setupData: CallSetupData? = current.callSetupData
    private var deviceState: LocalDeviceState = current.localDeviceState
    private var quality: CallQualityStats = current.qualityStats

    fun actionProcessor(p: ActionProcessor): CallServiceStateBuilder = apply { processor = p }
    fun callState(s: CallState): CallServiceStateBuilder = apply { state = s }
    fun callSetupData(d: CallSetupData?): CallServiceStateBuilder = apply { setupData = d }
    fun localDeviceState(d: LocalDeviceState): CallServiceStateBuilder = apply { deviceState = d }
    fun qualityStats(q: CallQualityStats): CallServiceStateBuilder = apply { quality = q }

    fun build(): CallServiceState = CallServiceState(
        actionProcessor = processor!!,
        callState = state,
        callSetupData = setupData,
        localDeviceState = deviceState,
        qualityStats = quality,
        callLogger = current.callLogger,
        observerRegistry = current.observerRegistry
    )
}
```

**Rules:**
- State is IMMUTABLE — never modify in place
- All modifications create new instance via builder
- Builder throws if `actionProcessor` is not set in build
- Include all state in single container — no scattered state

---

### C4: Create `IdleActionProcessor.kt`

**File:** `core/calls/src/main/java/org/enchant/core/calls/action/processors/IdleActionProcessor.kt`

**Purpose:** Handles actions when system is at rest — starting outgoing calls and receiving incoming calls.

```kotlin
package org.enchant.core.calls.action.processors

import android.util.Log
import org.enchant.core.calls.CallLogger
import org.enchant.core.calls.action.ActionProcessor
import org.enchant.core.calls.action.CallAction
import org.enchant.core.calls.action.CallPhase
import org.enchant.core.calls.model.CallDirection
import org.enchant.core.calls.model.CallStatus
import org.enchant.core.calls.observer.CallObserverRegistry
import org.enchant.core.calls.state.CallServiceState
import org.enchant.core.calls.state.CallSetupData
import java.util.UUID

class IdleActionProcessor(
    private val callLogger: CallLogger?,
    private val observerRegistry: CallObserverRegistry?
) : ActionProcessor {

    override val currentPhase: CallPhase = CallPhase.IDLE
    override val tag: String = "IdleActionProcessor"

    override fun process(state: CallServiceState, action: CallAction): CallServiceState {
        return when (action) {
            is CallAction.StartOutgoingCall -> handleStartOutgoingCall(state, action)
            is CallAction.ReceiveIncomingOffer -> handleReceiveIncomingOffer(state, action)
            else -> state // Default no-op for other actions in IDLE
        }
    }

    private fun handleStartOutgoingCall(state: CallServiceState, action: CallAction.StartOutgoingCall): CallServiceState {
        Log.d(tag, "handleStartOutgoingCall: remoteUserId=${action.remoteUserId}, isVideo=${action.isVideo}")

        val callId = UUID.randomUUID().toString()
        val newCallState = state.callState.copy(
            status = CallStatus.CALLING,
            remoteUserId = action.remoteUserId,
            callId = callId,
            isVideoCall = action.isVideo,
            direction = CallDirection.OUTGOING
        )

        val setupData = CallSetupData(
            remoteUserId = action.remoteUserId,
            callId = callId,
            isVideo = action.isVideo
        )

        observerRegistry?.notifyStarted(action.remoteUserId, action.isVideo)

        return state.builder()
            .actionProcessor(OutgoingCallActionProcessor(callLogger, observerRegistry, action.remoteUserId, action.isVideo))
            .callState(newCallState)
            .callSetupData(setupData)
            .build()
    }

    private fun handleReceiveIncomingOffer(state: CallServiceState, action: CallAction.ReceiveIncomingOffer): CallServiceState {
        Log.d(tag, "handleReceiveIncomingOffer: remoteUserId=${action.remoteUserId}, isVideo=${action.isVideo}")

        val callId = action.callId.ifBlank { UUID.randomUUID().toString() }
        val newCallState = state.callState.copy(
            status = CallStatus.RINGING,
            remoteUserId = action.remoteUserId,
            callId = callId,
            isVideoCall = action.isVideo,
            direction = CallDirection.INCOMING
        )

        val setupData = CallSetupData(
            remoteUserId = action.remoteUserId,
            callId = callId,
            isVideo = action.isVideo,
            offerSdp = action.sdp,
            receivedAt = System.currentTimeMillis()
        )

        observerRegistry?.notifyStarted(action.remoteUserId, action.isVideo)

        return state.builder()
            .actionProcessor(IncomingCallActionProcessor(callLogger, observerRegistry, action.remoteUserId, action.isVideo))
            .callState(newCallState)
            .callSetupData(setupData)
            .build()
    }
}
```

**Rules:**
- In IDLE, only handle `StartOutgoingCall` and `ReceiveIncomingOffer`
- All other actions return state unchanged (default no-op)
- Always log entrance with tag and relevant data
- When starting a call, transition to appropriate processor
- Include all necessary context (remoteUserId, isVideo, callId) in new processor

---

### C5: Create `OutgoingCallActionProcessor.kt`

**File:** `core/calls/src/main/java/org/enchant/core/calls/action/processors/OutgoingCallActionProcessor.kt`

**Purpose:** Manages outgoing call setup — sending offer, waiting for answer, handling busy/timeout.

```kotlin
package org.enchant.core.calls.action.processors

import android.util.Log
import org.enchant.core.calls.CallLogger
import org.enchant.core.calls.action.ActionProcessor
import org.enchant.core.calls.action.CallAction
import org.enchant.core.calls.action.CallPhase
import org.enchant.core.calls.model.CallStatus
import org.enchant.core.calls.observer.CallObserverRegistry
import org.enchant.core.calls.state.CallServiceState

class OutgoingCallActionProcessor(
    private val callLogger: CallLogger?,
    private val observerRegistry: CallObserverRegistry?,
    private val remoteUserId: String,
    private val isVideo: Boolean
) : ActionProcessor {

    override val currentPhase: CallPhase = CallPhase.OUTGOING_CALL
    override val tag: String = "OutgoingCallActionProcessor"

    override fun process(state: CallServiceState, action: CallAction): CallServiceState {
        return when (action) {
            is CallAction.ReceiveAnswer -> handleReceiveAnswer(state, action)
            is CallAction.CallConnected -> handleCallConnected(state)
            is CallAction.CancelOutgoingCall -> handleCancelOutgoingCall(state, action)
            is CallAction.CallFailed -> handleCallFailed(state, action)
            else -> state
        }
    }

    private fun handleReceiveAnswer(state: CallServiceState, action: CallAction.ReceiveAnswer): CallServiceState {
        Log.d(tag, "handleReceiveAnswer: remoteUserId=$remoteUserId")

        // Answer received — transition to CONNECTED once ICE completes
        val newState = state.callState.copy(status = CallStatus.CONNECTING)
        val setupData = state.callSetupData?.copy(answerSdp = action.sdp)

        return state.builder()
            .callState(newState)
            .callSetupData(setupData)
            .build()
    }

    private fun handleCallConnected(state: CallServiceState): CallServiceState {
        Log.d(tag, "handleCallConnected: remoteUserId=$remoteUserId")

        val newState = state.callState.copy(status = CallStatus.CONNECTED)

        observerRegistry?.notifyConnected()

        return state.builder()
            .actionProcessor(ConnectedCallActionProcessor(callLogger, observerRegistry, remoteUserId))
            .callState(newState)
            .callSetupData(null) // Clear setup data once connected
            .build()
    }

    private fun handleCancelOutgoingCall(state: CallServiceState, action: CallAction.CancelOutgoingCall): CallServiceState {
        Log.d(tag, "handleCancelOutgoingCall: reason=${action.reason}")

        observerRegistry?.notifyEnded(
            org.enchant.core.calls.model.CallEndReason.HANGUP_LOCAL,
            null
        )

        return state.builder()
            .actionProcessor(IdleActionProcessor(callLogger, observerRegistry))
            .callState(state.callState.copy(status = CallStatus.IDLE))
            .callSetupData(null)
            .build()
    }

    private fun handleCallFailed(state: CallServiceState, action: CallAction.CallFailed): CallServiceState {
        Log.e(tag, "handleCallFailed: error=${action.error}")

        val newState = state.callState.copy(
            status = CallStatus.ENDED,
            error = action.error
        )

        observerRegistry?.notifyError(action.error)

        return state.builder()
            .actionProcessor(IdleActionProcessor(callLogger, observerRegistry))
            .callState(newState)
            .callSetupData(null)
            .build()
    }
}
```

**Rules:**
- Only handle actions relevant to outgoing call setup
- On receive answer, stay in OUTGOING until call actually connects
- On call connected, transition to ConnectedCallActionProcessor
- On cancel/failure, transition back to IdleActionProcessor
- Always log all handler entrances

---

### C6: Create `IncomingCallActionProcessor.kt`

**File:** `core/calls/src/main/java/org/enchant/core/calls/action/processors/IncomingCallActionProcessor.kt`

**Purpose:** Manages incoming call setup — ringing, accept, deny, timeout.

```kotlin
package org.enchant.core.calls.action.processors

import android.util.Log
import org.enchant.core.calls.CallLogger
import org.enchant.core.calls.action.ActionProcessor
import org.enchant.core.calls.action.CallAction
import org.enchant.core.calls.action.CallPhase
import org.enchant.core.calls.model.CallEndReason
import org.enchant.core.calls.model.CallStatus
import org.enchant.core.calls.observer.CallObserverRegistry
import org.enchant.core.calls.state.CallServiceState

class IncomingCallActionProcessor(
    private val callLogger: CallLogger?,
    private val observerRegistry: CallObserverRegistry?,
    private val remoteUserId: String,
    private val isVideo: Boolean
) : ActionProcessor {

    override val currentPhase: CallPhase = CallPhase.INCOMING_CALL
    override val tag: String = "IncomingCallActionProcessor"

    override fun process(state: CallServiceState, action: CallAction): CallServiceState {
        return when (action) {
            is CallAction.AcceptIncomingCall -> handleAccept(state, action)
            is CallAction.DenyIncomingCall -> handleDeny(state, action)
            is CallAction.IncomingCallTimeout -> handleTimeout(state)
            is CallAction.CallConnected -> handleCallConnected(state)
            else -> state
        }
    }

    private fun handleAccept(state: CallServiceState, action: CallAction.AcceptIncomingCall): CallServiceState {
        Log.d(tag, "handleAccept: remoteUserId=$remoteUserId, withVideo=${action.withVideo}")

        val newState = state.callState.copy(
            status = CallStatus.CONNECTING,
            isVideoCall = action.withVideo
        )

        return state.builder()
            .callState(newState)
            .build()
    }

    private fun handleDeny(state: CallServiceState, action: CallAction.DenyIncomingCall): CallServiceState {
        Log.d(tag, "handleDeny: reason=${action.reason}")

        observerRegistry?.notifyEnded(CallEndReason.HANGUP_LOCAL, null)

        return state.builder()
            .actionProcessor(IdleActionProcessor(callLogger, observerRegistry))
            .callState(state.callState.copy(status = CallStatus.IDLE))
            .callSetupData(null)
            .build()
    }

    private fun handleTimeout(state: CallServiceState): CallServiceState {
        Log.d(tag, "handleTimeout: incoming call timed out")

        observerRegistry?.notifyEnded(CallEndReason.TIMEOUT, null)

        return state.builder()
            .actionProcessor(IdleActionProcessor(callLogger, observerRegistry))
            .callState(state.callState.copy(status = CallStatus.IDLE))
            .callSetupData(null)
            .build()
    }

    private fun handleCallConnected(state: CallServiceState): CallServiceState {
        Log.d(tag, "handleCallConnected: remoteUserId=$remoteUserId")

        val newState = state.callState.copy(status = CallStatus.CONNECTED)

        observerRegistry?.notifyConnected()

        return state.builder()
            .actionProcessor(ConnectedCallActionProcessor(callLogger, observerRegistry, remoteUserId))
            .callState(newState)
            .callSetupData(null)
            .build()
    }
}
```

**Rules:**
- Handle accept, deny, timeout, and call connected
- Timeout transitions to IDLE (call missed)
- Accept transitions to CONNECTING but stays in this processor until connected
- On connected, transition to ConnectedCallActionProcessor

---

### C7: Create `ConnectedCallActionProcessor.kt`

**File:** `core/calls/src/main/java/org/enchant/core/calls/action/processors/ConnectedCallActionProcessor.kt`

**Purpose:** Manages active connected calls — mute, speaker, video, hold, hangup, reconnection.

```kotlin
package org.enchant.core.calls.action.processors

import android.util.Log
import org.enchant.core.calls.CallLogger
import org.enchant.core.calls.action.ActionProcessor
import org.enchant.core.calls.action.CallAction
import org.enchant.core.calls.action.CallPhase
import org.enchant.core.calls.model.CallEndReason
import org.enchant.core.calls.model.CallStatus
import org.enchant.core.calls.observer.CallObserverRegistry
import org.enchant.core.calls.state.CallServiceState
import org.enchant.core.calls.state.LocalDeviceState

class ConnectedCallActionProcessor(
    private val callLogger: CallLogger?,
    private val observerRegistry: CallObserverRegistry?,
    private val remoteUserId: String
) : ActionProcessor {

    override val currentPhase: CallPhase = CallPhase.CONNECTED
    override val tag: String = "ConnectedCallActionProcessor"

    override fun process(state: CallServiceState, action: CallAction): CallServiceState {
        return when (action) {
            is CallAction.ToggleMute -> handleToggleMute(state)
            is CallAction.ToggleSpeaker -> handleToggleSpeaker(state)
            is CallAction.ToggleVideo -> handleToggleVideo(state)
            is CallAction.FlipCamera -> handleFlipCamera(state)
            is CallAction.SetOnHold -> handleSetOnHold(state, action)
            is CallAction.RaiseHand -> handleRaiseHand(state, action)
            is CallAction.CallEnded -> handleCallEnded(state)
            is CallAction.ReceiveHangup -> handleReceiveHangup(state, action)
            is CallAction.CallReconnecting -> handleReconnecting(state)
            is CallAction.CallReconnected -> handleReconnected(state)
            is CallAction.QualityUpdate -> handleQualityUpdate(state, action)
            else -> state
        }
    }

    private fun handleToggleMute(state: CallServiceState): CallServiceState {
        Log.d(tag, "handleToggleMute")
        val newDeviceState = state.localDeviceState.copy(isMuted = !state.localDeviceState.isMuted)
        return state.builder()
            .localDeviceState(newDeviceState)
            .build()
    }

    private fun handleToggleSpeaker(state: CallServiceState): CallServiceState {
        Log.d(tag, "handleToggleSpeaker")
        val newDeviceState = state.localDeviceState.copy(isSpeakerOn = !state.localDeviceState.isSpeakerOn)
        return state.builder()
            .localDeviceState(newDeviceState)
            .build()
    }

    private fun handleToggleVideo(state: CallServiceState): CallServiceState {
        Log.d(tag, "handleToggleVideo")
        val newDeviceState = state.localDeviceState.copy(isVideoEnabled = !state.localDeviceState.isVideoEnabled)
        return state.builder()
            .localDeviceState(newDeviceState)
            .build()
    }

    private fun handleFlipCamera(state: CallServiceState): CallServiceState {
        Log.d(tag, "handleFlipCamera")
        val newDeviceState = state.localDeviceState.copy(isCameraFlipped = !state.localDeviceState.isCameraFlipped)
        return state.builder()
            .localDeviceState(newDeviceState)
            .build()
    }

    private fun handleSetOnHold(state: CallServiceState, action: CallAction.SetOnHold): CallServiceState {
        Log.d(tag, "handleSetOnHold: hold=${action.hold}")
        val newDeviceState = state.localDeviceState.copy(isOnHold = action.hold)
        return state.builder()
            .localDeviceState(newDeviceState)
            .build()
    }

    private fun handleRaiseHand(state: CallServiceState, action: CallAction.RaiseHand): CallServiceState {
        Log.d(tag, "handleRaiseHand: raised=${action.raised}")
        val newDeviceState = state.localDeviceState.copy(isHandRaised = action.raised)
        return state.builder()
            .localDeviceState(newDeviceState)
            .build()
    }

    private fun handleCallEnded(state: CallServiceState): CallServiceState {
        Log.d(tag, "handleCallEnded")

        val summary = if (state.callState.durationSeconds > 0) {
            org.enchant.core.calls.model.CallSummary(
                state.callState.durationSeconds,
                state.callState.isVideoCall,
                state.callState.direction == org.enchant.core.calls.model.CallDirection.OUTGOING
            )
        } else null

        observerRegistry?.notifyEnded(CallEndReason.HANGUP_LOCAL, summary)

        return state.builder()
            .actionProcessor(IdleActionProcessor(callLogger, observerRegistry))
            .callState(state.callState.copy(status = CallStatus.IDLE))
            .build()
    }

    private fun handleReceiveHangup(state: CallServiceState, action: CallAction.ReceiveHangup): CallServiceState {
        Log.d(tag, "handleReceiveHangup: reason=${action.reason}")

        val summary = if (state.callState.durationSeconds > 0) {
            org.enchant.core.calls.model.CallSummary(
                state.callState.durationSeconds,
                state.callState.isVideoCall,
                state.callState.direction == org.enchant.core.calls.model.CallDirection.OUTGOING
            )
        } else null

        observerRegistry?.notifyEnded(CallEndReason.HANGUP_REMOTE, summary)

        return state.builder()
            .actionProcessor(IdleActionProcessor(callLogger, observerRegistry))
            .callState(state.callState.copy(status = CallStatus.IDLE))
            .build()
    }

    private fun handleReconnecting(state: CallServiceState): CallServiceState {
        Log.d(tag, "handleReconnecting")
        observerRegistry?.notifyReconnecting()
        return state.builder()
            .callState(state.callState.copy(status = CallStatus.RECONNECTING))
            .build()
    }

    private fun handleReconnected(state: CallServiceState): CallServiceState {
        Log.d(tag, "handleReconnected")
        observerRegistry?.notifyReconnected()
        return state.builder()
            .callState(state.callState.copy(status = CallStatus.CONNECTED))
            .build()
    }

    private fun handleQualityUpdate(state: CallServiceState, action: CallAction.QualityUpdate): CallServiceState {
        return state.builder()
            .qualityStats(action.stats)
            .build()
    }
}
```

**Rules:**
- Handle all call control actions: mute, speaker, video, camera, hold, hand raise
- Handle call ended and receive hangup (both go to IDLE)
- Handle reconnection states
- Handle quality stats update
- All control actions update `localDeviceState` but keep same processor
- Call ended transitions to IdleActionProcessor

---

### C8: Create `ActiveCallDelegate.kt` — Shared Logic

**File:** `core/calls/src/main/java/org/enchant/core/calls/action/delegates/ActiveCallDelegate.kt`

**Purpose:** Shared logic extracted from ConnectedCallActionProcessor for hangup and end call. Avoids code duplication.

```kotlin
package org.enchant.core.calls.action.delegates

import android.util.Log
import org.enchant.core.calls.CallLogger
import org.enchant.core.calls.model.CallEndReason
import org.enchant.core.calls.model.CallSummary
import org.enchant.core.calls.observer.CallObserverRegistry
import org.enchant.core.calls.state.CallServiceState

class ActiveCallDelegate(
    private val callLogger: CallLogger?,
    private val observerRegistry: CallObserverRegistry?
) {
    fun performHangup(state: CallServiceState): CallServiceState {
        Log.d("ActiveCallDelegate", "performHangup")

        val summary = buildSummary(state)

        observerRegistry?.notifyEnded(CallEndReason.HANGUP_LOCAL, summary)

        return state.builder()
            .actionProcessor(
                org.enchant.core.calls.action.processors.IdleActionProcessor(callLogger, observerRegistry)
            )
            .callState(state.callState.copy(status = org.enchant.core.calls.model.CallStatus.IDLE))
            .build()
    }

    fun performRemoteHangup(state: CallServiceState, reason: String?): CallServiceState {
        Log.d("ActiveCallDelegate", "performRemoteHangup: reason=$reason")

        val summary = buildSummary(state)
        val endReason = when (reason) {
            "busy" -> CallEndReason.BUSY
            "timeout" -> CallEndReason.TIMEOUT
            else -> CallEndReason.HANGUP_REMOTE
        }

        observerRegistry?.notifyEnded(endReason, summary)

        return state.builder()
            .actionProcessor(
                org.enchant.core.calls.action.processors.IdleActionProcessor(callLogger, observerRegistry)
            )
            .callState(state.callState.copy(status = org.enchant.core.calls.model.CallStatus.IDLE))
            .build()
    }

    private fun buildSummary(state: CallServiceState): CallSummary? {
        val callState = state.callState
        return if (callState.durationSeconds > 0) {
            CallSummary(
                callState.durationSeconds,
                callState.isVideoCall,
                callState.direction == org.enchant.core.calls.model.CallDirection.OUTGOING
            )
        } else null
    }
}
```

**Rules:**
- Use delegation for shared behavior between action processors
- Keep processors focused on their specific phase
- Delegate handles complex logic like building CallSummary

---

### C9: Update `DefaultCallManager` to Use Action Processor

**File:** `core/calls/src/main/java/org/enchant/core/calls/CallManager.kt`

**Purpose:** Rewrite `DefaultCallManager` to use the action processor pattern. All call actions go through the processor.

**Changes:**

1. Add `CallServiceState` and action processor to `DefaultCallManager`:
```kotlin
class DefaultCallManager(
    private val stateMachine: CallStateMachine,
    private val webRtcEngine: WebRtcEngine,
    private val mediaStreamManager: MediaStreamManager,
    private val sdpHandler: SdpHandler,
    private val iceHandler: IceCandidateHandler,
    private val signalingClient: SignalingClient,
    private val audioRouter: AudioRouter,
    private val audioFocusManager: AudioFocusManager,
    private val ringtonePlayer: RingtonePlayer,
    private val notificationManager: CallNotificationManager,
    private val callLogger: CallLogger,
    private val observerRegistry: CallObserverRegistry
) {
    // Replace direct state with action processor state
    private var serviceState: CallServiceState = CallServiceState(
        actionProcessor = IdleActionProcessor(callLogger, observerRegistry),
        callLogger = callLogger,
        observerRegistry = observerRegistry
    )

    val callState: StateFlow<CallState> = MutableStateFlow(CallState())
        get() = serviceState.callState

    private val callScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // Single entry point for all actions
    private fun processAction(action: CallAction) {
        serviceState = serviceState.actionProcessor.process(serviceState, action)
        // Update the callState flow from serviceState
    }
}
```

2. All existing methods (`startOutgoingCall`, `acceptCall`, `denyCall`, etc.) become action creators:
```kotlin
fun startOutgoingCall(remoteUserId: String, isVideo: Boolean) {
    processAction(CallAction.StartOutgoingCall(remoteUserId, isVideo))
}

fun acceptCall(withVideo: Boolean) {
    processAction(CallAction.AcceptIncomingCall(withVideo))
}

fun endCall() {
    processAction(CallAction.CallEnded)
}
```

3. Signaling callbacks become actions:
```kotlin
fun handleReceivedOffer(senderUserId: String, sdp: String, callId: String, isVideo: Boolean) {
    processAction(CallAction.ReceiveIncomingOffer(senderUserId, sdp, callId, isVideo))
}

fun handleReceivedAnswer(sdp: String) {
    processAction(CallAction.ReceiveAnswer(sdp))
}

fun handleReceivedIce(candidate: String) {
    processAction(CallAction.ReceiveIceCandidate(candidate))
}
```

4. PeerConnection observer callbacks become actions:
```kotlin
private fun createPeerConnectionObserver(): PeerConnection.Observer {
    return object : PeerConnection.Observer {
        override fun onIceConnectionChange(state: PeerConnection.IceConnectionState) {
            when (state) {
                PeerConnection.IceConnectionState.CONNECTED -> {
                    processAction(CallAction.CallConnected)
                }
                PeerConnection.IceConnectionState.DISCONNECTED -> {
                    processAction(CallAction.CallReconnecting)
                }
                PeerConnection.IceConnectionState.FAILED -> {
                    processAction(CallAction.CallFailed("ICE connection failed"))
                }
                else -> {}
            }
        }
        // ... other callbacks
    }
}
```

**Rules:**
- All state changes go through `processAction()`
- Action processor pattern ensures single-threaded, sequential state changes
- No direct state mutation outside of action processors
- All public methods become action creators
- Signaling and WebRTC callbacks become action dispatchers

---

### C10: Create Tests for Action Processor

**File:** `core/calls/src/test/java/org/enchant/core/calls/action/ActionProcessorTest.kt`

**Purpose:** Test the action processor state machine with all state transitions.

```kotlin
package org.enchant.core.calls.action

import org.enchant.core.calls.CallLogger
import org.enchant.core.calls.action.processors.IdleActionProcessor
import org.enchant.core.calls.action.processors.OutgoingCallActionProcessor
import org.enchant.core.calls.action.processors.IncomingCallActionProcessor
import org.enchant.core.calls.action.processors.ConnectedCallActionProcessor
import org.enchant.core.calls.model.CallStatus
import org.enchant.core.calls.observer.CallObserverRegistry
import org.enchant.core.calls.state.CallServiceState
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("ActionProcessor — Full Coverage")
class ActionProcessorTest {

    private lateinit var callLogger: CallLogger
    private lateinit var observerRegistry: CallObserverRegistry

    @BeforeEach
    fun setUp() {
        callLogger = mockk(relaxed = true)
        observerRegistry = mockk(relaxed = true)
    }

    @Nested @DisplayName("IdleActionProcessor")
    inner class IdleProcessorTest {
        @Test fun `StartOutgoingCall transitions to OutgoingCallActionProcessor`() {
            val state = CallServiceState(
                actionProcessor = IdleActionProcessor(callLogger, observerRegistry)
            )

            val newState = state.actionProcessor.process(
                state,
                CallAction.StartOutgoingCall("user1", false)
            )

            assertTrue(newState.actionProcessor is OutgoingCallActionProcessor)
            assertEquals(CallStatus.CALLING, newState.callState.status)
            assertEquals("user1", newState.callState.remoteUserId)
        }

        @Test fun `ReceiveIncomingOffer transitions to IncomingCallActionProcessor`() {
            val state = CallServiceState(
                actionProcessor = IdleActionProcessor(callLogger, observerRegistry)
            )

            val newState = state.actionProcessor.process(
                state,
                CallAction.ReceiveIncomingOffer("user1", "sdp", "call-1", true)
            )

            assertTrue(newState.actionProcessor is IncomingCallActionProcessor)
            assertEquals(CallStatus.RINGING, newState.callState.status)
            assertEquals("user1", newState.callState.remoteUserId)
            assertTrue(newState.callState.isVideoCall)
        }

        @Test fun `Other actions return same state`() {
            val state = CallServiceState(
                actionProcessor = IdleActionProcessor(callLogger, observerRegistry)
            )

            val result = state.actionProcessor.process(state, CallAction.ToggleMute)

            assertEquals(state, result)
        }
    }

    @Nested @DisplayName("OutgoingCallActionProcessor")
    inner class OutgoingProcessorTest {
        @Test fun `CallConnected transitions to ConnectedCallActionProcessor`() {
            val state = createOutgoingState()

            val newState = state.actionProcessor.process(state, CallAction.CallConnected)

            assertTrue(newState.actionProcessor is ConnectedCallActionProcessor)
            assertEquals(CallStatus.CONNECTED, newState.callState.status)
        }

        @Test fun `CallFailed transitions to IdleActionProcessor`() {
            val state = createOutgoingState()

            val newState = state.actionProcessor.process(state, CallAction.CallFailed("ICE failed"))

            assertTrue(newState.actionProcessor is IdleActionProcessor)
            assertEquals(CallStatus.ENDED, newState.callState.status)
        }

        private fun createOutgoingState(): CallServiceState {
            return CallServiceState(
                actionProcessor = OutgoingCallActionProcessor(callLogger, observerRegistry, "user1", false)
            )
        }
    }

    @Nested @DisplayName("ConnectedCallActionProcessor")
    inner class ConnectedProcessorTest {
        @Test fun `ToggleMute updates localDeviceState`() {
            val state = createConnectedState()

            val newState = state.actionProcessor.process(state, CallAction.ToggleMute)

            assertTrue(newState.localDeviceState.isMuted)
            assertTrue(newState.actionProcessor is ConnectedCallActionProcessor)
        }

        @Test fun `CallEnded transitions to IdleActionProcessor`() {
            val state = createConnectedState()

            val newState = state.actionProcessor.process(state, CallAction.CallEnded)

            assertTrue(newState.actionProcessor is IdleActionProcessor)
        }

        private fun createConnectedState(): CallServiceState {
            return CallServiceState(
                actionProcessor = ConnectedCallActionProcessor(callLogger, observerRegistry, "user1")
            )
        }
    }
}
```

**Rules:**
- Test all state transitions for each processor
- Test that wrong actions in wrong processor return unchanged state
- Test that each processor correctly transitions to next processor
- Use `mockk(relaxed = true)` for CallLogger and CallObserverRegistry
- All tests must be deterministic with no timing dependencies

---

## Implementation Order

1. **B1: CallForegroundService** — Start here (1-2 hours)
2. **B2: CallNotificationManager updates** — 1-2 hours
3. **B3: StatsCollector wiring** — 1-2 hours
4. **C1-C2: CallAction + ActionProcessor interface** — 2-3 hours
5. **C3: CallServiceState** — 2 hours
6. **C4-C7: All Action Processors** — 4-6 hours
7. **C8: ActiveCallDelegate** — 1 hour
8. **C9: Update DefaultCallManager** — 4-6 hours (biggest change)
9. **C10: Tests** — 4-6 hours

**Total estimated: 20-30 hours**

---

## Key Design Decisions

1. **Immutable State**: `CallServiceState` is immutable. All modifications create new copies.
2. **Single Action Processor**: Exactly one processor handles all actions for current state.
3. **Sealed Action Classes**: All actions are in `CallAction` sealed hierarchy.
4. **Delegation for Shared Logic**: Common behavior extracted to delegate classes.
5. **Single-threaded Execution**: All actions processed on single coroutine scope to avoid race conditions.
6. **Logging at Entry**: Every handler logs entrance with tag and relevant data.

---

## Dependencies

- `core/calls/model/` — CallState, CallStatus, CallEndReason, etc.
- `core/calls/observer/` — CallObserverRegistry
- `core/calls/CallLogger` — For logging call ends

## Files to Create/Modify

**New Files:**
- `core/calls/src/main/java/org/enchant/core/calls/action/CallAction.kt`
- `core/calls/src/main/java/org/enchant/core/calls/action/ActionProcessor.kt`
- `core/calls/src/main/java/org/enchant/core/calls/action/processors/IdleActionProcessor.kt`
- `core/calls/src/main/java/org/enchant/core/calls/action/processors/OutgoingCallActionProcessor.kt`
- `core/calls/src/main/java/org/enchant/core/calls/action/processors/IncomingCallActionProcessor.kt`
- `core/calls/src/main/java/org/enchant/core/calls/action/processors/ConnectedCallActionProcessor.kt`
- `core/calls/src/main/java/org/enchant/core/calls/action/delegates/ActiveCallDelegate.kt`
- `core/calls/src/main/java/org/enchant/core/calls/state/CallServiceState.kt`
- `core/calls/src/main/java/org/enchant/core/calls/state/CallServiceStateBuilder.kt`
- `core/calls/src/main/java/org/enchant/core/calls/notification/CallForegroundService.kt`
- `core/calls/src/test/java/org/enchant/core/calls/action/ActionProcessorTest.kt`

**Modified Files:**
- `core/calls/src/main/java/org/enchant/core/calls/CallManager.kt` — Add action processor, refactor all methods to use processAction()
- `core/calls/src/main/java/org/enchant/core/calls/notification/CallNotificationManager.kt` — Add updateDuration method
- `core/calls/src/main/java/org/enchant/core/calls/webrtc/StatsCollector.kt` — Ensure notifyQuality is wired
- `core/calls/src/main/AndroidManifest.xml` — Add CallForegroundService declaration

---

## Acceptance Criteria

- [ ] CallForegroundService starts with START_STICKY and proper foreground service type
- [ ] Active call notification updates every second with current duration
- [ ] StatsCollector emits quality stats to observers
- [ ] All action processors handle all relevant actions
- [ ] State transitions are deterministic and testable
- [ ] All existing tests still pass
- [ ] No direct state mutation outside of action processors
- [ ] All handlers log entrance with tag