# core:calls Audit

## Security Issues

### Critical: SignalingClient Interface - No Encryption Specified
- **File**: `SignalingClient.kt`
- **Issue**: The interface is a thin abstraction with no encryption guarantees. Methods like `sendOffer`, `sendAnswer`, `sendIceCandidate` transmit SDP and ICE candidates as raw strings over the wire. There is no TLS enforcement visible in the interface contract, and the actual transport implementation is external (injected).
- **Risk**: SDP payloads may contain IP addresses, codecs, and session keys. If signaling travels over unencrypted channels, man-in-the-middle attacks can intercept WebRTC session parameters.
- **Recommendation**: Require encrypted transport (TLS/WSS) in the interface contract and validate TLS certificates in implementations.

### Critical: TURN Credentials Stored in Memory as Plain Text
- **File**: `CallManager.kt` line 68-69, `WebRtcEngine.kt` line 50-53
- **Issue**: `turnServers` list stores `IceServer` objects containing `username` and `credential` fields. When creating `PeerConnection.IceServer`, the credential is passed as plain text:
  ```kotlin
  PeerConnection.IceServer.builder(s.urls)
      .setUsername(s.username ?: "")
      .setPassword(s.credential ?: "")
  ```
- **Risk**: Memory exposure if process is compromised. No secure storage (Keystore/AndroidKeyStore) used.
- **Recommendation**: Use short-lived TURN credentials fetched per-session. Implement credential refresh and avoid caching credentials longer than necessary.

### High: ICE Candidate Serialization Leaks Potentially Sensitive Data
- **File**: `IceCandidateHandler.kt` line 9-11
- **Issue**: Serialization format `sdpMid|sdpMLineIndex|sdp` embeds the candidate's `sdp` field which can contain private IP addresses (host candidates expose local LAN IPs).
  ```kotlin
  fun serialize(candidate: IceCandidate): String {
      return "${candidate.sdpMid}|${candidate.sdpMLineIndex}|${candidate.sdp}"
  }
  ```
- **Risk**: Local IP addresses transmitted over signaling channel and potentially logged.
- **Recommendation**: Normalize candidates to remove host candidates before transmission if possible. Avoid logging serialized candidates.

### Medium: CallNotificationReceiver Broadcast Vulnerability
- **File**: `CallNotificationReceiver.kt` lines 15-20
- **Issue**: Uses static `CallManager` singleton to handle intents without verifying call state or authorization. Any app with matching intent permission could trigger call actions.
- **Risk**: Confused deputy attack—malicious app could send `ACTION_ANSWER` to accept a call without user interaction.
- **Recommendation**: Use `checkCallingPermission()` for `android.permission.ANSWER_PHONE_CALLS` or validate against current call state before executing actions.

### Medium: No TLS Certificate Pinning in WebRTC Engine
- **File**: `WebRtcEngine.kt`
- **Issue**: The engine creates `PeerConnection` without any certificate pinning configuration. TURN/STUN server connections accept any certificate.
- **Recommendation**: Implement certificate pinning for TURN/STUN server connections.

---

## Bugs

### Bug: ICE Candidate Race Condition - PeerConnection May Not Exist
- **File**: `CallManager.kt` lines 217-224
- **Issue**: `handleReceivedIce(candidate: String)` queues candidates when `peerConnection` is null, but `iceHandler.queueRaw(candidate)` parses and only queues if successful. The subsequent `drainAndApply()` is called only when `onIceConnectionChange(CONNECTED)` fires in `createPeerConnectionObserver`. If remote peer's answer arrives before the local offer is set as local description (signaling race), ICE candidates may arrive before `PeerConnection` exists.
  ```kotlin
  fun handleReceivedIce(candidate: String) {
      val pc = peerConnection
      if (pc == null) {
          iceHandler.queueRaw(candidate)
          return
      }
      iceHandler.parse(candidate)?.let { pc.addIceCandidate(it) }
  }
  ```
- **Risk**: Race condition between receiving ICE candidates and creating PeerConnection. Candidates received before `startOutgoingCall` completes creating the peer connection are silently queued, but draining only happens on CONNECTED event from the *newest* peer connection.
- **Fix**: Ensure `drainAndApply` is called when PeerConnection is first created, not just on CONNECTED state.

