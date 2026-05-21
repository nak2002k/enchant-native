# Build Phases — libenchantcall

## Phase 1: Foundation (Week 1-2)

**Goal:** Repository structure, CMake build, basic WebRTC integration, 1:1 call lifecycle.

### 1.1 Repository Structure

```
libenchantcall/
├── CMakeLists.txt
├── README.md
├── AGENTS.md
├── docs/
│   ├── ARCHITECTURE.md
│   ├── PRODUCTION_READINESS.md
│   └── BUILD_PHASES/
│       ├── 01_foundation.md        ← You are here
│       ├── 02_signaling.md
│       ├── 03_encryption.md
│       ├── 04_group_calls.md
│       └── 05_polish.md
├── include/
│   └── enchantcall/
│       ├── enchantcall.h           ← Public C API header
│       ├── types.h                 ← Enums, structs
│       └── callbacks.h             ← Callback function pointers
├── src/
│   ├── media/
│   │   ├── peer_connection.cpp
│   │   ├── peer_connection.h
│   │   ├── audio_track.cpp
│   │   ├── audio_track.h
│   │   ├── video_track.cpp
│   │   ├── video_track.h
│   │   ├── audio_source.cpp
│   │   ├── audio_source.h
│   │   ├── video_source.cpp
│   │   ├── video_source.h
│   │   ├── ice_candidate.cpp
│   │   ├── ice_candidate.h
│   │   ├── sdp.cpp
│   │   └── sdp.h
│   ├── signaling/
│   │   ├── call_state_machine.cpp
│   │   ├── call_state_machine.h
│   │   ├── sdp_negotiation.cpp
│   │   └── sdp_negotiation.h
│   ├── secure/
│   │   ├── buffer.cpp
│   │   ├── buffer.h
│   │   ├── memzero.cpp
│   │   └── memzero.h
│   └── enchantcall.cpp             ← C API implementation
├── bindings/
│   └── android/
│       ├── enchant_call_jni.cpp
│       └── EnchantCall.kt          ← Kotlin wrapper
├── tests/
│   ├── test_peer_connection.cpp
│   ├── test_sdp.cpp
│   ├── test_ice.cpp
│   ├── test_state_machine.cpp
│   └── test_c_api.cpp
└── third_party/
    └── webrtc/                     ← WebRTC submodule or prebuilt
```

### 1.2 CMake Build System

**CMakeLists.txt** must:
- Set C++17 standard
- Find/link WebRTC (Android: prebuilt AAR, iOS: CocoaPods, desktop: source build)
- Build `libenchantcall` as shared library for mobile, static for backend
- Support cross-compilation for Android (arm64-v8a, armeabi-v7a, x86_64)
- Enable GoogleTest for tests

**Agent instructions:**
1. Create `CMakeLists.txt` with the structure above
2. Add `find_package()` or `FetchContent` for WebRTC
3. Add `add_library(enchantcall SHARED ...)` for mobile targets
4. Add `add_library(enchantcall_static STATIC ...)` for backend
5. Add `enable_testing()` and `add_executable(test_enchantcall ...)`
6. Verify build succeeds on at least one platform (start with macOS or Linux)

### 1.3 Public C API Header

**`include/enchantcall/enchantcall.h`** — Define the complete C API surface:

