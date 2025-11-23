package com.example.modulith.infrastructure.eventstore

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import arrow.core.raise.catch
import com.example.modulith.shared.domain.DomainError
import com.example.modulith.shared.domain.DomainEvent
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import jakarta.persistence.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.dao.DataIntegrityViolationException
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

            catch({
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

                repository.saveAll(entities)
            }) { e ->
                when (e) {
                    is DataIntegrityViolationException -> raise(
                        DomainError.ConcurrencyError(
                            "Concurrency conflict saving events for aggregate $aggregateId: ${e.message}"
                        )
                    )
                    else -> raise(DomainError.ValidationError("Error saving events: ${e.message}"))
                }
            }
        }
    }

    override suspend fun load(
        aggregateId: UUID,
        aggregateType: String
    ): Either<DomainError, List<DomainEvent>> = withContext(Dispatchers.IO) {
        either {
            catch({
                val entities = repository.findByAggregateIdAndAggregateTypeOrderByVersionAsc(
                    aggregateId, aggregateType
                )

                entities.map { entity ->
                    deserializeEvent(entity).bind()
                }
            }) { e ->
                raise(DomainError.ValidationError("Error loading events: ${e.message}"))
            }
        }
    }

    override suspend fun loadFrom(
        aggregateId: UUID,
        aggregateType: String,
        fromVersion: Long
    ): Either<DomainError, List<DomainEvent>> = withContext(Dispatchers.IO) {
        either {
            catch({
                val entities = repository.findByAggregateIdAndAggregateTypeAndVersionGreaterThanEqualOrderByVersionAsc(
                    aggregateId, aggregateType, fromVersion
                )

                entities.map { entity ->
                    deserializeEvent(entity).bind()
                }
            }) { e ->
                raise(DomainError.ValidationError("Error loading events: ${e.message}"))
            }
        }
    }

    override suspend fun getVersion(
        aggregateId: UUID,
        aggregateType: String
    ): Either<DomainError, Long> = withContext(Dispatchers.IO) {
        either {
            catch({
                repository.findFirstByAggregateIdAndAggregateTypeOrderByVersionDesc(
                    aggregateId, aggregateType
                )?.version ?: 0L
            }) { e ->
                raise(DomainError.ValidationError("Error getting version: ${e.message}"))
            }
        }
    }

    private fun deserializeEvent(entity: EventStoreEntity): Either<DomainError, DomainEvent> = either {
        val eventClass = eventRegistry.getEventClass(entity.eventType)
            ?: raise(DomainError.ValidationError("Unknown event type: ${entity.eventType}"))

        catch({
            objectMapper.readValue(entity.eventData, eventClass)
        }) { e ->
            raise(DomainError.ValidationError("Error deserializing event: ${e.message}"))
        }
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
