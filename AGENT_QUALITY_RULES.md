# Agent Quality Rules — Enchant Project

> **Read this before writing a single line of code.**
> These rules are non-negotiable. Violations result in immediate rejection or revert.

---

## Project Goal

Enchant is a **private, end-to-end encrypted messaging app** built with Kotlin. It is inspired by Signal's architecture but is a fully custom implementation. The codebase must be **production-quality** — fit for real users, on real devices, in real threat environments. There is **zero compromise on privacy, security, or code quality**. Every line of code, every test, every PR either meets this bar or does not land.

---

## Agent Code Quality Rules

### Rule 1 — Test Everything (No Exceptions)

Every function, every class, every provider, every widget **must** have corresponding tests covering:
- **Happy path** — expected input produces expected output
- **Error/edge paths** — null inputs, empty collections, network failures, malformed data, timeouts
- **Boundary conditions** — max lengths, pagination limits, buffer overflows, integer wrap-around

**No exceptions.** A one-line getter? Test it. A trivial utility? Test it. You do not get to skip.

### Rule 2 — Zero Code Duplication (DRY)

If the same logic appears twice, **extract it immediately**. Duplication is technical debt in its purest form. Common extractions: helper functions, extension methods, mixins, base classes, repository abstractions. If you catch yourself copying and pasting, stop. Extract.

### Rule 3 — OOP Best Practices (SOLID + Clean Architecture)

- **S**ingle Responsibility — one class, one reason to change
- **O**pen/Closed — extend without modifying
- **L**iskov Substitution — subtypes must be substitutable for their bases
- **I**nterface Segregation — don't force clients to depend on things they don't use
- **D**ependency Inversion — depend on abstractions, not concretions

Layers must be clean: **UI → State/Provider → Service/Repository → Data/Network**. No widget talks to `Dio` directly. No provider leaks platform details.

### Rule 4 — No Cheating on Tests

Tests must **actually assert real behavior**. The following are **strictly forbidden**:
- Empty test bodies (`test('foo', () { });`)
- `// TODO` comments inside tests
- Commented-out assertions
- Tests that never call `expect`, `verify`, or any assertion method
- Tests that catch exceptions silently without asserting the exception
- `expect(true, isTrue)` or any tautological assertion

A test that does not verify is worse than no test — it creates false confidence. **Any PR found to contain a non-asserting test is rejected on the spot.**

### Rule 5 — Coverage Thresholds

| Layer | Minimum Line Coverage |
|-------|----------------------|
| Core utilities (crypto, encoding, helpers) | **95%+** |
| Services / Providers / Repositories | **90%+** |
| Widgets | Every state covered (loading, error, empty, data) |

**Coverage drops below threshold = automatic revert.** Run `flutter test --coverage` before every commit. Do not rely on coverage as a substitute for thoughtful testing — hitting 95% with worthless tests violates Rule 4.

### Rule 6 — Security Invariants Must Be Tested

Every security-critical component must have tests that verify:
- **No plaintext leaks** — message content never appears in logs, error messages, or stack traces
- **No crashes on malformed data** — corrupted ciphertext, truncated envelopes, replayed nonces must not crash the app
- **No key exposure** — crypto keys are zeroed after use (`zeroBytes()`), never logged, never persisted outside secure storage
- **No downgrade attacks** — protocol version negotiation cannot be tricked to weaker ciphers
- **No replay attacks** — message decryption rejects duplicate nonces

If a change touches security code, security tests are **mandatory**, not optional.

### Rule 7 — State Transitions Must Be Tested

Every stateful component (provider, bloc, cubit, notifier) must have tests for all state transitions:

```
loading → success
loading → error
loading → empty
success → loading (refresh)
error → loading (retry)
offline → online
unauthenticated → authenticated
authenticated → unauthenticated (token expired)
```

One missing transition = one failing review.

### Rule 8 — Thread Safety

All **shared mutable state** must be protected:
- Use `synchronized` or explicit locks for shared data in isolates
- Use `StreamController` with proper broadcast semantics
- Never mutate provider state outside the main thread without synchronization
- Database writes must use transactions; concurrent access must be serialized

