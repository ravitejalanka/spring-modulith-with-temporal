package com.example.modulith.infrastructure.messaging

import arrow.core.Either
import com.example.modulith.shared.domain.DomainError
import com.example.modulith.shared.event.EventEnvelope
import com.example.modulith.shared.event.IntegrationEvent

/**
 * Abstraction for publishing integration events
 * Can be implemented with Spring Events (modulith) or Kafka (microservices)
 */
interface EventPublisher {
    /**
     * Publish a single integration event
     */
    suspend fun publish(event: IntegrationEvent): Either<DomainError, Unit>

    /**
     * Publish multiple integration events
     */
    suspend fun publishAll(events: List<IntegrationEvent>): Either<DomainError, Unit>

    /**
     * Publish event with metadata
     */
    suspend fun publishWithMetadata(envelope: EventEnvelope<out IntegrationEvent>): Either<DomainError, Unit>
}

/**
 * Listener interface for consuming integration events
 */
interface EventListener<T : IntegrationEvent> {
    /**
     * Handle the integration event
     */
    suspend fun handle(event: T): Either<DomainError, Unit>

    /**
     * The event type this listener handles
     */
    val eventType: Class<T>
}
