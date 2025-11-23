package com.example.modulith.shared.event

import java.time.Instant
import java.util.UUID

/**
 * Base interface for integration events (cross-module communication)
 */
interface IntegrationEvent {
    val eventId: UUID
    val occurredAt: Instant
    val eventType: String
}

/**
 * Event metadata for tracking and debugging
 */
data class EventMetadata(
    val correlationId: UUID,
    val causationId: UUID,
    val userId: UUID? = null,
    val timestamp: Instant = Instant.now(),
    val additionalProperties: Map<String, Any> = emptyMap()
)

/**
 * Event envelope for transporting events with metadata
 */
data class EventEnvelope<T : IntegrationEvent>(
    val event: T,
    val metadata: EventMetadata
) {
    val eventId: UUID get() = event.eventId
    val eventType: String get() = event.eventType
    val occurredAt: Instant get() = event.occurredAt
}
