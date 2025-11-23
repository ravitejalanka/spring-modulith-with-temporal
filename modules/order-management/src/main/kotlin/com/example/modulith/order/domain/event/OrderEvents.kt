package com.example.modulith.order.domain.event

import com.example.modulith.order.domain.model.*
import com.example.modulith.shared.domain.DomainEvent
import com.example.modulith.shared.domain.Money
import java.time.Instant
import java.util.UUID

/**
 * Base interface for all order domain events
 */
sealed interface OrderDomainEvent : DomainEvent

/**
 * Order created event
 */
data class OrderCreatedEvent(
    override val eventId: UUID,
    override val occurredAt: Instant,
    override val aggregateId: UUID,
    val orderId: OrderId,
    val customerId: CustomerId,
    val items: List<OrderItem>,
    val totalAmount: Money
) : OrderDomainEvent

/**
 * Order confirmed event
 */
data class OrderConfirmedEvent(
    override val eventId: UUID,
    override val occurredAt: Instant,
    override val aggregateId: UUID,
    val orderId: OrderId,
    val totalAmount: Money
) : OrderDomainEvent

/**
 * Order paid event
 */
data class OrderPaidEvent(
    override val eventId: UUID,
    override val occurredAt: Instant,
    override val aggregateId: UUID,
    val orderId: OrderId,
    val paymentId: PaymentId,
    val amount: Money
) : OrderDomainEvent

/**
 * Order fulfillment started event
 */
data class OrderFulfillmentStartedEvent(
    override val eventId: UUID,
    override val occurredAt: Instant,
    override val aggregateId: UUID,
    val orderId: OrderId
) : OrderDomainEvent

/**
 * Order completed event
 */
data class OrderCompletedEvent(
    override val eventId: UUID,
    override val occurredAt: Instant,
    override val aggregateId: UUID,
    val orderId: OrderId
) : OrderDomainEvent

/**
 * Order cancelled event
 */
data class OrderCancelledEvent(
    override val eventId: UUID,
    override val occurredAt: Instant,
    override val aggregateId: UUID,
    val orderId: OrderId,
    val reason: String
) : OrderDomainEvent
