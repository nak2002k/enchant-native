# core:jobmanager Audit

## Security Issues

### S-1: Job serialization data (potentially user content) stored without encryption

**File:** `FastJobStorage.kt`, `InMemoryJobStorage.kt`, `JobSpecs.kt`

Job data (`serializedData`, `serializedInputData`) is stored as raw `ByteArray?` in the database and in memory. The `ByteArrayConverter` in `JobSpecEntity.kt` stores byte arrays as hex strings in the database with no encryption. If a job carries user message content (e.g., attachment upload/download jobs, message send retry jobs), that content is persisted in plaintext on disk.

**Risk:** SQL injection or database file theft exposes message content. Signal encrypted storage is a core privacy feature; the job queue cannot be exempt.

**Recommendation:** Encrypt `serializedData` before storing. Consider using Android Keystore-backed encryption for job data at rest.

---

### S-2: Job data logged in plaintext via JobLogger

**File:** `JobLogger.kt`, `JobController.kt`

`JobLogger.jobEvent()` logs job IDs and events via `android.util.Log`. While job IDs alone may not be sensitive, the logging infrastructure is shared with `d()`, `i()`, `w()`, `e()` methods that could receive job state or serialized data as messages.

`JobController` calls `scheduler.schedule(firstJob.parameters.initialDelayMs, constraints)` where `firstJob.parameters` contains job metadata. If any caller passes user-derived content (e.g., message IDs, attachment URLs) into job parameters, it could appear in logs.

**Recommendation:** Audit all callers of `JobLogger` and ensure no user content (message text, user IDs, attachment URLs) is passed. Add a pre-logging sanitization step.

---

### S-3: No authentication/authorization on JobManager initialization

**File:** `JobManager.kt`

`JobManager.initialize(context, config)` is a public static method requiring no authentication. Any code with a Context can initialize the JobManager and register arbitrary job factories and constraint observers.

**Risk:** A malicious library or compromised module could register fake job factories to intercept or fabricate job results, or register observers to exfiltrate constraint state.

**Recommendation:** Add a module-level access control check before initialization. Consider making `Configuration` built only by a trusted `JobManagerBuilder` singleton.

---

### S-4: PendingIntent actions use random UUID in AlarmManagerScheduler

**File:** `AlarmManagerScheduler.kt`

```kotlin
intent.action = "org.enchant.jobmanager.RETRY_${UUID.randomUUID()}"
```

Each `schedule()` call generates a unique action string, creating a new `PendingIntent` every time. This bypasses `FLAG_UPDATE_CURRENT` semantics (there is no current intent to update because the action is always unique), meaning the alarm manager cannot de-duplicate or cancel previous retry alarms for the same job.

**Risk:** Orphaned PendingIntents accumulate. A job that retries multiple times spawns multiple alarm registrations that never get cleaned up.

---

### S-5: JobSchedulerSystemService uses EmptyQueueListener hack to stay alive

**File:** `JobSchedulerScheduler.kt`

```kotlin
JobManager.addOnEmptyQueueListener(object : EmptyQueueListener {
    override fun onQueueEmpty() {
        JobManager.removeOnEmptyQueueListener(this)
        jobFinished(params, false)
    }
})
```

The `JobService.onStartJob()` returns `true` (indicating work is running async) but immediately registers an empty queue listener. This is a known Android anti-pattern: the JobService is considered finished only when `jobFinished()` is called. If the queue never empties (e.g., constant stream of high-priority jobs), `jobFinished()` is never called and the JobService leaks.

**Risk:** JobService leak causing ANR or watchdog kill.

---

### S-6: InMemoryJobStorage JobSpec equality ignores `serializedData`

**File:** `JobSpecs.kt` — `JobSpec.equals()`

`JobSpec.equals()` compares `serializedData` via `contentEquals()`. However, `MinimalJobSpec` (derived from `JobSpec.toMinimal()`) does **not** include `serializedData`. This means two `JobSpec` objects with identical metadata but different serialized payloads would be considered equal when comparing their `MinimalJobSpec` counterparts, but different when comparing full `JobSpec`. This can cause subtle bugs in the `TreeSet` eligibleJobs where deduplication is based on `MinimalJobSpec` equality.

---

## Bugs

### B-1: `getDependencySpecsThatDependOnJob` scans entire dependency map

**File:** `FastJobStorage.kt` line 214

```kotlin
override fun getDependencySpecsThatDependOnJob(jobId: String): List<DependencySpec> {
    return dependenciesByJobId.values.flatten().filter { it.dependsOnJobId == jobId }
}
```

