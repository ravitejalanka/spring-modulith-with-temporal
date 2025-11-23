package com.example.modulith.order.domain.model

import arrow.core.Either
import arrow.core.NonEmptyList
import arrow.core.raise.either
import arrow.core.raise.ensure
import com.example.modulith.order.domain.event.*
import com.example.modulith.shared.domain.*
import java.time.Instant
import java.util.UUID

/**
 * Order aggregate root following DDD and Event Sourcing patterns
 */
sealed class Order(
    open val id: OrderId,
    open val customerId: CustomerId,
    open val version: Long = 0
) : AggregateRoot<OrderId> {

    protected val _events: MutableList<OrderDomainEvent> = mutableListOf()
    override val events: List<OrderDomainEvent> get() = _events.toList()

    /**
     * Pending order - initial state
     */
    data class PendingOrder(
        override val id: OrderId,
        override val customerId: CustomerId,
        val items: NonEmptyList<OrderItem>,
        override val version: Long = 0
    ) : Order(id, customerId, version) {

        val totalAmount: Money = items.fold(Money.ZERO) { acc, item ->
            acc + item.totalPrice
        }

        fun confirm(): Either<OrderError, ConfirmedOrder> = either {
            ensure(items.isNotEmpty()) { OrderError.EmptyOrder }
            ensure(totalAmount > Money.ZERO) { OrderError.InvalidAmount }

            val confirmed = ConfirmedOrder(
                id = id,
                customerId = customerId,
                items = items,
                totalAmount = totalAmount,
                version = version
            )

            confirmed._events.add(
                OrderConfirmedEvent(
                    eventId = UUID.randomUUID(),
                    occurredAt = Instant.now(),
                    aggregateId = id.value,
                    orderId = id,
                    totalAmount = totalAmount
                )
            )

            confirmed
        }

        fun cancel(): Either<OrderError, CancelledOrder> = either {
            val cancelled = CancelledOrder(
                id = id,
                customerId = customerId,
                items = items,
                totalAmount = totalAmount,
                cancellationReason = "Cancelled before confirmation",
                version = version
            )

            cancelled._events.add(
                OrderCancelledEvent(
                    eventId = UUID.randomUUID(),
                    occurredAt = Instant.now(),
                    aggregateId = id.value,
                    orderId = id,
                    reason = "Cancelled before confirmation"
                )
            )

            cancelled
        }
    }

    /**
     * Confirmed order - waiting for payment
     */
    data class ConfirmedOrder(
        override val id: OrderId,
        override val customerId: CustomerId,
        val items: NonEmptyList<OrderItem>,
        val totalAmount: Money,
        override val version: Long = 0
    ) : Order(id, customerId, version) {

        fun markAsPaid(paymentId: PaymentId): Either<OrderError, PaidOrder> = either {
            val paid = PaidOrder(
                id = id,
                customerId = customerId,
                items = items,
                totalAmount = totalAmount,
                paymentId = paymentId,
                version = version
            )

            paid._events.add(
                OrderPaidEvent(
                    eventId = UUID.randomUUID(),
                    occurredAt = Instant.now(),
                    aggregateId = id.value,
                    orderId = id,
                    paymentId = paymentId,
                    amount = totalAmount
                )
            )

            paid
        }

        fun cancel(): Either<OrderError, CancelledOrder> = either {
            val cancelled = CancelledOrder(
                id = id,
                customerId = customerId,
                items = items,
                totalAmount = totalAmount,
                cancellationReason = "Cancelled after confirmation",
                version = version
            )

            cancelled._events.add(
                OrderCancelledEvent(
                    eventId = UUID.randomUUID(),
                    occurredAt = Instant.now(),
                    aggregateId = id.value,
                    orderId = id,
                    reason = "Cancelled after confirmation"
                )
            )

            cancelled
        }
    }

    /**
     * Paid order - ready for fulfillment
     */
    data class PaidOrder(
        override val id: OrderId,
        override val customerId: CustomerId,
        val items: NonEmptyList<OrderItem>,
        val totalAmount: Money,
        val paymentId: PaymentId,
        override val version: Long = 0
    ) : Order(id, customerId, version) {

        fun startFulfillment(): Either<OrderError, FulfillingOrder> = either {
            val fulfilling = FulfillingOrder(
                id = id,
                customerId = customerId,
                items = items,
                totalAmount = totalAmount,
                paymentId = paymentId,
                version = version
            )

            fulfilling._events.add(
                OrderFulfillmentStartedEvent(
                    eventId = UUID.randomUUID(),
                    occurredAt = Instant.now(),
                    aggregateId = id.value,
                    orderId = id
                )
            )

            fulfilling
        }
    }

    /**
     * Order being fulfilled
     */
    data class FulfillingOrder(
        override val id: OrderId,
        override val customerId: CustomerId,
        val items: NonEmptyList<OrderItem>,
        val totalAmount: Money,
        val paymentId: PaymentId,
        override val version: Long = 0
    ) : Order(id, customerId, version) {

        fun complete(): Either<OrderError, CompletedOrder> = either {
            val completed = CompletedOrder(
                id = id,
                customerId = customerId,
                items = items,
                totalAmount = totalAmount,
                paymentId = paymentId,
                version = version
            )

            completed._events.add(
                OrderCompletedEvent(
                    eventId = UUID.randomUUID(),
                    occurredAt = Instant.now(),
                    aggregateId = id.value,
                    orderId = id
                )
            )

            completed
        }
    }

    /**
     * Completed order - terminal state
     */
    data class CompletedOrder(
        override val id: OrderId,
        override val customerId: CustomerId,
        val items: NonEmptyList<OrderItem>,
        val totalAmount: Money,
        val paymentId: PaymentId,
        override val version: Long = 0
    ) : Order(id, customerId, version)

    /**
     * Cancelled order - terminal state
     */
    data class CancelledOrder(
        override val id: OrderId,
        override val customerId: CustomerId,
        val items: NonEmptyList<OrderItem>,
        val totalAmount: Money,
        val cancellationReason: String,
        override val version: Long = 0
    ) : Order(id, customerId, version)

    companion object {
        /**
         * Create a new pending order
         */
        fun create(
            customerId: CustomerId,
            items: NonEmptyList<OrderItem>
        ): Either<OrderError, PendingOrder> = either {
            ensure(items.isNotEmpty()) { OrderError.EmptyOrder }

            val orderId = OrderId.generate()
            val order = PendingOrder(
                id = orderId,
                customerId = customerId,
                items = items,
                version = 0
            )

            val totalAmount = items.fold(Money.ZERO) { acc, item -> acc + item.totalPrice }

            order._events.add(
                OrderCreatedEvent(
                    eventId = UUID.randomUUID(),
                    occurredAt = Instant.now(),
                    aggregateId = orderId.value,
                    orderId = orderId,
                    customerId = customerId,
                    items = items.toList(),
                    totalAmount = totalAmount
                )
            )

            order
        }

        /**
         * Reconstitute order from events (event sourcing)
         */
        fun fromEvents(events: List<OrderDomainEvent>): Either<OrderError, Order> = either {
            ensure(events.isNotEmpty()) { OrderError.NoEventsToReconstitute }

            var order: Order? = null
            var version = 0L

            events.forEach { event ->
                version++
                order = when (event) {
                    is OrderCreatedEvent -> PendingOrder(
                        id = event.orderId,
                        customerId = event.customerId,
                        items = NonEmptyList.fromListUnsafe(event.items),
                        version = version
                    )

                    is OrderConfirmedEvent -> (order as? PendingOrder)?.let {
                        ConfirmedOrder(
                            id = it.id,
                            customerId = it.customerId,
                            items = it.items,
                            totalAmount = it.totalAmount,
                            version = version
                        )
                    }

                    is OrderPaidEvent -> (order as? ConfirmedOrder)?.let {
                        PaidOrder(
                            id = it.id,
                            customerId = it.customerId,
                            items = it.items,
                            totalAmount = it.totalAmount,
                            paymentId = event.paymentId,
                            version = version
                        )
                    }

                    is OrderFulfillmentStartedEvent -> (order as? PaidOrder)?.let {
                        FulfillingOrder(
                            id = it.id,
                            customerId = it.customerId,
                            items = it.items,
                            totalAmount = it.totalAmount,
                            paymentId = it.paymentId,
                            version = version
                        )
                    }

                    is OrderCompletedEvent -> (order as? FulfillingOrder)?.let {
                        CompletedOrder(
                            id = it.id,
                            customerId = it.customerId,
                            items = it.items,
                            totalAmount = it.totalAmount,
                            paymentId = it.paymentId,
                            version = version
                        )
                    }

                    is OrderCancelledEvent -> order?.let {
                        when (it) {
                            is PendingOrder -> CancelledOrder(
                                id = it.id,
                                customerId = it.customerId,
                                items = it.items,
                                totalAmount = it.totalAmount,
                                cancellationReason = event.reason,
                                version = version
                            )

                            is ConfirmedOrder -> CancelledOrder(
                                id = it.id,
                                customerId = it.customerId,
                                items = it.items,
                                totalAmount = it.totalAmount,
                                cancellationReason = event.reason,
                                version = version
                            )

                            else -> it
                        }
                    }
                } ?: raise(OrderError.InvalidEventSequence)
            }

            order ?: raise(OrderError.FailedToReconstitute)
        }
    }
}

