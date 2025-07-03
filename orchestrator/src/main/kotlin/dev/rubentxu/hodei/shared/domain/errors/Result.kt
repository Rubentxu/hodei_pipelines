package dev.rubentxu.hodei.shared.domain.errors

import kotlinx.serialization.Serializable

@Serializable
sealed interface Result<out T, out E> {
    @Serializable
    data class Success<out T>(val value: T) : Result<T, Nothing>
    
    @Serializable
    data class Failure<out E>(val error: E) : Result<Nothing, E>
}

inline fun <T, E> Result<T, E>.fold(
    onSuccess: (T) -> Unit = {},
    onFailure: (E) -> Unit = {}
) {
    when (this) {
        is Result.Success -> onSuccess(value)
        is Result.Failure -> onFailure(error)
    }
}

inline fun <T, E, R> Result<T, E>.map(transform: (T) -> R): Result<R, E> =
    when (this) {
        is Result.Success -> Result.Success(transform(value))
        is Result.Failure -> this
    }

inline fun <T, E, R> Result<T, E>.mapError(transform: (E) -> R): Result<T, R> =
    when (this) {
        is Result.Success -> this
        is Result.Failure -> Result.Failure(transform(error))
    }