Data races are **not acceptable** in any layer. If you touch shared state, you must reason about concurrency.

### Rule 9 — No Lazy Programming

The following are **strictly forbidden**:
- Empty `catch` blocks (`catch (e) { }` — always log, rethrow, or handle explicitly)
- `null!` assertions without a written justification comment explaining why it is safe
- `// TODO` without a corresponding ticket number (`// TODO(EN-1234): ...`)
- `as` casts without a null check or guard
- Silent swallowing of `Future` return values (`unawaited` warnings must be resolved, not suppressed)

If you are tempted to write lazy code, stop and design properly.

### Rule 10 — PR Gate: Lint + Typecheck + All Tests Must Pass

Before any PR is created or merged, **all three must pass**:
```
flutter analyze           # zero warnings, zero errors
flutter test --coverage   # all tests green, coverage met
dart run build_runner build  # if codegen is involved
```

A PR that fails any of these is not submitted. Period.

### Rule 11 — Documentation for Every Public API

Every public-facing declaration must have a **doc comment** that explains:
- **What** it does
- **Why** it exists (the problem it solves)
- **When** it should be used (if non-obvious)

```
/// Decrypts an incoming [Envelope] using the session's current chain key.
///
/// Returns the decrypted plaintext bytes. Throws [MacMismatchException] if
/// the MAC does not verify, which indicates tampering or a wrong key.
///
/// The caller must zero the returned bytes after use.
```

Private/internal functions do not require doc comments, but should be self-documenting through naming.

### Rule 12 — No Silent Failures

Every failure path must be **explicitly handled**:
- Network calls: catch and map to a typed failure/error state
- Database queries: handle `null` returns, constraint violations, lock errors
- Crypto operations: catch and handle corrupt data, wrong keys, MAC failures
- File I/O: handle permission denied, disk full, file not found

There is no "this will never happen" in production code. If it can fail, handle it. If you genuinely believe it cannot fail, add a comment explaining why and an assertion.

---

## Consequences

| Violation | Consequence |
|-----------|------------|
| Test found to be a "passing cheat" (empty body, no assertion, tautology) | **PR rejected immediately.** No discussion. |
| Coverage drops below threshold | **Automatic revert.** Fix coverage, resubmit. |
| Security rule violation | **Immediate fix required.** Code is blocked until fixed. No other work proceeds. |
| Three violations by the same agent | **Agent quarantined.** All future PRs from that agent require manual review by a senior maintainer. |

These are not guidelines. They are **rules**. Break them and your work does not ship.

---

## Testing Requirements — Detailed

### Unit Tests (arrange, act, assert)

```
// ARRANGE
final repository = MockMessageRepository();
when(repository.fetchMessages(any)).thenAnswer((_) async => [testMessage]);
final sut = MessageService(repository: repository);

// ACT
final result = await sut.loadMessages(chatId: 'chat_1');

// ASSERT
expect(result, hasLength(1));
expect(result.first.id, equals(testMessage.id));
verify(repository.fetchMessages('chat_1')).called(1);
```

### Widget Tests (pump, find, verify)

```
// PUMP
await tester.pumpWidget(MaterialApp(home: ChatScreen(chatId: '1')));
await tester.pumpAndSettle();

// FIND
expect(find.text('Hello'), findsOneWidget);

// VERIFY
expect(find.byType(LoadingIndicator), findsNothing);
```

### Integration Tests

Critical paths **require** integration tests:
- **Auth flow**: phone number input → verification code → profile setup → home screen
- **Message send/receive**: compose → encrypt → send → receive → decrypt → display
- **Contact discovery**: register → sync contacts → discover registered users
- **Group creation**: create group → add members → send group message → all members receive

### Testing Async Code

- Use `async`/`await` properly — never call `.then()` when `await` is cleaner
- Use `Completer` when you need to manually control async completion in tests
- Use `fake_async` / `FakeAsync` for timer-dependent code
- Never use `Future.delayed` in tests as a synchronization mechanism

### Testing Crypto

- **Known Answer Tests (KATs)**: For every cryptographic primitive, include test vectors from the spec (RFC 8032, RFC 7748, etc.)
- Verify that encrypt(plaintext, key) produces exactly the expected ciphertext
- Verify that decrypt(ciphertext, key) produces exactly the original plaintext
- Verify that wrong keys, truncated input, and corrupted ciphertext all fail with the correct exception

