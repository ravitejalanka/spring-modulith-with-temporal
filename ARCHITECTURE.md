# Architecture Design: Event-Driven Modulith with Microservices Flexibility

## Overview

This project demonstrates a modern, production-ready architecture that combines:
- **Spring Modulith** for modular monolith structure
- **Hexagonal Architecture** for clean separation of concerns
- **Domain-Driven Design** for rich domain modeling
- **Event Sourcing** for audit trail and temporal queries
- **Event-Driven Architecture** for loose coupling
- **Temporal.io** for reliable workflow orchestration
- **Arrow-kt** for functional programming patterns
- **Kotlin Coroutines** for reactive/async operations

## Core Architectural Principles

### 1. Deployment Flexibility

The system can run in two modes:

#### Modulith Mode (Default)
- All modules run in a single JVM process
- Uses Spring Application Events for inter-module communication
- Lower operational complexity, better performance
- Ideal for: startups, low-traffic systems, development

#### Microservices Mode
- Each module runs as independent service
- Uses Kafka for inter-service communication
- Independent scaling and deployment
- Ideal for: high traffic, team autonomy, polyglot requirements

**Switch via Spring Profile**: `modulith` or `microservices`

### 2. Module Structure (Bounded Contexts)

```
order-management/
├── domain/           # Pure business logic
│   ├── model/       # Aggregates, Entities, Value Objects
│   ├── event/       # Domain Events
│   └── repository/  # Repository interfaces (ports)
├── application/      # Use cases and orchestration
│   ├── usecase/     # Command and Query handlers
│   ├── port/        # Input/Output ports
│   └── service/     # Application services
├── infrastructure/   # Technical implementations
│   ├── persistence/ # Event Store, Projections, Repositories
│   ├── messaging/   # Event publishers/subscribers
│   └── config/      # Module configuration
└── api/             # REST API
    ├── controller/
    └── dto/

payment/
├── domain/
├── application/
├── infrastructure/
└── api/

fulfillment/
├── domain/
├── application/
├── infrastructure/
└── api/

shared-kernel/        # Shared concepts across contexts
├── domain/          # Common value objects, interfaces
├── event/           # Event infrastructure
└── functional/      # Arrow-kt extensions

temporal-workflows/   # Workflow orchestration
├── workflow/        # Workflow definitions
├── activity/        # Activity implementations
└── config/          # Temporal configuration
```

### 3. Hexagonal Architecture per Module

```
                    ┌─────────────────────┐
                    │   API Layer (REST)  │
                    │   Controllers/DTOs  │
                    └──────────┬──────────┘
                               │
                    ┌──────────▼──────────┐
                    │  Application Layer  │
                    │  Use Cases / Ports  │
                    └──────────┬──────────┘
                               │
                    ┌──────────▼──────────┐
                    │   Domain Layer      │
                    │  Aggregates/Events  │
                    └──────────┬──────────┘
                               │
    ┌──────────────┬───────────┴────────┬──────────────┐
    │              │                    │              │
┌───▼────┐  ┌──────▼──────┐  ┌─────────▼─────┐  ┌────▼────┐
│Database│  │Event Store  │  │  Messaging    │  │External │
│Adapter │  │  Adapter    │  │   Adapter     │  │ APIs    │
└────────┘  └─────────────┘  └───────────────┘  └─────────┘
```

### 4. Event-Driven Communication

#### Event Types

1. **Domain Events** (internal to aggregate)
   - Example: `OrderCreatedEvent`, `PaymentProcessedEvent`
   - Stored in event store
   - Used for event sourcing

2. **Integration Events** (cross-module)
   - Example: `OrderPlacedIntegrationEvent`
   - Published to event bus (Spring Events or Kafka)
   - Consumed by other modules

#### Event Abstraction

```kotlin
interface EventPublisher {
    suspend fun publish(event: IntegrationEvent)
    suspend fun publishAll(events: List<IntegrationEvent>)
}

// Modulith mode
class SpringEventPublisher : EventPublisher {
    // Uses ApplicationEventPublisher
}

// Microservices mode
class KafkaEventPublisher : EventPublisher {
    // Uses KafkaTemplate
}
```

### 5. Event Sourcing Pattern

#### Event Store Schema

