# Agent Instructions — libenchantcall

## Project Context

libenchantcall is a cross-platform C++17 encrypted calling library built on vanilla Google WebRTC (BSD-3-Clause). It provides the calling backbone for the Enchant messaging platform — powering 1:1 encrypted calls, group encrypted calls, and AI agent call participation across Android, iOS, macOS, Windows, Linux, and the backend server.

## Critical Rule: No Stubs, No Placeholders, No TODOs in Source

**MANDATORY:** Every function, method, and code block must contain **complete, working logic**.

**Forbidden patterns:**
```cpp
// ❌ NEVER do this
int create_offer(/* ... */) {
    // TODO: implement WebRTC offer creation
    return 0;
}

void connect_to_sfu() {
    // stub
}

int add_ice_candidate(/* ... */) {
    return ENCHANT_CALL_SUCCESS;  // fake success
}
```

**Required:**
- Every function must contain **actual implementation logic**
- Every error path must be **explicitly handled** (not silently passed)
- No `// TODO` comments in source code — if you can't implement it, **don't create the function**
- No placeholder return values — return real computed results or real error codes
- No empty function bodies (except destructors that rely on RAII)

**If you cannot complete a function:**
1. Do NOT create the file or function
2. Report to the orchestrator exactly what is blocked
3. Wait for clarification before proceeding

**Violation = immediate PR rejection.** A stub function is worse than no function — it creates a false sense of completion.

## Critical Rule: Update Progress on Every Change

**MANDATORY:** Whenever you implement a change that compiles and works:

1. **Update the relevant BUILD_PHASES document**:
   - Mark completed items with `[x]`
   - Add notes about what changed
   - Update any known issues

2. **Commit the change immediately**:
   ```bash
   git add -A
   git commit -m "[component] what changed and why"
   git push origin main
   ```

3. **No exceptions.** Even trivial fixes (typos, comment updates) get committed and pushed.

## Session Bootstrap

When starting a new session:
1. Read `AGENTS.md` first — it tells you the rules
2. Read `docs/ARCHITECTURE.md` for system overview
3. Read the relevant BUILD_PHASES document (e.g., `docs/BUILD_PHASES/01_foundation.md`)
4. Only then look at source code

## Testing — Security-First Mindset

**Rule:** Every test must ALSO test security, not just functionality.
When writing a test, consider EVERY attack vector:
- **Man-in-the-middle** — what happens if signaling is intercepted?
- **Replay attacks** — what happens if the same SDP/ICE is replayed?
- **Key compromise** — what happens if call keys are leaked mid-call?
- **SFU tampering** — what happens if SFU modifies media packets?
- **Rogue participant** — what happens if an unauthorized member joins a group call?
- **Buffer overflow** — what happens with malformed SDP/ICE data?
- **Memory leaks** — is key material zeroed after call ends?
- **Side-channel attacks** — is comparison constant-time?

Each test file should include a `Security` section or prefix security tests with `[SEC]`.

## Dev-vs-Prod Boundaries

**Rule:** Document every dev-only shortcut that would be unsafe in production.
When you take a deliberate shortcut for development speed, add an entry in `docs/PRODUCTION_READINESS.md`:
```
- [ ] Replace `X` with `Y` before production — reason, risk, priority
```

Never leave dev-only shortcuts undocumented. Either:
1. Add a `TODO` comment in the source code at the exact location
2. Add an entry in `PRODUCTION_READINESS.md` with context

## Coding Standards

- **C++17**, no `using namespace std` in headers
- **Google WebRTC** for media transport (BSD-3-Clause)
- **CMake 3.20+** for build system
- **RAII** for all resources, especially `SecureBuffer` for sensitive data
- **Clean C API** for FFI — all platform bindings call through this
- **No raw new/delete** — use `SecureBuffer`, `std::unique_ptr`, `std::vector`
- **All secret material zeroed** — `sodium_memzero()` before any free
- **Constant-time comparison** — `sodium_memcmp` for MACs/signatures
- **Structured error codes** — never throw exceptions across FFI boundary

## Git Workflow

- Commit working changes immediately
- Push to `origin/main` after every commit
- Never force push
- Keep commit messages descriptive: `[component] what changed and why`
- Branch naming: `feat/<description>`, `fix/<description>`, `refactor/<description>`

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
clang-format --dry-run src/**/*.cpp
```

## Contact
- Maintainer: nak2002k
- Repo: https://github.com/nak2002k/libenchantcall
- Parent project: https://github.com/nak2002k/enchant-native
