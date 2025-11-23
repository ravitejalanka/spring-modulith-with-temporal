package com.example.modulith.infrastructure.eventstore

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import com.example.modulith.shared.domain.DomainError
import com.example.modulith.shared.domain.DomainEvent
import com.example.modulith.shared.functional.catchingDatabase
import com.example.modulith.shared.functional.catchingSerialization
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import jakarta.persistence.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

/**
 * JPA Entity for event store
 */
@Entity
@Table(
    name = "event_store",
    indexes = [
        Index(name = "idx_aggregate", columnList = "aggregateId,version")
    ],
    uniqueConstraints = [
        UniqueConstraint(name = "uk_aggregate_version", columnNames = ["aggregateId", "version"])
    ]
)
class EventStoreEntity(
    @Id
    @Column(columnDefinition = "UUID")
    val eventId: UUID,

    @Column(nullable = false, columnDefinition = "UUID")
    val aggregateId: UUID,

    @Column(nullable = false, length = 255)
    val aggregateType: String,

    @Column(nullable = false, length = 255)
    val eventType: String,

    @Column(nullable = false, columnDefinition = "TEXT")
    val eventData: String,

    @Column(columnDefinition = "TEXT")
    val metadata: String? = null,

    @Column(nullable = false)
    val version: Long,

    @Column(nullable = false)
    val occurredAt: Instant
)

/**
 * JPA Repository for event store
 */
@Repository
interface EventStoreJpaRepository : org.springframework.data.jpa.repository.JpaRepository<EventStoreEntity, UUID> {
    fun findByAggregateIdAndAggregateTypeOrderByVersionAsc(
        aggregateId: UUID,
        aggregateType: String
    ): List<EventStoreEntity>

    fun findByAggregateIdAndAggregateTypeAndVersionGreaterThanEqualOrderByVersionAsc(
        aggregateId: UUID,
        aggregateType: String,
        fromVersion: Long
    ): List<EventStoreEntity>

    fun findFirstByAggregateIdAndAggregateTypeOrderByVersionDesc(
        aggregateId: UUID,
        aggregateType: String
    ): EventStoreEntity?
}

/**
 * JPA implementation of EventStore
 */
@Repository
@Transactional
class JpaEventStore(
    private val repository: EventStoreJpaRepository,
    private val objectMapper: ObjectMapper,
    private val eventRegistry: EventRegistry
) : EventStore {

    override suspend fun save(
        aggregateId: UUID,
        aggregateType: String,
        events: List<DomainEvent>,
        expectedVersion: Long
    ): Either<DomainError, Unit> = withContext(Dispatchers.IO) {
        either {
            ensure(events.isNotEmpty()) {
                DomainError.ValidationError("Cannot save empty event list")
            }

            val currentVersion = repository.findFirstByAggregateIdAndAggregateTypeOrderByVersionDesc(
                aggregateId, aggregateType
            )?.version ?: 0L

            ensure(currentVersion == expectedVersion) {
                DomainError.ConcurrencyError(
                    "Expected version $expectedVersion but found $currentVersion for aggregate $aggregateId"
                )
            }

            val entities = events.mapIndexed { index, event ->
                EventStoreEntity(
                    eventId = event.eventId,
                    aggregateId = aggregateId,
                    aggregateType = aggregateType,
                    eventType = event::class.java.simpleName,
                    eventData = objectMapper.writeValueAsString(event),
                    version = expectedVersion + index + 1,
                    occurredAt = event.occurredAt
                )
            }

            catchingDatabase {
                repository.saveAll(entities)
            }.bind()
        }
    }

    override suspend fun load(
        aggregateId: UUID,
        aggregateType: String
    ): Either<DomainError, List<DomainEvent>> = withContext(Dispatchers.IO) {
        either {
            val entities = catchingDatabase {
                repository.findByAggregateIdAndAggregateTypeOrderByVersionAsc(
                    aggregateId, aggregateType
                )
            }.bind()

            entities.map { entity ->
                deserializeEvent(entity).bind()
            }
        }
    }

    override suspend fun loadFrom(
        aggregateId: UUID,
        aggregateType: String,
        fromVersion: Long
    ): Either<DomainError, List<DomainEvent>> = withContext(Dispatchers.IO) {
        either {
            val entities = catchingDatabase {
                repository.findByAggregateIdAndAggregateTypeAndVersionGreaterThanEqualOrderByVersionAsc(
                    aggregateId, aggregateType, fromVersion
                )
            }.bind()

            entities.map { entity ->
                deserializeEvent(entity).bind()
            }
        }
    }

    override suspend fun getVersion(
        aggregateId: UUID,
        aggregateType: String
    ): Either<DomainError, Long> = withContext(Dispatchers.IO) {
        either {
            catchingDatabase {
                repository.findFirstByAggregateIdAndAggregateTypeOrderByVersionDesc(
                    aggregateId, aggregateType
                )?.version ?: 0L
            }.bind()
        }
    }

    private fun deserializeEvent(entity: EventStoreEntity): Either<DomainError, DomainEvent> = either {
        val eventClass = eventRegistry.getEventClass(entity.eventType)
            ?: raise(DomainError.ValidationError("Unknown event type: ${entity.eventType}"))

        catchingSerialization {
            objectMapper.readValue(entity.eventData, eventClass)
        }.bind()
    }
}

/**
 * Registry to map event type names to classes
 */
interface EventRegistry {
    fun register(eventType: String, eventClass: Class<out DomainEvent>)
    fun getEventClass(eventType: String): Class<out DomainEvent>?
}

@Repository
class InMemoryEventRegistry : EventRegistry {
    private val registry = mutableMapOf<String, Class<out DomainEvent>>()

    override fun register(eventType: String, eventClass: Class<out DomainEvent>) {
        registry[eventType] = eventClass
    }

    override fun getEventClass(eventType: String): Class<out DomainEvent>? {
        return registry[eventType]
    }
}
