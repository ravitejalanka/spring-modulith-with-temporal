package com.example.modulith.order.infrastructure.persistence

import arrow.core.Either
import arrow.core.raise.either
import com.example.modulith.order.application.port.OrderRepository
import com.example.modulith.order.domain.model.Order
import com.example.modulith.order.domain.model.OrderId
import com.example.modulith.shared.domain.DomainError
import com.example.modulith.shared.functional.catching
import com.example.modulith.temporal.workflow.OrderAggregateWorkflow
import com.example.modulith.temporal.workflow.OrderCommand
import com.example.modulith.temporal.workflow.OrderCommandResult
import io.temporal.client.WorkflowClient
import io.temporal.client.WorkflowOptions
import io.temporal.client.WorkflowStub
import org.springframework.stereotype.Repository
import java.time.Duration

/**
 * Repository implementation using Temporal workflows as the event store
 *
 * Instead of storing events in PostgreSQL, we use Temporal's built-in
 * event storage via workflow history.
 *
 * Each Order aggregate is a Temporal workflow instance.
 */
@Repository
class TemporalOrderRepository(
    private val workflowClient: WorkflowClient
) : OrderRepository {

    companion object {
        private const val TASK_QUEUE = "order-aggregate-task-queue"
        private fun workflowId(orderId: OrderId) = "order-${orderId.value}"
    }

    override suspend fun save(order: Order): Either<DomainError, Unit> = either {
        // Get or create workflow for this order
        val workflow = getOrCreateWorkflow(order.id).bind()

        // Apply new events to the workflow
        order.events.forEach { event ->
            catching {
                workflow.applyEvent(event)
            }.bind()
        }
    }

    override suspend fun findById(id: OrderId): Either<DomainError, Order?> = either {
        catching {
            val workflowStub = workflowClient.newWorkflowStub(
                OrderAggregateWorkflow::class.java,
                workflowId(id)
            )

            // Query current state from workflow
            workflowStub.getOrder()
        }.bind()
    }

    override suspend fun existsById(id: OrderId): Either<DomainError, Boolean> = either {
        findById(id).bind() != null
    }

    /**
     * Get existing workflow or create new one
     */
    private fun getOrCreateWorkflow(orderId: OrderId): Either<DomainError, OrderAggregateWorkflow> =
        catching {
            val options = WorkflowOptions.newBuilder()
                .setTaskQueue(TASK_QUEUE)
                .setWorkflowId(workflowId(orderId))
                .setWorkflowExecutionTimeout(Duration.ofDays(365))
                .build()

            workflowClient.newWorkflowStub(
                OrderAggregateWorkflow::class.java,
                options
            )
        }
}
