package com.example.modulith.shared.functional

import arrow.core.Either
import arrow.core.NonEmptyList
import arrow.core.raise.either
import arrow.core.raise.ensure
import com.example.modulith.shared.domain.DomainError

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
 * Execute a block and catch exceptions as Either
 */
inline fun <T> catching(block: () -> T): Either<DomainError, T> = either {
    try {
        block()
    } catch (e: Exception) {
        raise(DomainError.ValidationError(e.message ?: "Unknown error"))
    }
}

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