```c
#ifndef ENCHANTCALL_H
#define ENCHANTCALL_H

#include <stdint.h>
#include <stddef.h>
#include <stdbool.h>

#ifdef __cplusplus
extern "C" {
#endif

// Opaque handle
typedef int64_t enchant_call_handle_t;

// Enums
typedef enum {
    ENCHANT_CALL_SUCCESS = 0,
    ENCHANT_CALL_ERROR_INVALID_HANDLE = -1,
    ENCHANT_CALL_ERROR_WEBRTC_FAILED = -2,
    ENCHANT_CALL_ERROR_SDP_FAILED = -3,
    ENCHANT_CALL_ERROR_ICE_FAILED = -4,
    ENCHANT_CALL_ERROR_ENCRYPTION_FAILED = -5,
    ENCHANT_CALL_ERROR_NULL_POINTER = -6,
    ENCHANT_CALL_ERROR_OUT_OF_MEMORY = -7,
} enchant_call_error_t;

typedef enum {
    ENCHANT_CALL_TYPE_AUDIO = 0,
    ENCHANT_CALL_TYPE_VIDEO = 1,
} enchant_call_type_t;

typedef enum {
    ENCHANT_CALL_STATE_IDLE = 0,
    ENCHANT_CALL_STATE_OFFERING = 1,
    ENCHANT_CALL_STATE_RINGING = 2,
    ENCHANT_CALL_STATE_CONNECTING = 3,
    ENCHANT_CALL_STATE_CONNECTED = 4,
    ENCHANT_CALL_STATE_ENDED = 5,
    ENCHANT_CALL_STATE_FAILED = 6,
} enchant_call_state_t;

typedef enum {
    ENCHANT_AUDIO_DEVICE_DEFAULT = 0,
    ENCHANT_AUDIO_DEVICE_EARPIECE = 1,
    ENCHANT_AUDIO_DEVICE_SPEAKER = 2,
    ENCHANT_AUDIO_DEVICE_BLUETOOTH = 3,
    ENCHANT_AUDIO_DEVICE_WIRED_HEADSET = 4,
} enchant_audio_device_t;

typedef enum {
    ENCHANT_VIDEO_QUALITY_LOW = 0,
    ENCHANT_VIDEO_QUALITY_MEDIUM = 1,
    ENCHANT_VIDEO_QUALITY_HIGH = 2,
} enchant_video_quality_t;

// Callbacks
typedef void (*enchant_call_state_callback_t)(
    enchant_call_handle_t handle,
    enchant_call_state_t state,
    void* user_data
);

typedef void (*enchant_call_ice_callback_t)(
    enchant_call_handle_t handle,
    const char* candidate_json,
    size_t json_len,
    void* user_data
);

typedef void (*enchant_call_error_callback_t)(
    enchant_call_handle_t handle,
    enchant_call_error_t error,
    const char* message,
    void* user_data
);

// Lifecycle
enchant_call_handle_t enchant_call_create(
    const char* user_id,
    const char* device_id,
    enchant_call_type_t type
);

int enchant_call_destroy(enchant_call_handle_t handle);

// SDP
int enchant_call_create_offer(
    enchant_call_handle_t handle,
    char** sdp_out,
    size_t* sdp_len
);

int enchant_call_create_answer(
    enchant_call_handle_t handle,
    const char* remote_sdp,
    size_t remote_sdp_len,
    char** sdp_out,
    size_t* sdp_len
);

int enchant_call_set_remote_sdp(
    enchant_call_handle_t handle,
    const char* sdp,
    size_t sdp_len
);

// ICE
int enchant_call_add_ice_candidate(
    enchant_call_handle_t handle,
    const char* candidate_json,
    size_t json_len
);

// Control
int enchant_call_hangup(enchant_call_handle_t handle);
int enchant_call_mute_audio(enchant_call_handle_t handle);
int enchant_call_unmute_audio(enchant_call_handle_t handle);
int enchant_call_mute_video(enchant_call_handle_t handle);
int enchant_call_unmute_video(enchant_call_handle_t handle);

// Callbacks
void enchant_call_set_state_callback(
    enchant_call_handle_t handle,
    enchant_call_state_callback_t callback,
    void* user_data
);

void enchant_call_set_ice_callback(
    enchant_call_handle_t handle,
    enchant_call_ice_callback_t callback,
    void* user_data
);

void enchant_call_set_error_callback(
    enchant_call_handle_t handle,
    enchant_call_error_callback_t callback,
    void* user_data
);

// Audio routing
int enchant_call_set_audio_device(
    enchant_call_handle_t handle,
    enchant_audio_device_t device
);

// Video
int enchant_call_set_video_quality(
    enchant_call_handle_t handle,
    enchant_video_quality_t quality
);

// Library init
int enchant_call_init(void);
void enchant_call_shutdown(void);

#ifdef __cplusplus
}
#endif

#endif // ENCHANTCALL_H
```

### 1.4 WebRTC PeerConnection Wrapper

**`src/media/peer_connection.cpp`** — Wrap `webrtc::PeerConnectionInterface`:

- Create PeerConnectionFactory
- Configure RTCConfiguration (ICE servers, DTLS, SRTP)
- Create PeerConnection with observer
- Implement PeerConnectionObserver callbacks:
  - `OnSignalingChange()` → emit state callback
  - `OnIceCandidate()` → emit ICE callback
  - `OnIceConnectionChange()` → emit state callback
  - `OnIceGatheringChange()` → emit state callback
  - `OnAddTrack()` → attach media tracks
  - `OnDataChannel()` → (for future data channel support)
