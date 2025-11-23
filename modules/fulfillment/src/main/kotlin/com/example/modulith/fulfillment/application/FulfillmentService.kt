package com.example.modulith.fulfillment.application

import arrow.core.Either
import arrow.core.raise.either
import com.example.modulith.infrastructure.messaging.EventPublisher
import com.example.modulith.order.domain.event.OrderCompletedIntegrationEvent
import com.example.modulith.order.domain.event.OrderPaidIntegrationEvent
import com.example.modulith.shared.domain.DomainError
import kotlinx.coroutines.delay
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.UUID

/**
 * Fulfillment service that listens to payment events
 */
@Service
class FulfillmentService(
    private val eventPublisher: EventPublisher
) {

    /**
     * Listen to OrderPaid events and start fulfillment
     */
    @EventListener
    suspend fun onOrderPaid(event: OrderPaidIntegrationEvent): Either<DomainError, Unit> = either {
        // Simulate fulfillment processing
        delay(100)

        // Publish order completed event
        val completedEvent = OrderCompletedIntegrationEvent(
            eventId = UUID.randomUUID(),
            occurredAt = Instant.now(),
            orderId = event.orderId
        )

        eventPublisher.publish(completedEvent).bind()
    }
}
