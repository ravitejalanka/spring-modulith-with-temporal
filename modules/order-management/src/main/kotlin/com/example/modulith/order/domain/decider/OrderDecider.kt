package com.example.modulith.order.domain.decider

import arrow.core.Either
import arrow.core.NonEmptyList
import arrow.core.raise.either
import arrow.core.raise.ensure
import com.example.modulith.order.domain.command.OrderCommand
import com.example.modulith.order.domain.event.*
import com.example.modulith.order.domain.state.OrderState
import com.example.modulith.shared.domain.DomainError
import com.example.modulith.shared.domain.Money
import java.time.Instant
import java.util.UUID

/**
 * OrderDecider - Pure decision logic following the f{model} pattern
 *
 * This is the core of the domain model:
 * - decide(): (State, Command) -> Either<Error, NonEmptyList<Event>>
 * - evolve(): (State, Event) -> State
 *
 * All business logic is here, with NO side effects
 */
object OrderDecider {

    /**
     * Decision function: Given current state and a command, decide what events should happen
     *
     * This is a PURE function - no side effects, no I/O
     */
    fun decide(
        state: OrderState,
        command: OrderCommand
    ): Either<DomainError, NonEmptyList<OrderDomainEvent>> = either {
        when (command) {
            is OrderCommand.CreateOrder -> decideCreateOrder(state, command).bind()
            is OrderCommand.ConfirmOrder -> decideConfirmOrder(state, command).bind()
            is OrderCommand.MarkAsPaid -> decideMarkAsPaid(state, command).bind()
            is OrderCommand.StartFulfillment -> decideStartFulfillment(state, command).bind()
            is OrderCommand.CompleteOrder -> decideCompleteOrder(state, command).bind()
            is OrderCommand.CancelOrder -> decideCancelOrder(state, command).bind()
        }
    }

    /**
     * Evolution function: Given current state and an event, evolve to new state
     *
     * This is a PURE function - deterministic state transition
     */
    fun evolve(state: OrderState, event: OrderDomainEvent): OrderState {
        return when (event) {
            is OrderCreatedEvent -> evolveOrderCreated(state, event)
            is OrderConfirmedEvent -> evolveOrderConfirmed(state, event)
            is OrderPaidEvent -> evolveOrderPaid(state, event)
            is OrderFulfillmentStartedEvent -> evolveOrderFulfillmentStarted(state, event)
            is OrderCompletedEvent -> evolveOrderCompleted(state, event)
            is OrderCancelledEvent -> evolveOrderCancelled(state, event)
        }
    }

    /**
     * Reconstitute state from a list of events (event sourcing)
     */
    fun rehydrate(events: List<OrderDomainEvent>): OrderState {
        return events.fold(OrderState.Initial as OrderState) { state, event ->
            evolve(state, event)
        }
    }

    // ===== Decision Functions =====

    private fun decideCreateOrder(
        state: OrderState,
        command: OrderCommand.CreateOrder
    ): Either<DomainError, NonEmptyList<OrderDomainEvent>> = either {
        // Business rule: Can only create order if it doesn't exist
        ensure(state is OrderState.Initial) {
            DomainError.ValidationError("Order already exists")
        }

        // Business rule: Must have at least one item
        ensure(command.items.isNotEmpty()) {
            DomainError.ValidationError("Order must contain at least one item")
        }

        // Calculate total
        val totalAmount = command.items.fold(Money.ZERO) { acc, item ->
            acc + item.totalPrice
        }

        // Business rule: Total must be positive
        ensure(totalAmount > Money.ZERO) {
            DomainError.ValidationError("Order total must be positive")
        }

        // Decision: Create the order
        NonEmptyList.of(
            OrderCreatedEvent(
                eventId = UUID.randomUUID(),
                occurredAt = Instant.now(),
                aggregateId = command.orderId.value,
                orderId = command.orderId,
                customerId = command.customerId,
                items = command.items.toList(),
                totalAmount = totalAmount
            )
        )
    }

    private fun decideConfirmOrder(
        state: OrderState,
        command: OrderCommand.ConfirmOrder
    ): Either<DomainError, NonEmptyList<OrderDomainEvent>> = either {
        // Business rule: Can only confirm pending orders
        ensure(state is OrderState.Pending) {
            DomainError.ValidationError("Order can only be confirmed when in Pending state, current state: ${state::class.simpleName}")
        }

        // Business rule: Items must not be empty
        ensure(state.items.isNotEmpty()) {
            DomainError.ValidationError("Cannot confirm order with no items")
        }

        // Decision: Confirm the order
        NonEmptyList.of(
            OrderConfirmedEvent(
                eventId = UUID.randomUUID(),
                occurredAt = Instant.now(),
                aggregateId = command.orderId.value,
                orderId = command.orderId,
                totalAmount = state.totalAmount
            )
        )
    }

    private fun decideMarkAsPaid(
        state: OrderState,
        command: OrderCommand.MarkAsPaid
    ): Either<DomainError, NonEmptyList<OrderDomainEvent>> = either {
        // Business rule: Can only mark as paid when confirmed
        ensure(state is OrderState.Confirmed) {
            DomainError.ValidationError("Order can only be paid when in Confirmed state, current state: ${state::class.simpleName}")
        }

        // Decision: Mark as paid
        NonEmptyList.of(
            OrderPaidEvent(
                eventId = UUID.randomUUID(),
                occurredAt = Instant.now(),
                aggregateId = command.orderId.value,
                orderId = command.orderId,
                paymentId = command.paymentId,
                amount = state.totalAmount
            )
        )
    }