- Thread-safe: all WebRTC calls on signaling thread

**Agent instructions:**
1. Implement `PeerConnectionWrapper` class in C++
2. Use WebRTC's `CreatePeerConnectionFactory()` and `CreatePeerConnection()`
3. Implement `PeerConnectionObserver` interface
4. Map WebRTC states to `enchant_call_state_t`
5. Serialize ICE candidates to JSON for the C API
6. Test: create a peer connection, verify state transitions

### 1.5 SDP Offer/Answer

**`src/media/sdp.cpp`** — Wrap `webrtc::SessionDescriptionInterface`:

- Create offer: `CreateOffer()` → `SetLocalDescription()` → return SDP string
- Create answer: `CreateAnswer()` → `SetLocalDescription()` → return SDP string
- Set remote SDP: `SetRemoteDescription()`
- SDP manipulation: prefer Opus for audio, VP8/VP9/H264 for video
- Serialize/deserialize SDP to/from strings

**Agent instructions:**
1. Implement `SdpWrapper` class
2. Use `CreateOffer()`, `CreateAnswer()`, `SetLocalDescription()`, `SetRemoteDescription()`
3. Implement `CreateSessionDescriptionObserver` for async callbacks
4. Test: create offer, set as local, create answer, set as remote

### 1.6 ICE Candidate Handling

**`src/media/ice_candidate.cpp`** — Wrap `webrtc::IceCandidateInterface`:

- Create ICE candidate from JSON string
- Serialize ICE candidate to JSON
- Add candidate to PeerConnection: `AddIceCandidate()`
- Support trickle ICE (candidates arrive asynchronously)

**Agent instructions:**
1. Implement `IceCandidateWrapper` class
2. JSON format: `{"candidate": "...", "sdpMid": "...", "sdpMLineIndex": ...}`
3. Test: create candidate, serialize, deserialize, add to connection

### 1.7 Call State Machine

**`src/signaling/call_state_machine.cpp`** — Pure state machine, no WebRTC dependency:

```
States: IDLE → OFFERING → RINGING → CONNECTING → CONNECTED → ENDED
                                                        → FAILED

Transitions:
  IDLE → OFFERING: create_offer()
  IDLE → RINGING: receive_offer()
  OFFERING → CONNECTING: receive_answer()
  RINGING → CONNECTING: create_answer()
  CONNECTING → CONNECTED: ice_connected()
  CONNECTING → FAILED: ice_failed()
  CONNECTED → ENDED: hangup() or remote_hangup()
  CONNECTED → FAILED: ice_failed()
  * → FAILED: error()
  * → ENDED: hangup()
```

**Agent instructions:**
1. Implement `CallStateMachine` class with state enum and transition table
2. Each transition validates current state and produces new state
3. Invalid transitions return error (e.g., hangup from IDLE)
4. Test: all valid transitions, all invalid transitions

### 1.8 C API Implementation

**`src/enchantcall.cpp`** — Implement all C API functions:

- Map handle to C++ object (use `std::unordered_map<int64_t, CallInstance*>`)
- Thread-safe handle management (mutex-protected map)
- Generate unique handles (atomic counter)
- Implement each C function by delegating to C++ objects
- Error handling: return `enchant_call_error_t` codes
- Memory management: caller must `free()` output strings

**Agent instructions:**
1. Implement `CallInstance` struct that holds PeerConnectionWrapper, StateMachine, etc.
2. Implement handle registry with mutex
3. Implement each C function from the header
4. Test: full C API lifecycle (create → offer → answer → hangup → destroy)

### 1.9 Android JNI Binding

**`bindings/android/enchant_call_jni.cpp`** — JNI wrapper:

- `Java_org_enchant_call_EnchantCall_nativeCreate()` → `enchant_call_create()`
- `Java_org_enchant_call_EnchantCall_nativeCreateOffer()` → `enchant_call_create_offer()`
- `Java_org_enchant_call_EnchantCall_nativeSetRemoteSdp()` → `enchant_call_set_remote_sdp()`
- `Java_org_enchant_call_EnchantCall_nativeAddIceCandidate()` → `enchant_call_add_ice_candidate()`
- `Java_org_enchant_call_EnchantCall_nativeHangup()` → `enchant_call_hangup()`
- `Java_org_enchant_call_EnchantCall_nativeDestroy()` → `enchant_call_destroy()`
- JNI callbacks → Kotlin callbacks via `CallJavaDispatcher`

