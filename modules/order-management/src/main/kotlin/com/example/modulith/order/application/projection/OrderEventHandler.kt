package com.example.modulith.order.application.projection

import com.example.modulith.order.domain.event.*
import com.example.modulith.order.domain.model.OrderId
import com.example.modulith.shared.domain.DomainError
import arrow.core.Either

/**
 * Event Handler - Reacts to domain events in a decoupled manner
 *
 * This is the PROJECTION pattern - building read models from events
 * Decouples write side (commands/events) from read side (queries)
 *
 * Benefits:
 * - Decoupled: Event handlers don't know about command handlers
 * - Scalable: Can have multiple projections for different views
 * - CQRS: Separate read and write models
 * - Flexible: Easy to add new projections without changing domain
 */
interface OrderEventHandler {
    suspend fun handle(event: OrderDomainEvent): Either<DomainError, Unit>
}

/**
 * Projection builder - builds read models from events
 *
 * Use cases:
 * - Build materialized views for queries
 * - Update search indexes
 * - Send notifications
 * - Update analytics
 * - Build custom dashboards
 */
interface OrderProjection {
    suspend fun project(event: OrderDomainEvent): Either<DomainError, Unit>
}
