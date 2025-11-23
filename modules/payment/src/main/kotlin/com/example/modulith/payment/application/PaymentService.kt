package com.example.modulith.payment.application

import arrow.core.Either
import arrow.core.raise.either
import com.example.modulith.infrastructure.messaging.EventPublisher
import com.example.modulith.order.domain.event.OrderPlacedIntegrationEvent
import com.example.modulith.order.domain.event.OrderPaidIntegrationEvent
import com.example.modulith.order.domain.model.OrderId
import com.example.modulith.payment.domain.Payment
import com.example.modulith.shared.domain.DomainError
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.UUID

/**
 * Payment service that listens to order events
 */
@Service
class PaymentService(
    private val eventPublisher: EventPublisher
) {

    /**
     * Listen to OrderPlaced events and process payment
     */
    @EventListener
    suspend fun onOrderPlaced(event: OrderPlacedIntegrationEvent): Either<DomainError, Unit> = either {
        // Create payment
        val payment = Payment.create(event.orderId, event.totalAmount).bind()

        // Process payment (simplified - always succeeds)
        val processedPayment = payment.process().bind()

        // Publish payment processed event
        val paymentEvent = OrderPaidIntegrationEvent(
            eventId = UUID.randomUUID(),
            occurredAt = Instant.now(),
            orderId = event.orderId,
            paymentId = processedPayment.id,
            amount = processedPayment.amount
        )

        eventPublisher.publish(paymentEvent).bind()
    }
}
