package com.example.modulith.order.infrastructure.projection

import com.example.modulith.order.application.projection.OrderProjection
import com.example.modulith.order.domain.event.OrderDomainEvent
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

/**
 * Event Listener - Connects domain events to projections
 *
 * This is the infrastructure layer that:
 * - Listens to internal domain events
 * - Routes them to all registered projections
 * - Handles errors gracefully
 *
 * Pattern: Event-Driven Projections
 * - Domain publishes events
 * - Projections react independently
 * - Fully decoupled
 */
@Component
class OrderEventListener(
    private val projections: List<OrderProjection>
) {
    private val logger = LoggerFactory.getLogger(OrderEventListener::class.java)

    /**
     * Listen to ALL order domain events
     *
     * When an event is published (from Temporal workflow or anywhere):
     * 1. This listener catches it
     * 2. Routes to all registered projections
     * 3. Each projection updates its view
     *
     * Decoupled: Projections don't know about each other or the event source
     */
    @EventListener
    fun onOrderEvent(event: OrderDomainEvent) = runBlocking {
        logger.info("Received domain event: ${event::class.simpleName} for order ${event.aggregateId}")

        projections.forEach { projection ->
            try {
                projection.project(event).fold(
                    { error ->
                        logger.error(
                            "Projection ${projection::class.simpleName} failed for event ${event::class.simpleName}: ${error.message}"
                        )
                    },
                    {
                        logger.debug(
                            "Projection ${projection::class.simpleName} updated for event ${event::class.simpleName}"
                        )
                    }
                )
            } catch (e: Exception) {
                logger.error(
                    "Unexpected error in projection ${projection::class.simpleName}: ${e.message}",
                    e
                )
            }
        }
    }
}
