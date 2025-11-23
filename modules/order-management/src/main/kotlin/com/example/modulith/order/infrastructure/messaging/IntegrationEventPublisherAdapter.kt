package com.example.modulith.order.infrastructure.messaging

import arrow.core.Either
import com.example.modulith.infrastructure.messaging.EventPublisher
import com.example.modulith.order.application.port.IntegrationEventPublisher
import com.example.modulith.shared.domain.DomainError
import com.example.modulith.shared.event.IntegrationEvent
import org.springframework.stereotype.Component

/**
 * Adapter for integration event publishing
 * Delegates to the infrastructure EventPublisher (Spring Events or Kafka)
 */
@Component
class IntegrationEventPublisherAdapter(
    private val eventPublisher: EventPublisher
) : IntegrationEventPublisher {

    override suspend fun publish(event: IntegrationEvent): Either<DomainError, Unit> {
        return eventPublisher.publish(event)
    }

    override suspend fun publishAll(events: List<IntegrationEvent>): Either<DomainError, Unit> {
        return eventPublisher.publishAll(events)
    }
}
