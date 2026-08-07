package org.enchant.auth.screens

/**
 * App-lock PIN brute-force protection policy.
 *
 * After [MAX_ATTEMPTS] consecutive wrong PINs the lock is engaged for an
 * exponentially increasing period, so an attacker cannot simply brute-force
 * the 6-digit PIN. All logic is pure (no Android deps) so it is unit-testable.
 */
object AppLockPolicy {
    const val MAX_ATTEMPTS = 5

    /** Base lockout after the first lockout. */
    const val BASE_LOCKOUT_MS = 30_000L

    /** Never wait longer than this. */
    const val MAX_LOCKOUT_MS = 15 * 60_000L

    /**
     * Lockout duration given how many lockouts have already been triggered.
     * First lockout 30s, then 1min, 2min, 4min … capped at 15 minutes.
     */
    fun lockoutDurationMs(completedLockouts: Int): Long {
        if (completedLockouts < 0) return BASE_LOCKOUT_MS
        val factor = 1L shl completedLockouts.coerceAtMost(15)
        return (BASE_LOCKOUT_MS * factor).coerceAtMost(MAX_LOCKOUT_MS)
    }

    /** True when the current time is before the lockout deadline. */
    fun isLockedOut(lockoutUntilMs: Long, nowMs: Long): Boolean =
        nowMs < lockoutUntilMs

    /** Seconds remaining until the lockout lifts (0 when not locked). */
    fun remainingSeconds(lockoutUntilMs: Long, nowMs: Long): Long {
        if (!isLockedOut(lockoutUntilMs, nowMs)) return 0L
        return ((lockoutUntilMs - nowMs + 999) / 1000).coerceAtLeast(1L)
    }

    /**
     * Evaluates a failed PIN attempt. Returns the updated state:
     * when [failedAttempts] reaches [MAX_ATTEMPTS] a new lockout starts and
     * the attempt counter resets.
     */
    data class AfterFailure(
        val failedAttempts: Int,
        val lockoutUntilMs: Long,
        val completedLockouts: Int
    )

    fun onFailedAttempt(
        failedAttempts: Int,
        nowMs: Long,
        completedLockouts: Int
    ): AfterFailure {
        val next = failedAttempts + 1
        if (next < MAX_ATTEMPTS) {
            return AfterFailure(next, 0L, completedLockouts)
        }
        return AfterFailure(
            failedAttempts = 0,
            lockoutUntilMs = nowMs + lockoutDurationMs(completedLockouts),
            completedLockouts = completedLockouts + 1
        )
    }
}
