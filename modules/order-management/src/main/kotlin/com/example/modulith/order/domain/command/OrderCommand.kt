package com.example.modulith.order.domain.command

import arrow.core.NonEmptyList
import com.example.modulith.order.domain.model.*
import com.example.modulith.shared.domain.Money

/**
 * Commands represent user intentions - what they want to do
 *
 * Following the f{model} pattern for functional domain modeling
 */
sealed interface OrderCommand {
    val orderId: OrderId

    /**
     * Create a new order
     */
    data class CreateOrder(
        override val orderId: OrderId,
        val customerId: CustomerId,
        val items: NonEmptyList<OrderItem>
    ) : OrderCommand

    /**
     * Confirm an order
     */
    data class ConfirmOrder(
        override val orderId: OrderId
    ) : OrderCommand

    /**
     * Mark order as paid
     */
    data class MarkAsPaid(
        override val orderId: OrderId,
        val paymentId: PaymentId
    ) : OrderCommand

    /**
     * Start fulfillment process
     */
    data class StartFulfillment(
        override val orderId: OrderId
    ) : OrderCommand

    /**
     * Complete the order
     */
    data class CompleteOrder(
        override val orderId: OrderId
    ) : OrderCommand

    /**
     * Cancel the order
     */
    data class CancelOrder(
        override val orderId: OrderId,
        val reason: String
    ) : OrderCommand
}
