package com.example.modulith.infrastructure.messaging.spring

import arrow.core.Either
import arrow.core.raise.either
import com.example.modulith.infrastructure.messaging.EventPublisher
import com.example.modulith.shared.domain.DomainError
import com.example.modulith.shared.event.EventEnvelope
import com.example.modulith.shared.event.IntegrationEvent
import com.example.modulith.shared.functional.catchingMessaging
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

    override suspend fun publish(event: IntegrationEvent): Either<DomainError, Unit> =
        withContext(Dispatchers.Default) {
            catchingMessaging {
                applicationEventPublisher.publishEvent(event)
            }
        }

    override suspend fun publishAll(events: List<IntegrationEvent>): Either<DomainError, Unit> = either {
        events.forEach { event ->
            publish(event).bind()
        }
    }

    override suspend fun publishWithMetadata(
        envelope: EventEnvelope<out IntegrationEvent>
    ): Either<DomainError, Unit> =
        withContext(Dispatchers.Default) {
            catchingMessaging {
                // Spring Events will carry metadata in the envelope
                applicationEventPublisher.publishEvent(envelope)
            }
        }
}
