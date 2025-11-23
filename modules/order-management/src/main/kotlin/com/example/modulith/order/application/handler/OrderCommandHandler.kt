package com.example.modulith.order.application.handler

import arrow.core.Either
import arrow.core.raise.either
import com.example.modulith.order.application.port.IntegrationEventPublisher
import com.example.modulith.order.domain.command.OrderCommand
import com.example.modulith.order.domain.decider.OrderDecider
import com.example.modulith.order.domain.event.*
import com.example.modulith.order.domain.model.OrderId
import com.example.modulith.order.domain.state.OrderState
import com.example.modulith.shared.domain.DomainError
import com.example.modulith.temporal.workflow.OrderAggregateWorkflow
import io.temporal.client.WorkflowClient
import io.temporal.client.WorkflowOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.Instant
import java.util.UUID

/**
 * OrderCommandHandler - Orchestrates command handling using the Decider pattern
 *
 * Flow:
 * 1. Load current state from event store (Temporal)
 * 2. Use OrderDecider.decide() to get events
 * 3. Store events in event store (Temporal)
 * 4. Publish domain events (for projections)
 * 5. Publish integration events (for other modules)
 *
 * This follows the f{model} pattern for clean separation
 */
@Service
class OrderCommandHandler(
    private val workflowClient: WorkflowClient,
    private val integrationEventPublisher: IntegrationEventPublisher,
    private val applicationEventPublisher: ApplicationEventPublisher
) {

    companion object {
        private const val TASK_QUEUE = "order-aggregate-task-queue"
        private fun workflowId(orderId: OrderId) = "order-${orderId.value}"
    }

    /**
     * Handle a command by:
     * 1. Loading current state
     * 2. Using Decider to decide what events to emit
     * 3. Storing events
     * 4. Publishing integration events
     */
    suspend fun handle(command: OrderCommand): Either<DomainError, OrderState> = either {
        // 1. Load current state from event store (Temporal workflow)
        val currentState = loadState(command.orderId).bind()

        // 2. Use Decider to decide what events should happen (pure function)
        val events = OrderDecider.decide(currentState, command).bind()

        // 3. Store events in event store (Temporal workflow)
        storeEvents(command.orderId, events.toList()).bind()

        // 4. Publish domain events (for projections to listen)
        publishDomainEvents(events.toList())

        // 5. Evolve state with new events
        val newState = events.fold(currentState) { state, event ->
            OrderDecider.evolve(state, event)
        }

        // 6. Publish integration events (for other modules)
        publishIntegrationEvents(events.toList()).bind()

        newState
    }

    /**
     * Load current state from Temporal workflow (event store)
     */
    private suspend fun loadState(orderId: OrderId): Either<DomainError, OrderState> = either {
        withContext(Dispatchers.IO) {
            Either.catch {
                try {
                    // Try to get existing workflow
                    val workflow = workflowClient.newWorkflowStub(
                        OrderAggregateWorkflow::class.java,
                        workflowId(orderId)
                    )

                    // Query events from workflow
                    val events = workflow.getEvents()

                    // Rehydrate state from events
                    if (events.isEmpty()) {
                        OrderState.Initial
                    } else {
                        OrderDecider.rehydrate(events)
                    }
                } catch (e: Exception) {
                    // Workflow doesn't exist yet, return initial state
                    OrderState.Initial
                }
            }.mapLeft { e ->
                DomainError.ValidationError("Failed to load state: ${e.message}")
            }
        }.bind()
    }

    /**
     * Store events in Temporal workflow (event store)
     */
    private suspend fun storeEvents(
        orderId: OrderId,
        events: List<OrderDomainEvent>
    ): Either<DomainError, Unit> = either {
        withContext(Dispatchers.IO) {
            Either.catch {
                val workflow = getOrCreateWorkflow(orderId)

                // Signal workflow to apply events
                events.forEach { event ->
                    workflow.applyEvent(event)
                }
            }.mapLeft { e ->
                DomainError.ValidationError("Failed to store events: ${e.message}")
            }
        }.bind()
    }

    /**
     * Get or create Temporal workflow for this order
     */
    private fun getOrCreateWorkflow(orderId: OrderId): OrderAggregateWorkflow {
        val options = WorkflowOptions.newBuilder()
            .setTaskQueue(TASK_QUEUE)
            .setWorkflowId(workflowId(orderId))
            .setWorkflowExecutionTimeout(Duration.ofDays(365))
            .build()

        return workflowClient.newWorkflowStub(
            OrderAggregateWorkflow::class.java,
            options
        )
    }

    /**
     * Publish domain events for projections to listen
     *
     * This enables the PROJECTION pattern:
     * - Events are published to internal event bus
     * - Projections listen and update read models
     * - Fully decoupled from command side
     */
    private fun publishDomainEvents(events: List<OrderDomainEvent>) {
        events.forEach { event ->
            applicationEventPublisher.publishEvent(event)
        }
    }

    /**
     * Publish integration events for cross-module communication
     */
    private suspend fun publishIntegrationEvents(
        events: List<OrderDomainEvent>
    ): Either<DomainError, Unit> = either {
        events.forEach { event ->
            val integrationEvent = mapToIntegrationEvent(event)
            integrationEvent?.let {
                integrationEventPublisher.publish(it).bind()
            }
        }
    }

    /**
     * Map domain events to integration events (only for events that cross module boundaries)
     */
    private fun mapToIntegrationEvent(event: OrderDomainEvent): com.example.modulith.shared.event.IntegrationEvent? {
        return when (event) {
            is OrderCreatedEvent -> OrderPlacedIntegrationEvent(
                eventId = UUID.randomUUID(),
                occurredAt = Instant.now(),
                orderId = event.orderId,
                customerId = event.customerId,
                items = event.items.map { OrderItemDto.from(it) },
                totalAmount = event.totalAmount
            )
            is OrderPaidEvent -> OrderPaidIntegrationEvent(
                eventId = UUID.randomUUID(),
                occurredAt = Instant.now(),
                orderId = event.orderId,
                paymentId = event.paymentId,
                amount = event.amount
            )
            is OrderCompletedEvent -> OrderCompletedIntegrationEvent(
                eventId = UUID.randomUUID(),
                occurredAt = Instant.now(),
                orderId = event.orderId
            )
            // Other events are internal only
            else -> null
        }
    }
}
