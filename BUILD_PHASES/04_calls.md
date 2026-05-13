# Phase 4 — Calls

## Overview

Build WebRTC voice/video calling with proper state machine (like Signal's `SignalCallManager` + `WebRtcActionProcessor`), TURN relay, audio routing, and call log. 6 call screens + call log + call state machine.

**Architecture pattern:** State machine with per-state action processors (Signal pattern). A central `CallManager` routes actions to the correct processor based on current call state.

**Estimated files:** 18 files across `:core:calls` and `:feature:calls`
**Backend endpoints:** MRS (call signaling via WS), Gateway (TURN credentials)
**Prerequisites:** Phase 1 (WebSocket, networking) + Phase 3 (basic message pipeline)

---

## Backend API Contracts

### WebSocket Call Signaling
Sent/received as WS frames with message types:
- `CALL_OFFER` — SDP offer bytes
- `CALL_ANSWER` — SDP answer bytes
- `CALL_ICE` — ICE candidate bytes
- `CALL_END` — Hangup signal

### GET /v1/calls/turn-credentials
**Auth:** JWT required
**Rate limit:** 50/h per device
**Response:** TURN server credentials with username, password, urls

---

## Call State Machine

```
                         ┌──────────────────────────────────────────┐
                         │                                          │
                         v                                          │
    ┌──────────┐   ┌──────────┐   ┌──────────┐   ┌─────────────┐   │
    │  IDLE    │──▶│ PRE_JOIN │──▶│ CALLING  │──▶│  RINGING    │   │
    └──────────┘   └──────────┘   └──────────┘   └──────┬──────┘   │
         ▲                                                │          │
         │                                                v          │
         │                           ┌──────────┐   ┌──────────┐    │
         │                           │ ENDED    │◀──│CONNECTED │    │
         │                           └──────────┘   └─────┬────┘    │
         │                                ▲                │        │
         │                                │                v        │
         │                                │           ┌──────────┐  │
         └────────────────────────────────┴───────────│ RECONNECT│──┘
                                                       └──────────┘
```

---

## File Manifest

### `core/calls/src/main/java/org/enchant/core/calls/CallManager.kt`
**Purpose:** Signal's `SignalCallManager.java` equivalent — central call state machine, lives for app lifetime.

| Function | Signature | Description | Must Handle |
|---|---|---|---|
| `init` | `suspend fun init()` | Initialize WebRTC peer connection factory, audio manager | Already initialized → no-op |
| `process` | `fun process(action: CallAction)` | Route action to current state's processor | Unknown action in current state → log warning, no-op |
| `startOutgoingCall` | `fun startOutgoingCall(remoteUserId: String, isVideo: Boolean)` | Transition to CALLING, create offer, send CALL_OFFER | User already in call → show "already in call" |
| `acceptCall` | `fun acceptCall(callId: String, withVideo: Boolean)` | Transition to CONNECTING, create answer, send CALL_ANSWER | Call no longer valid → show error |
| `endCall` | `fun endCall()` | Send CALL_END → cleanup → transition to ENDED → transition to IDLE | — |
| `denyCall` | `fun denyCall()` | Send CALL_END (busy) → cleanup → IDLE | — |
| `toggleMute` | `fun toggleMute()` | Toggle local audio track enabled | — |
| `toggleVideo` | `fun toggleVideo()` | Toggle local video track enabled | — |
| `flipCamera` | `fun flipCamera()` | Switch between front/back camera | Only if video enabled |
| `toggleSpeaker` | `fun toggleSpeaker()` | Switch between speakerphone and earpiece | — |
| `setOnHold` | `fun setOnHold(hold: Boolean)` | Put call on hold (mute audio, pause video) | — |
| `raiseHand` | `fun raiseHand(raised: Boolean)` | Group call: raise/lower hand | Only in group calls |
| `react` | `fun react(emoji: String)` | Group call: send reaction | Only in group calls |
| `requestRemoteMute` | `fun requestRemoteMute(participantId: String)` | Group call: mute participant | Admin only |
| `removeParticipant` | `fun removeParticipant(participantId: String)` | Group call: remove participant | Admin only |
| `handleReceivedOffer` | `fun handleReceivedOffer(senderUserId: String, sdp: String, callId: String)` | Incoming call → transition to RINGING | Already in call → send busy |
| `handleReceivedAnswer` | `fun handleReceivedAnswer(sdp: String)` | Remote answered → set remote SDP → CONNECTED | — |
| `handleReceivedIce` | `fun handleReceivedIce(candidate: String)` | Add remote ICE candidate | Connection not started → buffer |
| `handleReceivedHangup` | `fun handleReceivedHangup()` | Remote ended → transition to ENDED | — |
| `selectAudioDevice` | `fun selectAudioDevice(device: AudioDevice)` | User-selected audio output | — |
| `retrieveTurnServers` | `suspend fun retrieveTurnServers()` | GET /v1/calls/turn-credentials | Cache for 1 hour |
| `callState` | `val callState: StateFlow<CallState>` | Observable full state | — |
| `localStream` | `val localStream: StateFlow<MediaStream?>` | Local video stream | Null if audio-only |
| `remoteStream` | `val remoteStream: StateFlow<MediaStream?>` | Remote video stream | Null if audio-only or not yet connected |
| `callLogs` | `fun getCallLogs(): Flow<List<CallLogEntry>>` | Observable call history | — |
| `insertMissedCall` | `suspend fun insertMissedCall(peerUserId: String, isVideo: Boolean)` | Insert missed call record | Called when incoming call not answered |
| `insertCallLog` | `suspend fun insertCallLog(remoteUserId: String, type: CallType, direction: CallDirection, duration: Int, status: CallStatus)` | Insert call history entry | — |

```kotlin
data class CallState(
    val status: CallStatusEnum,
    val remoteUserId: String?,
    val remoteName: String?,
    val callId: String?,
    val isVideoCall: Boolean,
    val isMuted: Boolean,
    val isSpeakerOn: Boolean,
    val isOnHold: Boolean,
    val durationSeconds: Int,
    val signalStrength: SignalStrength?  // GOOD, FAIR, POOR, NONE
)

enum class CallStatusEnum {
    IDLE, PRE_JOIN, CALLING, RINGING, CONNECTING, CONNECTED, RECONNECTING, ENDED
}

data class CallLogEntry(
    val callId: String,
    val remoteUserId: String,
    val type: CallType,           // AUDIO, VIDEO, GROUP_AUDIO, GROUP_VIDEO
    val direction: CallDirection, // INCOMING, OUTGOING
    val status: CallStatus,       // MISSED, ANSWERED, CANCELLED, OUTGOING
    val durationSeconds: Int,
    val timestamp: Long
)
```

**Test requirements:** 20 tests — state transitions each route correctly, outgoing call flow, incoming call flow, accept/hangup/deny, mute/video/speaker toggle, ICE candidate handling, TURN server fetch, inserted call log, missed call record, concurrent call prevention

---

### `core/calls/src/main/java/org/enchant/core/calls/WebRtcService.kt`
**Purpose:** Low-level WebRTC peer connection management.

| Function | Signature | Description | Must Handle |
|---|---|---|---|
| `createPeerConnection` | `suspend fun createPeerConnection(iceServers: List<IceServer>): RTCPeerConnection` | Create PC with STUN/TURN config | ICE servers empty → use defaults |
| `createOffer` | `suspend fun createOffer(pc: RTCPeerConnection): String` | Create SDP offer | — |
| `createAnswer` | `suspend fun createAnswer(pc: RTCPeerConnection): String` | Create SDP answer | — |
| `setRemoteDescription` | `fun setRemoteDescription(pc: RTCPeerConnection, sdp: String)` | Set remote SDP | Invalid SDP → throw |
| `addIceCandidate` | `fun addIceCandidate(pc: RTCPeerConnection, candidate: String)` | Add ICE candidate | — |
| `getLocalStream` | `suspend fun getLocalStream(isVideo: Boolean): MediaStream` | Get local audio/video stream | Camera permission → use audio only |
| `toggleAudioTrack` | `fun toggleAudioTrack(stream: MediaStream, enabled: Boolean)` | Enable/disable local audio | — |
| `toggleVideoTrack` | `fun toggleVideoTrack(stream: MediaStream, enabled: Boolean)` | Enable/disable local video | — |
| `switchCamera` | `fun switchCamera(videoTrack: VideoTrack)` | Front/back camera switch | — |
| `setSpeakerphoneOn` | `fun setSpeakerphoneOn(on: Boolean)` | Toggle speaker/earpiece | — |
| `getLocalFingerprint` | `fun getLocalFingerprint(pc: RTCPeerConnection): String?` | Get DTLS fingerprint for safety number | Not connected → null |
| `getRemoteFingerprint` | `fun getRemoteFingerprint(pc: RTCPeerConnection): String?` | Get remote DTLS fingerprint | Not connected → null |
| `dispose` | `fun dispose(pc: RTCPeerConnection)` | Clean up peer connection | — |

**Test requirements:** 10 tests — PC creation, offer/answer creation, SDP roundtrip, ICE add, stream management, camera switch, fingerprint extraction, dispose

---

### `core/calls/src/main/java/org/enchant/core/calls/AudioRouter.kt`
**Purpose:** Audio routing management — Signal's `SignalAudioManager` equivalent.

| Function | Signature | Description |
|---|---|---|
| `init` | `fun init()` | Initialize audio focus, bluetooth discovery | — |
| `startAudio` | `fun startAudio()` | Request audio focus, start audio session | — |
| `stopAudio` | `fun stopAudio(playDisconnect: Boolean)` | Release focus, stop audio | Play "call ended" tone if requested |
| `selectAudioDevice` | `fun selectAudioDevice(device: AudioDevice)` | Switch to specific device | Bluetooth, speaker, earpiece, wired headset |
| `setSpeakerphoneOn` | `fun setSpeakerphoneOn(on: Boolean)` | Toggle speaker | — |
| `startIncomingRinger` | `fun startIncomingRinger(ringtoneUri: Uri?)` | Play incoming call ringtone | Respect system DND |
| `startOutgoingRinger` | `fun startOutgoingRinger()` | Play "calling" tone | — |
| `stopRinger` | `fun stopRinger()` | Stop all ringtones | — |
| `vibrate` | `fun vibrate()` | Vibrate on incoming call | Check vibrate setting |

**Audio device priority:** Bluetooth headset > Wired headset > Speakerphone > Earpiece

**Tests:** 8 — init, start/stop audio, device selection each type, ringer start/stop, vibrate

---

### `feature/calls/src/main/java/org/enchant/calls/screens/IncomingCallScreen.kt`
**Route:** `/calls/incoming/{callId}` | **State:** `CallStatusEnum.RINGING`

Full-screen takeover with avatar, caller name, accept/decline buttons. Vibrate + ringtone. 30s auto-decline.

| UI Element | Behavior |
|---|---|
| Avatar | Large centered circle |
| Caller name | Below avatar, large text |
| "Incoming call" subtitle | Audio or video call label |
| Answer button | Green, phone icon. Tap → acceptCall(false) |
| Answer with video | Green, video icon. Tap → acceptCall(true) |
| Decline button | Red, phone-down icon. Tap → denyCall() |
| 30s timeout | Auto-decline after 30 seconds → insert missed call |
| E2EE label | "End-to-end encrypted" small text |

**Tests:** 6 — render, accept audio, accept video, decline, auto-decline after 30s, caller name display

---

### `feature/calls/src/main/java/org/enchant/calls/screens/OutgoingCallScreen.kt`
**Route:** `/calls/outgoing/{userId}` | **State:** `CallStatusEnum.CALLING`

Avatar, "Calling..." with bouncing dots animation, cancel button.

| UI Element | Behavior |
|---|---|
| Avatar | Large centered |
| "Calling..." with bouncing dots | Animation plays while CALLING state |
| Name below | Call recipient name |
| Speaker button | Toggle speaker on/off |
| End call button | Red. Tap → endCall(). Returns to chat. |
| Switch to video | Button to upgrade to video call |
| Timeout | 45s → auto-cancel → show "No answer" |

**Tests:** 4 — render, cancel tap, speaker toggle, timeout

---

### `feature/calls/src/main/java/org/enchant/calls/screens/ActiveVoiceCallScreen.kt`
**Route:** `/calls/active/{callId}` | **State:** `CallStatusEnum.CONNECTED`

Avatar, timer, signal quality indicator. Controls in bottom row.

| UI Element | Behavior |
|---|---|
| Avatar | Large centered circle |
| Name | Remote user name |
| Timer | MM:SS — increments every second |
| Signal quality | 3-4 dots changing with signal strength |
| Mute button | Toggle mute → icon changes (filled/outline) |
| Speaker button | Toggle speaker on/off |
| Keypad button | Show DTMF keypad |
| End call button | Red. Tap → endCall() |
| Video button | Switch to video call (upgrade) |
| Safety number | Tap → show safety number dialog |

**Tests:** 6 — render, timer increments, mute toggle, speaker toggle, end call, safety number dialog

---

### `feature/calls/src/main/java/org/enchant/calls/screens/ActiveVideoCallScreen.kt`
**Route:** `/calls/video/{callId}` | **State:** `CallStatusEnum.CONNECTED`

Remote video (full screen), self-view PiP (top-right corner), controls overlay.

| UI Element | Behavior |
|---|---|
| Remote video | Full screen, aspect-ratio fill |
| Self view PiP | Small rectangle, top-right, draggable |
| Controls overlay | Auto-hide after 3s, tap to show |
| Mute, Video flip, Speaker, End call | Same as voice call |
| Camera flip | Switch front/back |

**Tests:** 4 — render, PiP draggable, controls auto-hide, camera flip

---

### `feature/calls/src/main/java/org/enchant/calls/screens/GroupCallScreen.kt`
**Route:** `/calls/group/{callId}`

Participant grid, speaker view. Controls for individual participant management.

| UI Element | Behavior |
|---|---|
| Participant grid | Up to 6 visible, scroll for more |
| Speaker view | Tap participant to pin as speaker |
| Controls | Mute, video, speaker, add participant, end call |
| Admin controls | Mute participant, remove participant (if admin) |
| Raise hand | Toggle raise hand icon |
| Reactions | Send emoji reactions visible to all |

**Tests:** 4 — render grid, speaker view, admin controls, raise hand

---

### `feature/calls/src/main/java/org/enchant/calls/screens/CallLogScreen.kt`
**Route:** `/calls/log` | **Tab:** Calls tab in main navigation

Call history list with type icons, direction arrows, duration, missed/answered.

| UI Element | Behavior |
|---|---|
| Call row | Contact avatar, name, call type icon, direction arrow, duration, timestamp |
| Missed calls in red | Red indicator for missed calls |
| Unread count badge | On calls tab |
| Tap row | Show options: call back, message, view profile |
| Long press | Delete |

**Tests:** 4 — render list, missed call red, tap actions, delete

---

### `feature/calls/src/main/java/org/enchant/calls/SafetyNumberDialog.kt`
**Route:** Dialog — shows safety number for call verification.

| Function | Description |
|---|---|
| `showSafetyNumber(remoteUserId: String)` | Display safety number in XXXX-XXXX-XXXX-XXXX format + compare with remote |
| `formatFingerprint(fingerprint: String): String` | Format DTLS fingerprint into 4 groups of 4 hex chars |
| `verifyWithRecipient(remoteUserId: String)` | Compare safety numbers verbally/visually |

**Tests:** 3 — render, format fingerprint, verify action

---

## Module: `core/calls/src/main/java/org/enchant/core/calls/ActiveCallManager.kt`

**Purpose:** Manages ongoing call notification, screen lifecycle, and system interactions (foreground service, audio focus). Signal's `ActiveCallManager` equivalent. Applies ONLY when a call is active.

| Function | Signature | Description | Must Handle |
|---|---|---|---|
| `showCallNotification` | `fun showCallNotification(context: Context, remoteUserId: String, isVideoCall: Boolean)` | Show ongoing call notification with duration, mute/speaker/hangup actions | Update every 1s with new duration; use `MediaStyle` notification |
| `updateCallNotification` | `fun updateCallNotification(durationSeconds: Int)` | Update timer in notification | Called every second from CallManager |
| `cancelCallNotification` | `fun cancelCallNotification()` | Remove call notification | On call end |
| `startCallScreen` | `fun startCallScreen(context: Context, callId: String, isVideoCall: Boolean)` | Launch/update call activity | If activity already exists → bring to front |
| `stopCallScreen` | `fun stopCallScreen()` | Close call activity | On call end |
| `acquireAudioFocus` | `fun acquireAudioFocus(context: Context)` | Request audio focus for call | Abandon when call ends |
| `abandonAudioFocus` | `fun abandonAudioFocus(context: Context)` | Release audio focus | — |

**Notification actions:**
- Mute toggle: broadcasts `ACTION_MUTE` intent
- Speaker toggle: broadcasts `ACTION_SPEAKER` intent  
- End call: broadcasts `ACTION_HANGUP` intent

**Test requirements:** 6 tests — notification shows, timer updates, notification actions broadcast correct intents, call screen launches, audio focus acquired/released, notification cancels on end

---

## Module: `core/calls/src/main/java/org/enchant/core/calls/CallObserver.kt`

**Purpose:** Observer pattern for CallManager events — decouples call state changes from UI/screens. Signal's `CallManager.Observer` equivalent.

```kotlin
interface CallObserver {
    fun onCallStarted(remoteUserId: String, isVideoCall: Boolean) {}
    fun onCallEnded(reason: CallEndReason, summary: CallSummary?) {}
    fun onOfferSent(remoteUserId: String, sdp: String) {}
    fun onAnswerSent(remoteUserId: String, sdp: String) {}
    fun onIceCandidatesSent(remoteUserId: String, candidates: List<String>) {}
    fun onHangupSent(remoteUserId: String) {}
    fun onGroupCallRingUpdate(groupId: String, ringUpdate: RingUpdate) {}
    fun onMessageSentError(exception: Exception) {}
}

enum class CallEndReason { HANGUP_LOCAL, HANGUP_REMOTE, ANSWERED_ELSEWHERE, BUSY, TIMEOUT, ERROR }
data class CallSummary(val durationSeconds: Int, val wasVideoCall: Boolean, val wasOutgoing: Boolean)
```

| Function | Signature | Description |
|---|---|---|
| `registerObserver` | `fun registerObserver(observer: CallObserver)` | Register call event observer | Must handle multiple observers |
| `unregisterObserver` | `fun unregisterObserver(observer: CallObserver)` | Unregister observer | — |
| `notifyCallStarted` | `fun notifyCallStarted(...)` | Fire observers | Called by CallManager |
| `notifyCallEnded` | `fun notifyCallEnded(...)` | Fire observers | — |

**Test requirements:** 3 tests — register fires events, unregister stops events, multiple observers

---

## Module: `feature/calls/src/main/java/org/enchant/calls/calllinks/CallLinkManager.kt`

**Purpose:** Call links — shareable temporary call rooms that anyone with the link can join. Signal's `SignalCallLinkManager` equivalent.

| Function | Signature | Description | Must Handle |
|---|---|---|---|
| `createCallLink` | `suspend fun createCallLink(name: String, restrictions: CallLinkRestrictions): Result<String>` | Create call link with name + join restrictions | Link must be unique; owner can delete/edit |
| `getCallLink` | `suspend fun getCallLink(roomId: String): Result<CallLinkData>` | Get call link metadata | Works without auth for public links |
| `updateCallLinkName` | `suspend fun updateCallLinkName(roomId: String, name: String)` | Update link display name | Owner only |
| `updateCallLinkRestrictions` | `suspend fun updateCallLinkRestrictions(roomId: String, restrictions: CallLinkRestrictions)` | Update who can join | Owner only |
| `deleteCallLink` | `suspend fun deleteCallLink(roomId: String)` | Delete call link | Owner only |
| `joinCallLink` | `suspend fun joinCallLink(roomId: String): Result<CallLinkData>` | Join a call link — fetches credentials + connects | Handle expired/removed links |
| `getCallLinkCredentials` | `suspend fun getCallLinkCredentials(roomId: String): Result<CallLinkCredentials>` | Get auth credentials for joining | — |

```kotlin
data class CallLinkData(val roomId: String, val name: String, val creatorId: String, val restrictions: CallLinkRestrictions, val isActive: Boolean)
enum class CallLinkRestrictions { ANYONE, APPROVAL_REQUIRED, CONTACTS_ONLY }
data class CallLinkCredentials(val roomId: String, val authToken: String, val iceServers: List<IceServer>)
```

**Test requirements:** 6 tests — create, get, update name, update restrictions, delete, join with credentials

---

## Module: `feature/calls/src/main/java/org/enchant/calls/calllinks/CallLinkScreen.kt`

**Route:** `/call-link/{roomId}`

| UI Element | Behavior |
|---|---|
| Call link name + description | Display link metadata |
| "Join Call" button | Requests credentials → connects via CallManager |
| Share link | Shares room URL via Intent.ACTION_SEND |
| Admin controls (if owner) | Edit name, edit restrictions, delete link |

**Tests:** 3 — render, join call, share link

---

## Expanded CallLogViewModel (additions)

Add these functions to the existing `CallLogViewModel`:

| Function | Signature | Description |
|---|---|---|
| `startSelection` | `fun startSelection()` | Enter multi-select mode | — |
| `endSelection` | `fun endSelection()` | Exit multi-select mode | — |
| `toggleSelected` | `fun toggleSelected(callId: String)` | Toggle selection of a call entry | — |
| `selectAll` | `fun selectAll()` | Select all visible entries | — |
| `stageDeletion` | `fun stageDeletion(): StagedDeletion` | Stage selected or single entry for deletion | Returns a StagedDeletion object with preview (X calls will be deleted) |
| `confirmDeletion` | `suspend fun confirmDeletion(staged: StagedDeletion)` | Commit staged deletion | Show confirmation dialog first |
| `setFilter` | `fun setFilter(filter: CallLogFilter)` | Filter call log: ALL, MISSED, OUTGOING, INCOMING | — |
| `search` | `fun search(query: String)` | Search call log by contact name | Debounced 300ms |

```kotlin
data class StagedDeletion(val count: Int, val callIds: List<String>)
enum class CallLogFilter { ALL, MISSED, OUTGOING, INCOMING }
```

**Tests:** 6 tests — start/end selection, toggle, select all, stage/confirm deletion, filter, search

---

## Expanded CallManager (additions)

Add these functions to the existing `CallManager`:

| Function | Signature | Description |
|---|---|---|
| `setRingGroup` | `fun setRingGroup(shouldRing: Boolean)` | Set whether to ring on incoming group call | Only applies to group calls |
| `peekGroupCall` | `suspend fun peekGroupCall(groupId: String): PeekInfo?` | Peek group call without joining — get participant count, active status | Used to show "X people in call" badge |
| `peekCallLink` | `suspend fun peekCallLink(roomId: String): PeekInfo?` | Peek call link without joining | — |
| `sendGroupCallUpdateMessage` | `suspend fun sendGroupCallUpdateMessage(groupId: String, eraId: String, isCallFull: Boolean)` | Send "Alice joined the call" system message to group chat | — |
| `handleCallReconnect` | `suspend fun handleCallReconnect(newSession: String)` | Handle WebRTC reconnection after network drop | Preserve mute/video state |
| `handleReceivedOfferExpired` | `fun handleReceivedOfferExpired()` | Handle expired incoming call offer | Show "Call ended" if already ringing |
| `insertMissedCall` | `suspend fun insertMissedCall(peerUserId: String, isVideoCall: Boolean, timestamp: Long)` | Insert missed call record with full details | Must differentiate 1:1 vs group, incoming vs outgoing |
| `sendCallMessage` | `suspend fun sendCallMessage(remoteUserId: String, callMessage: CallMessage)` | Send call signaling message (opaque) via MRS | Used for custom call messages |

```kotlin
data class PeekInfo(val activeParticipants: Int, val maxParticipants: Int, val isActive: Boolean)
sealed class CallMessage {
    data class Offer(val sdp: String) : CallMessage()
    data class Answer(val sdp: String) : CallMessage()
    data class Ice(val candidate: String) : CallMessage()
    data object End : CallMessage()
}
```

**Tests:** 6 — ring group, peek group call, peek call link, send group update, reconnect preserves state, expired offer handled

---

## Acceptance Criteria (expanded)

All existing criteria plus:
- [ ] ActiveCallManager shows ongoing call notification with timer + mute/speaker/hangup actions
- [ ] Notification actions (mute, speaker, hangup) work correctly
- [ ] CallManager.Observer pattern: onboarding/offer/answer/ice/hangup callbacks fire and can be subscribed
- [ ] Call link created, shared, joined by another user
- [ ] Call link owner can edit name and restrictions
- [ ] Group call peek shows active participants without joining
- [ ] Call reconnection after network drop preserves mute/video state
- [ ] Call log: selection, staged deletion, filter, search all work
- [ ] Expired offer handled gracefully
- [ ] Group call update message sent to chat
- [ ] All tests pass (target: 100+ tests)