### Testing Database

- Use **in-memory databases** (`Database.inMemory()`) per test — never share database state between tests
- Each test starts with a clean schema
- Use `setUp` to initialize, `tearDown` to close
- Test migrations explicitly: create v1 schema, apply migration, verify v2 schema

## Testing Tools & Frameworks

All tests MUST use these tools consistently across the entire codebase:

| Tool | Library | Purpose | Usage Pattern |
|---|---|---|---|
| **JUnit 5** | `org.junit.jupiter:junit-jupiter-api` | Test framework | `@Test`, `@ParametrizedTest`, `@BeforeEach`, `@AfterEach`, `@BeforeAll`, `@DisplayName("descriptive name")` |
| **MockK** | `io.mockk:mockk` | Kotlin mocking library | `mockk<T>()`, `every { obj.method() } returns value`, `coEvery { obj.suspendMethod() } coReturns value`, `verify { obj.methodWasCalled() }`, `slot<T>()` for capturing args, `capture(slot)` |
| **Turbine** | `app.cash.turbine:turbine` | Kotlin Flow testing | `viewModel.someFlow.test { val item = awaitItem(); assertEquals(expected, item); cancel() }` |
| **Coroutines Test** | `kotlinx-coroutines-test` | Async coroutine testing | `runTest { }`, `StandardTestDispatcher()`, `advanceUntilIdle()`, `TestCoroutineScheduler`, `Dispatchers.setMain(testDispatcher)` |
| **Compose UI Test** | `androidx.compose.ui:ui-test-junit4` | Compose UI testing | `createComposeRule()`, `onNodeWithText("Hello")`, `performClick()`, `assertIsDisplayed()`, `assertDoesNotExist()` |
| **Robolectric** | `org.robolectric:robolectric` | Android framework unit testing | `@RunWith(RobolectricTestRunner::class)`, `RuntimeEnvironment.application`, `buildActivity<MainActivity>().setup()` |
| **MockWebServer** | `com.squareup.okhttp3:mockwebserver` | HTTP API mocking | `MockWebServer()`, `server.enqueue(MockResponse().setBody("{}"))`, `server.url("/")`, `server.takeRequest()` |

### Test File Naming Convention

```
src/test/java/org/enchant/core/network/ApiClientTest.kt                    // Pure unit test (no Android framework)
src/androidTest/java/org/enchant/feature/auth/WelcomeScreenTest.kt          // Instrumentation test (emulator)
src/test/java/org/enchant/feature/chat/ConversationViewModelTest.kt         // ViewModel test (Robolectric or mocked)
```

### Example: ApiClientTest with MockWebServer

```kotlin
@DisplayName("ApiClient")
class ApiClientTest {
    private val server = MockWebServer()
    private lateinit var client: ApiClient

    @BeforeEach
    fun setUp() {
        server.start(8080)
        client = ApiClient(baseUrl = server.url("/").toString())
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    @Test
    @DisplayName("GET returns parsed JSON on success")
    fun `get request returns parsed JSON`() = runTest {
        server.enqueue(MockResponse().setBody("""{"key": "value"}"""))
        val result = client.get<JsonObject>("/test")
        assertTrue(result.isSuccess)
        assertEquals("value", result.getOrNull()?.get("key")?.asString)
    }

    @Test
    @DisplayName("GET auto-refreshes JWT on 401")
    fun `get auto-refreshes JWT on 401`() = runTest {
        server.enqueue(MockResponse().setResponseCode(401))
        server.enqueue(MockResponse().setBody("""{"access_token": "new_jwt"}"""))
        server.enqueue(MockResponse().setBody("""{"key": "value"}"""))
        val result = client.get<JsonObject>("/test")
        assertTrue(result.isSuccess)
        assertEquals(3, server.requestCount)  // original + refresh + retry
    }
}
```

### Example: ConversationViewModelTest with Turbine