This scans every dependency in memory rather than looking up the reverse index. For a messaging app with thousands of jobs, this is an O(n) lookup on every failure to find dependents. The `dependenciesByJobId` map already exists but is indexed by `jobId` (the dependent), not by the job being depended on.

Contrast with `InMemoryJobStorage` at line 136 which has the same issue.

**Fix:** Add a reverse index `dependentsByJobId: ConcurrentHashMap<String, CopyOnWriteArrayList<DependencySpec>>` and query that instead.

---

### B-2: FastJobStorage race condition on `markJobAsRunning`

**File:** `FastJobStorage.kt` lines 96–105

```kotlin
override fun markJobAsRunning(id: String, currentTime: Long) {
    runBlocking {
        jobDao.markJobAsRunning(id, currentTime)  // DB write
    }
    val spec = fullSpecCache[id] ?: return       // read cache
    val updated = spec.copy(isRunning = true, lastRunAttemptTime = currentTime)
    fullSpecCache[id] = updated                   // cache update - NOT in runBlocking
    minimalJobs[id] = updated.toMinimal()
    eligibleJobs.remove(spec.toMinimal())
}
```

The DB write is async (runBlocking) but the subsequent cache update is NOT protected. Between the DB write and the cache update, another coroutine could call `getNextEligibleJob()` and see the stale cached spec. This is a data race.

**Fix:** Move cache updates inside `runBlocking`, or use a mutex for the entire operation.

---

### B-3: `updateAllJobsToBePending` rebuilds eligibleJobs from fullSpecCache while jobs may be running

**File:** `FastJobStorage.kt` lines 132–149, `InMemoryJobStorage.kt` lines 91–102

`updateAllJobsToBePending()` is called in `JobController.init()` before `startJobRunners()`. This sets all jobs to `isRunning = false`. However, if the app was killed while jobs were running, the state may be inconsistent. More critically, `fullSpecCache` is cleared and rebuilt sequentially without a lock. If job runners are already active (they shouldn't be at init time, but the code pattern doesn't enforce this), concurrent modifications could cause `ConcurrentHashMap` corruption.

---

### B-4: `FastJobStorage.insertJobs` caches entities before DB insert completes

**File:** `FastJobStorage.kt` lines 58–72

```kotlin
override fun insertJobs(fullSpecs: List<FullSpec>) {
    runBlocking {
        val entities = fullSpecs.map { it.toEntity() }
        jobDao.insertJobs(entities)   // DB insert
        for (spec in fullSpecs) {
            val jobSpec = spec.toJobSpec()
            fullSpecCache[spec.id] = jobSpec  // cache update AFTER DB
            // ...
        }
    }
}
```

If the `jobDao.insertJobs()` fails (e.g., constraint violation), the exception propagates and the cache is never updated. However, if some inserts succeed before a failure, partial state exists. More importantly, the cache is only updated inside the `runBlocking`, but the method returns immediately to the caller who may call `getJobSpec()` before all items are cached (they would get null for failed inserts).

**Fix:** Use transactional semantics: either the full batch inserts and caches, or neither.

---

### B-5: Circular dependency detection only runs at init time

**File:** `FastJobStorage.kt` line 29, `InMemoryJobStorage.kt` line 14

`detectAndRemoveCircularDependencies()` is called only in `init()`. If a new job chain is submitted that creates a cycle, it will not be detected. The cycle will cause jobs to wait indefinitely for each other.

**Fix:** Detect cycles at `submitNewJobChain()` time, before inserting.

---

### B-6: `JobRunner.run()` catches Exception but rethrows RuntimeException

**File:** `JobRunner.kt` lines 59–66

```kotlin
} catch (e: Exception) {
    val dependents = controller.onFailure(job)
    job.onFailure()
    dependents.forEach { it.onFailure() }
    if (e is RuntimeException) {
        throw e
    }
}
```

Any checked exception (e.g., `IOException`, `CancellationException`) that is not a `RuntimeException` is silently swallowed. `CancellationException` being swallowed is especially dangerous — it means a coroutine job that is canceled may report as a silent success, potentially leaving jobs in an inconsistent state.

**Fix:** Catch `CancellationException` explicitly and rethrow it. For other checked exceptions, treat them as failures, not silent swallows.

---

## Completeness Gaps

### C-1: No message send/retry job types defined

