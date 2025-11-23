package com.example.modulith.order.application.usecase

import arrow.core.Either
import arrow.core.NonEmptyList
import arrow.core.raise.either
import arrow.core.raise.ensure
import com.example.modulith.order.application.port.DomainEventPublisher
import com.example.modulith.order.application.port.IntegrationEventPublisher
import com.example.modulith.order.application.port.OrderRepository
import com.example.modulith.order.domain.event.OrderItemDto
import com.example.modulith.order.domain.event.OrderPlacedIntegrationEvent
import com.example.modulith.order.domain.model.*
import com.example.modulith.shared.domain.DomainError
import com.example.modulith.shared.domain.Money
import com.example.modulith.shared.domain.Quantity
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

/**
 * Command to create a new order
 */
data class CreateOrderCommand(
    val customerId: UUID,
    val items: List<CreateOrderItemDto>
)

data class CreateOrderItemDto(
    val productId: UUID,
    val productName: String,
    val quantity: Int,
    val unitPrice: String
)

/**
 * Use case for creating orders
 */
@Service
@Transactional
class CreateOrderUseCase(
    private val orderRepository: OrderRepository,
    private val domainEventPublisher: DomainEventPublisher,
    private val integrationEventPublisher: IntegrationEventPublisher
) {
    suspend fun execute(command: CreateOrderCommand): Either<DomainError, OrderId> = either {
        // Validate and map command
        val customerId = CustomerId(command.customerId)

        ensure(command.items.isNotEmpty()) {
            DomainError.ValidationError("Order must contain at least one item")
        }

        val orderItems = command.items.map { dto ->
            val productId = ProductId(dto.productId)
            val quantity = Quantity.of(dto.quantity).bind()
            val unitPrice = Money.of(dto.unitPrice).bind()

            OrderItem.create(
                productId = productId,
                productName = dto.productName,
                quantity = quantity,
                unitPrice = unitPrice
            ).bind()
        }

        val itemsNel = NonEmptyList.fromListUnsafe(orderItems)

        // Create order aggregate
        val pendingOrder = Order.create(customerId, itemsNel).bind()

        // Confirm order immediately (simplified flow)
        val confirmedOrder = pendingOrder.confirm().bind()

        // Persist domain events to event store
        domainEventPublisher.publish(
            confirmedOrder.id,
            confirmedOrder.events
        ).bind()

        // Publish integration event for other modules
        val integrationEvent = OrderPlacedIntegrationEvent(
            eventId = UUID.randomUUID(),
            occurredAt = Instant.now(),
            orderId = confirmedOrder.id,
            customerId = confirmedOrder.customerId,
            items = confirmedOrder.items.map { OrderItemDto.from(it) },
            totalAmount = confirmedOrder.totalAmount
        )

        integrationEventPublisher.publish(integrationEvent).bind()

        confirmedOrder.id
    }
}
