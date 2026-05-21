# Enchant-Native vs Signal-Android Call Module: Full Comparison

## Executive Summary

This document provides a comprehensive side-by-side comparison of the calling infrastructure in **Enchant-Native** (enchant-native) and **Signal-Android**, covering architecture, function coverage, security posture, state management, error handling, WebRTC integration, and group calling capabilities.

**Key Finding:** Signal's calling system is ~10 years mature with extensive edge-case handling, identity verification, telecom integration, multi-device support, and proven production hardening. Enchant-Native has a solid architectural foundation (better separation of concerns, cleaner Kotlin coroutines-based async) but lacks many critical production features that Signal has built over years of real-world usage.

---

## 1. Architecture Overview

### Enchant-Native Architecture

```
CallManager (DefaultCallManager)
├── CallStateMachine (standalone state tracking)
├── WebRtcEngine (PeerConnection factory)
├── MediaStreamManager (local media)
├── SdpHandler (SDP creation/parsing)
├── IceCandidateHandler (queuing/draining)
├── SignalingClient (interface, HTTP-based)
├── AudioRouter / AudioFocusManager
├── RingtonePlayer
├── CallNotificationManager
├── CallLogger / DatabaseCallLogDao
├── ObserverRegistry
└── Action Processors (6 processors, state-machine pattern)
    ├── IdleActionProcessor
    ├── OutgoingCallActionProcessor
    ├── IncomingCallActionProcessor
    ├── ConnectedCallActionProcessor
    ├── GroupCallActionProcessor
    └── IncomingGroupCallActionProcessor
```

**State Flow:**
- `CallServiceState` holds all state + current `ActionProcessor`
- Actions flow through `processAction()` → processor's `process()` → returns new state
- State updates synchronized via single `_serviceState` StateFlow

