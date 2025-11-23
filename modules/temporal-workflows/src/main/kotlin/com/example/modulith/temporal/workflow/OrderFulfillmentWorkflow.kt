package com.example.modulith.temporal.workflow

import arrow.core.Either
import com.example.modulith.order.domain.model.OrderId
import com.example.modulith.shared.domain.DomainError
import com.example.modulith.shared.domain.Money
import io.temporal.workflow.WorkflowInterface
import io.temporal.workflow.WorkflowMethod
import java.util.UUID

/**
 * Input for order fulfillment workflow
 */
data class OrderFulfillmentInput(
    val orderId: UUID,
    val customerId: UUID,
    val totalAmount: Money
)

/**
 * Result of order fulfillment workflow
 */
sealed interface OrderFulfillmentResult {
    data class Success(val orderId: UUID) : OrderFulfillmentResult
    data class Failed(val orderId: UUID, val reason: String) : OrderFulfillmentResult
}

/**
 * Temporal workflow for orchestrating order fulfillment saga
 *
 * This workflow coordinates:
 * 1. Payment processing
 * 2. Inventory reservation
 * 3. Shipping initiation
 *
 * With automatic compensation on failure
 */
@WorkflowInterface
interface OrderFulfillmentWorkflow {

    @WorkflowMethod
    fun fulfillOrder(input: OrderFulfillmentInput): OrderFulfillmentResult
}
