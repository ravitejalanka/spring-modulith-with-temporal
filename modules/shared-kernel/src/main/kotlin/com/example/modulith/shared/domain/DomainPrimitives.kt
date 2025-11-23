package com.example.modulith.shared.domain

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.util.UUID

/**
 * Base interface for all domain events
 */
interface DomainEvent {
    val eventId: UUID
    val occurredAt: Instant
    val aggregateId: UUID
}

/**
 * Base interface for aggregates
 */
interface AggregateRoot<ID> {
    val id: ID
    val version: Long
    val events: List<DomainEvent>
}

/**
 * Marker interface for value objects
 */
interface ValueObject

/**
 * Common error types
 */
sealed interface DomainError {
    val message: String

    data class ValidationError(override val message: String) : DomainError
    data class NotFoundError(val entityType: String, val id: String) : DomainError {
        override val message: String = "$entityType with id $id not found"
    }
    data class ConcurrencyError(override val message: String) : DomainError
    data class BusinessRuleViolation(override val message: String) : DomainError
}

/**
 * Money value object with currency-safe operations
 */
@JvmInline
value class Money(val amount: BigDecimal) : ValueObject {
    init {
        require(amount >= BigDecimal.ZERO) { "Money amount cannot be negative" }
        require(amount.scale() <= 2) { "Money amount can have at most 2 decimal places" }
    }

    operator fun plus(other: Money): Money = Money(
        (amount + other.amount).setScale(2, RoundingMode.HALF_UP)
    )

    operator fun minus(other: Money): Either<DomainError, Money> = either {
        val result = amount - other.amount
        ensure(result >= BigDecimal.ZERO) {
            DomainError.ValidationError("Cannot subtract: result would be negative")
        }
        Money(result.setScale(2, RoundingMode.HALF_UP))
    }

    operator fun times(multiplier: Int): Money = Money(
        (amount * BigDecimal(multiplier)).setScale(2, RoundingMode.HALF_UP)
    )

    operator fun compareTo(other: Money): Int = amount.compareTo(other.amount)

    companion object {
        val ZERO = Money(BigDecimal.ZERO.setScale(2))

        fun of(amount: String): Either<DomainError, Money> = either {
            val decimal = amount.toBigDecimalOrNull()
                ?: raise(DomainError.ValidationError("Invalid money format: $amount"))
            ensure(decimal >= BigDecimal.ZERO) {
                DomainError.ValidationError("Money amount cannot be negative")
            }
            Money(decimal.setScale(2, RoundingMode.HALF_UP))
        }

        fun of(amount: BigDecimal): Either<DomainError, Money> = either {
            ensure(amount >= BigDecimal.ZERO) {
                DomainError.ValidationError("Money amount cannot be negative")
            }
            Money(amount.setScale(2, RoundingMode.HALF_UP))
        }

        fun of(amount: Double): Either<DomainError, Money> =
            of(BigDecimal.valueOf(amount))
    }
}

/**
 * Quantity value object
 */
@JvmInline
value class Quantity(val value: Int) : ValueObject {
    init {
        require(value > 0) { "Quantity must be positive" }
    }

    operator fun plus(other: Quantity): Quantity = Quantity(value + other.value)
    operator fun times(multiplier: Int): Quantity = Quantity(value * multiplier)

    companion object {
        fun of(value: Int): Either<DomainError, Quantity> = either {
            ensure(value > 0) { DomainError.ValidationError("Quantity must be positive") }
            Quantity(value)
        }
    }
}

/**
 * Email value object
 */
@JvmInline
value class Email(val value: String) : ValueObject {
    init {
        require(value.matches(EMAIL_REGEX)) { "Invalid email format" }
    }

    companion object {
        private val EMAIL_REGEX = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}\$".toRegex()

        fun of(value: String): Either<DomainError, Email> = either {
            ensure(value.matches(EMAIL_REGEX)) {
                DomainError.ValidationError("Invalid email format: $value")
            }
            Email(value)
        }
    }
}

/**
 * Base class for strongly-typed IDs
 */
abstract class EntityId(open val value: UUID) : ValueObject {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is EntityId) return false
        return value == other.value
    }

    override fun hashCode(): Int = value.hashCode()
    override fun toString(): String = value.toString()
}
