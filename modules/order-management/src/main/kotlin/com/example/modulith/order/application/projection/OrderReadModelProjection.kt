package com.example.modulith.order.application.projection

import arrow.core.Either
import arrow.core.raise.either
import com.example.modulith.order.domain.event.*
import com.example.modulith.shared.domain.DomainError
import com.example.modulith.shared.domain.Money
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Order Read Model - Optimized for QUERIES
 *
 * This is separate from the write model (OrderState/Order aggregate)
 * Built from events using the projection pattern
 *
 * Benefits:
 * - Fast queries (no event replay needed)
 * - Custom views for different use cases
 * - Can be rebuilt anytime from events
 * - Decoupled from write model
 */
data class OrderReadModel(
    val orderId: UUID,
    val customerId: UUID,
    val status: String,
    val totalAmount: Money,
    val itemCount: Int,
    val createdAt: Instant,
    val updatedAt: Instant,

    // Fields useful for queries
    val isPaid: Boolean = false,
    val isShipped: Boolean = false,
    val isCancelled: Boolean = false,

    // Metadata
    val version: Long = 0
)

/**
 * Projection that builds OrderReadModel from events
 *
 * This is the PROJECTION PATTERN:
 * - Listens to domain events
 * - Builds optimized read models
 * - Stores in database (or cache, or search index)
 * - Completely decoupled from command side
 */
@Component
class OrderReadModelProjection : OrderProjection {

    // In-memory store (in real app, use database)
    private val readModels = ConcurrentHashMap<UUID, OrderReadModel>()

    /**
     * Project event into read model
     *
     * Pattern:
     * Event → Update Read Model → Store
     */
    override suspend fun project(event: OrderDomainEvent): Either<DomainError, Unit> = either {
        when (event) {
            is OrderCreatedEvent -> projectOrderCreated(event)
            is OrderConfirmedEvent -> projectOrderConfirmed(event)
            is OrderPaidEvent -> projectOrderPaid(event)
            is OrderFulfillmentStartedEvent -> projectFulfillmentStarted(event)
            is OrderCompletedEvent -> projectOrderCompleted(event)
            is OrderCancelledEvent -> projectOrderCancelled(event)
        }
    }

    /**
     * Query methods - Read side
     */
    fun findById(orderId: UUID): OrderReadModel? = readModels[orderId]

    fun findByCustomerId(customerId: UUID): List<OrderReadModel> =
        readModels.values.filter { it.customerId == customerId }

    fun findByStatus(status: String): List<OrderReadModel> =
        readModels.values.filter { it.status == status }

    fun findUnpaidOrders(): List<OrderReadModel> =
        readModels.values.filter { !it.isPaid && !it.isCancelled }

    fun findShippedOrders(): List<OrderReadModel> =
        readModels.values.filter { it.isShipped }

    // Projection handlers (private)

    private fun projectOrderCreated(event: OrderCreatedEvent) {
        val readModel = OrderReadModel(
            orderId = event.orderId.value,
            customerId = event.customerId.value,
            status = "PENDING",
            totalAmount = event.totalAmount,
            itemCount = event.items.size,
            createdAt = event.occurredAt,
            updatedAt = event.occurredAt,
            version = 1
        )
        readModels[event.orderId.value] = readModel
    }

    private fun projectOrderConfirmed(event: OrderConfirmedEvent) {
        readModels.compute(event.orderId.value) { _, existing ->
            existing?.copy(
                status = "CONFIRMED",
                updatedAt = event.occurredAt,
                version = existing.version + 1
            )
        }
    }

    private fun projectOrderPaid(event: OrderPaidEvent) {
        readModels.compute(event.orderId.value) { _, existing ->
            existing?.copy(
                status = "PAID",
                isPaid = true,
                updatedAt = event.occurredAt,
                version = existing.version + 1
            )
        }
    }

    private fun projectFulfillmentStarted(event: OrderFulfillmentStartedEvent) {
        readModels.compute(event.orderId.value) { _, existing ->
            existing?.copy(
                status = "FULFILLING",
                updatedAt = event.occurredAt,
                version = existing.version + 1
            )
        }
    }

    private fun projectOrderCompleted(event: OrderCompletedEvent) {
        readModels.compute(event.orderId.value) { _, existing ->
            existing?.copy(
                status = "COMPLETED",
                isShipped = true,
                updatedAt = event.occurredAt,
                version = existing.version + 1
            )
        }
    }

    private fun projectOrderCancelled(event: OrderCancelledEvent) {
        readModels.compute(event.orderId.value) { _, existing ->
            existing?.copy(
                status = "CANCELLED",
                isCancelled = true,
                updatedAt = event.occurredAt,
                version = existing.version + 1
            )
        }
    }
}

/**
 * Example: Analytics Projection
 *
 * This is another projection for different use case
 */
@Component
class OrderAnalyticsProjection : OrderProjection {

    private var totalOrders = 0
    private var totalRevenue = Money.ZERO
    private var cancelledOrders = 0

    override suspend fun project(event: OrderDomainEvent): Either<DomainError, Unit> = either {
        when (event) {
            is OrderCreatedEvent -> {
                totalOrders++
                totalRevenue += event.totalAmount
            }
            is OrderCancelledEvent -> {
                cancelledOrders++
            }
            else -> { /* ignore other events */ }
        }
    }

    // Analytics queries
    fun getTotalOrders() = totalOrders
    fun getTotalRevenue() = totalRevenue
    fun getCancellationRate() = if (totalOrders > 0) cancelledOrders.toDouble() / totalOrders else 0.0
}