```kotlin
@DisplayName("ConversationViewModel")
class ConversationViewModelTest {
    private val mockRepo = mockk<ConversationRepository>()
    private val mockSendPipeline = mockk<MessageSendPipeline>()
    private lateinit var viewModel: ConversationViewModel

    @BeforeEach
    fun setUp() {
        coEvery { mockRepo.getMessages(any(), any(), any()) } returns flowOf(emptyList())
        viewModel = ConversationViewModel(mockRepo, mockSendPipeline)
    }

    @Test
    @DisplayName("sendTextMessage emits message in state")
    fun `sendTextMessage success`() = runTest {
        coEvery { mockSendPipeline.sendMessage(any(), any(), any()) } returns SendResult.Success("env123")
        viewModel.sendTextMessage("Hello")
        viewModel.messages.test {
            val messages = awaitItem()
            assertTrue(messages.any { it.content == "Hello" })
            cancel()
        }
    }
}
```

### Prohibited Testing Patterns

These are **NOT ALLOWED** under any circumstances:

| ❌ Forbidden | Why |
|---|---|
| `test("foo", () { })` | Empty test body, no assertions |
| `expect(true, isTrue)` | Tautology — tests nothing |
| `runBlocking { }` in tests | Blocks the test thread; use `runTest { }` instead |
| `Thread.sleep()` for async | Flaky; use `advanceUntilIdle()` or `TestCoroutineScheduler` |
| `TODO("write test")` | Not a test — rejected in review |
| Mocking what you're testing | Don't mock the system under test itself |
| `coEvery { ... } returns null` without handling | Silent null passes; always verify the null path is tested |

---

## Workflow

1. **Read all rule files** — Always start by reading `AGENTS.md`, `AGENT_QUALITY_RULES.md`, the relevant security/scalability docs, and **`BACKEND_API_REFERENCE.md`** for the API endpoints you'll be calling **before writing any code**. For production-critical components, also read `PRODUCTION_REFERENCE.md` which documents the 10 production rules proven by Signal Android at scale.
2. **Understand the phase** — Read `docs/CURRENT_PROGRESS.md` and the relevant phase document before writing code
3. **Run tests before every commit** — `flutter test --coverage` must pass. No exceptions.
4. **Update progress tracking** — After completing any work item, update `docs/CURRENT_PROGRESS.md` with status and notes
5. **Branch naming** follows this pattern:
   - `feat/<short-description>` — new features
   - `fix/<short-description>` — bug fixes
   - `chore/<short-description>` — tooling, CI, refactoring, documentation
6. **Commit early, commit often** — Each logical change gets its own commit. Do not batch unrelated changes.
7. **Create a PR** — Target `main`, fill in the summary, link related issues. Ensure all gate checks pass.
8. **Review and merge** — PRs require review. After merge, verify the deploy preview (if applicable).

---

## Multi-Agent Orchestration

### Orchestrator Agent

When multiple agents work in parallel, one agent acts as the **Orchestrator**. The orchestrator:
- Tracks what every worker agent is doing (maintains a shared task board)
- Resolves branch conflicts when they arise
- Merges completed branches into `main`
- Enforces all quality rules on incoming PRs
- Is the single point of decision for merge order and timing

### Worker → Orchestrator Communication Protocol

Every worker agent **must** send structured status updates to the orchestrator:

| Event | Message Format |
|-------|---------------|
| **Start** | `[WORKER] Starting task <X> on branch feat/<X>` |
| **Progress** | `[WORKER] <N>% — <file Y> built, tests passing` |
| **Blocked** | `[WORKER] BLOCKED — <file Z> conflicts with feat/<W>` |
| **Complete** | `[WORKER] DONE — feat/<X> ready, <N> tests passing, <files> modified` |

The orchestrator **must acknowledge** each message within a reasonable window. Silence means the orchestrator assumes the worker is still progressing.

### Branch Isolation

**Every agent works on its own branch.** The rules:
1. Each agent creates its own branch: `feat/<short-description>`, `fix/<short-description>`, or `chore/<short-description>`
2. Work exclusively on that branch — never commit directly to `main`
3. Push regularly — GitHub is the source of truth, not local state
4. When done, create a PR targeting `main`
5. Orchestrator reviews and merges

### File Conflict Avoidance

