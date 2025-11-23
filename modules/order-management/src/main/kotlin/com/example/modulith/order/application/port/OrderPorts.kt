package com.example.modulith.order.application.port

import arrow.core.Either
import com.example.modulith.order.domain.model.Order
import com.example.modulith.order.domain.model.OrderId
import com.example.modulith.shared.domain.DomainError
import com.example.modulith.shared.domain.DomainEvent
import com.example.modulith.shared.event.IntegrationEvent

/**
 * Output port for Order repository
 */
interface OrderRepository {
    suspend fun save(order: Order): Either<DomainError, Unit>
    suspend fun findById(id: OrderId): Either<DomainError, Order?>
    suspend fun existsById(id: OrderId): Either<DomainError, Boolean>
}

/**
 * Output port for publishing domain events to event store
 */
interface DomainEventPublisher {
    suspend fun publish(
        aggregateId: OrderId,
        events: List<DomainEvent>
    ): Either<DomainError, Unit>
}

/**
 * Output port for publishing integration events
 */
interface IntegrationEventPublisher {
    suspend fun publish(event: IntegrationEvent): Either<DomainError, Unit>
    suspend fun publishAll(events: List<IntegrationEvent>): Either<DomainError, Unit>
}