```sql
CREATE TABLE event_store (
    event_id UUID PRIMARY KEY,
    aggregate_id UUID NOT NULL,
    aggregate_type VARCHAR(255) NOT NULL,
    event_type VARCHAR(255) NOT NULL,
    event_data JSONB NOT NULL,
    metadata JSONB,
    version INT NOT NULL,
    occurred_at TIMESTAMP NOT NULL,
    UNIQUE (aggregate_id, version)
);

CREATE INDEX idx_aggregate ON event_store(aggregate_id, version);
```

#### Read Model Projections

```sql
CREATE TABLE order_projection (
    order_id UUID PRIMARY KEY,
    customer_id UUID NOT NULL,
    status VARCHAR(50) NOT NULL,
    total_amount DECIMAL(10,2),
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);
```

### 6. Temporal.io Workflow Orchestration

#### Order Fulfillment Workflow

```kotlin
@WorkflowInterface
interface OrderFulfillmentWorkflow {
    @WorkflowMethod
    suspend fun processOrder(orderId: OrderId): Either<OrderError, OrderResult>
}

// Implementation coordinates:
// 1. Validate Order
// 2. Process Payment (with compensation)
// 3. Reserve Inventory (with compensation)
// 4. Initiate Shipping
// 5. Complete Order or Rollback
```

#### Benefits
- Automatic retries with exponential backoff
- Durable execution (survives crashes)
- Built-in compensation for saga pattern
- Observable workflow state
- Time-based scheduling

### 7. Domain-Driven Design Patterns

#### Aggregates
```kotlin
sealed class Order private constructor(
    val id: OrderId,
    val customerId: CustomerId,
    private val _events: MutableList<OrderEvent> = mutableListOf()
) {
    val events: List<OrderEvent> get() = _events.toList()

    data class PendingOrder(
        val id: OrderId,
        val customerId: CustomerId,
        val items: NonEmptyList<OrderItem>,
        val totalAmount: Money
    ) : Order(id, customerId) {

        fun confirm(): Either<OrderError, ConfirmedOrder> = either {
            // Business validation
            ensureNot(items.isEmpty()) { OrderError.EmptyOrder }

            val confirmed = ConfirmedOrder(id, customerId, items, totalAmount)
            _events.add(OrderConfirmedEvent(id, Instant.now()))
            confirmed
        }
    }

    data class ConfirmedOrder(...) : Order(...)
    data class CancelledOrder(...) : Order(...)
}
```

#### Value Objects (Arrow-kt)
```kotlin
@JvmInline
value class OrderId(val value: UUID) {
    companion object {
        fun generate(): OrderId = OrderId(UUID.randomUUID())
    }
}

@JvmInline
value class Money(val amount: BigDecimal) {
    operator fun plus(other: Money) = Money(amount + other.amount)

    companion object {
        fun of(amount: String): Either<MoneyError, Money> = either {
            val decimal = amount.toBigDecimalOrNull()
                ?: raise(MoneyError.InvalidFormat)
            ensure(decimal >= BigDecimal.ZERO) { MoneyError.NegativeAmount }
            Money(decimal)
        }
    }
}
```

### 8. Arrow-kt Functional Patterns

#### Error Handling with Either
```kotlin
suspend fun createOrder(command: CreateOrderCommand): Either<OrderError, OrderId> = either {
    // Validate
    val customerId = CustomerId(command.customerId)
    val items = command.items.toNonEmptyListOrNull()
        ?: raise(OrderError.EmptyOrder)

    // Create aggregate
    val order = Order.create(customerId, items).bind()

    // Persist events
    eventStore.save(order.events).bind()

    // Publish integration event
    eventPublisher.publish(OrderCreatedIntegrationEvent(order.id)).bind()

    order.id
}
```

#### Validation with Validated
```kotlin
data class CreateOrderCommand(
    val customerId: UUID,
    val items: List<OrderItemDto>
)

fun validateCreateOrder(cmd: CreateOrderCommand): ValidatedNel<ValidationError, ValidatedOrder> =
    zipOrAccumulate(
        validateCustomerId(cmd.customerId),
        validateItems(cmd.items),
        validateTotalAmount(cmd.items)
    ) { customerId, items, total ->
        ValidatedOrder(customerId, items, total)
    }
```

### 9. Technology Stack

#### Core
- **Kotlin** 2.0+
- **Spring Boot** 3.2+
- **Spring Modulith** 1.1+
- **Kotlin Coroutines** 1.8+
- **Arrow-kt** 1.2+

#### Workflow Orchestration
- **Temporal SDK** (Java/Kotlin)

#### Data
- **PostgreSQL** (event store, projections)
- **Spring Data JPA/R2DBC**
- **Flyway** (migrations)

