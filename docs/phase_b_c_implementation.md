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
3. **Single-threaded execution** — all actions processed on single executor (NOT aspirational — MUST be implemented)
4. **Delegation** — shared logic extracted to delegate classes

### Threading Model (Signal-Style — CRITICAL)

Signal uses `HandlerThread` ("signal-web-rtc-service") with Handler.post() to serialize ALL state mutations on a single thread. This prevents race conditions.

**Our implementation MUST use single-threaded dispatcher:**

```kotlin
// In DefaultCallManager
private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default.limitedParallelism(1))

// All state changes go through this single-threaded entry point
private suspend fun processAction(action: CallAction) {
    serviceScope.launch {
        _serviceState.value = _serviceState.value.actionProcessor.process(_serviceState.value, action)
    }
}
```

**Why this matters:**
- `Dispatchers.Default` with unlimited parallelism allows concurrent state mutations
- Multiple coroutines modifying `_serviceState` simultaneously can cause race conditions
- Using `limitedParallelism(1)` ensures ALL actions are processed sequentially
- This matches Signal's HandlerThread + Handler.post() guarantees

**Rules:**
- ALL state changes MUST go through the single-threaded `serviceScope`
- No direct `_serviceState.value = ...` outside of `processAction()`
- PeerConnection callbacks MUST dispatch actions, not modify state directly
- The single-threaded guarantee ensures `process()` is atomic

### Exhaustive Compilation (Signal-Style)

Signal's `WebRtcActionProcessor` base class has ~60 protected methods. Adding a new action to the base class causes compiler to warn which processors don't override it.

**Our interface approach requires manual `when` matching. To make it exhaustive:**

```kotlin
interface ActionProcessor {
    fun process(state: CallServiceState, action: CallAction): CallServiceState {
        // Default implementation logs and returns unchanged state
        Log.w(tag, "Unhandled action: ${action::class.simpleName}")
        return state
    }
    // ...
}
```

**Better: Abstract base class with default no-op:**

```kotlin
abstract class BaseActionProcessor : ActionProcessor {
    override fun process(state: CallServiceState, action: CallAction): CallServiceState {
        return when (action) {
            is CallAction.StartOutgoingCall -> handleStartOutgoingCall(state, action)
            is CallAction.ReceiveIncomingOffer -> handleReceiveIncomingOffer(state, action)
            is CallAction.ReceiveAnswer -> handleReceiveAnswer(state, action)
            is CallAction.CallConnected -> handleCallConnected(state)
            is CallAction.CallEnded -> handleCallEnded(state)
            is CallAction.CallFailedTimeout -> handleCallFailedTimeout(state)
            is CallAction.CallFailedBusy -> handleCallFailedBusy(state)
            // etc... compiler will warn if we miss one
            else -> state
        }
    }

    protected abstract fun handleStartOutgoingCall(state: CallServiceState, action: CallAction.StartOutgoingCall): CallServiceState
    protected abstract fun handleReceiveIncomingOffer(state: CallServiceState, action: CallAction.ReceiveIncomingOffer): CallServiceState
    // etc...
}
```

**Rules:**
- Use abstract base class with protected methods for exhaustive compilation
- Each processor ONLY overrides the handlers it cares about
- Unhandled actions return unchanged state (no-op)
- Add new handler methods to base class — compiler tells you which processors need updating

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

    // ── Granular Error Reasons (Signal-style) ──
    // These map to proper UI states (busy tone, declined elsewhere, etc.)
    data object CallFailedTimeout : CallAction()           // Signaling timed out
    data object CallFailedIce : CallAction()              // ICE connection failed
    data object CallFailedDeclinedElsewhere : CallAction() // Call accepted on another device
    data object CallFailedBusy : CallAction()             // Remote user is busy
    data object CallFailedEndedElsewhere : CallAction()   // Call ended on another device
    data class CallFailedWithReason(val reason: CallEndReason) : CallAction() // Generic failure with reason

    // ── System Actions ──
    data object CallConnected : CallAction()
    data object CallReconnecting : CallAction()
    data object CallReconnected : CallAction()
    data object CallEnded : CallAction()
    data class QualityUpdate(val stats: CallQualityStats) : CallAction()

    // ── Timeout Actions ──
    data object IncomingCallTimeout : CallAction()
    data object SignalingTimeout : CallAction()
}

enum class CallEndReason {
    HANGUP_LOCAL,
    HANGUP_REMOTE,
    ANSWERED_ELSEWHERE,
    BUSY,
    TIMEOUT,
    ERROR,
    NETWORK_LOST,
    DECLINED_ELSEWHERE,
    ENDED_ELSEWHERE
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

