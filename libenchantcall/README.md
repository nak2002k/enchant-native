# libenchantcall

Cross-platform C++17 encrypted calling library with Triple Ratchet media encryption.

## Overview

libenchantcall provides the calling backbone for the Enchant messaging platform. It powers 1:1 encrypted calls, group encrypted calls, and AI agent call participation across Android, iOS, macOS, Windows, Linux, and backend servers.

**Built on vanilla Google WebRTC (BSD-3-Clause)** — no AGPLv3 copyleft, no forced open-source.

## Key Features

- **1:1 Encrypted Calls** — Voice and video with Triple Ratchet media encryption (forward secrecy)
- **Group Encrypted Calls** — Multi-party SFU-based calls with epoch-based post-leave secrecy
- **Cross-Platform** — Android (JNI), iOS/macOS (Swift), Windows (C#), Linux (native), Backend (C++)
- **AI Agent Calls** — Agents participate as first-class call participants
- **Hash-Chained Call Log** — Tamper-evident audit trail for every call event

## Why Not RingRTC?

| Feature | RingRTC | libenchantcall |
|---------|---------|----------------|
| License | AGPLv3 (copyleft) | BSD-3-Clause + MIT (permissive) |
| Media Encryption | DTLS/SRTP only | Triple Ratchet (E2EE + forward secrecy) |
| Group Calls | SFU | SFU + epoch-based post-leave secrecy |
| Post-Quantum | No | Designed for PQ KEM ratchet |
| Cross-Platform | Android, iOS, Desktop | Android, iOS, Windows, Linux, macOS, Backend |
| AI Agent Calls | No | Yes |

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                  libenchantcall (C++17)                     │
│                                                             │
│  ┌───────────────────────────────────────────────────────┐ │
│  │              Clean C API (FFI Layer)                   │ │
│  └──────────────────────┬────────────────────────────────┘ │
│                         │                                   │
│  ┌──────────────────────▼────────────────────────────────┐ │
│  │              Call Protocol Layer                       │ │
│  │  Signaling  │  SDP Negotiation  │  ICE Handling       │ │
│  └──────────────────────┬────────────────────────────────┘ │
│                         │                                   │
│  ┌──────────────────────▼────────────────────────────────┐ │
│  │              Media Encryption Layer                    │ │
│  │  Triple Ratchet for media  │  Group key derivation    │ │
│  └──────────────────────┬────────────────────────────────┘ │
│                         │                                   │
│  ┌──────────────────────▼────────────────────────────────┐ │
│  │              Media Layer (WebRTC wrappers)             │ │
│  │  PeerConnection  │  AudioTrack  │  VideoTrack         │ │
│  └──────────────────────┬────────────────────────────────┘ │
│                         │                                   │
│  ┌──────────────────────▼────────────────────────────────┐ │
│  │          Google WebRTC (BSD-3-Clause License)          │ │
│  └───────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────┘
```

## Quick Start

### Prerequisites

- CMake 3.20+
- C++17 compiler (Clang 14+, GCC 11+, MSVC 2022+)
- Google WebRTC (prebuilt or built from source)
- Android NDK r26+ (for Android builds)
- Xcode 15+ (for iOS/macOS builds)

### Build (macOS)

```bash
cmake -B build
cmake --build build
ctest --output-on-failure
```

### Build (Android)

```bash
cmake -B build-android \
  -DCMAKE_TOOLCHAIN_FILE=$NDK/build/cmake/android.toolchain.cmake \
  -DANDROID_ABI=arm64-v8a
cmake --build build-android
```

### Build (iOS)

```bash
cmake -B build-ios \
  -DCMAKE_TOOLCHAIN_FILE=ios.toolchain.cmake \
  -DPLATFORM=OS64
cmake --build build-ios
```

## Documentation

- [Architecture](docs/ARCHITECTURE.md) — System design, layer architecture, call flows, wire format
- [Build Phases](docs/BUILD_PHASES/) — Detailed implementation plans
- [Production Readiness](docs/PRODUCTION_READINESS.md) — Dev-vs-prod boundaries, security checklist
- [Agent Instructions](AGENTS.md) — Rules for AI agents working on this codebase

## Security

libenchantcall provides:

1. **Confidentiality** — Only call participants can decrypt media
2. **Integrity** — Tampered media packets fail authentication
3. **Authentication** — Caller identity verified via libenchantcrypto
4. **Forward Secrecy** — Compromised call key doesn't expose past media
5. **Post-Compromise Security** — Ratchet recovers from key compromise
6. **Post-Join Secrecy** — New group members can't decrypt past media
7. **Post-Leave Secrecy** — Departed members can't decrypt future media

## License

- **libenchantcall wrapper code:** MIT
- **Google WebRTC:** BSD-3-Clause
- **libenchantcrypto integration:** ISC

Your application code remains proprietary. No copyleft obligations.

## Contact

- **Maintainer:** nak2002k
- **Parent project:** https://github.com/nak2002k/enchant-native
- **Crypto library:** https://github.com/nak2002k/libenchantcrypto