### Bug: Signaling Timeout Only Checks CALLING/CONNECTING - Misses RINGING
- **File**: `CallManager.kt` lines 124-130
- **Issue**: The signaling timeout coroutine checks only `CALLING` or `CONNECTING` status but not `RINGING`. If an incoming call is answered elsewhere but the local device is still RINGING, the 30-second timeout does not trigger `SignalingTimeout`.
  ```kotlin
  if (current.status == CallStatus.CALLING || current.status == CallStatus.CONNECTING) {
      processAction(CallAction.SignalingTimeout)
  }
  ```
- **Risk**: Incoming call stays in RINGING state if peer answers elsewhere, leading to zombie call state.

### Bug: Double Processing of Answer in `handleReceivedAnswer`
- **File**: `CallManager.kt` line 207-215
- **Issue**: The function calls `processAction(CallAction.ReceiveAnswer(sdp))` which transitions state, and then calls `sdpHandler.setRemoteDescription(pc, sdp, ...)` separately. The `OutgoingCallActionProcessor.handleReceiveAnswer` only updates state but does NOT set the remote description—the calling code must do it. If the caller forgets (which it doesn't here, but it's fragile), the SDP is never applied.
- **Risk**: Unclear responsibility—action processing shouldn't be state-only if side effects are needed.
- **Recommendation**: Move remote description setting into the action processor OR ensure all callers consistently perform both operations.

### Bug: `handleAcceptIncomingCall` Has Race Window for `remoteUserId` Access
- **File**: `CallManager.kt` lines 153-183
- **Issue**: In `acceptCall`, the code reads `remoteUserId` from state:
  ```kotlin
  val remoteId = _serviceState.value.callState.remoteUserId
  if (remoteId != null) {
      signalingClient.sendAnswer(remoteId, sdp)
  }
  ```
  Between the time the offer was received (when `remoteUserId` was set) and the time `acceptCall` runs, the state could have been modified. The `OfferSdp` is stored in `CallSetupData` but `remoteUserId` is in `CallState`, creating a split data model.
- **Risk**: Race condition if multiple calls arrive simultaneously.

### Bug: `IncomingGroupCallActionProcessor.handleGroupCallRingUpdate` Uses `==` on GroupCallState
- **File**: `IncomingGroupCallActionProcessor.kt` lines 50-58
- **Issue**: Uses `state.groupCallState == GroupCallState.IDLE` which is reference equality on an enum. While Kotlin enums are singletons, this pattern is fragile if the enum changes.
- **Risk**: Low - but if `GroupCallState` were ever改成 class-based, this would break.

### Bug: `IceCandidateHandler.parse` Can Produce Wrong SDP on Pipe Characters
- **File**: `IceCandidateHandler.kt` line 13-21
- **Issue**: The parse function splits by `|` and joins with `|`: `parts.drop(2).joinToString("|")`. If the SDP itself contains `|` characters (rare but possible in candidate lines), reconstruction produces the wrong string.
  ```kotlin
  val sdp = parts.drop(2).joinToString("|")
  ```
- **Risk**: Corrupted ICE candidate SDP if SDP contains pipe characters.

---

## Completeness Gaps

### Gap: `peekGroupCall` Always Returns Null
- **File**: `CallManager.kt` lines 276-278
- **Issue**: The method is stubbed to return `null`:
  ```kotlin
  suspend fun peekGroupCall(groupId: String): PeekInfo? {
      return null
  }
  ```
- **Impact**: Group call peek functionality is completely unimplemented.

### Gap: SignalingClient Interface Lacks Group Call Signaling
- **File**: `SignalingClient.kt`
- **Issue**: The interface only has `sendOffer`, `sendAnswer`, `sendIceCandidate`, `sendHangup`, and `fetchTurnServers`. For group calls, you'd need methods like `sendGroupJoin`, `sendGroupLeave`, `sendRingUpdate`, `sendHandRaise`, `sendParticipantUpdate`. These are handled through `CallAction` dispatch in action processors but no signaling methods exist.

### Gap: No Call Link (Room-Based) Signaling Support
- **File**: `CallLinkModels.kt` has `CallLinkData`, `CallLinkCredentials`, and `CallLinkRestrictions`, but no signaling methods to create/join/leave call links. `SignalingClient` interface does not include call link operations.

### Gap: TURN Credential Fetch Has No Retry Logic
- **File**: `CallManager.kt` lines 288-295
- **Issue**: `fetchTurnServers()` catches all exceptions and falls back to a public STUN server. If TURN fetch fails repeatedly, the fallback is always the same public STUN without any exponential backoff or alerting.
  ```kotlin
  return signalingClient.fetchTurnServers().getOrElse {
      listOf(IceServer(urls = listOf("stun:stun.l.google.com:19302")))
  }
  ```