Two agents **must never** edit the same file simultaneously. Rules:
1. Before starting, declare the files you will modify to the orchestrator
2. If a file is already being worked on by another agent:
   - **Preferred:** Work on a different file or wait
   - **If unavoidable:** Create a copy (`file_agentname.dart`), work on the copy
   - Notify the orchestrator about the copy immediately
3. When done, the orchestrator merges the copy and resolves conflicts
4. The orchestrator is responsible for the final merged version

### Atomic Tasks

Each agent works on **one logical unit** — no exceptions:
- One feature, one screen, one service, one database table
- No "build everything" agents — they produce garbage code
- If a task is too big, break it into sub-tasks and spawn sub-agents
- Each sub-agent reports to the orchestrator with the same protocol

### Dependency Declaration

Before starting any work, an agent must declare to the orchestrator:
- **What** files it will modify or create
- **What** other agents' work it depends on (blocking dependencies)
- **What** other agents' work it conflicts with (if known)
- **Estimated** duration (small/medium/large)

The orchestrator uses this to plan merge order and detect conflicts early.

---

## Dedicated Test Agent

After every feature implementation is complete, a **separate test agent** must be spawned to write tests. The implementation agent **does not** write their own tests — the test agent does.

### Test Agent Rules

1. The test agent reads the implementation agent's code **in full** before writing a single test
2. Tests must cover:
   - **All happy paths** — every valid input, every success state
   - **All error paths** — every exception, every failure state, every null/empty/malformed input
   - **All boundary conditions** — max lengths, empty collections, timeouts, pagination edges
   - **All state transitions** — loading → success → error → loading, offline → online, etc.
   - **All security invariants** — no plaintext leaks, no crash on corrupt data, no key exposure
3. If a test **fails**, the test agent **must debug and fix** the issue:
   - The test agent may modify the implementation code to fix bugs found during testing
   - **No faking** — never change a test assertion to make it pass without understanding why the implementation is wrong
   - Never delete a failing test — either fix the implementation or fix the test if it was wrong
4. If the test agent discovers a design flaw, they report it to the orchestrator with a clear description of the problem and a proposed fix
5. The test agent runs the full test suite before declaring completion — not just their new tests

### What "No Cheating" Means in Testing

| ❌ Forbidden | ✅ Required |
|---|---|
| Empty test body `test('foo', () { })` | Real assertions that verify behavior |
| Hardcoded mock data that never varies | Fixtures/generators that produce varied inputs |
| Testing only the happy path | Testing every error and edge case |
| Catching exceptions without asserting | Asserting the exception type, message, and state |
| `expect(true, isTrue)` tautologies | Real assertions on actual outputs |
| Deleting a failing test | Debugging the root cause and fixing it |
| Using `// TODO` as an excuse to skip | Writing the test properly |

---

## Self-Review Checklist

Before marking any PR as ready, the agent must verify **every** item:

```
[ ] No hardcoded test data — use fixtures or generators
[ ] No // TODO, // FIXME, print(), console.log() left in code
[ ] All new public APIs have doc comments
[ ] No duplicate code — DRY check performed
[ ] Coverage targets met (95% core, 90% services, every widget state)
[ ] Lint passes with zero warnings
[ ] No secrets, keys, or tokens committed
[ ] All error paths handled — no silent failures
[ ] Security invariants tested if applicable
[ ] State transitions tested if applicable
```

If any box is unchecked, the PR is **not ready**. Fix it first.

---

## No Partial Work

If an agent cannot complete its task (timeout, complexity, blocker), it must:
1. **Roll back** its branch to the last clean state
2. **Report** to the orchestrator exactly what was completed and what was left undone
3. **Delete** any partially created files (temp files, half-finished implementations, broken tests)
4. **Never leave broken code** on a branch — a branch is either clean and complete or rolled back

The orchestrator may then reassign the task to another agent with more context.

---

## Agent Resource Cleanup

After completing work and merging the PR, the agent must:
1. Delete any temp files, test artifacts, generated stubs created during development
2. Reset any modified configs back to their defaults (unless the change was intentional)
3. Leave the workspace in a clean state
4. Remove local branches that have been merged

---

*Last Updated: 2026-05-11*
