package com.example.modulith.order.domain.state

import arrow.core.NonEmptyList
import com.example.modulith.order.domain.model.*
import com.example.modulith.shared.domain.Money

/**
 * Order state - represents the current state of an order aggregate
 *
 * Following the f{model} pattern for functional domain modeling
 * This is a pure data structure with no behavior
 */
sealed interface OrderState {
    val orderId: OrderId
    val customerId: CustomerId
    val items: NonEmptyList<OrderItem>
    val totalAmount: Money
    val version: Long

    /**
     * No order exists yet
     */
    data object Initial : OrderState {
        override val orderId: OrderId get() = throw IllegalStateException("No order ID in initial state")
        override val customerId: CustomerId get() = throw IllegalStateException("No customer ID in initial state")
        override val items: NonEmptyList<OrderItem> get() = throw IllegalStateException("No items in initial state")
        override val totalAmount: Money get() = Money.ZERO
        override val version: Long get() = 0
    }

    /**
     * Order created but not yet confirmed
     */
    data class Pending(
        override val orderId: OrderId,
        override val customerId: CustomerId,
        override val items: NonEmptyList<OrderItem>,
        override val totalAmount: Money,
        override val version: Long = 0
    ) : OrderState

    /**
     * Order confirmed, waiting for payment
     */
    data class Confirmed(
        override val orderId: OrderId,
        override val customerId: CustomerId,
        override val items: NonEmptyList<OrderItem>,
        override val totalAmount: Money,
        override val version: Long = 0
    ) : OrderState

    /**
     * Order paid, ready for fulfillment
     */
    data class Paid(
        override val orderId: OrderId,
        override val customerId: CustomerId,
        override val items: NonEmptyList<OrderItem>,
        override val totalAmount: Money,
        val paymentId: PaymentId,
        override val version: Long = 0
    ) : OrderState

    /**
     * Order fulfillment in progress
     */
    data class Fulfilling(
        override val orderId: OrderId,
        override val customerId: CustomerId,
        override val items: NonEmptyList<OrderItem>,
        override val totalAmount: Money,
        val paymentId: PaymentId,
        override val version: Long = 0
    ) : OrderState

    /**
     * Order completed successfully
     */
    data class Completed(
        override val orderId: OrderId,
        override val customerId: CustomerId,
        override val items: NonEmptyList<OrderItem>,
        override val totalAmount: Money,
        val paymentId: PaymentId,
        override val version: Long = 0
    ) : OrderState

    /**
     * Order cancelled
     */
    data class Cancelled(
        override val orderId: OrderId,
        override val customerId: CustomerId,
        override val items: NonEmptyList<OrderItem>,
        override val totalAmount: Money,
        val cancellationReason: String,
        override val version: Long = 0
    ) : OrderState
}
