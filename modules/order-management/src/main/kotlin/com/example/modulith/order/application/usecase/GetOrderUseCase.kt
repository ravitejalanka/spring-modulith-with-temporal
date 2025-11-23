package com.example.modulith.order.application.usecase

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import com.example.modulith.order.application.port.OrderRepository
import com.example.modulith.order.domain.model.Order
import com.example.modulith.order.domain.model.OrderId
import com.example.modulith.shared.domain.DomainError
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * Query to get an order by ID
 */
data class GetOrderQuery(
    val orderId: UUID
)

/**
 * Use case for retrieving orders
 */
@Service
@Transactional(readOnly = true)
class GetOrderUseCase(
    private val orderRepository: OrderRepository
) {
    suspend fun execute(query: GetOrderQuery): Either<DomainError, Order> = either {
        val orderId = OrderId(query.orderId)

        val order = orderRepository.findById(orderId).bind()

        ensure(order != null) {
            DomainError.NotFoundError("Order", orderId.value.toString())
        }

        order
    }
}
