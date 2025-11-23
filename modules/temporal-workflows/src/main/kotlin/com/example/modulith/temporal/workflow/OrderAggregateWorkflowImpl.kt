package com.example.modulith.temporal.workflow

import com.example.modulith.order.domain.decider.OrderDecider
import com.example.modulith.order.domain.event.OrderDomainEvent
import com.example.modulith.order.domain.state.OrderState
import io.temporal.workflow.Workflow
import org.slf4j.LoggerFactory

/**
 * Implementation of OrderAggregateWorkflow using Temporal as the event store
 *
 * This eliminates the need for a separate PostgreSQL event store.
 * All events are stored in Temporal's durable workflow history.
 *
 * Now uses the Decider pattern from f{model} for clean, testable decision logic
 */
class OrderAggregateWorkflowImpl : OrderAggregateWorkflow {

    private val logger = LoggerFactory.getLogger(OrderAggregateWorkflowImpl::class.java)

    // Current order state (reconstituted from events using OrderDecider)
    private var currentState: OrderState = OrderState.Initial

    // Domain events (stored in Temporal's history)
    private val events = mutableListOf<OrderDomainEvent>()

    override fun execute(command: OrderCommand): OrderCommandResult {
        logger.info("Executing command: ${command::class.simpleName} for order ${command.orderId}")

        // Convert Temporal command to domain command
        val domainCommand = mapToDomainCommand(command)

        // Use Decider to decide what events should happen (pure function)
        val decisionResult = OrderDecider.decide(currentState, domainCommand)

        return decisionResult.fold(
            { error ->
                logger.error("Command failed: ${error.message}")
                OrderCommandResult.Failure(error)
            },
            { newEvents ->
                // Store events in Temporal's history
                newEvents.forEach { event ->
                    events.add(event)
                    logger.info("Event recorded: ${event::class.simpleName}")
                }

                // Evolve state using Decider
                currentState = OrderDecider.rehydrate(events)

                // Convert state back to Order for backward compatibility
                OrderCommandResult.Success(convertStateToOrder(currentState))
            }
        )
    }

    override fun getOrder(): com.example.modulith.order.domain.model.Order? {
        return if (currentState is OrderState.Initial) {
            null
        } else {
            convertStateToOrder(currentState)
        }
    }

    override fun getEvents(): List<OrderDomainEvent> = events.toList()

    override fun applyEvent(event: OrderDomainEvent) {
        events.add(event)
        // Use Decider to evolve state
        currentState = OrderDecider.evolve(currentState, event)
        logger.info("Event applied: ${event::class.simpleName}, new state: ${currentState::class.simpleName}")
    }

    /**
     * Map Temporal workflow command to domain command
     */
    private fun mapToDomainCommand(command: OrderCommand): com.example.modulith.order.domain.command.OrderCommand {
        return when (command) {
            is OrderCommand.CreateOrder -> com.example.modulith.order.domain.command.OrderCommand.CreateOrder(
                orderId = command.orderId,
                customerId = command.customerId,
                items = arrow.core.NonEmptyList.fromListUnsafe(command.items)
            )
            is OrderCommand.ConfirmOrder -> com.example.modulith.order.domain.command.OrderCommand.ConfirmOrder(
                orderId = command.orderId
            )
            is OrderCommand.MarkAsPaid -> com.example.modulith.order.domain.command.OrderCommand.MarkAsPaid(
                orderId = command.orderId,
                paymentId = command.paymentId
            )
            is OrderCommand.StartFulfillment -> com.example.modulith.order.domain.command.OrderCommand.StartFulfillment(
                orderId = command.orderId
            )
            is OrderCommand.CompleteOrder -> com.example.modulith.order.domain.command.OrderCommand.CompleteOrder(
                orderId = command.orderId
            )
            is OrderCommand.CancelOrder -> com.example.modulith.order.domain.command.OrderCommand.CancelOrder(
                orderId = command.orderId,
                reason = command.reason
            )
        }
    }

    /**
     * Convert OrderState to Order for backward compatibility
     * This is temporary until we fully migrate to using OrderState everywhere
     */
    private fun convertStateToOrder(state: OrderState): com.example.modulith.order.domain.model.Order? {
        return when (state) {
            is OrderState.Initial -> null
            is OrderState.Pending -> com.example.modulith.order.domain.model.Order.PendingOrder(
                id = state.orderId,
                customerId = state.customerId,
                items = state.items,
                version = state.version
            )
            is OrderState.Confirmed -> com.example.modulith.order.domain.model.Order.ConfirmedOrder(
                id = state.orderId,
                customerId = state.customerId,
                items = state.items,
                totalAmount = state.totalAmount,
                version = state.version
            )
            is OrderState.Paid -> com.example.modulith.order.domain.model.Order.PaidOrder(
                id = state.orderId,
                customerId = state.customerId,
                items = state.items,
                totalAmount = state.totalAmount,
                paymentId = state.paymentId,
                version = state.version
            )
            is OrderState.Fulfilling -> com.example.modulith.order.domain.model.Order.FulfillingOrder(
                id = state.orderId,
                customerId = state.customerId,
                items = state.items,
                totalAmount = state.totalAmount,
                paymentId = state.paymentId,
                version = state.version
            )
            is OrderState.Completed -> com.example.modulith.order.domain.model.Order.CompletedOrder(
                id = state.orderId,
                customerId = state.customerId,
                items = state.items,
                totalAmount = state.totalAmount,
                paymentId = state.paymentId,
                version = state.version
            )
            is OrderState.Cancelled -> com.example.modulith.order.domain.model.Order.CancelledOrder(
                id = state.orderId,
                customerId = state.customerId,
                items = state.items,
                totalAmount = state.totalAmount,
                cancellationReason = state.cancellationReason,
                version = state.version
            )
        }
    }
}
