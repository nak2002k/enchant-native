package org.enchant.core.base

sealed class Result<out S, out F> {

    data class Success<out S>(val success: S) : Result<S, Nothing>()

    data class Failure<out F>(val failure: F) : Result<Nothing, F>()

    companion object {
        fun <S> success(value: S): Result<S, Nothing> = Success(value)
        fun <F> failure(value: F): Result<Nothing, F> = Failure(value)
    }

    fun <T> map(onSuccess: (S) -> T): Result<T, F> = when (this) {
        is Success -> success(onSuccess(success))
        is Failure -> this
    }

    fun <T> either(onSuccess: (S) -> T, onFailure: (F) -> T): T = when (this) {
        is Success -> onSuccess(success)
        is Failure -> onFailure(failure)
    }

    fun isSuccess(): Boolean = this is Success
    fun isFailure(): Boolean = this is Failure

    fun getOrNull(): S? = (this as? Success)?.success
    fun failureOrNull(): F? = (this as? Failure)?.failure

    fun getOrElse(default: @UnsafeVariance S): S = when (this) {
        is Success -> success
        is Failure -> default
    }
}

fun <T, S, F> Result<S, F>.flatMap(onSuccess: (S) -> Result<T, F>): Result<T, F> = when (this) {
    is Result.Success -> onSuccess(success)
    is Result.Failure -> this
}

typealias Try<S> = Result<S, Throwable>
