package com.example.modulith.order.api.dto

import com.example.modulith.order.application.usecase.CreateOrderItemDto
import com.example.modulith.order.domain.model.Order
import java.math.BigDecimal
import java.util.UUID

/**
 * Request to create an order
 */
data class CreateOrderRequest(
    val customerId: UUID,
    val items: List<CreateOrderItemDto>
)

/**
 * Response DTOs for orders
 */
sealed interface OrderResponse {
    data class Created(val orderId: UUID) : OrderResponse

    data class Error(val message: String) : OrderResponse

    data class OrderDetails(
        val orderId: UUID,
        val customerId: UUID,
        val status: String,
        val items: List<OrderItemResponse>,
        val totalAmount: BigDecimal
    ) : OrderResponse

    companion object {
        fun from(order: Order): OrderResponse {
            return when (order) {
                is Order.PendingOrder -> OrderDetails(
                    orderId = order.id.value,
                    customerId = order.customerId.value,
                    status = "PENDING",
                    items = order.items.map { OrderItemResponse.from(it) },
                    totalAmount = order.totalAmount.amount
                )

                is Order.ConfirmedOrder -> OrderDetails(
                    orderId = order.id.value,
                    customerId = order.customerId.value,
                    status = "CONFIRMED",
                    items = order.items.map { OrderItemResponse.from(it) },
                    totalAmount = order.totalAmount.amount
                )

                is Order.PaidOrder -> OrderDetails(
                    orderId = order.id.value,
                    customerId = order.customerId.value,
                    status = "PAID",
                    items = order.items.map { OrderItemResponse.from(it) },
                    totalAmount = order.totalAmount.amount
                )

                is Order.FulfillingOrder -> OrderDetails(
                    orderId = order.id.value,
                    customerId = order.customerId.value,
                    status = "FULFILLING",
                    items = order.items.map { OrderItemResponse.from(it) },
                    totalAmount = order.totalAmount.amount
                )

                is Order.CompletedOrder -> OrderDetails(
                    orderId = order.id.value,
                    customerId = order.customerId.value,
                    status = "COMPLETED",
                    items = order.items.map { OrderItemResponse.from(it) },
                    totalAmount = order.totalAmount.amount
                )

                is Order.CancelledOrder -> OrderDetails(
                    orderId = order.id.value,
                    customerId = order.customerId.value,
                    status = "CANCELLED",
                    items = order.items.map { OrderItemResponse.from(it) },
                    totalAmount = order.totalAmount.amount
                )
            }
        }
    }
}

data class OrderItemResponse(
    val productId: UUID,
    val productName: String,
    val quantity: Int,
    val unitPrice: BigDecimal,
    val totalPrice: BigDecimal
) {
    companion object {
        fun from(item: com.example.modulith.order.domain.model.OrderItem) = OrderItemResponse(
            productId = item.productId.value,
            productName = item.productName,
            quantity = item.quantity.value,
            unitPrice = item.unitPrice.amount,
            totalPrice = item.totalPrice.amount
        )
    }
}
