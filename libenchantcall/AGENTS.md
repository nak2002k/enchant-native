# Agent Instructions — libenchantcall

## Project Context

libenchantcall is a cross-platform C++17 encrypted calling library built on vanilla Google WebRTC (BSD-3-Clause). It provides the calling backbone for the Enchant messaging platform — powering 1:1 encrypted calls, group encrypted calls, and AI agent call participation across Android, iOS, macOS, Windows, Linux, and the backend server.

**This library follows the exact same architecture pattern as libenchantcrypto.**
Read `docs/ARCHITECTURE.md` for the full system design.

---

## CRITICAL RULE: No Stubs, No Placeholders, No TODOs, No Dead Code

**MANDATORY:** Every function, method, class, and code block must contain **complete, working, tested logic**.

### Forbidden Patterns (VIOLATION = IMMEDIATE REJECTION)

```cpp
// ❌ NEVER — empty body
int create_offer() { }

// ❌ NEVER — TODO comment
int create_offer() {
    // TODO: implement WebRTC offer creation
    return 0;
}

// ❌ NEVER — fake success return
int add_ice_candidate() {
    return ENCHANT_CALL_SUCCESS;  // stub
}

// ❌ NEVER — placeholder body
void connect_to_sfu() {
    // stub
}

// ❌ NEVER — unimplemented virtual method
class PeerConnectionObserver {
    virtual void OnIceCandidate() override { }  // empty
};

// ❌ NEVER — dead code (unreachable, unused, commented-out blocks)
#if 0
void old_function() { ... }
#endif

// ❌ NEVER — unused includes, imports, variables
#include <unused_header.h>
int unused_variable = 0;
```

### Required Patterns

```cpp
// ✅ CORRECT — complete implementation with error handling
int create_offer(int64_t handle, char** sdp_out, size_t* sdp_len) {
    if (!sdp_out || !sdp_len) return ENCHANT_CALL_ERROR_NULL_POINTER;

    auto* call = get_call(handle);
    if (!call) return ENCHANT_CALL_ERROR_INVALID_HANDLE;

    auto future = call->peer_connection()->CreateOffer();
    auto result = future.get();
    if (!result) return ENCHANT_CALL_ERROR_SDP_FAILED;

    *sdp_out = strdup(result->sdp().c_str());
    *sdp_len = result->sdp().size();
    return ENCHANT_CALL_SUCCESS;
}

// ✅ CORRECT — every error path explicitly handled
int set_remote_sdp(int64_t handle, const char* sdp, size_t len) {
    if (!sdp || len == 0) return ENCHANT_CALL_ERROR_NULL_POINTER;

    auto* call = get_call(handle);
    if (!call) return ENCHANT_CALL_ERROR_INVALID_HANDLE;

    auto desc = webrtc::CreateSessionDescription("offer", std::string(sdp, len));
    if (!desc) return ENCHANT_CALL_ERROR_SDP_FAILED;

    auto result = call->peer_connection()->SetRemoteDescription(std::move(desc)).get();
    if (!result.ok()) return ENCHANT_CALL_ERROR_WEBRTC_FAILED;

    return ENCHANT_CALL_SUCCESS;
}
```

### If You Cannot Complete a Function

1. **Do NOT create the file or function**
2. **Report to the orchestrator** exactly what is blocked and why
3. **Wait for clarification** before proceeding
4. A missing function is better than a stub — it's honest

### Dead Code Prevention

- **No unused includes** — every `#include` must be used
- **No unused variables** — every variable must be read
- **No commented-out code blocks** — delete, don't comment out
- **No `#if 0` blocks** — delete, don't hide
- **No unreachable code** — every path must be reachable
- **No empty destructors** (unless RAII handles everything)
- **No unused parameters** — use `(void)param;` only if the interface requires it but the implementation doesn't need it (document why)

### Enforcement

- **clang-tidy** must pass with zero warnings
- **clang-format** must pass (run `clang-format --dry-run --Werror`)
- **Compiler warnings as errors** (`-Wall -Wextra -Werror -Wpedantic`)
- **No warnings suppressed** — fix the warning, don't suppress it