    private fun decideStartFulfillment(
        state: OrderState,
        command: OrderCommand.StartFulfillment
    ): Either<DomainError, NonEmptyList<OrderDomainEvent>> = either {
        // Business rule: Can only start fulfillment when paid
        ensure(state is OrderState.Paid) {
            DomainError.ValidationError("Order can only start fulfillment when in Paid state, current state: ${state::class.simpleName}")
        }

        // Decision: Start fulfillment
        NonEmptyList.of(
            OrderFulfillmentStartedEvent(
                eventId = UUID.randomUUID(),
                occurredAt = Instant.now(),
                aggregateId = command.orderId.value,
                orderId = command.orderId
            )
        )
    }

    private fun decideCompleteOrder(
        state: OrderState,
        command: OrderCommand.CompleteOrder
    ): Either<DomainError, NonEmptyList<OrderDomainEvent>> = either {
        // Business rule: Can only complete when fulfilling
        ensure(state is OrderState.Fulfilling) {
            DomainError.ValidationError("Order can only be completed when in Fulfilling state, current state: ${state::class.simpleName}")
        }

        // Decision: Complete the order
        NonEmptyList.of(
            OrderCompletedEvent(
                eventId = UUID.randomUUID(),
                occurredAt = Instant.now(),
                aggregateId = command.orderId.value,
                orderId = command.orderId
            )
        )
    }

    private fun decideCancelOrder(
        state: OrderState,
        command: OrderCommand.CancelOrder
    ): Either<DomainError, NonEmptyList<OrderDomainEvent>> = either {
        // Business rule: Can only cancel pending or confirmed orders
        ensure(state is OrderState.Pending || state is OrderState.Confirmed) {
            DomainError.ValidationError("Order can only be cancelled when in Pending or Confirmed state, current state: ${state::class.simpleName}")
        }

        // Decision: Cancel the order
        NonEmptyList.of(
            OrderCancelledEvent(
                eventId = UUID.randomUUID(),
                occurredAt = Instant.now(),
                aggregateId = command.orderId.value,
                orderId = command.orderId,
                reason = command.reason
            )
        )
    }

    // ===== Evolution Functions =====

    private fun evolveOrderCreated(state: OrderState, event: OrderCreatedEvent): OrderState {
        return OrderState.Pending(
            orderId = event.orderId,
            customerId = event.customerId,
            items = NonEmptyList.fromListUnsafe(event.items),
            totalAmount = event.totalAmount,
            version = if (state is OrderState.Initial) 1 else state.version + 1
        )
    }

    private fun evolveOrderConfirmed(state: OrderState, event: OrderConfirmedEvent): OrderState {
        return when (state) {
            is OrderState.Pending -> OrderState.Confirmed(
                orderId = state.orderId,
                customerId = state.customerId,
                items = state.items,
                totalAmount = state.totalAmount,
                version = state.version + 1
            )
            else -> state // Invalid transition, state unchanged
        }
    }

    private fun evolveOrderPaid(state: OrderState, event: OrderPaidEvent): OrderState {
        return when (state) {
            is OrderState.Confirmed -> OrderState.Paid(
                orderId = state.orderId,
                customerId = state.customerId,
                items = state.items,
                totalAmount = state.totalAmount,
                paymentId = event.paymentId,
                version = state.version + 1
            )
            else -> state // Invalid transition, state unchanged
        }
    }

    private fun evolveOrderFulfillmentStarted(state: OrderState, event: OrderFulfillmentStartedEvent): OrderState {
        return when (state) {
            is OrderState.Paid -> OrderState.Fulfilling(
                orderId = state.orderId,
                customerId = state.customerId,
                items = state.items,
                totalAmount = state.totalAmount,
                paymentId = state.paymentId,
                version = state.version + 1
            )
            else -> state // Invalid transition, state unchanged
        }
    }

    private fun evolveOrderCompleted(state: OrderState, event: OrderCompletedEvent): OrderState {
        return when (state) {
            is OrderState.Fulfilling -> OrderState.Completed(
                orderId = state.orderId,
                customerId = state.customerId,
                items = state.items,
                totalAmount = state.totalAmount,
                paymentId = state.paymentId,
                version = state.version + 1
            )
            else -> state // Invalid transition, state unchanged
        }
    }

    private fun evolveOrderCancelled(state: OrderState, event: OrderCancelledEvent): OrderState {
        return when (state) {
            is OrderState.Pending -> OrderState.Cancelled(
                orderId = state.orderId,
                customerId = state.customerId,
                items = state.items,
                totalAmount = state.totalAmount,
                cancellationReason = event.reason,
                version = state.version + 1
            )
            is OrderState.Confirmed -> OrderState.Cancelled(
                orderId = state.orderId,
                customerId = state.customerId,
                items = state.items,
                totalAmount = state.totalAmount,
                cancellationReason = event.reason,
                version = state.version + 1
            )
            else -> state // Invalid transition, state unchanged
        }
    }
}