**Agent instructions:**
1. Implement JNI functions for each C API function
2. Use `JNIEnv->NewStringUTF()` for string returns
3. Use `JNIEnv->GetByteArrayRegion()` for byte array inputs
4. Implement callback dispatch to Kotlin (use `CallJavaDispatcher`)
5. Test: build `.so` for arm64-v8a, load in Android app

### 1.10 Kotlin Wrapper

**`bindings/android/EnchantCall.kt`** — Kotlin facade:

```kotlin
class EnchantCall(
    val userId: String,
    val deviceId: String,
    val type: CallType = CallType.AUDIO
) {
    var state: CallState = CallState.IDLE
        private set

    var onStateChange: ((CallState) -> Unit)? = null
    var onIceCandidate: ((IceCandidate) -> Unit)? = null
    var onError: ((CallError, String) -> Unit)? = null

    fun createOffer(): String
    fun createAnswer(remoteSdp: String): String
    fun setRemoteSdp(sdp: String)
    fun addIceCandidate(candidate: IceCandidate)
    fun hangup()
    fun muteAudio()
    fun unmuteAudio()
    fun muteVideo()
    fun unmuteVideo()
    fun destroy()

    companion object {
        fun init()
        fun shutdown()
    }
}
```

### 1.11 Tests

**`tests/`** — GoogleTest tests:

- `test_peer_connection.cpp`: create, destroy, state transitions
- `test_sdp.cpp`: create offer, create answer, set remote SDP
- `test_ice.cpp`: create candidate, serialize, deserialize, add
- `test_state_machine.cpp`: all valid/invalid transitions
- `test_c_api.cpp`: full lifecycle through C API

**Agent instructions:**
1. Write tests for each component
2. Use mock WebRTC if needed (or skip WebRTC-dependent tests for now)
3. State machine tests must cover 100% of transitions
4. Run: `ctest --output-on-failure`

### 1.12 Deliverables

- [ ] Repository structure created
- [ ] CMake builds successfully on at least one platform
- [ ] `enchantcall.h` with complete C API
- [ ] PeerConnection wrapper working
- [ ] SDP offer/answer working
- [ ] ICE candidate handling working
- [ ] Call state machine with full transition coverage
- [ ] C API implementation complete
- [ ] Android JNI binding builds
- [ ] Kotlin wrapper with clean API
- [ ] All tests pass
- [ ] AGENTS.md written (no stubs rule, testing rule, commit rule)

---

## Phase 2: Signaling & Quality (Week 3-4)

**Goal:** Complete signaling protocol, quality monitoring, audio routing, camera control.

### 2.1 SDP Negotiation Enhancements

- Codec preference ordering (Opus > G722 > PCMU for audio; VP9 > VP8 > H264 for video)
- Bandwidth constraints in SDP
- Simulcast configuration for video
- DTLS fingerprint extraction for identity verification

### 2.2 Call Quality Monitor

**`src/signaling/call_quality_monitor.cpp`**:

- RTCP statistics: jitter, packet loss, RTT, bitrate
- Quality score calculation (0-100)
- Quality callback emission
- Adaptive bitrate based on network conditions

### 2.3 Audio Routing

**`src/media/audio_routing.cpp`**:

- Platform-specific audio routing (Android: `AudioManager`, iOS: `AVAudioSession`)
- Device detection (earpiece, speaker, Bluetooth, wired headset)
- Automatic routing on device connect/disconnect
- Manual override API

### 2.4 Camera Control

**`src/media/camera_control.cpp`**:

- Front/back camera enumeration
- Camera switching during active call
- Resolution/FPS selection
- Freeze detection (no frames for N seconds)

### 2.5 Protobuf Signaling Messages

**`src/proto/`**:

- `call_offer.proto` — CallOffer message
- `call_answer.proto` — CallAnswer message
- `call_ice.proto` — IceCandidate message
- `call_control.proto` — CallControl message
- Generate C++ code with `protoc`

### 2.6 Tests

- Quality monitor tests with mock RTCP stats
- Audio routing tests (platform-dependent, may need mocks)
- Camera control tests (platform-dependent)
- Protobuf serialization/deserialization tests