---

## CRITICAL RULE: Update Progress on Every Change

**MANDATORY:** Whenever you implement a change that compiles and works:

1. **Update the relevant BUILD_PHASES document**:
   - Mark completed items with `[x]`
   - Add notes about what changed
   - Update any known issues or blockers

2. **Commit the change immediately**:
   ```bash
   git add -A
   git commit -m "[component] what changed and why"
   git push origin main
   ```

3. **No exceptions.** Even trivial fixes (typos, comment updates, formatting) get committed and pushed.

---

## Session Bootstrap

When starting a new session:

1. Read `AGENTS.md` (this file) — it tells you the rules
2. Read `docs/ARCHITECTURE.md` — system overview and layer design
3. Read the relevant BUILD_PHASES document (e.g., `docs/BUILD_PHASES/01_foundation.md`)
4. Read `docs/PRODUCTION_READINESS.md` — dev-vs-prod boundaries
5. **Only then** look at source code

---

## Testing — Security-First Mindset

**Rule:** Every test must ALSO test security, not just functionality.

### Attack Vectors to Test

| Attack Vector | What to Test |
|--------------|--------------|
| **Man-in-the-middle** | Intercepted signaling, modified SDP, fake ICE candidates |
| **Replay attacks** | Same SDP/ICE replayed, same call_id reused |
| **Key compromise** | Keys leaked mid-call, past media still secure |
| **SFU tampering** | SFU modifies media packets, drops packets, reorders packets |
| **Rogue participant** | Unauthorized member joins group call, departed member tries to rejoin |
| **Buffer overflow** | Malformed SDP (too long, null bytes, unicode), malformed ICE JSON |
| **Memory leaks** | Key material not zeroed after call ends, handles not freed |
| **Side-channel attacks** | Timing differences in key comparison, error messages leak info |
| **Denial of service** | Rapid call create/destroy, huge SDP payloads, ICE flooding |
| **State machine abuse** | Invalid state transitions, concurrent operations on same handle |

### Test Structure

Every test file must include:
1. **Happy path tests** — expected inputs produce expected outputs
2. **Error/edge case tests** — null inputs, empty strings, malformed data
3. **Boundary condition tests** — max lengths, timeouts, limits
4. **State transition tests** — all valid and invalid transitions
5. **Security tests** — prefixed with `[SEC]` or in a `Security` section

```cpp
// Example security test
TEST(CallRatchet, Sec_ReplayAttackDetection) {
    // Given: a ratchet with known state
    CallRatchet ratchet;
    ratchet.init(secret, sizeof(secret));

    // When: encrypt packet at index 5
    auto ct1 = ratchet.encrypt(plaintext, 5);

    // And: replay the same packet at index 5
    auto result = ratchet.decrypt(ct1, 5);

    // Then: replay must be rejected
    EXPECT_FALSE(result.ok());
    EXPECT_EQ(result.error(), RatchetError::REPLAY_DETECTED);
}
```

---

## Dev-vs-Prod Boundaries

**Rule:** Document every dev-only shortcut that would be unsafe in production.

When you take a deliberate shortcut for development speed, add an entry in `docs/PRODUCTION_READINESS.md`:

```
- [ ] Replace `X` with `Y` before production — reason, risk, priority
```

**Examples of dev shortcuts that MUST be documented:**
- Using prebuilt WebRTC instead of building from source
- Skipping certificate pinning in debug builds
- Using fixed STUN/TURN servers instead of fetching from backend
- Disabling key rotation for testing
- Using mock SFU instead of real SFU

**Never leave dev-only shortcuts undocumented.** Either:
1. Add a comment in the source code at the exact location explaining why
2. Add an entry in `PRODUCTION_READINESS.md` with full context

---

## Coding Standards

### Language
- **C++17** — no C++20 features, no C++14 fallbacks
- **No `using namespace std;`** in headers (`.h` files)
- **No `using namespace std;`** in source files (`.cpp` files) — use explicit `std::` prefix

