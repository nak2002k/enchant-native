# Task: Build libenchantcall — Phase 1 (Foundation)

## Context

You are building **libenchantcall**, a cross-platform C++17 encrypted calling library for the Enchant messaging platform. This is Phase 1: Foundation.

**Reference architecture:** Read `libenchantcrypto/docs/ARCHITECTURE.md` first to understand the layered pattern we use (primitives → protocol → FFI). libenchantcall follows the exact same pattern.

**Full architecture:** `libenchantcall/docs/ARCHITECTURE.md`
**Build phases:** `libenchantcall/docs/BUILD_PHASES/01_foundation.md`
**Agent rules:** `libenchantcall/AGENTS.md`

## What to Build (Phase 1)

1. **Repository structure** — Create the full directory tree as specified in `01_foundation.md`
2. **CMake build system** — CMakeLists.txt that builds libenchantcall with WebRTC
3. **Public C API header** — `include/enchantcall/enchantcall.h` with the complete API surface (provided in `01_foundation.md`)
4. **WebRTC PeerConnection wrapper** — `src/media/peer_connection.cpp` wrapping `webrtc::PeerConnectionInterface`
5. **SDP offer/answer** — `src/media/sdp.cpp` for creating and setting SDP
6. **ICE candidate handling** — `src/media/ice_candidate.cpp` for trickle ICE
7. **Call state machine** — `src/signaling/call_state_machine.cpp` with full transition table
8. **C API implementation** — `src/enchantcall.cpp` implementing all functions from the header
9. **Android JNI binding** — `bindings/android/enchant_call_jni.cpp`
10. **Kotlin wrapper** — `bindings/android/EnchantCall.kt`
11. **Tests** — GoogleTest tests for all components

## Critical Rules

1. **NO STUBS, NO PLACEHOLDERS, NO TODOS** — Every function must be complete and working
2. **Update BUILD_PHASES** on every change — mark `[x]` completed items
3. **Commit and push** after every logical change
4. **Test security** — every test must also test attack vectors
5. **No raw new/delete** — use RAII, smart pointers
6. **All secret material zeroed** — before any free
7. **Structured error codes** — never throw exceptions across FFI boundary

## Where to Start

1. Read `libenchantcall/AGENTS.md`
2. Read `libenchantcall/docs/ARCHITECTURE.md`
3. Read `libenchantcall/docs/BUILD_PHASES/01_foundation.md`
4. Start with repository structure and CMakeLists.txt
5. Then implement C API header
6. Then implement each layer bottom-up (media → signaling → C API → bindings → tests)

## Deliverables for Phase 1

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
- [ ] AGENTS.md written

## License

- WebRTC: BSD-3-Clause (permissive, no copyleft)
- libenchantcall wrapper: MIT (permissive)
- Your code stays proprietary