**Key Design Decisions:**
- Kotlin coroutines for async (vs Signal's ExecutorService + RxJava)
- Immutable `CallServiceState` with builder pattern
- Sealed class `CallAction` for type-safe actions (33 variants)
- `ActionProcessor` interface with `BaseActionProcessor` abstract class (33 handler methods)
- No dependency on Android telecom framework

### Signal-Android Architecture

```
SignalCallManager (1447 lines, implements CallManager.Observer, GroupCall.Observer)
├── CallManager (RingRTC native layer, created via CallManager.createCallManager())
├── WebRtcInteractor (bridges CallManager ↔ action processors)
├── WebRtcServiceState (mutable with defensive copy)
├── RxStore<WebRtcEphemeralState> (ephemeral UI state)
└── Action Processors (12+ processors, extend WebRtcActionProcessor base)
    ├── IdleActionProcessor
    ├── PreJoinActionProcessor
    ├── OutgoingCallActionProcessor
    ├── IncomingCallActionProcessor
    ├── ConnectedCallActionProcessor
    ├── DeviceAwareActionProcessor
    ├── GroupPreJoinActionProcessor
    ├── IncomingGroupCallActionProcessor
    ├── GroupJoiningActionProcessor
    ├── GroupConnectedActionProcessor
    ├── GroupNetworkUnavailableActionProcessor
    └── GroupActionProcessor
```

**State Flow:**
- `WebRtcServiceState` is mutable class (not data class)
- Actions processed via `ProcessAction` lambda submitted to single-threaded `serviceExecutor`
- State updates posted via `EventBus` (sticky events)
- `WebRtcViewModel` is the UI-facing state holder

**Key Design Decisions:**
- Java ExecutorService for serial execution (vs Kotlin coroutines)
- RxJava `Flowable` for ephemeral state streams
- `ResultReceiver` for telecom ↔ service communication
- Heavy use of `EventBus` for decoupled communication
- RingRTC native library handles WebRTC complexity
- ZKGroup for call link authentication

---

## 2. Action Processor Comparison

### WebRtcActionProcessor Base Class (Signal)

Signal's `WebRtcActionProcessor` is a **single abstract base class** (~1005 lines) with ~60 protected handler methods. Each processor overrides only the methods it handles; unhandled methods return the state unchanged (no logging).

**Key handlers in base class:**
- `handleSendOffer`, `handleSendAnswer`, `handleSendIceCandidates`, `handleSendHangup`, `handleSendBusy` — all `final` (cannot override)
- `handleReceivedOffer` — validates identity, PNI handling, notification profiles, opaque data, then delegates to `handleValidatedReceivedOffer`
- `handleCallConcluded` — cleans up EglBase, removes peer from map
- `callFailure` — resets call manager, force-releases EglBase, terminates call
- `terminate` (synchronized) — stops audio, updates phone state, stops foreground service, deinitializes video

### Enchant ActionProcessor Pattern

Enchant splits into **6 concrete processors** each handling a phase. `BaseActionProcessor` has 33 `open` handler methods with default no-op behavior (logs warning). The `process()` method routes all actions through a when-expression.

**Enchant advantage:** Cleaner separation by phase, easier to reason about what actions are valid in each state.

**Signal advantage:** All handlers in one class means no possibility of accidentally exposing handler methods that shouldn't be called in a phase — the base class returns currentState for all unoverridden methods silently.

---

## 3. State Management Comparison

### Signal State Structure

```
WebRtcServiceState
├── actionProcessor: WebRtcActionProcessor
├── callInfoState: WebRtcCallInfoState
│   ├── callRecipient: Recipient
│   ├── callState: WebRtcViewModel.State
│   ├── groupCallState: WebRtcViewModel.GroupCallState
│   ├── remotePeers: Map<Int, RemotePeer>
│   ├── groupCall: GroupCall?
│   └── ...
├── localDeviceState: WebRtcLocalDeviceState
│   ├── cameraState: CameraState
│   ├── audioDevice: SignalAudioManager.AudioDevice
│   ├── networkRoute: NetworkRoute
│   └── ...
├── callSetupState: Map<CallId, CallSetupState>
├── videoState: VideoState
│   ├── localSink: BroadcastVideoSink?
│   ├── router: OutgoingVideoSourceRouter?
│   └── ...
└── turnServers: List<PeerConnection.IceServer>
```

**Key characteristics:**
- `WebRtcServiceState` is **mutable** — modifications happen in place
- Builder (`WebRtcServiceStateBuilder`) creates new instance on build
- `WebRtcCallInfoState` holds `Map<Int, RemotePeer>` for multi-device calls
- `CallId` wraps long value (vs enchant's String UUID)
- `RemotePeer` is a rich class with call state, recipient, timing info
- `GroupCall` from RingRTC native library manages group call state

### Enchant State Structure

```
CallServiceState
├── actionProcessor: ActionProcessor
├── callState: CallState (status, remoteUserId, callId, isVideoCall, direction, duration, etc.)
├── callSetupData: CallSetupData? (offerSdp, answerSdp, receivedAt)
├── localDeviceState: LocalDeviceState (isMuted, isSpeakerOn, isVideoEnabled, isCameraFlipped, isOnHold, isHandRaised, handRaisedTimestamp, isAdmin)
├── qualityStats: CallQualityStats (rttMs, packetsLost, jitterMs, bytesReceived, bytesSent)
├── groupCallState: GroupCallState (enum: IDLE, RINGING, DISCONNECTED, CONNECTING, RECONNECTING, CONNECTED, CONNECTED_AND_PENDING, CONNECTED_AND_JOINING, CONNECTED_AND_JOINED)
├── groupCallParticipants: List<GroupCallParticipant>
├── callLogger: CallLogger?
└── observerRegistry: CallObserverRegistry?
```

**Key characteristics:**
- `CallServiceState` is **immutable** data class
- `CallServiceStateBuilder` for constructing new states
- `CallState` (model) is separate from `CallServiceState` (state machine)
- `GroupCallState` is simple enum (Signal uses more complex state machine via `WebRtcViewModel.GroupCallState`)
- No multi-device peer map — single `remoteUserId: String`
- No `CallId` wrapper type — uses plain String

**Comparison:**

| Aspect | Signal | Enchant |
|--------|--------|---------|
| Immutability | Mutable class, defensive copy on build | Immutable data class |
| Multi-device | Map<Int, RemotePeer> | Not supported |
| Call identification | CallId (long wrapper) | String (UUID) |
| Group call state | Complex enum + RingRTC GroupCall | Simple enum |
| Local device state | Rich CameraState, AudioDevice, NetworkRoute | Basic boolean flags |
| Builder pattern | WebRtcServiceStateBuilder (mutable builder) | CallServiceStateBuilder |
| Separate UI state | WebRtcEphemeralState via RxStore | Combined in CallServiceState |

---

## 4. Security Comparison

### Signal Security Features (NOT present in Enchant)

#### Identity Verification
```java
// Signal: handleReceivedOffer validates identity key before proceeding
if (receivedOfferMetadata.getRemoteIdentityKey() == null) {
    Log.w(tag, "Unable to locate remote identity key for caller, bailing");
    currentState = currentState.getActionProcessor().handleSendHangup(
        currentState, callMetadata, 
        WebRtcData.HangupMetadata.fromType(HangupMessage.Type.NORMAL), true);
    webRtcInteractor.insertMissedCall(...);
    return currentState;
}
```
**Enchant:** No identity key verification. Accepts incoming offers without validating sender identity.

#### PNI (Phone Number Identifier) Handling
```java
// Signal: Special handling when caller uses PNI (not ACI)
if (receivedOfferMetadata.getDestinationServiceId() instanceof ServiceId.PNI) {
    if (RecipientUtil.isCallRequestAccepted(callMetadata.getRemotePeer().getRecipient())) {
        // Trusted caller on PNI - insert missed call and send hangup
    } else {
        // Untrusted - insert missed call, don't send hangup
    }
    return currentState;
}
```
**Enchant:** No PNI concept. No distinction between ACI/PNI callers.

#### Notification Profile Restrictions
```java
// Signal: Check notification profiles before accepting call
NotificationProfile activeProfile = NotificationProfiles.getActiveProfile(
    SignalDatabase.notificationProfiles().getProfiles());
if (activeProfile != null && !(activeProfile.isRecipientAllowed(callMetadata.getRemotePeer().getId()) 
    || activeProfile.getAllowAllCalls())) {
    Log.w(tag, "Caller is excluded by notification profile.");
    webRtcInteractor.insertMissedCall(..., CallTable.Event.MISSED_NOTIFICATION_PROFILE);
    return currentState;
}
```
**Enchant:** No notification profile concept. All incoming calls treated equally.

#### Untrusted Identity Handling
```java
// Signal: If identity changes mid-call, transitions to UNTRUSTED_IDENTITY state
if (errorCallState == WebRtcViewModel.State.UNTRUSTED_IDENTITY) {
    CallParticipant participant = currentState.getCallInfoState()
        .getRemoteCallParticipant(activePeer.getRecipient());
    CallParticipant untrusted = participant.withIdentityKey(identityKey.orElse(null));
    builder.changeCallInfoState()
           .callState(WebRtcViewModel.State.UNTRUSTED_IDENTITY)
           .putParticipant(activePeer.getRecipient(), untrusted)
           .commit();
}
```
**Enchant:** No identity change detection. No UNTRUSTED_IDENTITY state.

#### PSTN Line Busy Detection
```java
// Signal: Check if phone line is busy before accepting incoming call
if (TelephonyUtil.isAnyPstnLineBusy(context)) {
    Log.i(tag, "PSTN line is busy.");
    currentState = currentState.getActionProcessor()
        .handleSendBusy(currentState, callMetadata, true);
    webRtcInteractor.insertMissedCall(...);
    return currentState;
}
```
**Enchant:** No PSTN busy detection.

#### Safety Number Verification
Signal has a full safety number verification flow before calls. Enchant has `SafetyNumberDialog` in the UI layer but no enforcement — no blocking of calls to unverified recipients.

#### Sealed Sender Verification
```java
// Signal: onSendCallMessage uses SealedSenderAccessUtil.getSealedSenderAccessFor(recipient)
try {
    AppDependencies.getSignalServiceMessageSender().sendCallMessage(
        RecipientUtil.toSignalServiceAddress(context, recipient),
        recipient.isSelf() ? SealedSenderAccess.NONE : SealedSenderAccessUtil.getSealedSenderAccessFor(recipient),
        callMessage);
} catch (UntrustedIdentityException e) {
    // Handles identity key changes gracefully
}
```
**Enchant:** `SignalingClient` is a simple interface with no sealed sender concept.

#### Hangup Type Specification
```java
// Signal: handleSendHangup includes hangup type (NORMAL, NEED_PERMISSION, etc.)
public @NonNull WebRtcServiceState handleSendHangup(@NonNull WebRtcServiceState currentState,
    @NonNull CallMetadata callMetadata,
    @NonNull HangupMetadata hangupMetadata,
    boolean broadcast)
```
**Enchant:** `ReceiveHangup` has optional `reason: String?` — no typed hangup reasons.

### Enchant Security Advantages

1. **Immutable state** — harder to have race conditions where state is modified during processing
2. **Type-safe actions** — sealed class `CallAction` prevents invalid action combinations
3. **Cleaner separation** — action processors are isolated, easier to audit

### Security Gap Summary

| Security Feature | Signal | Enchant |
|-----------------|--------|---------|
| Identity key verification | ✅ | ❌ |
| PNI vs ACI handling | ✅ | ❌ |
| Notification profiles | ✅ | ❌ |
| Identity change detection | ✅ | ❌ |
| PSTN busy detection | ✅ | ❌ |
| Safety number enforcement | ✅ | ❌ (UI exists, no blocking) |
| Sealed sender verification | ✅ | ❌ |
| Typed hangup reasons | ✅ | ❌ (String?) |
| Message send retry on identity change | ✅ | ❌ |
| Call link ZK authentication | ✅ | ❌ |

---

## 5. Function Coverage Comparison

### Call Lifecycle Functions

| Function | Signal | Enchant | Notes |
|----------|--------|---------|-------|
| Start outgoing call | ✅ `startOutgoingCall` | ✅ `startOutgoingCall` | Signal differentiates audio/video via separate methods |
| Receive offer | ✅ `receivedOffer` | ✅ `handleReceivedOffer` | Signal validates identity, PNI, profiles |
| Accept call | ✅ `acceptCall` | ✅ `acceptCall` | Signal checks PSTN busy |
| Deny call | ✅ `denyCall` | ✅ `denyCall` | Signal also stops ringtone |
| Hang up (local) | ✅ `localHangup` | ✅ `endCall` | Signal sends hangup via action processor |
| Hang up (remote) | ✅ `onCallEnded` callback | ✅ `handleReceiveHangup` | Signal handles 10+ end reasons |
| Cancel outgoing | ✅ `cancelPreJoin` | ✅ `CancelOutgoingCall` | Different API shape |
| Pre-join call | ✅ `startPreJoinCall` | ❌ | Signal allows preview before dialing |
| Call ended (by remote) | ✅ `onCallEnded` | ❌ | Signal handles via CallManager.Observer callback |

### Media Control Functions

| Function | Signal | Enchant |
|----------|--------|---------|
| Toggle mute | ✅ | ✅ |
| Toggle speaker | ✅ | ✅ |
| Toggle video | ✅ | ✅ |
| Flip camera | ✅ | ✅ |
| Set on hold | ✅ | ✅ |
| Screen share start | ✅ | ❌ |
| Screen share stop | ✅ | ❌ |
| Self raise hand | ✅ | ✅ |
| Send reaction | ✅ | ✅ (only logs) |
| Audio device change | ✅ | ❌ (handled internally) |

### Call Quality & State Functions

| Function | Signal | Enchant |
|----------|--------|---------|
| Quality update | ✅ | ✅ |
| Audio levels | ✅ | ❌ (only quality stats) |
| Reconnecting state | ✅ | ✅ |
| Reconnected state | ✅ | ✅ |
| Network route changed | ✅ | ❌ |
| Data mode update | ✅ | ❌ |
| Orientation changed | ✅ | ❌ |
| Camera switch completed | ✅ | ❌ |
| Screen off change | ✅ | ❌ |
| Bluetooth permission denied | ✅ | ❌ |

### Group Call Functions

| Function | Signal | Enchant |
|----------|--------|---------|
| Peek group call | ✅ | ❌ (`peekGroupCall` returns null) |
| Join group call | ✅ | ✅ (switches processor) |
| Leave group call | ✅ | ✅ |
| Group call ring update | ✅ | ✅ (only logs) |
| Request membership proof | ✅ | ❌ |
| Group members updated | ✅ | ✅ |
| Group raised hand | ✅ | ✅ |
| Group reactions | ✅ | ✅ (only logs) |
| Group local device state changed | ✅ | ❌ |
| Group remote device state changed | ✅ | ❌ |
| Remote mute request | ✅ | ✅ |
| Observed remote mute | ✅ | ❌ |
| Group call ended | ✅ | ✅ |
| Group message sent error | ✅ | ❌ |
| Approve safety number change | ✅ | ❌ |
| Resend media keys | ✅ | ❌ |
| Group call peek for ringing | ✅ | ❌ |

### Call Link Functions

| Function | Signal | Enchant |
|----------|--------|---------|
| Create call link | ✅ | ❌ (via API, not integrated) |
| Peek call link | ✅ | ❌ |
| Join call link | ✅ | ✅ (via CallLinkManager) |
| Set join request accepted | ✅ | ❌ |
| Set join request rejected | ✅ | ❌ |
| Send remote mute request | ✅ | ❌ |
| Remove from call link | ✅ | ❌ |
| Block from call link | ✅ | ❌ |
| ZK proof verification | ✅ | ❌ |

### Telecom Integration

| Function | Signal | Enchant |
|----------|--------|---------|
| Telecom approved callback | ✅ | ❌ |
| Drop call via telecom | ✅ | ❌ |
| ResultReceiver for call queries | ✅ | ❌ |

---

## 6. WebRTC Integration Comparison

### Signal WebRTC Integration

Signal uses **RingRTC** (a Signal-maintained wrapper over libwebrtc) which provides:
- `CallManager.createCallManager(Observer)` — creates native call manager
- `CallManager` methods: `receivedOffer`, `receivedAnswer`, `receivedIceCandidates`, `receivedHangup`, `hangup`, `accept`, `setMute`, `setVideo`, etc.
- `GroupCall` for group calls with native handling
- `CallId`, `CallSummary`, `CallEndReason`, `CallMediaType` enums
- `Remote` interface (implemented by `RemotePeer`) for call-specific remote peer handling

**Key callback interfaces:**
- `CallManager.Observer` — `onStartCall`, `onCallEnded`, `onCallEvent`, `onSendOffer`, `onSendAnswer`, `onSendIceCandidates`, `onSendHangup`, `onSendBusy`, `onNetworkRouteChanged`, `onAudioLevels`, etc.
- `GroupCall.Observer` — `onLocalDeviceStateChanged`, `onAudioLevels`, `onRaisedHands`, `onRemoteDeviceStatesChanged`, `onEnded`, etc.
- `CameraEventListener` — camera switch, camera stopped

**Offer/Answer metadata handling:**
```java
// Signal separates metadata from the actual offer/answer
public void receivedOffer(@NonNull WebRtcData.CallMetadata callMetadata,
                          @NonNull WebRtcData.OfferMetadata offerMetadata,
                          @NonNull WebRtcData.ReceivedOfferMetadata receivedOfferMetadata)
```
Where `CallMetadata` contains `CallId`, `RemotePeer`, `remoteDevice`, and `OfferMetadata` contains opaque bytes + offer type.

### Enchant WebRTC Integration

Enchant uses **libwebrtc directly** via:
- `PeerConnectionFactory` — created in `WebRtcEngine.initialize()`
- `PeerConnection` — created per-call via `webRtcEngine.createPeerConnection()`
- `PeerConnection.Observer` — handles ICE candidates, connection state changes, stream additions
- `MediaStream` — local stream created via `MediaStreamManager.createLocalStream()`

**Enchant's PeerConnection observer handles:**
- `onIceCandidate` — serializes and sends via signaling
- `onIceConnectionChange` — CONNECTED → `CallConnected`, DISCONNECTED → `CallReconnecting`, FAILED → `CallFailedIce`
- `onAddStream` (empty implementation)
- Other callbacks (empty implementations)

**Key limitation:** Enchant's `PeerConnection.Observer` is a simplified adapter — Signal's observer pattern is richer with more event types.

### Comparison

| Aspect | Signal | Enchant |
|--------|--------|---------|
| WebRTC wrapper | RingRTC (native) | libwebrtc directly |
| Call management | Native CallManager | PeerConnection only |
| Group call management | Native GroupCall | Not implemented |
| Multi-device support | Full via CallManager | Not supported |
| Call events | 20+ event types via observer | 3 main states |
| SRTP key negotiation | Handled by RingRTC | Handled by libwebrtc |
| Peer connection factory | RingRTC internal | Explicit `PeerConnectionFactory` |
| Video codec selection | RingRTC handles | Hardcoded defaults |
| Data channel | Supported via `onDataChannel` | Empty implementation |

---

## 7. Error Handling Comparison

### Signal End Reason Handling

Signal's `CallManager.CallEndReason` enum has **20+ variants**:
- `LOCAL_HANGUP`, `REMOTE_HANGUP`, `REMOTE_HANGUP_NEED_PERMISSION`, `REMOTE_HANGUP_ACCEPTED`, `REMOTE_HANGUP_DECLINED`, `REMOTE_HANGUP_BUSY`, `REMOTE_BUSY`, `REMOTE_GLARE`, `REMOTE_RECALL`
- `TIMEOUT`, `INTERNAL_FAILURE`, `SIGNALING_FAILURE`, `CONNECTION_FAILURE`
- `DEVICE_EXPLICITLY_DISCONNECTED`, `SERVER_EXPLICITLY_DISCONNECTED`
- `DENIED_REQUEST_TO_JOIN_CALL`, `REMOVED_FROM_CALL`
- `CALL_MANAGER_IS_BUSY`, `SFU_CLIENT_FAILED_TO_JOIN`
- `FAILED_TO_CREATE_PEER_CONNECTION_FACTORY`, `FAILED_TO_NEGOTIATE_SRTP_KEYS`, `FAILED_TO_CREATE_PEER_CONNECTION`, `FAILED_TO_START_PEER_CONNECTION`, `FAILED_TO_UPDATE_PEER_CONNECTION`, `FAILED_TO_SET_MAX_SEND_BITRATE`
- `ICE_FAILED_WHILE_CONNECTING`, `ICE_FAILED_AFTER_CONNECTED`

Each reason triggers appropriate handler — `handleEndedRemote`, `handleEnded`, or is logged and ignored.

### Enchant End Reason Handling

Enchant's `CallEndReason` enum has **7 variants**:
- `HANGUP_LOCAL`, `HANGUP_REMOTE`, `ANSWERED_ELSEWHERE`, `BUSY`, `TIMEOUT`, `ERROR`, `NETWORK_LOST`

Limited granularity compared to Signal. The action processors handle:
- `handleCallFailedTimeout` (OutgoingCallActionProcessor)
- `handleCallFailedBusy` (OutgoingCallActionProcessor)
- `handleCallFailedIce` (OutgoingCallActionProcessor, ConnectedCallActionProcessor)
- `handleCallFailedDeclinedElsewhere` (OutgoingCallActionProcessor)
- `handleCallFailedEndedElsewhere` (ConnectedCallActionProcessor)

**Missing handlers:**
- `IncomingCallActionProcessor` has no failure handlers — ICE failure in incoming phase would silently return state
- `handleCallFailedWithReason` is defined in `BaseActionProcessor` but never called

### Error Recovery Comparison

| Error Type | Signal Handling | Enchant Handling |
|------------|-----------------|------------------|
| ICE failure while connecting | Transitions to `handleEnded` with `ICE_FAILED_WHILE_CONNECTING` | `handleCallFailedIce` in OutgoingCallActionProcessor |
| ICE failure after connect | `handleCallReconnect` event + retry logic | `handleCallReconnecting` sets status, but no auto-retry |
| Signaling timeout | `handleSetupFailure` | `handleCallFailedTimeout` + `SignalingTimeout` action |
| Message send failure | `handleMessageSentError` with error state (UNTRUSTED_IDENTITY, NETWORK_FAILURE) | Not handled |
| Identity change | Transitions to UNTRUSTED_IDENTITY state | Not detected |
| Peer connection factory failure | `handleEnded` with `FAILED_TO_CREATE_PEER_CONNECTION_FACTORY` | Returns null from `createPeerConnection`, calls `endCall()` |
| Glare handling failure | `handleEnded` with `INTERNAL_FAILURE` | Not handled |

---

## 8. Signaling & Message Handling

### Signal Signaling

Signal uses the **Signals Service API** via `SignalServiceMessageSender`:
- `sendCallMessage` — sends call messages with sealed sender encryption
- `sendGroupCallMessage` — group message sending with ZK proofs
- Handles `UntrustedIdentityException` by kicking off `RetrieveProfileJob`
- Handles `ProofRequiredException` via `ProofRequiredExceptionHandler`
- Retry logic for failed sends

**Message types:**
- `SignalServiceCallMessage.forOffer(offerMessage, destinationDeviceId)`
- `SignalServiceCallMessage.forAnswer(answerMessage, destinationDeviceId)`
- `SignalServiceCallMessage.forIceUpdates(iceUpdateMessages, destinationDeviceId)`
- `SignalServiceCallMessage.forHangup(hangupMessage, destinationDeviceId)`
- `SignalServiceCallMessage.forBusy(busyMessage, destinationDeviceId)`
- `SignalServiceCallMessage.forOpaque(opaqueMessage, destinationDeviceId)` — for group calls
- `SignalServiceCallMessage.forOutgoingGroupOpaque` — for group opaque messages

**Key difference:** Signal's messages include `destinationDeviceId` for multi-device targeting. Broadcasts use `null`.

### Enchant Signaling

Enchant uses `SignalingClient` interface — a simple abstraction:
```kotlin
interface SignalingClient {
    suspend fun sendOffer(remoteUserId: String, sdp: String): Boolean
    suspend fun sendAnswer(remoteUserId: String, sdp: String): Boolean
    suspend fun sendIceCandidate(remoteUserId: String, candidate: String): Boolean
    suspend fun sendHangup(remoteUserId: String): Boolean
    suspend fun fetchTurnServers(): Result<List<IceServer>>
}
```

**Limitations:**
- No multi-device targeting (no `destinationDeviceId`)
- No sealed sender encryption
- No retry on identity failure
- No group message support
- No busy/idle indication support
- No opaque message support

---

## 9. Foreground Service & Notification

### Signal Foreground Service

Signal uses `WebRtcCallService` (extends Service) with:
- `START_STICKY` return value
- `FOREGROUND_SERVICE_TYPE_PHONE_CALL` (Android 14+ `FOREGROUND_SERVICE_TYPE_ONGOING_CALL`)
- Notification via `CallNotificationBuilder`
- `CallStateNotificationInfo` for call details
- Telecom integration via `Connection` subclass

### Enchant Foreground Service

`CallForegroundService` (135 lines):
- `START_STICKY` return value
- `FOREGROUND_SERVICE_TYPE_PHONE_CALL` + optional `CAMERA` for video
- Actions: `ACTION_START`, `ACTION_UPDATE`, `ACTION_STOP`
- Notification via `CallNotificationManager.buildForegroundNotification()`

**Comparison:**
- Signal's notification builder is more sophisticated (shows caller info, handles ongoing call notification updates)
- Enchant's notification is simpler (just shows remote user ID and duration)
- Signal has `CallNotificationBuilder.API_LEVEL_CALL_STYLE` for Android 14+ call style notifications
- Signal integrates with Android's telecom framework via `TelecomManager`

---

## 10. Database & Persistence

### Signal Database

Signal uses `SignalDatabase.calls()` which provides:
- `insertOneToOneCall` / `insertOrUpdateGroupCallFromRingState`
- `updateOneToOneCall`
- `updateGroupCallFromPeek`
- `insertAdHocCallFromLocalObserveEvent`
- Call event tracking: `MISSED`, `MISSED_NOTIFICATION_PROFILE`, `ACCEPTED`, etc.

### Enchant Database

Enchant uses `DatabaseCallLogDao` with:
- `insert(CallLogEntry)` — logs call with ID, remote user, type, direction, status, duration
- `insertMissed(peerUserId, isVideo, timestamp)` — inserts missed call entry
- `getAll(limit)` — retrieves call logs

**Comparison:**
- Signal's call table is more sophisticated with foreign keys to recipients and groups
- Signal tracks individual call events per call ID
- Enchant's `CallLogger` is simpler — just logging, no event history

---

## 11. Group Call Deep Comparison

### Signal Group Call Architecture

Signal uses RingRTC's `GroupCall` native class:
- Joined via `callManager.joinGroupCall(...)` with membership proof
- `GroupCall.Observer` receives all group call events
- ZKGroup-based authorization via `CallLinkAuthCredentialPresentation`
- Ring management via `cancelGroupRing`
- Peek with authorization tokens

**Key actions:**
- `handleGroupLocalDeviceStateChanged` — updates local device state in group
- `handleGroupRemoteDeviceStateChanged` — updates remote participants
- `handleGroupAudioLevelsChanged` — audio levels for all participants
- `handleGroupCallReaction` — receives and broadcasts reactions
- `handleGroupCallRaisedHand` — tracks raised hands (as `List<Long>` demuxIds)
- `handleGroupRequestMembershipProof` — requests proof from server
- `handleGroupMembershipProofResponse` — processes server response
- `handleGroupRequestUpdateMembers` — requests member list
- `handleGroupJoinedMembershipChanged` — when peek data changes
- `handleGroupCallEnded` — cleanup with reason
- `handleRemoteMuteRequest` — admin mutes participant
- `handleObservedRemoteMute` — observes mute state change
- `handleGroupCallSpeechEvent` — speaking notifications

### Enchant Group Call Architecture

Enchant's `GroupCallActionProcessor` (227 lines):
- Simpler state tracking via `GroupCallState` enum
- `handleJoinGroupCall` → sets state to CONNECTING
- `handleLeaveGroupCall` → resets to IDLE
- `handleGroupCallRaisedHand` — updates participant's hand raised flag
- `handleGroupMembersUpdated` — maps `CallParticipant` list to `GroupCallParticipant`
- `handleRemoveParticipant` / `handleBlockParticipant` — filters from list
- Remote mute/unmute — only affects `localDeviceState.isMuted` if target is self

**Gaps:**
- No membership proof verification
- No ring management
- No peek info (except as stub returning null)
- No ZK authentication for call links
- No reactions (just logs)
- No audio levels per participant
- No speaking notifications
- No peer connection management for group calls
- No SFU interaction

---

## 12. Which Side Is Better

### Where Signal Is Better (Required for Production)

1. **Identity Verification** — Cannot safely accept calls without verifying the caller's identity key. Enchant is vulnerable to man-in-the-middle attacks on the signaling layer.

2. **Multi-Device Support** — Signal correctly sends to specific device IDs and handles device-specific failures. Enchant broadcasts to "remoteUserId" with no device targeting.

3. **End Reason Granularity** — With 20+ end reasons, Signal can properly diagnose failures and take appropriate action. Enchant's 7 reasons miss critical distinctions (e.g., ICE failure while connecting vs after connected).

4. **Group Call with SFU** — Signal integrates with group calling infrastructure (membership proofs, ZK auth, ring management). Enchant has no real group call support.

5. **Call Link Security** — ZKGroup-based authentication for call links prevents unauthorized joining. Enchant's call links have no cryptographic verification.

6. **Telecom Integration** — Android telecom framework integration for system call UI, car立体声, etc. Enchant doesn't integrate.

7. **Message Retry on Identity Change** — Signal handles `UntrustedIdentityException` by updating identity and retrying. Enchant has no retry mechanism.

8. **Notification Profiles** — Enterprise/notification management features that Enchant doesn't implement.

9. **Safety Number Blocking** — Signal can block calls to unverified recipients. Enchant's UI shows safety numbers but doesn't enforce verification.

10. **Real PeerConnection Observer Events** — Signal handles all 20+ WebRTC events. Enchant handles only 3 states.

### Where Enchant Is Better (Design Improvements)

1. **Immutable State** — Enchant's `CallServiceState` being immutable is architecturally cleaner and prevents subtle concurrency bugs. Signal's mutable `WebRtcServiceState` relies on careful defensive copying.

2. **Kotlin Coroutines** — Using structured concurrency with coroutines is cleaner than Signal's `ExecutorService` + `KeyedSerialMonoLifoExecutor` pattern.

3. **Type-Safe Actions** — Enchant's sealed class `CallAction` with 33 variants is more type-safe than Signal's pattern of method-overridden handlers.

4. **Builder Pattern** — Enchant's `CallServiceStateBuilder` is cleaner than Signal's `WebRtcServiceStateBuilder` which has mutable internal state.

5. **Separation of Concerns** — Enchant's processor-per-phase is easier to reason about. Signal's single 1005-line `WebRtcActionProcessor` is harder to navigate.

6. **Action Processor Isolation** — Each Enchant processor only knows about actions relevant to its phase. Signal processors tend to have access to more state.

### Verdict

**Signal is significantly more production-ready.** Enchant has a better foundation in terms of code organization and type safety, but is missing critical production features that Signal has built over ~10 years of real-world usage:

- Security: No identity verification, no sealed sender, no safety number enforcement
- Reliability: No retry logic, coarse error granularity, no message send error recovery
- Features: No multi-device, no telecom integration, no group call SFU integration, no call links
- Completeness: ~40% function coverage compared to Signal

**Enchant is acceptable for a private/development build with trusted participants. It is NOT acceptable for a production communication app serving millions of users.**

---

## 14. Recommendations to Make Enchant Production-Ready

### Critical (Security - Must Have Before Production)

1. **Identity Verification**
   - Add `IdentityKey` storage and verification
   - Implement `handleReceivedOffer` validation similar to Signal
   - Block calls from untrusted identities

2. **Sealed Sender Integration**
   - Replace simple `SignalingClient` with sealed sender-aware messaging
   - Handle `UntrustedIdentityException` with profile retrieval + retry

3. **Safety Number Enforcement**
   - Add verification state to call state
   - Block or warn on calls to unverified recipients

4. **Typed Hangup Reasons**
   - Replace `String?` reason with `CallEndReason` enum variants
   - Add `HANGUP_NEED_PERMISSION`, `HANGUP_DECLINED`, etc.

### High (Reliability)

5. **Multi-Device Support**
   - Add `destinationDeviceId` to signaling
   - Handle device-specific message failures

6. **Message Send Error Handling**
   - Implement `handleMessageSentError` in action processors
   - Add retry on network failure

7. **More Granular End Reasons**
   - Expand `CallEndReason` enum to 20+ variants
   - Handle all `CallManager.CallEndReason` variants

### Medium (Features)

8. **Group Call with Membership Proofs**
   - Implement ZKGroup verification
   - Add ring management

9. **Call Link ZK Authentication**
   - Integrate ZKGroup for call link auth
   - Add proof verification before joining

10. **Audio Levels per Participant**
    - Add `Map<String, Int>` for audio levels
    - Update `CallObserver` with audio level callbacks

11. **Screen Share**
    - Add `MediaProjection` handling
    - Implement `handleSetLocalScreenShare`

### Low (Polish)

12. Telecom integration for system call UI
13. Android 14+ `CallNotificationBuilder` style notifications
14. Camera event listener for camera errors
15. Low bandwidth for video notification handling

---

## 15. File-by-File Reference

### Enchant Core Files

| File | Lines | Purpose |
|------|-------|---------|
| `CallManager.kt` | 389 | Central coordinator, all WebRTC setup |
| `CallStateMachine.kt` | 119 | Standalone state tracking (redundant with CallServiceState) |
| `CallsModule.kt` | 63 | DI provider |
| `CallLogger.kt` | ~50 | Call log persistence |
| `SignalingClient.kt` | 9 | Interface only, no implementation |
| `CallAction.kt` | 129 | 33 sealed action variants |
| `ActionProcessor.kt` | 262 | Base class + interface with 33 handlers |
| `CallPhase.kt` | 11 | 7-phase enum |
| `IdleActionProcessor.kt` | 76 | Start outgoing, receive incoming |
| `OutgoingCallActionProcessor.kt` | 100 | 7 failure handlers + answer + connected |
| `IncomingCallActionProcessor.kt` | 85 | Accept, deny, timeout, connected, hangup |
| `ConnectedCallActionProcessor.kt` | 151 | Mute, speaker, video, hold, hand, quality, failures |
| `GroupCallActionProcessor.kt` | 227 | Group control, reactions, mute |
| `IncomingGroupCallActionProcessor.kt` | 105 | Accept, deny, join group |
| `ActiveCallDelegate.kt` | ~100 | Delegates for active call |
| `CallServiceState.kt` | 76 | Immutable state + builder |
| `CallState.kt` (model) | 75 | Status, direction, type, end reason, etc. |
| `GroupCallModels.kt` | 46 | GroupCallState enum, GroupCallParticipant data class |
| `CallLinkModels.kt` | 30 | CallLinkData, CallLinkCredentials, CallParticipant |
| `CallObserver.kt` | 80 | Observer pattern for call events |
| `CallForegroundService.kt` | 135 | Foreground service with START/UPDATE/STOP |
| `CallNotificationManager.kt` | ~100 | Notification building |
| `WebRtcEngine.kt` | 78 | PeerConnection factory management |
| `MediaStreamManager.kt` | ~100 | Local media stream creation |
| `SdpHandler.kt` | ~80 | Offer/answer SDP creation |
| `IceCandidateHandler.kt` | ~100 | ICE candidate queuing and draining |
| `StatsCollector.kt` | ~80 | WebRTC stats collection |
| `AudioRouter.kt` | ~80 | Audio device routing |
| `AudioFocusManager.kt` | ~60 | Audio focus handling |
| `RingtonePlayer.kt` | ~60 | Incoming call ringtone |

### Signal Core Files

| File | Lines | Purpose |
|------|-------|---------|
| `SignalCallManager.java` | 1447 | Main entry point, Observer implementation |
| `WebRtcActionProcessor.java` | 1005 | Base action processor with ~60 handlers |
| `WebRtcInteractor.java` | ~800 | Bridges CallManager ↔ processors |
| `IdleActionProcessor.java` | ~200 | |
| `OutgoingCallActionProcessor.java` | ~300 | |
| `IncomingCallActionProcessor.java` | ~300 | |
| `ConnectedCallActionProcessor.java` | ~400 | |
| `PreJoinActionProcessor.java` | ~150 | |
| `GroupActionProcessor.java` | ~500 | |
| `GroupConnectedActionProcessor.java` | ~400 | |
| `IncomingGroupCallActionProcessor.java` | ~200 | |
| `WebRtcServiceState.java` | ~300 | Mutable state holder |
| `WebRtcServiceStateBuilder.java` | ~200 | Builder with mutable internals |
| `WebRtcData.java` | ~200 | Metadata classes |
| `WebRtcUtil.java` | ~200 | Utility methods |
| `WebRtcVideoUtil.java` | ~150 | Video utilities |