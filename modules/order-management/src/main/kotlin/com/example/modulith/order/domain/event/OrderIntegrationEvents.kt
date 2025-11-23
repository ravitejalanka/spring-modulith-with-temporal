package com.example.modulith.order.domain.event

import com.example.modulith.order.domain.model.*
import com.example.modulith.shared.domain.Money
import com.example.modulith.shared.event.IntegrationEvent
import java.time.Instant
import java.util.UUID

/**
 * Integration events for cross-module communication
 */

/**
 * Published when an order is placed (created and confirmed)
 */
data class OrderPlacedIntegrationEvent(
    override val eventId: UUID,
    override val occurredAt: Instant,
    val orderId: OrderId,
    val customerId: CustomerId,
    val items: List<OrderItemDto>,
    val totalAmount: Money
) : IntegrationEvent {
    override val eventType: String = "OrderPlaced"
}

/**
 * Published when an order payment is completed
 */
data class OrderPaidIntegrationEvent(
    override val eventId: UUID,
    override val occurredAt: Instant,
    val orderId: OrderId,
    val paymentId: PaymentId,
    val amount: Money
) : IntegrationEvent {
    override val eventType: String = "OrderPaid"
}

/**
 * Published when an order is completed
 */
data class OrderCompletedIntegrationEvent(
    override val eventId: UUID,
    override val occurredAt: Instant,
    val orderId: OrderId
) : IntegrationEvent {
    override val eventType: String = "OrderCompleted"
}

/**
 * Published when an order is cancelled
 */
data class OrderCancelledIntegrationEvent(
    override val eventId: UUID,
    override val occurredAt: Instant,
    val orderId: OrderId,
    val reason: String
) : IntegrationEvent {
    override val eventType: String = "OrderCancelled"
}

/**
 * DTO for order items in integration events
 */
data class OrderItemDto(
    val productId: UUID,
    val productName: String,
    val quantity: Int,
    val unitPrice: Money
) {
    companion object {
        fun from(item: OrderItem) = OrderItemDto(
            productId = item.productId.value,
            productName = item.productName,
            quantity = item.quantity.value,
            unitPrice = item.unitPrice
        )
    }
}
