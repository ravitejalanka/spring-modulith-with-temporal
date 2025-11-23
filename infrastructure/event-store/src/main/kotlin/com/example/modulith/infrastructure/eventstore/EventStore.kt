package com.example.modulith.infrastructure.eventstore

import arrow.core.Either
import com.example.modulith.shared.domain.DomainError
import com.example.modulith.shared.domain.DomainEvent
import java.util.UUID

/**
 * Event store interface for event sourcing
 */
interface EventStore {
    /**
     * Save domain events for an aggregate
     */
    suspend fun save(
        aggregateId: UUID,
        aggregateType: String,
        events: List<DomainEvent>,
        expectedVersion: Long
    ): Either<DomainError, Unit>

    /**
     * Load all events for an aggregate
     */
    suspend fun load(
        aggregateId: UUID,
        aggregateType: String
    ): Either<DomainError, List<DomainEvent>>

    /**
     * Load events for an aggregate from a specific version
     */
    suspend fun loadFrom(
        aggregateId: UUID,
        aggregateType: String,
        fromVersion: Long
    ): Either<DomainError, List<DomainEvent>>

    /**
     * Get current version of an aggregate
     */
    suspend fun getVersion(
        aggregateId: UUID,
        aggregateType: String
    ): Either<DomainError, Long>
}

/**
 * Event store entry for persistence
 */
data class EventStoreEntry(
    val eventId: UUID,
    val aggregateId: UUID,
    val aggregateType: String,
    val eventType: String,
    val eventData: String,
    val metadata: String? = null,
    val version: Long,
    val occurredAt: java.time.Instant
)
