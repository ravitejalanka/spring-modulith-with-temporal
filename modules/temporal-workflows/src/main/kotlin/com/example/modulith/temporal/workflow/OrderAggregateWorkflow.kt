package com.example.modulith.temporal.workflow

import arrow.core.Either
import com.example.modulith.order.domain.event.OrderDomainEvent
import com.example.modulith.order.domain.model.Order
import com.example.modulith.order.domain.model.OrderId
import com.example.modulith.shared.domain.DomainError
import io.temporal.workflow.QueryMethod
import io.temporal.workflow.SignalMethod
import io.temporal.workflow.WorkflowInterface
import io.temporal.workflow.WorkflowMethod

/**
 * Temporal workflow that acts as an Order Aggregate
 *
 * This workflow stores domain events in Temporal's event storage,
 * eliminating the need for a separate event store.
 *
 * Key capabilities:
 * - Event sourcing via Temporal's history
 * - Durable execution
 * - Event replay for aggregate reconstitution
 * - Time-travel queries
 */
@WorkflowInterface
interface OrderAggregateWorkflow {

    /**
     * Main workflow method - processes commands and returns current state
     */
    @WorkflowMethod
    fun execute(command: OrderCommand): OrderCommandResult

    /**
     * Query current order state
     */
    @QueryMethod
    fun getOrder(): Order?

    /**
     * Query all domain events (stored in Temporal's history)
     */
    @QueryMethod
    fun getEvents(): List<OrderDomainEvent>

    /**
     * Signal to apply an event (for event sourcing)
     */
    @SignalMethod
    fun applyEvent(event: OrderDomainEvent)
}

/**
 * Commands for order operations
 */
sealed interface OrderCommand {
    val orderId: OrderId

    data class CreateOrder(
        override val orderId: OrderId,
        val customerId: com.example.modulith.order.domain.model.CustomerId,
        val items: List<com.example.modulith.order.domain.model.OrderItem>
    ) : OrderCommand

    data class ConfirmOrder(
        override val orderId: OrderId
    ) : OrderCommand

    data class MarkAsPaid(
        override val orderId: OrderId,
        val paymentId: com.example.modulith.order.domain.model.PaymentId
    ) : OrderCommand

    data class StartFulfillment(
        override val orderId: OrderId
    ) : OrderCommand

    data class CompleteOrder(
        override val orderId: OrderId
    ) : OrderCommand

    data class CancelOrder(
        override val orderId: OrderId,
        val reason: String
    ) : OrderCommand
}

/**
 * Result of command execution
 */
sealed interface OrderCommandResult {
    data class Success(val order: Order) : OrderCommandResult
    data class Failure(val error: DomainError) : OrderCommandResult
}