The module provides `DisappearingMessagesWorker` but no `MessageSendWorker`, `MessageRetryWorker`, or `AttachmentUploadWorker`. A messaging app requires background message sending with retry logic. The current job infrastructure is in place but these critical job types are absent from the codebase.

**Missing jobs likely needed:**
- `MessageSendWorker` — sends encrypted messages, retries with exponential backoff
- `AttachmentDownloadWorker` — downloads attachments with cellular/wifi constraints
- `PrekeyUploadWorker` — uploads prekeys after key changes
- `ContactSyncWorker` — syncs contact list with server

---

### C-2: No WorkManager integration despite using custom job runners

The module uses a custom `JobRunner` based on `Thread` instead of Android's `WorkManager`. While the custom implementation provides more control (priority queues, backoff, dependency chains), it means:
- No built-in battery-aware scheduling
- No automatic job persistence across process death
- No WorkManager's built-in constraint handling

The `AlarmManagerScheduler` and `JobSchedulerScheduler` are used only as wake-up mechanisms, not as primary schedulers. This is a design trade-off, but WorkManager is the reference implementation's primary scheduler and provides guarantees this custom implementation must replicate manually.

---

### C-3: No job deduplication across queue keys

Multiple jobs of the same type can be queued for the same recipient/queue. The module has `maxInstancesForFactory` and `maxInstancesForQueue` in `JobParameters` but these are **never checked** in `JobController.submitNewJobChain()` or `InMemoryJobStorage.insertJobs()`. This means duplicate jobs accumulate.

---

### C-4: No job cleanup for completed/failed jobs beyond deletion

Jobs that succeed are immediately deleted from storage. Jobs that fail are deleted along with their dependents. But there is no periodic cleanup of orphaned jobs, no job history, and no debugging/tracing mechanism for jobs that were inserted but never ran.

---

### C-5: Constraint observers are not unregistered on job manager shutdown

`NetworkConstraintObserver`, `BatteryNotLowConstraintObserver`, `ChargingConstraintObserver`, and `WifiConstraintObserver` all register system callbacks but there is no `shutdown()` or `unregisterAll()` method on `JobManager` to clean these up. This causes receiver leaks if the app process is destroyed but the app is not.

---

## Code Quality Issues

### Q-1: `JobManager` is a singleton `object` — no dependency injection, no testing isolation

**File:** `JobManager.kt`

```kotlin
object JobManager {
    private lateinit var internalController: JobController
    ...
}
```

The singleton pattern makes it impossible to replace `JobManager` with a mock in tests. The `JobController` and `JobStorage` are internal lateinit vars, not constructor-injected. This violates the Dependency Inversion principle (Rule 3 of AGENT_QUALITY_RULES.md) and makes unit testing the JobManager impossible without reflection hacks.

The `Configuration` object is the only seam for testing, but it cannot be used to inject a mock `JobManager` for integration tests.

---

### Q-2: `JobRunner` uses bare `Thread` instead of coroutine-based execution

**File:** `JobRunner.kt`

```kotlin
class JobRunner(...) : Thread(name) {
    override fun run() {
        while (running) {
            val job = controller.pullNextEligibleJob(predicate, idleTimeoutMs)
            ...
            val result = runBlocking { job.run() }
        }
    }
}
```

The runner is a `Thread` that wraps a suspend function with `runBlocking`. This blocks the thread for the duration of `job.run()`. With 4+ core runners and 16 max general runners, this means up to 16 blocked threads. The reference implementation uses `WorkManager` with `CoroutineContext` for efficient thread reuse.

**Recommendation:** Replace `Thread` with `CoroutineScope` using a shared `ExecutorService` or `Dispatchers.IO` with a bounded concurrency pool.

---

### Q-3: `InMemoryJobStorage` and `FastJobStorage` duplicate logic

Both classes implement the same `JobStorage` interface with nearly identical logic for:
- `insertJobs` (creates `JobSpec` from `FullSpec`)
- `getNextEligibleJob` (same TreeSet + filter pattern)
- `updateAllJobsToBePending` (same clear + rebuild pattern)
- `detectAndRemoveCircularDependencies` (identical code)
- `isEligible` and `hasEligibleRunTime` (identical)

This is a DRY violation. A common base class or shared `JobStorageDelegate` should extract the common logic.

---

### Q-4: `JobSchedulerSystemService` lacks `getPendingJob` null handling

**File:** `JobSchedulerScheduler.kt` line 24

```kotlin
if (js.getPendingJob(jobId.toInt()) != null) return
```

