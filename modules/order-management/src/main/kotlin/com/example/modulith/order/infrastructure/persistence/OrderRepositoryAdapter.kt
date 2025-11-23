package com.example.modulith.order.infrastructure.persistence

import arrow.core.Either
import arrow.core.raise.either
import com.example.modulith.infrastructure.eventstore.EventStore
import com.example.modulith.order.application.port.DomainEventPublisher
import com.example.modulith.order.application.port.OrderRepository
import com.example.modulith.order.domain.event.OrderDomainEvent
import com.example.modulith.order.domain.model.Order
import com.example.modulith.order.domain.model.OrderId
import com.example.modulith.shared.domain.DomainError
import com.example.modulith.shared.domain.DomainEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.stereotype.Repository

/**
 * Event-sourced repository adapter for Order aggregate
 */
@Repository
class EventSourcedOrderRepository(
    private val eventStore: EventStore
) : OrderRepository {

    companion object {
        private const val AGGREGATE_TYPE = "Order"
    }

    override suspend fun save(order: Order): Either<DomainError, Unit> = withContext(Dispatchers.IO) {
        either {
            if (order.events.isEmpty()) {
                return@either
            }

            eventStore.save(
                aggregateId = order.id.value,
                aggregateType = AGGREGATE_TYPE,
                events = order.events,
                expectedVersion = order.version
            ).bind()
        }
    }

    override suspend fun findById(id: OrderId): Either<DomainError, Order?> = withContext(Dispatchers.IO) {
        either {
            val events = eventStore.load(
                aggregateId = id.value,
                aggregateType = AGGREGATE_TYPE
            ).bind()

            if (events.isEmpty()) {
                return@either null
            }

            @Suppress("UNCHECKED_CAST")
            val orderEvents = events as List<OrderDomainEvent>
            Order.fromEvents(orderEvents).bind()
        }
    }

    override suspend fun existsById(id: OrderId): Either<DomainError, Boolean> = withContext(Dispatchers.IO) {
        either {
            val version = eventStore.getVersion(
                aggregateId = id.value,
                aggregateType = AGGREGATE_TYPE
            ).bind()

            version > 0
        }
    }
}

/**
 * Domain event publisher adapter
 */
@Repository
class EventStoreDomainEventPublisher(
    private val eventStore: EventStore
) : DomainEventPublisher {

    override suspend fun publish(
        aggregateId: OrderId,
        events: List<DomainEvent>
    ): Either<DomainError, Unit> = either {
        if (events.isEmpty()) {
            return@either
        }

        // Get current version
        val currentVersion = eventStore.getVersion(
            aggregateId = aggregateId.value,
            aggregateType = "Order"
        ).bind()

        // Save events
        eventStore.save(
            aggregateId = aggregateId.value,
            aggregateType = "Order",
            events = events,
            expectedVersion = currentVersion
        ).bind()
    }
}
