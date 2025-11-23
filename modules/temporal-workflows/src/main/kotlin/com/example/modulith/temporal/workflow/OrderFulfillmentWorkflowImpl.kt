package com.example.modulith.temporal.workflow

import com.example.modulith.temporal.activity.OrderActivity
import com.example.modulith.temporal.activity.PaymentActivity
import com.example.modulith.temporal.activity.FulfillmentActivity
import io.temporal.activity.ActivityOptions
import io.temporal.common.RetryOptions
import io.temporal.workflow.Workflow
import java.time.Duration

/**
 * Implementation of OrderFulfillmentWorkflow
 */
class OrderFulfillmentWorkflowImpl : OrderFulfillmentWorkflow {

    private val retryOptions = RetryOptions.newBuilder()
        .setInitialInterval(Duration.ofSeconds(1))
        .setMaximumInterval(Duration.ofSeconds(10))
        .setBackoffCoefficient(2.0)
        .setMaximumAttempts(3)
        .build()

    private val activityOptions = ActivityOptions.newBuilder()
        .setStartToCloseTimeout(Duration.ofMinutes(5))
        .setRetryOptions(retryOptions)
        .build()

    private val orderActivity = Workflow.newActivityStub(
        OrderActivity::class.java,
        activityOptions
    )

    private val paymentActivity = Workflow.newActivityStub(
        PaymentActivity::class.java,
        activityOptions
    )

    private val fulfillmentActivity = Workflow.newActivityStub(
        FulfillmentActivity::class.java,
        activityOptions
    )

    override fun fulfillOrder(input: OrderFulfillmentInput): OrderFulfillmentResult {
        return try {
            // Step 1: Validate order
            val orderValid = orderActivity.validateOrder(input.orderId)
            if (!orderValid) {
                return OrderFulfillmentResult.Failed(
                    input.orderId,
                    "Order validation failed"
                )
            }

            // Step 2: Process payment
            val paymentProcessed = paymentActivity.processPayment(
                input.orderId,
                input.totalAmount
            )
            if (!paymentProcessed) {
                return OrderFulfillmentResult.Failed(
                    input.orderId,
                    "Payment processing failed"
                )
            }

            // Step 3: Reserve inventory
            val inventoryReserved = try {
                fulfillmentActivity.reserveInventory(input.orderId)
            } catch (e: Exception) {
                // Compensate: refund payment
                paymentActivity.refundPayment(input.orderId)
                return OrderFulfillmentResult.Failed(
                    input.orderId,
                    "Inventory reservation failed: ${e.message}"
                )
            }

            if (!inventoryReserved) {
                // Compensate: refund payment
                paymentActivity.refundPayment(input.orderId)
                return OrderFulfillmentResult.Failed(
                    input.orderId,
                    "Inventory reservation failed"
                )
            }

            // Step 4: Initiate shipping
            val shippingInitiated = try {
                fulfillmentActivity.initiateShipping(input.orderId)
            } catch (e: Exception) {
                // Compensate: release inventory and refund payment
                fulfillmentActivity.releaseInventory(input.orderId)
                paymentActivity.refundPayment(input.orderId)
                return OrderFulfillmentResult.Failed(
                    input.orderId,
                    "Shipping initiation failed: ${e.message}"
                )
            }

            if (!shippingInitiated) {
                // Compensate: release inventory and refund payment
                fulfillmentActivity.releaseInventory(input.orderId)
                paymentActivity.refundPayment(input.orderId)
                return OrderFulfillmentResult.Failed(
                    input.orderId,
                    "Shipping initiation failed"
                )
            }

            // Step 5: Complete order
            orderActivity.completeOrder(input.orderId)

            OrderFulfillmentResult.Success(input.orderId)

        } catch (e: Exception) {
            // Global error handling
            OrderFulfillmentResult.Failed(
                input.orderId,
                "Unexpected error: ${e.message}"
            )
        }
    }
}
