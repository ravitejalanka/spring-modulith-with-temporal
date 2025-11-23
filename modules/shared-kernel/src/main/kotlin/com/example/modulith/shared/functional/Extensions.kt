package com.example.modulith.shared.functional

import arrow.core.Either
import arrow.core.NonEmptyList
import arrow.core.raise.either
import arrow.core.raise.ensure
import com.example.modulith.shared.domain.DomainError
import org.springframework.dao.DataIntegrityViolationException

/**
 * Extension functions for working with Either in a more idiomatic way
 */

/**
 * Convert nullable to Either with error
 */
fun <T> T?.toEither(error: () -> DomainError): Either<DomainError, T> =
    this?.let { Either.Right(it) } ?: Either.Left(error())

/**
 * Convert List to NonEmptyList with error
 */
fun <T> List<T>.toNonEmptyListOrError(error: () -> DomainError): Either<DomainError, NonEmptyList<T>> =
    NonEmptyList.fromList(this).toEither(error)

/**
 * Ensure collection is not empty
 */
suspend fun <T> ensureNotEmpty(
    collection: Collection<T>,
    error: () -> DomainError
): Either<DomainError, NonEmptyList<T>> = either {
    ensure(collection.isNotEmpty(), error)
    NonEmptyList.fromListUnsafe(collection.toList())
}

/**
 * Apply a validation function to each element in a list
 */
suspend fun <A, B> List<A>.traverseEither(
    f: suspend (A) -> Either<DomainError, B>
): Either<DomainError, List<B>> = either {
    map { f(it).bind() }
}

/**
 * Fold a list of Either into a single Either
 */
fun <A> List<Either<DomainError, A>>.sequence(): Either<DomainError, List<A>> =
    fold(Either.Right(emptyList()) as Either<DomainError, List<A>>) { acc, either ->
        acc.flatMap { list ->
            either.map { list + it }
        }
    }

/**
 * Common error handlers
 */
object ErrorHandlers {
    /**
     * Handle database-related exceptions
     */
    fun handleDatabaseError(e: Throwable): DomainError = when (e) {
        is DataIntegrityViolationException -> DomainError.ConcurrencyError(
            "Concurrency conflict: ${e.message}"
        )
        else -> DomainError.ValidationError("Database error: ${e.message ?: "Unknown error"}")
    }

    /**
     * Handle serialization/deserialization errors
     */
    fun handleSerializationError(e: Throwable): DomainError =
        DomainError.ValidationError("Serialization error: ${e.message ?: "Unknown error"}")

    /**
     * Handle messaging errors
     */
    fun handleMessagingError(e: Throwable): DomainError =
        DomainError.ValidationError("Messaging error: ${e.message ?: "Unknown error"}")

    /**
     * Generic error handler
     */
    fun handleGenericError(e: Throwable): DomainError =
        DomainError.ValidationError(e.message ?: "Unknown error")
}

/**
 * Execute a block and catch exceptions as Either using fold pattern
 */
inline fun <T> catching(crossinline block: () -> T): Either<DomainError, T> =
    Either.catch { block() }
        .fold(
            { e -> Either.Left(ErrorHandlers.handleGenericError(e)) },
            { result -> Either.Right(result) }
        )

/**
 * Execute a database operation and handle exceptions
 */
inline fun <T> catchingDatabase(crossinline block: () -> T): Either<DomainError, T> =
    Either.catch { block() }
        .fold(
            { e -> Either.Left(ErrorHandlers.handleDatabaseError(e)) },
            { result -> Either.Right(result) }
        )

/**
 * Execute a serialization operation and handle exceptions
 */
inline fun <T> catchingSerialization(crossinline block: () -> T): Either<DomainError, T> =
    Either.catch { block() }
        .fold(
            { e -> Either.Left(ErrorHandlers.handleSerializationError(e)) },
            { result -> Either.Right(result) }
        )

/**
 * Execute a messaging operation and handle exceptions
 */
inline fun <T> catchingMessaging(crossinline block: () -> T): Either<DomainError, T> =
    Either.catch { block() }
        .fold(
            { e -> Either.Left(ErrorHandlers.handleMessagingError(e)) },
            { result -> Either.Right(result) }
        )

/**
 * Tap into the success case without changing the value
 */
inline fun <A, B> Either<A, B>.tapRight(f: (B) -> Unit): Either<A, B> {
    if (this is Either.Right) f(value)
    return this
}

/**
 * Tap into the error case without changing the value
 */
inline fun <A, B> Either<A, B>.tapLeft(f: (A) -> Unit): Either<A, B> {
    if (this is Either.Left) f(value)
    return this
}