#### Messaging
- **Spring Application Events** (modulith mode)
- **Apache Kafka** (microservices mode)

#### Observability
- **Spring Boot Actuator**
- **Micrometer** (metrics)
- **OpenTelemetry** (distributed tracing)

#### Build
- **Gradle** 8+ (Kotlin DSL)

### 10. Project Structure

```
spring-modulith-temporal/
├── modules/
│   ├── order-management/
│   ├── payment/
│   ├── fulfillment/
│   ├── shared-kernel/
│   └── temporal-workflows/
├── applications/
│   ├── modulith-app/        # Single application
│   ├── order-service/       # Microservice runner
│   ├── payment-service/     # Microservice runner
│   └── fulfillment-service/ # Microservice runner
├── infrastructure/
│   ├── event-store/         # Event sourcing infrastructure
│   ├── messaging/           # Event bus abstraction
│   └── temporal-config/     # Temporal setup
├── docker/
│   ├── docker-compose.yml   # Full stack (Postgres, Kafka, Temporal)
│   └── temporal/            # Temporal server config
├── gradle/
├── build.gradle.kts
└── settings.gradle.kts
```

### 11. Key Design Decisions

#### Why Spring Modulith?
- Enforces module boundaries at compile time
- Supports gradual extraction to microservices
- Built-in module documentation and testing
- Event publication registry

#### Why Temporal.io?
- Durable execution (workflow survives crashes)
- Built-in retries and timeouts
- Easy compensation logic (saga pattern)
- Workflow as code (no external workflow engines)
- Time-based operations (delays, schedules)

#### Why Event Sourcing?
- Complete audit trail
- Temporal queries (state at any point in time)
- Easy to add new projections
- Natural fit with event-driven architecture

#### Why Arrow-kt?
- Type-safe error handling (`Either`, `Validated`)
- Immutability helpers
- Functional composition
- Railway-oriented programming

#### Why Kotlin Coroutines?
- Lightweight concurrency
- Structured concurrency
- Integration with Spring WebFlux
- Natural async/await syntax

### 12. Example Flows

#### Create Order Flow (Modulith Mode)

```
1. POST /api/orders
2. OrderController → CreateOrderUseCase
3. CreateOrderUseCase:
   - Validate command
   - Create Order aggregate
   - Save events to event store
   - Publish OrderCreatedIntegrationEvent (Spring Events)
4. PaymentModule listens to OrderCreatedIntegrationEvent
5. Start Temporal workflow: OrderFulfillmentWorkflow
6. Workflow:
   - Activity: ProcessPayment
   - Activity: ReserveInventory
   - Activity: InitiateShipping
7. Return order ID to client
```

#### Create Order Flow (Microservices Mode)

```
1. POST /api/orders (to order-service)
2. OrderController → CreateOrderUseCase
3. CreateOrderUseCase:
   - Validate command
   - Create Order aggregate
   - Save events to event store
   - Publish OrderCreatedIntegrationEvent (to Kafka topic)
4. payment-service consumes from Kafka
5. Start Temporal workflow: OrderFulfillmentWorkflow
6. Workflow:
   - Activity: ProcessPayment (call payment-service)
   - Activity: ReserveInventory (call fulfillment-service)
   - Activity: InitiateShipping (call fulfillment-service)
7. Return order ID to client
```

### 13. Testing Strategy

#### Unit Tests
- Pure domain logic (aggregates, value objects)
- Use cases with mocked ports
- Arrow-kt assertions

#### Integration Tests
- Spring Modulith verification tests
- Event publication/subscription tests
- Repository tests with test containers

#### Workflow Tests
- Temporal TestWorkflowEnvironment
- Mock activities
- Test compensations

#### E2E Tests
- Full flow tests in modulith mode
- Docker Compose with all services

## Next Steps

1. ✅ Define architecture and design
2. Set up Gradle multi-module project
3. Implement shared-kernel module
4. Implement Order Management module
5. Implement Payment module
6. Implement Fulfillment module
7. Implement Temporal workflows
8. Add Docker Compose infrastructure
9. Create comprehensive documentation

## References

- [Spring Modulith Documentation](https://spring.io/projects/spring-modulith)
- [Temporal.io Documentation](https://docs.temporal.io/)
- [Arrow-kt Documentation](https://arrow-kt.io/)
- [Hexagonal Architecture](https://alistair.cockburn.us/hexagonal-architecture/)
- [Event Sourcing Pattern](https://martinfowler.com/eaaDev/EventSourcing.html)
