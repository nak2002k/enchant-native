# Architecture — libenchantcall

## Overview

**libenchantcall** is a cross-platform C++17 encrypted calling library built on vanilla Google WebRTC (BSD-3-Clause). It provides the calling backbone for the Enchant messaging platform.

### What It Does
- **1:1 encrypted calls** — Voice and video calls with Triple Ratchet media encryption
- **Group encrypted calls** — Multi-party calls with per-epoch key derivation
- **Call signaling** — SDP/ICE exchange via app-layer transport (WebSocket)
- **Audio routing** — Earpiece, speaker, Bluetooth, wired headset
- **Camera control** — Front/back switch, quality selection, freeze detection
- **Cross-platform** — Android (JNI), iOS/macOS (Swift), Windows (C#), Linux (native), Backend (direct C++ link)

### What It Does NOT Do
- Network transport (handled by app layer via WebSocket)
- UI/UX (handled by app layer — video renderers, call screens)
- Push notification delivery (handled by app layer)
- Call recording or transcription (handled by app layer)

### Why Not RingRTC?
- RingRTC is **AGPLv3** — would force Enchant to be open-source
- RingRTC is Signal-specific — tightly coupled to Signal's infrastructure
- RingRTC lacks call encryption — media is encrypted by WebRTC DTLS/SRTP but not E2EE beyond the transport layer
- We need **Triple Ratchet** media encryption — ratcheting keys during the call for forward secrecy

---

## System Architecture

```
┌─────────────────────────────────────────────────────────────────────┐
│                        Enchant Platform                            │
│                                                                     │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐           │
│  │ Android   │  │ iOS      │  │ Windows  │  │ Backend  │           │
│  │ (Kotlin)  │  │ (Swift)  │  │ (C#)     │  │ (C++)    │           │
│  └────┬─────┘  └────┬─────┘  └────┬─────┘  └────┬─────┘           │
│       │              │             │              │                  │
│       ▼              ▼             ▼              ▼                  │
│  ┌─────────────────────────────────────────────────────────────┐   │
│  │                  libenchantcall (C++17)                     │   │
│  │                                                             │   │
│  │  ┌───────────────────────────────────────────────────────┐ │   │
│  │  │              Clean C API (FFI Layer)                   │ │   │
│  │  │  enchant_call_create()  enchant_call_connect()        │ │   │
│  │  │  enchant_call_mute()    enchant_call_send_sdp()       │ │   │
│  │  │  ... all platform bindings call through here           │ │   │
│  │  └──────────────────────┬────────────────────────────────┘ │   │
│  │                         │                                   │   │
│  │  ┌──────────────────────▼────────────────────────────────┐ │   │
│  │  │              Call Protocol Layer                       │ │   │
│  │  │  Signaling  │  SDP Negotiation  │  ICE Handling       │ │   │
│  │  │  Call State Machine  │  Group Call Coordination        │ │   │
│  │  └──────────────────────┬────────────────────────────────┘ │   │
│  │                         │                                   │   │
│  │  ┌──────────────────────▼────────────────────────────────┐ │   │
│  │  │              Media Encryption Layer                    │ │   │
│  │  │  Triple Ratchet for media  │  Group key derivation    │ │   │
│  │  │  (integrates with libenchantcrypto)                    │ │   │
│  │  └──────────────────────┬────────────────────────────────┘ │   │
│  │                         │                                   │   │
│  │  ┌──────────────────────▼────────────────────────────────┐ │   │
│  │  │              Media Layer (WebRTC wrappers)             │ │   │
│  │  │  PeerConnection  │  AudioTrack  │  VideoTrack         │ │   │
│  │  │  AudioSource  │  VideoSource  │  ICE Candidate        │ │   │
│  │  └──────────────────────┬────────────────────────────────┘ │   │
│  │                         │                                   │   │
│  │  ┌──────────────────────▼────────────────────────────────┐ │   │
│  │  │          Google WebRTC (BSD-3-Clause License)          │ │   │
│  │  │  PeerConnection  │  MediaStream  │  ICE/STUN/TURN      │ │   │
│  │  └───────────────────────────────────────────────────────┘ │   │
│  └─────────────────────────────────────────────────────────────┘   │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘
```

---

## Layer Architecture

### Layer 1: Media (`src/media/`)

Thin wrappers around Google WebRTC C++ API. No protocol logic — just clean, safe wrappers that manage WebRTC object lifecycles.

| File | Wraps | Purpose |
|------|-------|---------|
| `peer_connection.cpp` | `webrtc::PeerConnectionInterface` | Create, configure, manage peer connections |
| `audio_track.cpp` | `webrtc::AudioTrackInterface` | Audio track creation, mute/unmute, volume |
| `video_track.cpp` | `webrtc::VideoTrackInterface` | Video track creation, camera switch, quality |
| `audio_source.cpp` | `webrtc::AudioSourceInterface` | Microphone capture, audio processing |
| `video_source.cpp` | `webrtc::VideoTrackSourceInterface` | Camera capture, video frames |
| `ice_candidate.cpp` | `webrtc::IceCandidateInterface` | ICE candidate creation, serialization |
| `sdp.cpp` | `webrtc::SessionDescriptionInterface` | SDP offer/answer creation, parsing |
| `media_stream.cpp` | `webrtc::MediaStreamInterface` | Stream management, track attachment |
| `audio_routing.cpp` | Platform audio APIs | Earpiece/speaker/Bluetooth routing |
| `camera_control.cpp` | Platform camera APIs | Front/back switch, resolution, FPS |

### Layer 2: Media Encryption (`src/encryption/`)

Triple Ratchet-based media encryption. Integrates with libenchantcrypto for key material.

| File | Purpose |
|------|---------|
| `call_ratchet.cpp` | Triple Ratchet for call media keys (classical DH + PQ KEM + symmetric ratchet) |
| `media_cipher.cpp` | Encrypt/decrypt RTP packets with ratcheting keys |
| `group_call_crypto.cpp` | Group call key derivation from epoch secrets |
| `key_rotation.cpp` | Periodic key rotation during active calls |
| `srtp_transform.cpp` | Custom SRTP transform using ratchet-derived keys |

### Layer 3: Signaling (`src/signaling/`)

Call signaling protocol — SDP/ICE exchange, call state machine, offer/answer negotiation.

| File | Protocol | Description |
|------|----------|-------------|
| `call_state_machine.cpp` | State Machine | Idle → Offering → Ringing → Connected → Ended |
| `sdp_negotiation.cpp` | SDP O/A | Offer/answer negotiation with codec preference |
| `ice_handler.cpp` | ICE | Candidate gathering, trickle ICE, connectivity checks |
| `signaling_messages.cpp` | Protobuf | Call offer, answer, ICE candidate, hangup, hold, resume |
| `call_quality_monitor.cpp` | RTCP | Jitter, packet loss, RTT monitoring, quality scoring |

### Layer 4: Group Calls (`src/groups/`)

Multi-party call coordination with SFU (Selective Forwarding Unit) architecture.

| File | Purpose |
|------|---------|
| `group_call.cpp` | Group call lifecycle, member management |
| `sfu_client.cpp` | SFU connection, stream subscription, layer selection |
| `group_crypto.cpp` | Group call encryption with epoch-based key rotation |
| `speaker_selection.cpp` | Active speaker detection, dominant speaker signaling |
| `grid_layout.cpp` | Stream layout metadata for UI |

### Layer 5: Proto (`src/proto/`)

Protobuf definitions for call signaling messages.

| File | Purpose |
|------|---------|
| `call_offer.cpp` | Call offer message (SDP + caller info) |
| `call_answer.cpp` | Call answer message (SDP + callee info) |
| `call_ice.cpp` | ICE candidate exchange |
| `call_control.cpp` | Hangup, hold, resume, mute notifications |
| `call_quality.cpp` | Quality metrics, survey responses |

### Layer 6: Secure (`src/secure/`)

Memory safety utilities (shared with libenchantcrypto pattern).

| File | Purpose |
|------|---------|
| `buffer.cpp` | SecureBuffer for key material |
| `memzero.cpp` | Zero sensitive memory after use |
| `lock.cpp` | Mutex wrappers for thread safety |

### Layer 7: FFI Bindings (`bindings/`)

Platform-specific bindings to the C API.

| Platform | Binding | Output |
|----------|---------|--------|
| Android | JNI (`enchant_call_jni.cpp`) | `libenchantcall.so` + Kotlin wrapper |
| iOS/macOS | Swift C interop | `libenchantcall.a` + Swift wrapper |
| Windows | C# P/Invoke | `enchantcall.dll` + C# wrapper |
| Linux | Direct C link | `libenchantcall.so` |
| Backend | Direct C++ link | Static library |

---

## Call Flow: 1:1 Encrypted Call

```
Alice (Caller)                                    Backend                                    Bob (Callee)
      │                                               │                                              │
      │  1. Create call offer                         │                                              │
      │  call = enchant_call_create()                 │                                              │
      │  sdp = enchant_call_create_offer(call)        │                                              │
      │                                               │                                              │
      │  2. Send offer via WebSocket                  │                                              │
      │  WS POST /v1/calls/offer ────────────────────►│                                              │
      │  { sdp, ice_candidates, call_id, caller_id }  │                                              │
      │                                               │  3. Push notification to Bob                 │
      │                                               │  FCM push ──────────────────────────────────►│
      │                                               │                                              │
      │                                               │  4. Bob accepts, creates answer              │
      │                                               │  call = enchant_call_create()                │
      │                                               │  sdp = enchant_call_create_answer(call)      │
      │                                               │                                              │
      │                                               │  5. Send answer via WebSocket                │
      │                                               │  WS POST /v1/calls/answer ◄──────────────────│
      │                                               │  { sdp, ice_candidates, call_id }            │
      │                                               │                                              │
      │  6. Receive answer, set remote SDP            │                                              │
      │  enchant_call_set_remote_sdp(call, answer_sdp)│                                              │
      │                                               │                                              │
      │  7. ICE candidate exchange (trickle ICE)      │                                              │
      │  WS POST /v1/calls/ice ──────────────────────►│                                              │
      │                                               │  WS POST /v1/calls/ice ◄─────────────────────│
      │                                               │                                              │
      │  8. ICE connected → media flowing             │                                              │
      │                                               │                                              │
      │  9. Triple Ratchet media encryption           │                                              │
      │  - Derive initial media keys from call secret │                                              │
      │  - Ratchet keys every N packets / T seconds   │                                              │
      │  - Forward secrecy: compromised key ≠ past media│                                             │
      │                                               │                                              │
      │  10. Active call (audio/video)                │                                              │
      │  - Quality monitoring via RTCP                │                                              │
      │  - Key rotation every 60 seconds              │                                              │
      │  - Mute/unmute, camera switch                 │                                              │
      │                                               │                                              │
      │  11. Hangup                                   │                                              │
      │  enchant_call_hangup(call) ──────────────────►│                                              │
      │                                               │  WS push hangup ─────────────────────────────►│
      │                                               │                                              │
      │  12. Cleanup                                  │                                              │
      │  enchant_call_destroy(call)                   │  enchant_call_destroy(call)                  │
      │  Zero all key material                        │  Zero all key material                       │
```

---

## Call Flow: Group Encrypted Call (SFU Architecture)

```
Alice (Creator)          Backend (SFU)              Bob                  Carol
      │                       │                       │                     │
      │  1. Create group call │                       │                     │
      │  group = enchant_group_call_create()          │                     │
      │  sdp = enchant_group_call_create_offer(group) │                     │
      │                       │                       │                     │
      │  2. Connect to SFU    │                       │                     │
      │  WS connect ─────────►│                       │                     │
      │  Send upstream ──────►│                       │                     │
      │                       │                       │                     │
      │  3. Invite members    │                       │                     │
      │  POST /v1/calls/group/invite ─────────────────►│                     │
      │  POST /v1/calls/group/invite ──────────────────────────────────────►│
      │                       │                       │                     │
      │  4. Bob joins         │                       │                     │
      │  group = enchant_group_call_create()          │                     │
      │  Connect to SFU ─────►│                       │                     │
      │  Subscribe streams ◄──│                       │                     │
      │                       │                       │                     │
      │  5. Carol joins       │                       │                     │
      │  Connect to SFU ─────►│                       │                     │
      │  Subscribe streams ◄──│                       │                     │
      │                       │                       │                     │
      │  6. Group encryption  │                       │                     │
      │  - SFU generates epoch secret                 │                     │
      │  - Distributes to all members via E2EE        │                     │
      │  - Each member derives media keys from epoch  │                     │
      │  - SFU is cryptographically blind             │                     │
      │                       │                       │                     │
      │  7. Active group call │                       │                     │
      │  - SFU forwards streams (selective)           │                     │
      │  - Active speaker detection                   │                     │
      │  - Key rotation every 30 seconds (faster than 1:1) │                │
      │  - Layer selection (simulcast)                │                     │
      │                       │                       │                     │
      │  8. Member leaves     │                       │                     │
      │  ────────────────────►│                       │                     │
      │  New epoch generated  │                       │                     │
      │  New keys distributed │                       │                     │
      │  (post-join secrecy: departed member can't decrypt)                 │
```

---

## Wire Format

### Call Offer (Protobuf)

```protobuf
message CallOffer {
  string call_id = 1;              // UUID
  string caller_id = 2;            // User ID
  string caller_device_id = 3;     // Device ID
  CallType call_type = 4;          // AUDIO, VIDEO, GROUP_AUDIO, GROUP_VIDEO
  bytes sdp_offer = 5;             // SDP offer (serialized)
  repeated IceCandidate ice_candidates = 6;  // Initial ICE candidates
  bytes call_secret_encrypted = 7; // E2EE call secret (encrypted with recipient's key)
  uint64 timestamp = 8;            // Unix ms
  bool supports_triple_ratchet = 9; // Capability flag
  GroupCallInfo group_info = 10;   // Set if group call
}

message CallAnswer {
  string call_id = 1;
  string callee_id = 2;
  string callee_device_id = 3;
  bytes sdp_answer = 4;
  repeated IceCandidate ice_candidates = 5;
  uint64 timestamp = 6;
  bool triple_ratchet_accepted = 7;
}

message IceCandidate {
  string foundation = 1;
  int32 component = 2;
  string protocol = 3;
  int32 priority = 4;
  string ip = 5;
  int32 port = 6;
  CandidateType type = 7;  // HOST, SRFLX, PRFLX, RELAY
  string related_address = 8;
  int32 related_port = 9;
}

message CallControl {
  string call_id = 1;
  ControlType type = 2;  // HANGUP, HOLD, RESUME, MUTE_AUDIO, MUTE_VIDEO, SCREEN_SHARE_START, SCREEN_SHARE_STOP
  uint64 timestamp = 3;
}

message GroupCallInfo {
  string group_id = 1;
  uint64 epoch = 2;
  repeated string member_ids = 3;
  bytes epoch_secret_encrypted = 4;  // Encrypted with each member's key
  int32 max_participants = 5;
}
```

### Media Encryption Header (RTP Extension)

```
For Triple Ratchet encrypted media:
  [ratchet_version(1) | packet_index(4) | chain_key_hash(32) | auth_tag(16)]

- packet_index: monotonic counter for ratchet advancement
- chain_key_hash: H(chain_key) for verification (not the key itself)
- auth_tag: HMAC of RTP payload for integrity
```

---

## API Surface (C API)

The library exposes a **pure C API** for FFI. All platform bindings call through this API.

### Call Lifecycle
```c
// Create a new call instance
int64_t enchant_call_create(const char* user_id, const char* device_id, CallType type);

// Create SDP offer
int enchant_call_create_offer(int64_t call_handle, char** sdp_out, size_t* sdp_len);

// Set remote SDP answer
int enchant_call_set_remote_sdp(int64_t call_handle, const char* sdp, size_t sdp_len);

// Add ICE candidate
int enchant_call_add_ice_candidate(int64_t call_handle, const char* candidate, size_t len);

// Get local ICE candidates
int enchant_call_get_ice_candidates(int64_t call_handle, char*** candidates_out, int* count);

// Hangup
int enchant_call_hangup(int64_t call_handle);

// Destroy call and zero all keys
int enchant_call_destroy(int64_t call_handle);
```

### Media Control
```c
// Audio
int enchant_call_mute_audio(int64_t call_handle);
int enchant_call_unmute_audio(int64_t call_handle);
int enchant_call_set_audio_device(int64_t call_handle, AudioDevice device);

// Video
int enchant_call_mute_video(int64_t call_handle);
int enchant_call_unmute_video(int64_t call_handle);
int enchant_call_switch_camera(int64_t call_handle, CameraDirection direction);
int enchant_call_set_video_quality(int64_t call_handle, VideoQuality quality);

// Screen share
int enchant_call_start_screen_share(int64_t call_handle);
int enchant_call_stop_screen_share(int64_t call_handle);
```

### Group Calls
```c
// Create group call
int64_t enchant_group_call_create(const char* group_id, const char* user_id);

// Connect to SFU
int enchant_group_call_connect(int64_t group_handle, const char* sfu_url);

// Subscribe to member streams
int enchant_group_call_subscribe(int64_t group_handle, const char* member_id);

// Unsubscribe
int enchant_group_call_unsubscribe(int64_t group_handle, const char* member_id);

// Get active speaker
int enchant_group_call_get_active_speaker(int64_t group_handle, char** speaker_id_out);

// Destroy group call
int enchant_group_call_destroy(int64_t group_handle);
```

### Encryption
```c
// Enable Triple Ratchet media encryption
int enchant_call_enable_encryption(int64_t call_handle, const uint8_t* call_secret, size_t secret_len);

// Get ratchet state for verification
int enchant_call_get_ratchet_state(int64_t call_handle, RatchetState* state_out);

// Force key rotation
int enchant_call_rotate_keys(int64_t call_handle);
```

### Quality Monitoring
```c
// Get call quality metrics
int enchant_call_get_quality_metrics(int64_t call_handle, QualityMetrics* metrics_out);

// Set quality callback
void enchant_call_set_quality_callback(int64_t call_handle, QualityCallback cb, void* user_data);
```

### Audio Routing
```c
// Get available audio devices
int enchant_get_audio_devices(AudioDevice** devices_out, int* count);

// Set audio route
int enchant_call_set_audio_route(int64_t call_handle, AudioRoute route);

// Get current audio route
AudioRoute enchant_call_get_audio_route(int64_t call_handle);
```

---

## Build Matrix

| Platform | Compiler | Output | Architecture |
|----------|----------|--------|--------------|
| Android | Clang (NDK) | `.so` | arm64-v8a, armeabi-v7a, x86_64 |
| iOS | Clang (Xcode) | `.a` | arm64 |
| macOS | Clang (Xcode) | `.dylib` | arm64, x86_64 |
| Windows | MSVC | `.dll` | x86_64 |
| Linux | GCC/Clang | `.so` | x86_64, arm64 |
| Backend | GCC/Clang | `.a` | x86_64 (server) |

---

## Dependencies

| Dependency | License | Purpose |
|------------|---------|---------|
| **Google WebRTC** | BSD-3-Clause | Media transport, PeerConnection, ICE/STUN/TURN |
| **libenchantcrypto** | ISC (ours) | Triple Ratchet key derivation, identity verification |
| **CMake 3.20+** | BSD | Build system |
| **Android NDK r26+** | Apache 2.0 | Android builds |
| **GoogleTest** | BSD-3 | Testing framework |

---

## Security Model

### Threat Model
- **Server/SFU is untrusted** — SFU forwards encrypted packets, never sees decrypted media
- **Network is hostile** — all media encrypted with Triple Ratchet (not just DTLS/SRTP)
- **Call recording by participant** — detectable via audio watermarking (future)
- **Man-in-the-middle during signaling** — prevented by E2EE call secret exchange
- **Key compromise during call** — Triple Ratchet provides post-compromise security

### Security Guarantees
1. **Confidentiality** — Only call participants can decrypt media
2. **Integrity** — Tampered media packets fail authentication
3. **Authentication** — Caller identity verified via libenchantcrypto
4. **Forward Secrecy** — Compromised call key doesn't expose past media
5. **Post-Compromise Security** — Ratchet recovers from key compromise within N packets
6. **Post-Join Secrecy** — New group members can't decrypt past media
7. **Post-Leave Secrecy** — Departed members can't decrypt future media (epoch rotation)

### Triple Ratchet for Calls

The call Triple Ratchet combines three ratchets:

1. **Symmetric Ratchet** — Advances every packet, derives per-packet encryption keys
2. **DH Ratchet** — Exchanges new DH keys every N seconds, provides forward secrecy
3. **PQ KEM Ratchet** — Post-quantum key encapsulation every M seconds (future)

```
K_packet = HKDF(symmetric_chain_key || dh_shared_secret || pq_shared_secret,
                info="EnchantCallMedia", 32)
```

Key rotation schedule:
- 1:1 calls: every 60 seconds or 10,000 packets (whichever comes first)
- Group calls: every 30 seconds or 5,000 packets (faster due to more attack surface)

---

## What Makes This Better Than RingRTC

| Feature | RingRTC | libenchantcall |
|---------|---------|----------------|
| **License** | AGPLv3 (copyleft) | BSD-3-Clause + MIT (permissive) |
| **Media Encryption** | DTLS/SRTP only (transport-level) | Triple Ratchet (E2EE + forward secrecy) |
| **Group Calls** | Yes (SFU) | Yes (SFU) + epoch-based post-leave secrecy |
| **Post-Quantum** | No | Designed for PQ KEM ratchet |
| **Cross-Platform** | Android, iOS, Desktop | Android, iOS, Windows, Linux, macOS, Backend |
| **AI Agent Calls** | No | Yes — agents can join calls as participants |
| **Call Links** | Yes | Yes (via backend) |
| **Quality Survey** | Yes | Yes |
| **Call Recording Detection** | No | Planned (audio watermarking) |
| **Hash-Chained Call Log** | No | Yes — each call event is hash-chained for audit |

---

## AI Agent Integration

AI agents can participate in calls:

1. **Agent Identity** — Agent has a call identity derived from its E2EE identity key
2. **Agent in 1:1 Calls** — Agent joins as a regular participant, media encrypted with Triple Ratchet
3. **Agent in Group Calls** — Agent receives epoch secret like any other member
4. **Agent Capabilities** — Agent can mute/unmute, switch camera (if has video), send DTMF tones
5. **Agent Transcription** — Agent can transcribe call audio locally (on-device) and send summaries as messages

---

## Current Status

**Phase 1: Foundation** — Not started
- [ ] Repository structure created
- [ ] CMake build system
- [ ] WebRTC integration (Android AAR first)
- [ ] Basic peer connection wrapper
- [ ] SDP offer/answer creation
- [ ] ICE candidate handling
- [ ] Clean C API skeleton

See `docs/BUILD_PHASES/` for detailed phase plans.