### Memory
- **No raw `new`/`delete`** — use `std::unique_ptr`, `std::shared_ptr`, `std::vector`
- **RAII for all resources** — files, locks, WebRTC objects
- **All secret material zeroed** — `sodium_memzero()` before any free
- **SecureBuffer for keys** — never store keys in plain `std::vector`

### Threading
- **Mutex-protected shared state** — no data races
- **No blocking on main thread** — all WebRTC operations async
- **Thread-safe handle registry** — atomic counter for handles, mutex-protected map

### Error Handling
- **Structured error codes** — return `enchant_call_error_t`, never throw across FFI
- **No exceptions across FFI boundary** — catch all exceptions, convert to error codes
- **Every error path logged** — `LOG_ERROR("function: failed because %s", reason)`

### FFI
- **Clean C API** — all platform bindings call through `enchantcall.h`
- **No C++ types in C API** — no `std::string`, no `std::vector`, no classes
- **Caller manages memory** — caller `free()`'s output strings, caller provides buffers
- **Null pointer checks** — every pointer parameter checked before dereference

### Style
- **clang-format** — run `clang-format -i` on every file before committing
- **clang-tidy** — run `clang-tidy` with our config before committing
- **Include order** — system headers first, then third-party, then project headers
- **Header guards** — `#ifndef ENCHANTCALL_FOO_H` / `#define` / `#endif`

---

## Git Workflow

- **Commit working changes immediately**
- **Push to `origin/main`** after every commit
- **Never force push** — use `git pull --rebase` if needed
- **Keep commit messages descriptive**: `[component] what changed and why`
- **Branch naming**: `feat/<description>`, `fix/<description>`, `refactor/<description>`
- **No empty commits** — every commit must change something meaningful

---

## Build Rules

### Never Build All Platforms Simultaneously

When building for multiple platforms, **build one at a time**. Cross-compilation is resource-intensive and parallel builds will fail.

```bash
# Build for Android first
cmake -B build-android -DCMAKE_TOOLCHAIN_FILE=$NDK/build/cmake/android.toolchain.cmake
cmake --build build-android

# Then iOS
cmake -B build-ios -DCMAKE_TOOLCHAIN_FILE=ios.toolchain.cmake
cmake --build build-ios

# Then macOS
cmake -B build-macos
cmake --build build-macos
```

### Pre-Commit Checklist

```bash
# Run before every commit
cmake -B build -DCMAKE_CXX_FLAGS="-fsanitize=address,undefined"
cmake --build build
ctest --output-on-failure
clang-tidy src/**/*.cpp -- -I include
clang-format --dry-run --Werror src/**/*.cpp include/**/*.h
```

### CI Requirements (Future)

- Build passes on all platforms
- All tests pass
- No clang-tidy warnings
- No clang-format violations
- No memory leaks (AddressSanitizer clean)
- No undefined behavior (UBSan clean)

---

## Comparison with Signal's RingRTC

| Aspect | RingRTC | libenchantcall |
|--------|---------|----------------|
| **Language** | Rust + C++ WebRTC fork | C++17 + vanilla WebRTC |
| **License** | AGPLv3 | MIT + BSD-3-Clause |
| **Build system** | Cargo + Make | CMake |
| **WebRTC** | Custom fork (`signalapp/webrtc`) | Vanilla Google WebRTC |
| **Media encryption** | DTLS/SRTP (transport-level) | Triple Ratchet (E2EE) |
| **Group calls** | SFU | SFU + epoch-based post-leave secrecy |
| **FFI** | JNI + Swift + Node.js | JNI + Swift + C# + direct C++ |

**Key takeaway:** We use vanilla WebRTC (BSD-3-Clause) with our own C++ encryption layer. RingRTC uses a custom WebRTC fork and Rust. Our approach is simpler to build and legally safer.

---

## Contact

- **Maintainer:** nak2002k
- **Repo:** https://github.com/nak2002k/libenchantcall
- **Parent project:** https://github.com/nak2002k/enchant-native
- **Crypto library:** https://github.com/nak2002k/libenchantcrypto