---

## Phase 3: Media Encryption (Week 5-6)

**Goal:** Triple Ratchet media encryption, SRTP transform, key rotation.

### 3.1 Call Ratchet

**`src/encryption/call_ratchet.cpp`**:

- Triple Ratchet for call media (symmetric + DH + PQ KEM)
- Integrate with libenchantcrypto for DH operations
- Per-packet key derivation
- Key serialization/deserialization

### 3.2 Media Cipher

**`src/encryption/media_cipher.cpp`**:

- Encrypt RTP packets with ratchet-derived keys
- Decrypt RTP packets
- Auth tag verification
- Replay protection (packet index tracking)

### 3.3 SRTP Transform

**`src/encryption/srtp_transform.cpp`**:

- Custom SRTP transform using ratchet keys (not DTLS-derived keys)
- Override WebRTC's default SRTP with our encrypted keys
- Implement `webrtc::FrameDecryptorInterface` and `webrtc::FrameEncryptorInterface`

### 3.4 Key Rotation

**`src/encryption/key_rotation.cpp`**:

- Automatic key rotation every N seconds or M packets
- Seamless rotation (no media interruption)
- Force rotation API

### 3.5 Tests

- Ratchet state machine tests
- Encrypt/decrypt roundtrip tests
- Key rotation tests
- Replay attack tests
- Out-of-order packet tests

---

## Phase 4: Group Calls (Week 7-8)

**Goal:** SFU-based group calls, epoch-based encryption, active speaker detection.

### 4.1 Group Call

**`src/groups/group_call.cpp`**:

- Group call lifecycle (create, join, leave, destroy)
- Member management (add, remove, enumerate)
- SFU connection management
- Stream subscription/unsubscription

### 4.2 SFU Client

**`src/groups/sfu_client.cpp`**:

- Connect to SFU via WebSocket
- Send upstream media to SFU
- Subscribe to downstream streams
- Simulcast layer selection
- SFU reconnection logic

### 4.3 Group Crypto

**`src/groups/group_crypto.cpp`**:

- Epoch-based key derivation for group calls
- Distribute epoch secret to members via E2EE
- Post-join secrecy (new members can't decrypt past media)
- Post-leave secrecy (departed members can't decrypt future media)
- Epoch rotation on member change

### 4.4 Speaker Selection

**`src/groups/speaker_selection.cpp`**:

- Active speaker detection (audio level analysis)
- Dominant speaker signaling to UI
- Last-speaker fallback

### 4.5 Tests

- Group call lifecycle tests
- SFU client tests (mock SFU)
- Group crypto tests (epoch derivation, post-join/leave secrecy)
- Speaker selection tests

---

## Phase 5: Polish & Production (Week 9-10)

**Goal:** Production readiness, iOS/macOS bindings, Windows/Linux builds, comprehensive tests.

### 5.1 iOS/macOS Swift Binding

**`bindings/ios/`**:

- Swift wrapper around C API
- `EnchantCall` class with async/await support
- Callback bridging (C callbacks → Swift closures)
- Build `.a` for iOS, `.dylib` for macOS

### 5.2 Windows C# Binding

**`bindings/windows/`**:

- C# P/Invoke wrapper
- `EnchantCall` class with async/await
- Build `.dll`

### 5.3 Linux Build

- Build `.so` for x86_64 and arm64
- CI/CD pipeline for all platforms

### 5.4 Production Readiness

- Update `PRODUCTION_READINESS.md`
- Security audit checklist
- Performance benchmarks (CPU, memory, latency)
- Stress tests (100+ concurrent calls)

### 5.5 Final Tests

- End-to-end 1:1 call test
- End-to-end group call test (3+ participants)
- Encryption verification tests
- Cross-platform interoperability tests

---

## Critical Rules (Same as libenchantcrypto)

1. **NO STUBS, NO PLACEHOLDERS, NO TODOS** — Every function must be complete and working
2. **Update BUILD_PHASES** on every change
3. **Commit and push** after every logical change
4. **Test security** — every test must also test attack vectors
5. **No raw new/delete** — use RAII, smart pointers
6. **All secret material zeroed** — `sodium_memzero()` before any free
7. **Constant-time comparison** — for MACs/signatures
8. **Structured error codes** — never throw exceptions across FFI boundary
