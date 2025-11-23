package com.example.modulith.infrastructure.messaging.spring

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.catch
import com.example.modulith.infrastructure.messaging.EventPublisher
import com.example.modulith.shared.domain.DomainError
import com.example.modulith.shared.event.EventEnvelope
import com.example.modulith.shared.event.IntegrationEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component

/**
 * Spring Application Events implementation of EventPublisher
 * Used in modulith mode for in-process event communication
 */
@Component
@ConditionalOnProperty(
    name = ["messaging.mode"],
    havingValue = "spring",
    matchIfMissing = true
)
class SpringEventPublisher(
    private val applicationEventPublisher: ApplicationEventPublisher
) : EventPublisher {

    override suspend fun publish(event: IntegrationEvent): Either<DomainError, Unit> = either {
        withContext(Dispatchers.Default) {
            catch({
                applicationEventPublisher.publishEvent(event)
            }) { e ->
                raise(DomainError.ValidationError("Failed to publish event: ${e.message}"))
            }
        }
    }

    override suspend fun publishAll(events: List<IntegrationEvent>): Either<DomainError, Unit> = either {
        events.forEach { event ->
            publish(event).bind()
        }
    }

    override suspend fun publishWithMetadata(
        envelope: EventEnvelope<out IntegrationEvent>
    ): Either<DomainError, Unit> = either {
        withContext(Dispatchers.Default) {
            catch({
                // Spring Events will carry metadata in the envelope
                applicationEventPublisher.publishEvent(envelope)
            }) { e ->
                raise(DomainError.ValidationError("Failed to publish event envelope: ${e.message}"))
            }
        }
    }
}