    private fun handleCallFailedTimeout(state: CallServiceState): CallServiceState {
        Log.e(tag, "handleCallFailedTimeout: signaling timed out")
        observerRegistry?.notifyEnded(CallEndReason.TIMEOUT, null)
        return state.builder()
            .actionProcessor(IdleActionProcessor(callLogger, observerRegistry))
            .callState(state.callState.copy(status = CallStatus.ENDED, error = "Signaling timed out"))
            .callSetupData(null)
            .build()
    }

    private fun handleCallFailedBusy(state: CallServiceState): CallServiceState {
        Log.d(tag, "handleCallFailedBusy: remote user is busy")
        observerRegistry?.notifyEnded(CallEndReason.BUSY, null)
        return state.builder()
            .actionProcessor(IdleActionProcessor(callLogger, observerRegistry))
            .callState(state.callState.copy(status = CallStatus.ENDED, error = "User is busy"))
            .callSetupData(null)
            .build()
    }

    private fun handleCallFailedIce(state: CallServiceState): CallServiceState {
        Log.e(tag, "handleCallFailedIce: ICE connection failed")
        observerRegistry?.notifyEnded(CallEndReason.ERROR, null)
        return state.builder()
            .actionProcessor(IdleActionProcessor(callLogger, observerRegistry))
            .callState(state.callState.copy(status = CallStatus.ENDED, error = "Connection failed"))
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

    // ── Granular Error Handling (Signal-Style) ──

    private fun handleCallFailedTimeout(state: CallServiceState): CallServiceState {
        Log.e(tag, "handleCallFailedTimeout: signaling timed out")

        observerRegistry?.notifyEnded(CallEndReason.TIMEOUT, null)

        return state.builder()
            .actionProcessor(IdleActionProcessor(callLogger, observerRegistry))
            .callState(state.callState.copy(status = CallStatus.ENDED, error = "Signaling timed out"))
            .build()
    }

    private fun handleCallFailedBusy(state: CallServiceState): CallServiceState {
        Log.d(tag, "handleCallFailedBusy: remote user is busy")

        observerRegistry?.notifyEnded(CallEndReason.BUSY, null)

        return state.builder()
            .actionProcessor(IdleActionProcessor(callLogger, observerRegistry))
            .callState(state.callState.copy(status = CallStatus.ENDED, error = "User is busy"))
            .build()
    }

    private fun handleCallFailedDeclinedElsewhere(state: CallServiceState): CallServiceState {
        Log.d(tag, "handleCallFailedDeclinedElsewhere: call accepted on another device")

        observerRegistry?.notifyEnded(CallEndReason.DECLINED_ELSEWHERE, null)

        return state.builder()
            .actionProcessor(IdleActionProcessor(callLogger, observerRegistry))
            .callState(state.callState.copy(status = CallStatus.ENDED, error = "Call answered elsewhere"))
            .build()
    }

    private fun handleCallFailedEndedElsewhere(state: CallServiceState): CallServiceState {
        Log.d(tag, "handleCallFailedEndedElsewhere: call ended on another device")

        observerRegistry?.notifyEnded(CallEndReason.ENDED_ELSEWHERE, null)

        return state.builder()
            .actionProcessor(IdleActionProcessor(callLogger, observerRegistry))
            .callState(state.callState.copy(status = CallStatus.ENDED, error = "Call ended elsewhere"))
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

---

## Phase D: CallLogViewModel Full Rewrite

### D1: Signal-Style CallLogViewModel Implementation

**Reference:** Signal's `CallLogViewModel.kt` at `/home/nsk/project/Signal-Android-main/app/src/main/java/org/thoughtcrime/securesms/calls/log/`

Signal uses:
- **RxStore pattern** with `BehaviorProcessor` for reactive state
- **CallLogRepository** for database abstraction
- **PagedData** for call log loading
- **Three selection states**: `Includes` (opt-in), `Excludes` (opt-out), `All`

**File:** `feature/calls/src/main/java/org/enchant/calls/CallLogViewModel.kt`

**Implementation:**

```kotlin
package org.enchant.calls

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.enchant.core.calls.CallDirection
import org.enchant.core.calls.CallEndReason
import org.enchant.core.calls.CallLogFilter
import org.enchant.core.calls.CallManager
import org.enchant.core.calls.model.CallLogEntry

data class CallLogUiState(
    val entries: List<CallLogEntry> = emptyList(),
    val filter: CallLogFilter = CallLogFilter.ALL,
    val isLoading: Boolean = false,
    val isSelectionMode: Boolean = false,
    val selectedIds: Set<String> = emptySet(),
    val stagedDeletion: StagedDeletion? = null,
    val error: String? = null
)

sealed class CallLogSelectionState {
    data class Includes(val ids: Set<String>) : CallLogSelectionState()
    data class Excludes(val ids: Set<String>) : CallLogSelectionState()
    object All : CallLogSelectionState()
}

class CallLogViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(CallLogUiState())
    val uiState: StateFlow<CallLogUiState> = _uiState.asStateFlow()

    private val selectionState = MutableStateFlow<CallLogSelectionState>(CallLogSelectionState.Includes(emptySet()))

    init {
        loadCallLogs()
    }

    fun loadCallLogs() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val allLogs = CallManager.getCallLogs(100)
                val filteredLogs = applyFilter(allLogs, _uiState.value.filter)
                _uiState.update { it.copy(entries = filteredLogs, isLoading = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun setFilter(filter: CallLogFilter) {
        viewModelScope.launch {
            _uiState.update { it.copy(filter = filter) }
            loadCallLogs()
        }
    }

    private fun applyFilter(logs: List<CallLogEntry>, filter: CallLogFilter): List<CallLogEntry> {
        return when (filter) {
            CallLogFilter.ALL -> logs
            CallLogFilter.MISSED -> logs.filter { isMissedCall(it) }
            CallLogFilter.OUTGOING -> logs.filter { it.direction == CallDirection.OUTGOING }
            CallLogFilter.INCOMING -> logs.filter { it.direction == CallDirection.INCOMING }
        }
    }

    private fun isMissedCall(entry: CallLogEntry): Boolean {
        return entry.direction == CallDirection.INCOMING && 
               entry.status in listOf(CallEndReason.BUSY, CallEndReason.TIMEOUT)
    }

    fun startSelection() {
        _uiState.update { it.copy(isSelectionMode = true, selectedIds = emptySet()) }
        selectionState.value = CallLogSelectionState.Includes(emptySet())
    }

    fun endSelection() {
        _uiState.update { it.copy(isSelectionMode = false, selectedIds = emptySet()) }
        selectionState.value = CallLogSelectionState.Includes(emptySet())
    }

    fun toggleSelected(callId: String) {
        val current = _uiState.value.selectedIds
        val newSelection = if (callId in current) {
            current - callId
        } else {
            current + callId
        }
        _uiState.update { it.copy(selectedIds = newSelection) }
        selectionState.value = CallLogSelectionState.Includes(newSelection)
    }

    fun selectAll() {
        val allIds = _uiState.value.entries.map { it.callId }.toSet()
        _uiState.update { it.copy(selectedIds = allIds) }
        selectionState.value = CallLogSelectionState.All
    }

    fun stageDeletion(): StagedDeletion {
        val selectedIds = _uiState.value.selectedIds.toList()
        val staged = StagedDeletion(selectedIds)
        _uiState.update { it.copy(stagedDeletion = staged) }
        return staged
    }

    fun confirmDeletion(staged: StagedDeletion) {
        viewModelScope.launch {
            try {
                val pool = org.enchant.core.database.DatabasePool.instance
                pool?.writer?.let { db ->
                    db.execSQL("DELETE FROM call_logs WHERE call_id = ?", staged.callIds.toTypedArray())
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Failed to delete: ${e.message}") }
            }
            _uiState.update { 
                it.copy(
                    stagedDeletion = null, 
                    isSelectionMode = false, 
                    selectedIds = emptySet()
                )
            }
            loadCallLogs()
        }
    }
}
```

**Signal-Style Enhancements (Beyond Basic):**

Signal's `CallLogRepository` and `CallEventCache` add these production features:

1. **Paging** — Load 20 items at a time instead of all 100:
```kotlin
// PagedCallLogSource.kt
class PagedCallLogSource(
    private val callLogger: CallLogger,
    private val pageSize: Int = 20
) {
    suspend fun loadPage(offset: Int): List<CallLogEntry> {
        return callLogger.getCallLogs(limit = pageSize, offset = offset)
    }
}
```

2. **4-Hour Clustering** — Group consecutive calls to same peer within 4 hours:
```kotlin
// CallEventCluster.kt
data class CallEventCluster(
    val parentCallId: String,
    val childCallIds: Set<String>,
    val peerId: String,
    val direction: CallDirection,
    val callCount: Int,
    val latestTimestamp: Long
) {
    fun isWithinTimeout(other: CallLogEntry): Boolean {
        val fourHours = 4 * 60 * 60 * 1000L
        return (latestTimestamp - other.timestamp) < fourHours
    }
}

fun clusterCallLogs(entries: List<CallLogEntry>): List<CallEventCluster> {
    // Group by peer + direction + event type within 4-hour windows
    // Returns single cluster with children for display
}
```

3. **Includes/Excludes Deletion** — Efficient "delete all except" SQL:
```kotlin
sealed class CallLogSelectionState {
    data class Includes(val ids: Set<String>) : CallLogSelectionState()
    data class Excludes(val ids: Set<String>) : CallLogSelectionState()
    object All : CallLogSelectionState()

    fun isExclusionary(): Boolean = this is Excludes || this is All
}

fun buildDeletionQuery(state: CallLogSelectionState, filter: CallLogFilter): String {
    return when {
        state is CallLogSelectionState.All -> 
            "DELETE FROM call_logs WHERE filter = ?"
        state is CallLogSelectionState.Excludes -> 
            "DELETE FROM call_logs WHERE call_id NOT IN (?) AND filter = ?"
        state is CallLogSelectionState.Includes -> 
            "DELETE FROM call_logs WHERE call_id IN (?)"
    }
}
```

**Rules (Signal Pattern):**
- Use `viewModelScope.launch` for all async operations
- Apply filter in `applyFilter()` method, not in database query
- `isMissedCall()` checks direction INCOMING + status BUSY/TIMEOUT
- Selection state tracks `Set<String>` of call IDs — includes/excludes/all pattern
- Always reload logs after deletion
- Consider adding paging for large call histories (100+ entries)
- Consider adding clustering for repeated calls to same peer

---

### D2: CallLogViewModel Tests

**File:** `feature/calls/src/test/java/org/enchant/calls/CallLogViewModelTest.kt`

**Test Pattern (from Signal's `CallEventCacheTest`):**

```kotlin
package org.enchant.calls

import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.enchant.core.calls.CallDirection
import org.enchant.core.calls.CallEndReason
import org.enchant.core.calls.CallLogFilter
import org.enchant.core.calls.CallManager
import org.enchant.core.calls.CallsModule
import org.enchant.core.calls.model.CallLogEntry
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
@DisplayName("CallLogViewModel — Full Coverage")
class CallLogViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var mockCallManager: org.enchant.core.calls.DefaultCallManager

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        mockCallManager = mockk(relaxed = true)
        mockkObject(CallsModule)
        every { CallsModule.getCallManager() } returns mockCallManager
        coEvery { mockCallManager.getCallLogs(any()) } returns emptyList()
    }

    @AfterEach
    fun tearDown() {
        unmockkObject(CallsModule)
        Dispatchers.resetMain()
    }

    @Nested @DisplayName("Load Call Logs")
    inner class LoadCallLogsTest {
        @Test @DisplayName("loadCallLogs calls getCallLogs")
        fun `load call logs`() = runTest {
            val viewModel = CallLogViewModel()
            testDispatcher.scheduler.runCurrent()
            coEvery { mockCallManager.getCallLogs(100) } returns emptyList()
            viewModel.loadCallLogs()
            testDispatcher.scheduler.runCurrent()
            coVerify { mockCallManager.getCallLogs(100) }
        }

        @Test @DisplayName("loadCallLogs applies filter")
        fun `load call logs with filter`() = runTest {
            val logs = listOf(
                CallLogEntry("id1", "user1", null, org.enchant.core.calls.model.CallType.AUDIO, 
                    CallDirection.INCOMING, CallEndReason.BUSY, 0, System.currentTimeMillis()),
                CallLogEntry("id2", "user2", null, org.enchant.core.calls.model.CallType.AUDIO,
                    CallDirection.OUTGOING, CallEndReason.HANGUP_LOCAL, 60, System.currentTimeMillis())
            )
            coEvery { mockCallManager.getCallLogs(100) } returns logs
            val viewModel = CallLogViewModel()
            testDispatcher.scheduler.runCurrent()
            viewModel.setFilter(CallLogFilter.MISSED)
            testDispatcher.scheduler.runCurrent()
            assertTrue(viewModel.uiState.value.entries.isNotEmpty())
        }
    }

    @Nested @DisplayName("Filter")
    inner class FilterTest {
        @Test @DisplayName("setFilter changes filter and reloads")
        fun `set filter`() = runTest {
            val viewModel = CallLogViewModel()
            viewModel.setFilter(CallLogFilter.MISSED)
            testDispatcher.scheduler.runCurrent()
            assertEquals(CallLogFilter.MISSED, viewModel.uiState.value.filter)
        }
    }

    @Nested @DisplayName("Selection")
    inner class SelectionTest {
        @Test @DisplayName("startSelection enables selection mode")
        fun `start selection`() = runTest {
            val viewModel = CallLogViewModel()
            viewModel.startSelection()
            assertTrue(viewModel.uiState.value.isSelectionMode)
        }

        @Test @DisplayName("toggleSelected adds to selection")
        fun `toggle selected`() = runTest {
            val viewModel = CallLogViewModel()
            viewModel.startSelection()
            viewModel.toggleSelected("call-1")
            assertTrue(viewModel.uiState.value.selectedIds.contains("call-1"))
        }

        @Test @DisplayName("selectAll selects all entries")
        fun `select all`() = runTest {
            val logs = listOf(
                CallLogEntry("id1", "user1", null, org.enchant.core.calls.model.CallType.AUDIO,
                    CallDirection.OUTGOING, CallEndReason.HANGUP_LOCAL, 60, System.currentTimeMillis()),
                CallLogEntry("id2", "user2", null, org.enchant.core.calls.model.CallType.AUDIO,
                    CallDirection.OUTGOING, CallEndReason.HANGUP_LOCAL, 60, System.currentTimeMillis())
            )
            coEvery { mockCallManager.getCallLogs(100) } returns logs
            val viewModel = CallLogViewModel()
            testDispatcher.scheduler.runCurrent()
            viewModel.startSelection()
            viewModel.selectAll()
            assertEquals(2, viewModel.uiState.value.selectedIds.size)
        }
    }

    @Nested @DisplayName("Deletion")
    inner class DeletionTest {
        @Test @DisplayName("stageDeletion returns selected IDs")
        fun `stage deletion`() = runTest {
            val viewModel = CallLogViewModel()
            viewModel.startSelection()
            viewModel.toggleSelected("call-1")
            viewModel.toggleSelected("call-2")
            val staged = viewModel.stageDeletion()
            assertEquals(2, staged.callIds.size)
            assertTrue(staged.callIds.contains("call-1"))
            assertTrue(staged.callIds.contains("call-2"))
        }

        @Test @DisplayName("confirmDeletion clears selection and reloads")
        fun `confirm deletion`() = runTest {
            mockkStatic("org.enchant.core.database.DatabasePool")
            val mockPool = mockk<org.enchant.core.database.DatabasePool>(relaxed = true)
            val mockDb = mockk<org.enchant.core.database.DatabaseWriter>(relaxed = true)
            every { org.enchant.core.database.DatabasePool.instance } returns mockPool
            every { mockPool.writer } returns mockDb

            val viewModel = CallLogViewModel()
            testDispatcher.scheduler.runCurrent()
            viewModel.startSelection()
            viewModel.toggleSelected("call-1")
            val staged = viewModel.stageDeletion()
            viewModel.confirmDeletion(staged)
            testDispatcher.scheduler.runCurrent()
            assertFalse(viewModel.uiState.value.isSelectionMode)
            assertTrue(viewModel.uiState.value.selectedIds.isEmpty())
        }
    }

    @Nested @DisplayName("UI State")
    inner class UiStateTest {
        @Test @DisplayName("uiState has default values")
        fun `ui state defaults`() = runTest {
            val viewModel = CallLogViewModel()
            val state = viewModel.uiState.value
            assertNotNull(state)
            assertTrue(state.entries.isEmpty())
            assertEquals(CallLogFilter.ALL, state.filter)
            assertFalse(state.isLoading)
            assertFalse(state.isSelectionMode)
            assertTrue(state.selectedIds.isEmpty())
        }
    }
}
```

**Rules (Signal Pattern):**
- Mock `CallsModule.getCallManager()` returning mock `DefaultCallManager`
- Use `coEvery { mockCallManager.getCallLogs(any()) }` for suspend functions
- Use `testDispatcher.scheduler.runCurrent()` to advance async operations
- For database deletion test, mock `DatabasePool.instance` and `DatabasePool.writer`
- All tests must be deterministic with no timing dependencies

---

## Phase E: End-to-End Integration Tests

### E1: Test All Action Processors End-to-End

**File:** `core/calls/src/test/java/org/enchant/core/calls/action/ActionProcessorEndToEndTest.kt`

```kotlin
package org.enchant.core.calls.action

import io.mockk.mockk
import org.enchant.core.calls.CallLogger
import org.enchant.core.calls.action.processors.*
import org.enchant.core.calls.model.CallDirection
import org.enchant.core.calls.model.CallStatus
import org.enchant.core.calls.observer.CallObserverRegistry
import org.enchant.core.calls.state.CallServiceState
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("ActionProcessor End-to-End")
class ActionProcessorEndToEndTest {

    private lateinit var callLogger: CallLogger
    private lateinit var observerRegistry: CallObserverRegistry

    @BeforeEach
    fun setUp() {
        callLogger = mockk(relaxed = true)
        observerRegistry = mockk(relaxed = true)
    }

    @Nested @DisplayName("Full Call Lifecycle")
    inner class FullLifecycleTest {
        @Test fun `outgoing call goes through all phases`() {
            var state = CallServiceState(
                actionProcessor = IdleActionProcessor(callLogger, observerRegistry)
            )

            // IDLE -> OUTGOING
            state = state.actionProcessor.process(state, CallAction.StartOutgoingCall("user1", false))
            assertTrue(state.actionProcessor is OutgoingCallActionProcessor)
            assertEquals(CallStatus.CALLING, state.callState.status)

            // OUTGOING -> CONNECTED
            state = state.actionProcessor.process(state, CallAction.CallConnected)
            assertTrue(state.actionProcessor is ConnectedCallActionProcessor)
            assertEquals(CallStatus.CONNECTED, state.callState.status)

            // CONNECTED -> toggle mute
            state = state.actionProcessor.process(state, CallAction.ToggleMute)
            assertTrue(state.localDeviceState.isMuted)

            // CONNECTED -> end call
            state = state.actionProcessor.process(state, CallAction.CallEnded)
            assertTrue(state.actionProcessor is IdleActionProcessor)
            assertEquals(CallStatus.IDLE, state.callState.status)
        }

        @Test fun `incoming call goes through all phases`() {
            var state = CallServiceState(
                actionProcessor = IdleActionProcessor(callLogger, observerRegistry)
            )

            // IDLE -> INCOMING
            state = state.actionProcessor.process(state, CallAction.ReceiveIncomingOffer("user1", "sdp", "call-1", false))
            assertTrue(state.actionProcessor is IncomingCallActionProcessor)
            assertEquals(CallStatus.RINGING, state.callState.status)

            // INCOMING -> CONNECTING (accept)
            state = state.actionProcessor.process(state, CallAction.AcceptIncomingCall(false))
            assertEquals(CallStatus.CONNECTING, state.callState.status)

            // CONNECTING -> CONNECTED
            state = state.actionProcessor.process(state, CallAction.CallConnected)
            assertTrue(state.actionProcessor is ConnectedCallActionProcessor)
            assertEquals(CallStatus.CONNECTED, state.callState.status)

            // CONNECTED -> hangup
            state = state.actionProcessor.process(state, CallAction.ReceiveHangup(null))
            assertTrue(state.actionProcessor is IdleActionProcessor)
        }

        @Test fun `incoming call deny goes back to idle`() {
            var state = CallServiceState(
                actionProcessor = IdleActionProcessor(callLogger, observerRegistry)
            )

            state = state.actionProcessor.process(state, CallAction.ReceiveIncomingOffer("user1", "sdp", "call-1", true))
            assertEquals(CallStatus.RINGING, state.callState.status)

            state = state.actionProcessor.process(state, CallAction.DenyIncomingCall(null))
            assertTrue(state.actionProcessor is IdleActionProcessor)
            assertEquals(CallStatus.IDLE, state.callState.status)
        }

        @Test fun `incoming call timeout goes back to idle`() {
            var state = CallServiceState(
                actionProcessor = IdleActionProcessor(callLogger, observerRegistry)
            )

            state = state.actionProcessor.process(state, CallAction.ReceiveIncomingOffer("user1", "sdp", "call-1", true))
            state = state.actionProcessor.process(state, CallAction.IncomingCallTimeout)
            assertTrue(state.actionProcessor is IdleActionProcessor)
        }
    }

    @Nested @DisplayName("Error Handling")
    inner class ErrorHandlingTest {
        @Test fun `call failed transitions to idle`() {
            var state = CallServiceState(
                actionProcessor = IdleActionProcessor(callLogger, observerRegistry)
            )

            state = state.actionProcessor.process(state, CallAction.StartOutgoingCall("user1", false))
            state = state.actionProcessor.process(state, CallAction.CallFailed("ICE connection failed"))

            assertTrue(state.actionProcessor is IdleActionProcessor)
            assertEquals(CallStatus.ENDED, state.callState.status)
            assertEquals("ICE connection failed", state.callState.error)
        }

        @Test fun `outgoing call cancel transitions to idle`() {
            var state = CallServiceState(
                actionProcessor = IdleActionProcessor(callLogger, observerRegistry)
            )

            state = state.actionProcessor.process(state, CallAction.StartOutgoingCall("user1", false))
            state = state.actionProcessor.process(state, CallAction.CancelOutgoingCall(null))

            assertTrue(state.actionProcessor is IdleActionProcessor)
        }
    }
}
```

---

### E2: Test DefaultCallManager with Action Processor

**File:** `core/calls/src/test/java/org/enchant/core/calls/DefaultCallManagerActionProcessorTest.kt`

```kotlin
package org.enchant.core.calls

import io.mockk.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.enchant.core.calls.action.CallAction
import org.enchant.core.calls.model.CallState
import org.enchant.core.calls.model.CallStatus
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("DefaultCallManager — Action Processor Integration")
class DefaultCallManagerActionProcessorTest {

    private lateinit var mockStateMachine: CallStateMachine
    private lateinit var mockWebRtcEngine: WebRtcEngine
    private lateinit var mockMediaStreamManager: MediaStreamManager
    private lateinit var mockSdpHandler: SdpHandler
    private lateinit var mockIceHandler: IceCandidateHandler
    private lateinit var mockSignalingClient: SignalingClient
    private lateinit var mockAudioRouter: AudioRouter
    private lateinit var mockAudioFocusManager: AudioFocusManager
    private lateinit var mockRingtonePlayer: RingtonePlayer
    private lateinit var mockNotificationManager: CallNotificationManager
    private lateinit var mockCallLogger: CallLogger
    private lateinit var mockObserverRegistry: CallObserverRegistry

    @BeforeEach
    fun setUp() {
        mockStateMachine = mockk(relaxed = true)
        mockWebRtcEngine = mockk(relaxed = true)
        mockMediaStreamManager = mockk(relaxed = true)
        mockSdpHandler = mockk(relaxed = true)
        mockIceHandler = mockk(relaxed = true)
        mockSignalingClient = mockk(relaxed = true)
        mockAudioRouter = mockk(relaxed = true)
        mockAudioFocusManager = mockk(relaxed = true)
        mockRingtonePlayer = mockk(relaxed = true)
        mockNotificationManager = mockk(relaxed = true)
        mockCallLogger = mockk(relaxed = true)
        mockObserverRegistry = mockk(relaxed = true)

        every { mockStateMachine.state } returns MutableStateFlow(CallState())
    }

    @Nested @DisplayName("Action Processing")
    inner class ActionProcessingTest {
        @Test fun `startOutgoingCall dispatches StartOutgoingCall action`() {
            val manager = DefaultCallManager(
                stateMachine = mockStateMachine,
                webRtcEngine = mockWebRtcEngine,
                mediaStreamManager = mockMediaStreamManager,
                sdpHandler = mockSdpHandler,
                iceHandler = mockIceHandler,
                signalingClient = mockSignalingClient,
                audioRouter = mockAudioRouter,
                audioFocusManager = mockAudioFocusManager,
                ringtonePlayer = mockRingtonePlayer,
                notificationManager = mockNotificationManager,
                callLogger = mockCallLogger,
                observerRegistry = mockObserverRegistry
            )

            // The action processor should receive StartOutgoingCall
            // We verify this by checking state transitions
            verify { mockObserverRegistry.notifyStarted(any(), any()) }
        }
    }
}
```

---

## Complete Implementation Order

### Phase A (Already Done ✅)
- Tests passing (107 feature:calls + 121 core:calls = 228 total)

### Phase B (Incremental Improvements)
1. **B1: CallForegroundService** — 1-2 hours
2. **B2: CallNotificationManager updates** — 1-2 hours
3. **B3: StatsCollector wiring** — 1-2 hours

### Phase C (Action Processor State Machine)
4. **C1: CallAction sealed classes** — 1 hour
5. **C2: ActionProcessor interface** — 30 min
6. **C3: CallServiceState + Builder** — 2 hours
7. **C4: IdleActionProcessor** — 1 hour
8. **C5: OutgoingCallActionProcessor** — 1 hour
9. **C6: IncomingCallActionProcessor** — 1 hour
10. **C7: ConnectedCallActionProcessor** — 1 hour
11. **C8: ActiveCallDelegate** — 1 hour
12. **C9: Update DefaultCallManager** — 4-6 hours
13. **C10: ActionProcessor tests** — 2-3 hours

### Phase D (CallLogViewModel Rewrite)
14. **D1: CallLogViewModel implementation** — 3-4 hours
15. **D2: CallLogViewModel tests** — 2-3 hours

### Phase E (End-to-End Tests)
16. **E1: ActionProcessor end-to-end tests** — 2 hours
17. **E2: DefaultCallManager integration tests** — 2 hours

**Total estimated: 25-35 hours**

---

## All Files Summary

**New Files to Create:**
- `core/calls/src/main/java/org/enchant/core/calls/action/CallAction.kt`
- `core/calls/src/main/java/org/enchant/core/calls/action/ActionProcessor.kt`
- `core/calls/src/main/java/org/enchant/core/calls/action/CallPhase.kt`
- `core/calls/src/main/java/org/enchant/core/calls/action/processors/IdleActionProcessor.kt`
- `core/calls/src/main/java/org/enchant/core/calls/action/processors/OutgoingCallActionProcessor.kt`
- `core/calls/src/main/java/org/enchant/core/calls/action/processors/IncomingCallActionProcessor.kt`
- `core/calls/src/main/java/org/enchant/core/calls/action/processors/ConnectedCallActionProcessor.kt`
- `core/calls/src/main/java/org/enchant/core/calls/action/delegates/ActiveCallDelegate.kt`
- `core/calls/src/main/java/org/enchant/core/calls/state/CallServiceState.kt`
- `core/calls/src/main/java/org/enchant/core/calls/state/CallServiceStateBuilder.kt`
- `core/calls/src/main/java/org/enchant/core/calls/notification/CallForegroundService.kt`
- `core/calls/src/test/java/org/enchant/core/calls/action/ActionProcessorTest.kt`
- `core/calls/src/test/java/org/enchant/core/calls/action/ActionProcessorEndToEndTest.kt`
- `core/calls/src/test/java/org/enchant/core/calls/DefaultCallManagerActionProcessorTest.kt`

**Modified Files:**
- `core/calls/src/main/java/org/enchant/core/calls/CallManager.kt` — Add action processor
- `core/calls/src/main/java/org/enchant/core/calls/notification/CallNotificationManager.kt` — Add updateDuration
- `core/calls/src/main/java/org/enchant/core/calls/webrtc/StatsCollector.kt` — Wire notifyQuality
- `app/src/main/AndroidManifest.xml` — Already has CallForegroundService declaration
- `feature/calls/src/main/java/org/enchant/calls/CallLogViewModel.kt` — Full rewrite (D1)
- `feature/calls/src/test/java/org/enchant/calls/CallLogViewModelTest.kt` — Full rewrite (D2)

---

## Complete Acceptance Criteria

### Core Functionality
- [ ] All 228 tests pass (107 feature:calls + 121 core:calls)
- [ ] CallForegroundService starts with START_STICKY and proper foreground service type
- [ ] Active call notification updates every second with current duration
- [ ] StatsCollector emits quality stats to observers

### Action Processor (Signal-Style)
- [ ] All action processors (Idle, Outgoing, Incoming, Connected) handle all relevant actions
- [ ] Granular error reasons (Timeout, Busy, DeclinedElsewhere, EndedElsewhere, IceFailed)
- [ ] State transitions are deterministic and testable
- [ ] No direct state mutation outside of action processors
- [ ] All handlers log entrance with tag

### Threading (Signal-Style)
- [ ] Single-threaded dispatcher via `limitedParallelism(1)` — NOT aspirational
- [ ] ALL state changes go through `processAction()` on single thread
- [ ] PeerConnection callbacks dispatch actions, not modify state directly

### Exhaustive Compilation (Signal-Style)
- [ ] Abstract base class with protected methods for each action
- [ ] Adding new action causes compiler to warn which processors need updating

### Call Log (Signal-Style)
- [ ] CallLogViewModel supports all filters (ALL, MISSED, OUTGOING, INCOMING)
- [ ] CallLogViewModel supports staged deletion
- [ ] Paging support for large call histories (20 items per page)
- [ ] 4-hour clustering for repeated calls to same peer

### Selection/Deletion (Signal-Style)
- [ ] Includes/Excludes/All selection states
- [ ] Efficient SQL for "delete all except" (Excludes pattern)
- [ ] `CallLogSelectionState.isExclusionary()` determines deletion strategy

### Reconnection Handling (Signal-Style)
- [ ] CONNECTED → RECONNECTING → CONNECTED properly handled
- [ ] `CallReconnecting` and `CallReconnected` actions processed correctly
- [ ] Quality stats updated during reconnection

### End-to-End
- [ ] End-to-end tests verify full call lifecycle (outgoing + incoming)
- [ ] All new code follows SOLID principles
- [ ] No duplication between action processors (use ActiveCallDelegate)
- [ ] DefaultCallManager integration tests pass