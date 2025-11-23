package com.example.modulith.infrastructure.messaging.kafka

import arrow.core.Either
import arrow.core.raise.either
import com.example.modulith.infrastructure.messaging.EventPublisher
import com.example.modulith.shared.domain.DomainError
import com.example.modulith.shared.event.EventEnvelope
import com.example.modulith.shared.event.IntegrationEvent
import com.example.modulith.shared.functional.catchingMessaging
import com.fasterxml.jackson.databind.ObjectMapper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.future.await
import kotlinx.coroutines.withContext
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component

/**
 * Kafka implementation of EventPublisher
 * Used in microservices mode for inter-service communication
 */
@Component
@ConditionalOnProperty(
    name = ["messaging.mode"],
    havingValue = "kafka"
)
class KafkaEventPublisher(
    private val kafkaTemplate: KafkaTemplate<String, String>,
    private val objectMapper: ObjectMapper,
    private val topicResolver: KafkaTopicResolver
) : EventPublisher {

    override suspend fun publish(event: IntegrationEvent): Either<DomainError, Unit> =
        withContext(Dispatchers.IO) {
            catchingMessaging {
                val topic = topicResolver.resolve(event.eventType)
                val payload = objectMapper.writeValueAsString(event)

                kafkaTemplate.send(topic, event.eventId.toString(), payload)
                    .await()
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
        withContext(Dispatchers.IO) {
            catchingMessaging {
                val topic = topicResolver.resolve(envelope.eventType)
                val payload = objectMapper.writeValueAsString(envelope)

                kafkaTemplate.send(topic, envelope.eventId.toString(), payload)
                    .await()
            }
        }
}

/**
 * Resolves Kafka topic names from event types
 */
interface KafkaTopicResolver {
    fun resolve(eventType: String): String
}

@Component
class DefaultKafkaTopicResolver : KafkaTopicResolver {
    override fun resolve(eventType: String): String {
        // Convention: convert CamelCase to kebab-case and prefix with domain
        return "integration-events.${eventType.toKebabCase()}"
    }

    private fun String.toKebabCase(): String {
        return replace(Regex("([a-z])([A-Z])"), "$1-$2")
            .lowercase()
    }
}
