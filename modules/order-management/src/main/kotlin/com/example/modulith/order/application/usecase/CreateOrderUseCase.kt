package com.example.modulith.order.application.usecase

import arrow.core.Either
import arrow.core.NonEmptyList
import arrow.core.raise.either
import arrow.core.raise.ensure
import com.example.modulith.order.application.handler.OrderCommandHandler
import com.example.modulith.order.domain.command.OrderCommand
import com.example.modulith.order.domain.model.*
import com.example.modulith.shared.domain.DomainError
import com.example.modulith.shared.domain.Money
import com.example.modulith.shared.domain.Quantity
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
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
 *
 * Now uses the CommandHandler which follows the f{model} pattern:
 * - Commands represent intentions
 * - Decider makes decisions (pure function)
 * - Events are stored in Temporal
 * - State is evolved from events
 */
@Service
@Transactional
class CreateOrderUseCase(
    private val commandHandler: OrderCommandHandler
) {
    suspend fun execute(command: CreateOrderCommand): Either<DomainError, OrderId> = either {
        // 1. Validate and map command to domain command
        val orderId = OrderId.generate()
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

        // 2. Create domain command
        val createCommand = OrderCommand.CreateOrder(
            orderId = orderId,
            customerId = customerId,
            items = itemsNel
        )

        // 3. Handle command using CommandHandler (which uses Decider pattern)
        // This will:
        // - Load current state
        // - Use Decider to decide events
        // - Store events in Temporal
        // - Publish integration events
        commandHandler.handle(createCommand).bind()

        // 4. Optionally confirm the order immediately (simplified flow)
        val confirmCommand = OrderCommand.ConfirmOrder(orderId = orderId)
        commandHandler.handle(confirmCommand).bind()

        orderId
    }
}