- **Impact**: Calls behind restrictive NAT will fail without TURN relay fallback.

### Gap: No Video Quality Adaptation
- **File**: `MediaStreamManager.kt` line 106
- **Issue**: Camera capture is hardcoded to 1280x720 at 30fps with no adaptation based on network conditions.
  ```kotlin
  capturer.startCapture(1280, 720, 30)
  ```
- **Impact**: Poor performance on low-bandwidth connections.

### Gap: No Call Recording Support
- **File**: None of the action processors or managers handle call recording. `CallAction` has no recording-related actions.

### Gap: No Call Transfer / Call Swap Support
- **File**: `CallManager.kt`
- **Issue**: No methods for attended transfer (call swap) or call forwarding.

---

## Code Quality Issues

### Issue: Singleton Pattern in `CallsModule` Is Thread-Unsafe
- **File**: `CallsModule.kt` lines 14-62
- **Issue**: The module uses a simple nullable `_callManager` with no synchronization:
  ```kotlin
  private var _callManager: DefaultCallManager? = null
  fun getCallManager(): DefaultCallManager = _callManager ?: throw IllegalStateException(...)
  fun setCallManager(manager: DefaultCallManager) { _callManager = manager }
  ```
- **Risk**: Race condition between `setCallManager` and `getCallManager` under concurrent access.

### Issue: Action Processors Accept Nullable Logger/Registry
- **File**: `IdleActionProcessor.kt` line 14, `OutgoingCallActionProcessor.kt` line 15
- **Issue**: `callLogger: CallLogger?` and `observerRegistry: CallObserverRegistry?` are nullable throughout the action processor hierarchy. Every call site uses null-safe access (`?.`) which pollutes the code and makes it easy to accidentally skip notifications.
  ```kotlin
  observerRegistry?.notifyConnected()
  ```
- **Risk**: Silent failures—no notification if registry is null. Hard to debug missing call events.

### Issue: State Machine and Action Processor Are Redundant
- **File**: `CallStateMachine.kt` and `ActionProcessor` hierarchy
- **Issue**: Two separate state management systems exist: `CallStateMachine` (used only in `startDurationTimer` to update duration) and `ActionProcessor` state transitions. The `CallStateMachine` has methods like `startOutgoing`, `receiveIncoming`, `acceptCall`, but these are NOT called from `CallManager`—only `updateDuration` is used. All actual state transitions happen via `processAction` which delegates to action processors.
- **Risk**: Confusion about which state machine is authoritative. Dead code in `CallStateMachine` methods that are never invoked.

### Issue: Duration Timer Counts CALLING State
- **File**: `CallManager.kt` lines 303-314
- **Issue**: Duration timer increments during `CALLING` status:
  ```kotlin
  if (current.status == CallStatus.CONNECTED || current.status == CallStatus.CALLING) {
      stateMachine.updateDuration(current.durationSeconds + 1)
  }
  ```
  This means call setup time (ringing/calling) counts toward call duration, which is semantically wrong—call duration should start from `CONNECTED`.

### Issue: No Cleanup of Video Capturer on Error in `createLocalStream`
- **File**: `MediaStreamManager.kt` lines 42-48
- **Issue**: If `addVideoTrack` fails, `release()` is not called, so capturer remains active.

### Issue: `CallNotificationReceiver` Uses Unscoped Coroutine
- **File**: `CallNotificationReceiver.kt` line 11
- **Issue**: Uses a static `CoroutineScope(SupervisorJob() + Dispatchers.IO)` that never cancels. If the receiver crashes, the scope continues running orphaned coroutines.
  ```kotlin
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  ```

### Issue: No Backpressure on ICE Candidate Queue
- **File**: `IceCandidateHandler.kt` line 7
- **Issue**: `pendingCandidates` is a `MutableList` with no size limit. A malicious peer could send thousands of candidates to cause memory exhaustion.
  ```kotlin
  private val pendingCandidates = mutableListOf<IceCandidate>()
  ```
- **Fix**: Add bounded queue with overflow rejection.

### Issue: `handleReceiveIceCandidate` in `BaseActionProcessor` Is Empty Stub
- **File**: `ActionProcessor.kt` lines 93-96
- **Issue**: The base implementation logs a warning and returns state unchanged. ICE candidates are handled directly in `CallManager.handleReceivedIce` bypassing the action processor entirely.
- **Risk**: Two code paths for ICE handling—bypassing the action system means state transitions triggered by ICE events happen outside the processor, making the system harder to reason about.

