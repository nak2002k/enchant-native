package org.enchant.core.base

/**
 * A discriminated union representing either a success value or a failure value.
 *
 * Unlike Kotlin's built-in `Result`, this type allows distinct success and
 * failure types, making it suitable for typed error handling.
 *
 * Usage:
 * ```
 * val result: Result<User, NetworkError> = fetchUser()
 * result.either(
 *     onSuccess = { display(it) },
 *     onFailure = { showError(it) }
 * )
 * ```
 */
sealed class Result<out S, out F> {

    data class Success<out S>(val success: S) : Result<S, Nothing>()

    data class Failure<out F>(val failure: F) : Result<Nothing, F>()

    companion object {
        fun <S> success(value: S): Result<S, Nothing> = Success(value)
        fun <F> failure(value: F): Result<Nothing, F> = Failure(value)
    }

    /**
     * Transforms the success value, leaving failures unchanged.
     */
    fun <T> map(onSuccess: (S) -> T): Result<T, F> = when (this) {
        is Success -> success(onSuccess(success))
        is Failure -> this
    }

    /**
     * Handles both success and failure cases, returning a common type.
     */
    fun <T> either(onSuccess: (S) -> T, onFailure: (F) -> T): T = when (this) {
        is Success -> onSuccess(success)
        is Failure -> onFailure(failure)
    }

    /** Returns true if this is a success. */
    fun isSuccess(): Boolean = this is Success

    /** Returns true if this is a failure. */
    fun isFailure(): Boolean = this is Failure

    /** Returns the success value, or null if this is a failure. */
    fun getOrNull(): S? = (this as? Success)?.success

    /** Returns the failure value, or null if this is a success. */
    fun failureOrNull(): F? = (this as? Failure)?.failure

    /** Returns the success value, or [default] if this is a failure. */
    fun getOrElse(default: @UnsafeVariance S): S = when (this) {
        is Success -> success
        is Failure -> default
    }
}

/**
 * Chains two Result-producing operations. If this is a success, applies
 * [onSuccess] and returns its result. If this is a failure, returns it unchanged.
 */
fun <T, S, F> Result<S, F>.flatMap(onSuccess: (S) -> Result<T, F>): Result<T, F> = when (this) {
    is Result.Success -> onSuccess(success)
    is Result.Failure -> this
}

/**
 * A [Result] where the failure type is always [Throwable].
 */
typealias Try<S> = Result<S, Throwable>