/**
 * Order item value object
 */
data class OrderItem(
    val productId: ProductId,
    val productName: String,
    val quantity: Quantity,
    val unitPrice: Money
) : ValueObject {
    val totalPrice: Money = unitPrice * quantity.value

    companion object {
        fun create(
            productId: ProductId,
            productName: String,
            quantity: Quantity,
            unitPrice: Money
        ): Either<OrderError, OrderItem> = either {
            ensure(productName.isNotBlank()) { OrderError.InvalidProductName }
            ensure(unitPrice > Money.ZERO) { OrderError.InvalidPrice }

            OrderItem(productId, productName, quantity, unitPrice)
        }
    }
}

/**
 * Strongly-typed IDs
 */
@JvmInline
value class OrderId(override val value: UUID) : EntityId(value) {
    companion object {
        fun generate() = OrderId(UUID.randomUUID())
        fun from(value: String) = Either.catch { OrderId(UUID.fromString(value)) }
            .mapLeft { DomainError.ValidationError("Invalid OrderId: $value") }
    }
}

@JvmInline
value class CustomerId(override val value: UUID) : EntityId(value) {
    companion object {
        fun generate() = CustomerId(UUID.randomUUID())
        fun from(value: String) = Either.catch { CustomerId(UUID.fromString(value)) }
            .mapLeft { DomainError.ValidationError("Invalid CustomerId: $value") }
    }
}

