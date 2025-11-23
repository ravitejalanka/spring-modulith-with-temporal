package com.example.modulith.temporal.workflow

import arrow.core.NonEmptyList
import com.example.modulith.order.domain.event.OrderDomainEvent
import com.example.modulith.order.domain.model.Order
import io.temporal.workflow.Workflow
import org.slf4j.LoggerFactory

/**
 * Implementation of OrderAggregateWorkflow using Temporal as the event store
 *
 * This eliminates the need for a separate PostgreSQL event store.
 * All events are stored in Temporal's durable workflow history.
 */
class OrderAggregateWorkflowImpl : OrderAggregateWorkflow {

    private val logger = LoggerFactory.getLogger(OrderAggregateWorkflowImpl::class.java)

    // Current order state (reconstituted from events)
    private var currentOrder: Order? = null

    // Domain events (stored in Temporal's history)
    private val events = mutableListOf<OrderDomainEvent>()

    override fun execute(command: OrderCommand): OrderCommandResult {
        logger.info("Executing command: ${command::class.simpleName} for order ${command.orderId}")

        return when (command) {
            is OrderCommand.CreateOrder -> handleCreateOrder(command)
            is OrderCommand.ConfirmOrder -> handleConfirmOrder(command)
            is OrderCommand.MarkAsPaid -> handleMarkAsPaid(command)
            is OrderCommand.StartFulfillment -> handleStartFulfillment(command)
            is OrderCommand.CompleteOrder -> handleCompleteOrder(command)
            is OrderCommand.CancelOrder -> handleCancelOrder(command)
        }
    }

    override fun getOrder(): Order? = currentOrder

    override fun getEvents(): List<OrderDomainEvent> = events.toList()

    override fun applyEvent(event: OrderDomainEvent) {
        events.add(event)
        // Reconstitute order from all events
        reconstitute()
    }

    // Command Handlers

    private fun handleCreateOrder(command: OrderCommand.CreateOrder): OrderCommandResult {
        if (currentOrder != null) {
            return OrderCommandResult.Failure(
                com.example.modulith.shared.domain.DomainError.ValidationError(
                    "Order already exists"
                )
            )
        }

        val itemsNel = NonEmptyList.fromListUnsafe(command.items)

        return Order.create(command.customerId, itemsNel)
            .fold(
                { error -> OrderCommandResult.Failure(error) },
                { order ->
                    // Store events in Temporal's history
                    order.events.forEach { event ->
                        events.add(event)
                        logger.info("Event recorded: ${event::class.simpleName}")
                    }

                    currentOrder = order
                    OrderCommandResult.Success(order)
                }
            )
    }

    private fun handleConfirmOrder(command: OrderCommand.ConfirmOrder): OrderCommandResult {
        val order = currentOrder
            ?: return OrderCommandResult.Failure(
                com.example.modulith.shared.domain.DomainError.NotFoundError(
                    "Order",
                    command.orderId.value.toString()
                )
            )

        return when (order) {
            is Order.PendingOrder -> order.confirm()
                .fold(
                    { error -> OrderCommandResult.Failure(error) },
                    { confirmedOrder ->
                        confirmedOrder.events.forEach { events.add(it) }
                        currentOrder = confirmedOrder
                        OrderCommandResult.Success(confirmedOrder)
                    }
                )
            else -> OrderCommandResult.Failure(
                com.example.modulith.shared.domain.DomainError.ValidationError(
                    "Order cannot be confirmed in current state"
                )
            )
        }
    }

    private fun handleMarkAsPaid(command: OrderCommand.MarkAsPaid): OrderCommandResult {
        val order = currentOrder
            ?: return OrderCommandResult.Failure(
                com.example.modulith.shared.domain.DomainError.NotFoundError(
                    "Order",
                    command.orderId.value.toString()
                )
            )

        return when (order) {
            is Order.ConfirmedOrder -> order.markAsPaid(command.paymentId)
                .fold(
                    { error -> OrderCommandResult.Failure(error) },
                    { paidOrder ->
                        paidOrder.events.forEach { events.add(it) }
                        currentOrder = paidOrder
                        OrderCommandResult.Success(paidOrder)
                    }
                )
            else -> OrderCommandResult.Failure(
                com.example.modulith.shared.domain.DomainError.ValidationError(
                    "Order cannot be marked as paid in current state"
                )
            )
        }
    }

    private fun handleStartFulfillment(command: OrderCommand.StartFulfillment): OrderCommandResult {
        val order = currentOrder
            ?: return OrderCommandResult.Failure(
                com.example.modulith.shared.domain.DomainError.NotFoundError(
                    "Order",
                    command.orderId.value.toString()
                )
            )

        return when (order) {
            is Order.PaidOrder -> order.startFulfillment()
                .fold(
                    { error -> OrderCommandResult.Failure(error) },
                    { fulfillingOrder ->
                        fulfillingOrder.events.forEach { events.add(it) }
                        currentOrder = fulfillingOrder
                        OrderCommandResult.Success(fulfillingOrder)
                    }
                )
            else -> OrderCommandResult.Failure(
                com.example.modulith.shared.domain.DomainError.ValidationError(
                    "Order cannot start fulfillment in current state"
                )
            )
        }
    }

    private fun handleCompleteOrder(command: OrderCommand.CompleteOrder): OrderCommandResult {
        val order = currentOrder
            ?: return OrderCommandResult.Failure(
                com.example.modulith.shared.domain.DomainError.NotFoundError(
                    "Order",
                    command.orderId.value.toString()
                )
            )

        return when (order) {
            is Order.FulfillingOrder -> order.complete()
                .fold(
                    { error -> OrderCommandResult.Failure(error) },
                    { completedOrder ->
                        completedOrder.events.forEach { events.add(it) }
                        currentOrder = completedOrder
                        OrderCommandResult.Success(completedOrder)
                    }
                )
            else -> OrderCommandResult.Failure(
                com.example.modulith.shared.domain.DomainError.ValidationError(
                    "Order cannot be completed in current state"
                )
            )
        }
    }

    private fun handleCancelOrder(command: OrderCommand.CancelOrder): OrderCommandResult {
        val order = currentOrder
            ?: return OrderCommandResult.Failure(
                com.example.modulith.shared.domain.DomainError.NotFoundError(
                    "Order",
                    command.orderId.value.toString()
                )
            )

        return when (order) {
            is Order.PendingOrder -> order.cancel()
                .fold(
                    { error -> OrderCommandResult.Failure(error) },
                    { cancelledOrder ->
                        cancelledOrder.events.forEach { events.add(it) }
                        currentOrder = cancelledOrder
                        OrderCommandResult.Success(cancelledOrder)
                    }
                )
            is Order.ConfirmedOrder -> order.cancel()
                .fold(
                    { error -> OrderCommandResult.Failure(error) },
                    { cancelledOrder ->
                        cancelledOrder.events.forEach { events.add(it) }
                        currentOrder = cancelledOrder
                        OrderCommandResult.Success(cancelledOrder)
                    }
                )
            else -> OrderCommandResult.Failure(
                com.example.modulith.shared.domain.DomainError.ValidationError(
                    "Order cannot be cancelled in current state"
                )
            )
        }
    }

    /**
     * Reconstitute order from events (event sourcing)
     */
    private fun reconstitute() {
        if (events.isEmpty()) {
            currentOrder = null
            return
        }

        currentOrder = Order.fromEvents(events)
            .fold(
                { error ->
                    logger.error("Failed to reconstitute order: ${error.message}")
                    null
                },
                { order -> order }
            )
    }
}