### Issue: Inconsistent Use of `CallPhase` vs `CallStatus`
- **File**: Multiple files
- **Issue**: `CallPhase` (IDLE, OUTGOING_CALL, INCOMING_CALL, CONNECTED, RECONNECTING, GROUP_CONNECTED, CALL_LINK) and `CallStatus` (IDLE, CALLING, RINGING, CONNECTING, CONNECTED, RECONNECTING, ENDED) are separate enums with overlapping concepts. `CallPhase.CONNECTED` vs `CallStatus.CONNECTED` is confusing.
- **Risk**: Bugs from mismatched state checks across the two systems.

### Issue: `StatsCollector` Only Collects From Single `candidate-pair`
- **File**: `StatsCollector.kt` lines 47-66
- **Issue**: The stats collection only looks at the first `candidate-pair` entry. If there are multiple candidate pairs (multiple network paths), only the first is used, and `bytesReceived`/`bytesSent` aggregate from ALL `inbound-rtp`/`outbound-rtp` entries, which is inconsistent.

### Issue: `DatabaseCallLogDao.mapStatus` Always Returns HANGUP_LOCAL for Unknown Status
- **File**: `DatabaseCallLogDao.kt` lines 122-127
- **Issue**: Unknown status values all map to `HANGUP_LOCAL`:
  ```kotlin
  private fun mapStatus(raw: String): CallEndReason = when (raw) {
      "missed" -> CallEndReason.BUSY
      "answered" -> CallEndReason.HANGUP_LOCAL
      "cancelled" -> CallEndReason.HANGUP_LOCAL
      else -> CallEndReason.HANGUP_LOCAL  // loses original meaning
  }
  ```
- **Risk**: Loss of fidelity—`TIMEOUT`, `ERROR`, `NETWORK_LOST` all become indistinguishable.

---

## Recommendations (Prioritized)

### P0 - Critical (Fix Immediately)
1. **Implement encryption for signaling transport** - Add TLS/WSS requirement to SignalingClient interface contract.
2. **Fix ICE candidate race condition** - Call `drainAndApply` immediately after PeerConnection creation, not just on CONNECTED event.
3. **Add bounds to pending ICE candidate queue** - Prevent memory exhaustion from malicious candidate flooding.

### P1 - High Priority
4. **Implement `peekGroupCall`** - Currently returns null, blocking group call peek UI.
5. **Fix `handleAcceptIncomingCall` race window** - Use `CallSetupData` consistently for remote user ID rather than splitting across `CallState` and `CallSetupData`.
6. **Add TURN credential refresh with retry logic** - Current fallback to public STUN leaves callers behind restrictive NAT without relay.
7. **Remove duplicate state machines** - `CallStateMachine` is only used for duration; `ActionProcessor` is the real state machine. Remove confusion.

### P2 - Medium Priority
8. **Add certificate pinning for TURN/STUN** - WebRTC engine should pin TLS certificates.
9. **Fix duration timer to start from CONNECTED** - Don't count CALLING time toward call duration.
10. **Add video quality adaptation** - Hardcoded 720p is inappropriate for low-bandwidth scenarios.
11. **Fix `mapStatus` to preserve original meaning** - Unknown DB values should not silently become `HANGUP_LOCAL`.
12. **Add proper scoped lifecycle to `CallNotificationReceiver`** - Use bound lifecycle instead of static scope.
13. **Implement signaling methods for group calls** - `SignalingClient` needs group call join/leave/ring methods.
14. **Add call link signaling support** - `CallLinkCredentials` exist but no signaling to use them.

### P3 - Low Priority / Tech Debt
15. **Fix `IceCandidateHandler.parse` SDP reconstruction** - Use a delimiter that can't appear in SDP.
16. **Add checkCallingPermission to CallNotificationReceiver** - Prevent confused deputy attacks.
17. **Clean up nullable `CallLogger`/`ObserverRegistry`** - Make them non-null or remove them entirely.
18. **Unify `CallPhase` and `CallStatus`** - Two parallel enum systems cause confusion.
19. **Fix `StatsCollector` consistency** - Either aggregate all candidate pairs or use single selected pair consistently.
20. **Implement call recording support** - Add `CallAction.Recording*` actions and handlers.