If `jobId.toInt()` is negative (which can happen if `constraintNames.hashCode()` produces a negative value on older platforms), `getPendingJob` behavior is undefined. Also, if the job ID collides (two different constraint sets produce the same hash), one job will silently skip scheduling.

---

### Q-5: `ConstraintRegistry` is an uninitialized global singleton

**File:** `constraints/ConstraintRegistry.kt`

```kotlin
object ConstraintRegistry {
    private val factories = mutableMapOf<String, Constraint.Factory<out Constraint>>()
    
    fun initialize(context: Context) { ... }
    fun getAllFactories(): Map<String, Constraint.Factory<out Constraint>> = factories.toMap()
}
```

`factories.clear()` is called inside `initialize()`, meaning if `getAllFactories()` is called before `initialize()`, an empty map is returned. No error is raised. This is a silent failure that would cause all constraint lookups to fail.

---

### Q-6: `JobLogger` catches all RuntimeExceptions in logging

**File:** `JobLogger.kt` lines 7–36

```kotlin
fun d(message: String) {
    try {
        android.util.Log.d(TAG, message)
    } catch (_: RuntimeException) { }
}
```

Catching `RuntimeException` and silently ignoring it means any logging failure (e.g., buffer full) is hidden. For a system that should log job failures for debugging, this is counterproductive. At minimum, a failed log should log to an alternate destination (e.g., stderr).

---

### Q-7: `FastJobStorage.transformJobs` does not update `eligibleJobs` correctly after DB write

**File:** `FastJobStorage.kt` lines 282–319

After `jobDao.insertJobs()`, the code rebuilds `fullSpecCache`, `minimalJobs`, and `eligibleJobs`. However, it only adds jobs to `eligibleJobs` if `isEligible(spec)` is true. If a job's eligibility depends on the DB update completing (e.g., `isRunning` flag was set by a concurrent operation), the in-memory index could be out of sync with the DB state. This is especially problematic because `transformJobs` is used by `JobMigrator` which runs during initialization, when job runners may already be polling.

---

### Q-8: Missing `@Synchronized` or `lock` protection in `InMemoryJobStorage`

`InMemoryJobStorage` uses raw `ConcurrentHashMap` and `TreeSet` but several methods (`insertJobs`, `deleteJob`, `updateJobAfterRetry`) perform multiple operations that should be atomic. For example, in `insertJobs`:

```kotlin
val jobSpec = JobSpec(...)
jobs[spec.id] = jobSpec
if (isEligible(jobSpec)) {
    eligibleJobs.add(jobSpec.toMinimal())
}
```

Between the `jobs` insert and `eligibleJobs` add, another thread could observe partial state. A synchronized wrapper or transactional update pattern is needed.

---

## Recommendations (prioritized)

### P0 — Security Fixes
1. **Encrypt job data at rest** — Add Android Keystore-backed encryption for `serializedData` in `FastJobStorage`
2. **Fix JobManager initialization access control** — Add privileged context check in `initialize()`
3. **Audit JobLogger callers** — Ensure no user content is logged
4. **Fix AlarmManagerScheduler PendingIntent accumulation** — Use stable action names, not random UUIDs per call

### P1 — Critical Bugs
5. **Fix race condition in FastJobStorage.markJobAsRunning** — Move cache updates inside `runBlocking` or add mutex
6. **Fix circular dependency detection** — Run detection at `submitNewJobChain()` time, not just at init
7. **Fix CancellationException swallowing** — Explicitly re-throw `CancellationException` in `JobRunner.run()`
8. **Add job deduplication check** — Enforce `maxInstancesForFactory` and `maxInstancesForQueue` before inserting

### P2 — Completeness
9. **Implement missing job types** — `MessageSendWorker`, `AttachmentDownloadWorker`, `PrekeyUploadWorker`, `ContactSyncWorker`
10. **Add constraint observer cleanup** — Implement `shutdown()` on `JobManager` to unregister all observers
11. **Add reverse index for dependency lookups** — Replace O(n) `getDependencySpecsThatDependOnJob` with O(1) reverse map

### P3 — Code Quality
12. **Replace Thread-based JobRunner with CoroutineScope** — Use `Dispatchers.IO` with bounded concurrency
13. **Extract common JobStorage logic** — Create shared base class or delegate for `InMemoryJobStorage` and `FastJobStorage`
14. **Add DI seam to JobManager** — Allow `JobManager` to be injected/replaced for testing
15. **Fix ConstraintRegistry silent failure** — Throw `IllegalStateException` if accessed before `initialize()`