@JvmInline
value class ProductId(override val value: UUID) : EntityId(value) {
    companion object {
        fun generate() = ProductId(UUID.randomUUID())
        fun from(value: String) = Either.catch { ProductId(UUID.fromString(value)) }
            .mapLeft { DomainError.ValidationError("Invalid ProductId: $value") }
    }
}

@JvmInline
value class PaymentId(override val value: UUID) : EntityId(value) {
    companion object {
        fun generate() = PaymentId(UUID.randomUUID())
        fun from(value: String) = Either.catch { PaymentId(UUID.fromString(value)) }
            .mapLeft { DomainError.ValidationError("Invalid PaymentId: $value") }
    }
}

/**
 * Order-specific errors
 */
sealed interface OrderError : DomainError {
    data object EmptyOrder : OrderError {
        override val message = "Order must contain at least one item"
    }

    data object InvalidAmount : OrderError {
        override val message = "Order amount must be greater than zero"
    }

    data object InvalidProductName : OrderError {
        override val message = "Product name cannot be blank"
    }

    data object InvalidPrice : OrderError {
        override val message = "Price must be greater than zero"
    }

    data object NoEventsToReconstitute : OrderError {
        override val message = "Cannot reconstitute order without events"
    }

    data object InvalidEventSequence : OrderError {
        override val message = "Invalid event sequence for order reconstitution"
    }

    data object FailedToReconstitute : OrderError {
        override val message = "Failed to reconstitute order from events"
    }
}
