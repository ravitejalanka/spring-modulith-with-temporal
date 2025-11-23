# CLAUDE.md - Development Guide

## Introduction

This document serves as a comprehensive guide for AI assistants (Claude) and developers working on this event-driven modulith application. It covers architectural decisions, coding standards, and best practices specific to this project.

## Table of Contents

1. [Project Philosophy](#project-philosophy)
2. [Functional Error Handling with Arrow-kt](#functional-error-handling-with-arrow-kt)
3. [Architecture Patterns](#architecture-patterns)
4. [Module Structure](#module-structure)
5. [Event Sourcing Guidelines](#event-sourcing-guidelines)
6. [Coding Standards](#coding-standards)
7. [Testing Strategy](#testing-strategy)
8. [Common Patterns](#common-patterns)
9. [Anti-Patterns to Avoid](#anti-patterns-to-avoid)
10. [Temporal Workflow Considerations](#temporal-workflow-considerations)

---

## Project Philosophy

### Core Principles

1. **Type Safety First**: Use Arrow-kt's `Either` and `Validated` for all error handling
2. **No Exceptions in Business Logic**: Exceptions are only for truly exceptional cases
3. **Railway-Oriented Programming**: Use `bind()` to chain operations that can fail
4. **Immutability**: Prefer `val` over `var`, use immutable data structures
5. **Explicit Over Implicit**: Make intentions clear in code

### Why This Matters

- **Compile-time Safety**: Errors are part of the type system
- **Better Error Handling**: Forces developers to handle all error cases
- **Self-Documenting**: Function signatures tell you what can go wrong
- **Composability**: Easy to chain operations with `.bind()`

---

## Functional Error Handling with Arrow-kt

### ⛔ NEVER Use Try-Catch in Business Logic

**❌ Bad - Exceptions swallow context:**
```kotlin
fun processPayment(amount: Money): PaymentResult {
    try {
        val validation = validateAmount(amount)
        val processed = gateway.process(validation)
        return PaymentResult.Success(processed)
    } catch (e: ValidationException) {
        return PaymentResult.Failed(e.message)
    } catch (e: GatewayException) {
        return PaymentResult.Failed(e.message)
    }
}
```

**✅ Good - Use Either and catch():**
```kotlin
suspend fun processPayment(amount: Money): Either<PaymentError, Payment> = either {
    val validation = validateAmount(amount).bind()

    catch({
        gateway.process(validation)
    }) { e ->
        when (e) {
            is ValidationException -> raise(PaymentError.InvalidAmount(e.message))
            is GatewayException -> raise(PaymentError.GatewayFailure(e.message))
            else -> raise(PaymentError.UnexpectedError(e.message ?: "Unknown error"))
        }
    }.bind()
}
```

### The `either {}` DSL

The `either {}` block is the foundation of error handling in this project:

```kotlin
suspend fun createOrder(command: CreateOrderCommand): Either<OrderError, OrderId> = either {
    // Validate customer
    val customerId = CustomerId(command.customerId)

    // Validate items (can fail)
    val items = command.items.toNonEmptyListOrError {
        OrderError.EmptyOrder
    }.bind() // bind() short-circuits on Left

    // Create aggregate (can fail)
    val order = Order.create(customerId, items).bind()

    // Save events (can fail)
    eventStore.save(order.events).bind()

    // Publish integration event (can fail)
    eventPublisher.publish(OrderCreatedEvent(...)).bind()

    // Return success
    order.id
}
```

### Using `catch {}` for Exception Boundaries

Use `catch {}` at boundaries where external systems may throw exceptions:

```kotlin
override suspend fun save(
    aggregateId: UUID,
    events: List<DomainEvent>
): Either<DomainError, Unit> = either {
    catch({
        val entities = events.map { /* map to entities */ }
        repository.saveAll(entities)
    }) { e ->
        when (e) {
            is DataIntegrityViolationException -> raise(
                DomainError.ConcurrencyError("Concurrency conflict: ${e.message}")
            )
            else -> raise(DomainError.ValidationError("Error saving: ${e.message}"))
        }
    }
}
```

### Raising Errors

Use `raise()` to short-circuit with an error:

```kotlin
suspend fun validateOrder(order: Order): Either<OrderError, Unit> = either {
    ensure(order.items.isNotEmpty()) {
        OrderError.EmptyOrder
    }

    ensure(order.totalAmount > Money.ZERO) {
        OrderError.InvalidAmount
    }

    // Or use raise directly
    if (order.customerId == null) {
        raise(OrderError.MissingCustomer)
    }
}
```

### Combining Multiple Validations

Use `zipOrAccumulate` to collect all errors:

```kotlin
fun validateCreateOrder(cmd: CreateOrderCommand): ValidatedNel<ValidationError, ValidatedOrder> =
    zipOrAccumulate(
        validateCustomerId(cmd.customerId),
        validateItems(cmd.items),
        validateTotalAmount(cmd.items)
    ) { customerId, items, total ->
        ValidatedOrder(customerId, items, total)
    }
```

---

## Architecture Patterns

### Hexagonal Architecture (Ports & Adapters)

Each module follows this structure:

```
module/
├── domain/           # Pure business logic (no dependencies)
│   ├── model/       # Aggregates, Entities, Value Objects
│   └── event/       # Domain Events
├── application/      # Use cases (orchestration)
│   ├── usecase/     # Command/Query handlers
│   └── port/        # Interfaces (ports)
├── infrastructure/   # Technical implementations (adapters)
│   ├── persistence/ # Repository implementations
│   └── messaging/   # Event publishers
└── api/             # REST controllers (input adapter)
```

### Domain Layer Rules

**✅ Domain layer MUST:**
- Contain only pure Kotlin (no Spring, no frameworks)
- Use Arrow-kt `Either` for all operations that can fail
- Be 100% testable without mocks
- Encapsulate all business rules
- Return domain events for state changes

**❌ Domain layer MUST NOT:**
- Import Spring annotations
- Use try-catch
- Depend on infrastructure
- Have any I/O operations

**Example - Pure Domain Aggregate:**
```kotlin
sealed class Order(
    open val id: OrderId,
    open val customerId: CustomerId
) {
    data class PendingOrder(...) : Order(...) {
        fun confirm(): Either<OrderError, ConfirmedOrder> = either {
            ensure(items.isNotEmpty()) { OrderError.EmptyOrder }

            val confirmed = ConfirmedOrder(...)
            confirmed._events.add(OrderConfirmedEvent(...))
            confirmed
        }
    }
}
```

### Application Layer Rules

**✅ Application layer:**
- Orchestrates domain objects
- Uses ports (interfaces) for infrastructure
- Handles transactions
- Publishes integration events

**Example - Use Case:**
```kotlin
@Service
@Transactional
class CreateOrderUseCase(
    private val orderRepository: OrderRepository, // Port
    private val eventPublisher: IntegrationEventPublisher // Port
) {
    suspend fun execute(command: CreateOrderCommand): Either<DomainError, OrderId> = either {
        val order = Order.create(customerId, items).bind()

        orderRepository.save(order).bind()

        eventPublisher.publish(OrderPlacedEvent(...)).bind()

        order.id
    }
}
```

### Infrastructure Layer Rules

**✅ Infrastructure layer:**
- Implements ports defined in application layer
- Handles external I/O (database, messaging, etc.)
- Uses `catch {}` for exception boundaries
- Translates exceptions to domain errors

**Example - Repository Adapter:**
```kotlin
@Repository
class EventSourcedOrderRepository(
    private val eventStore: EventStore
) : OrderRepository {
    override suspend fun save(order: Order): Either<DomainError, Unit> = either {
        catch({
            eventStore.save(
                aggregateId = order.id.value,
                events = order.events
            )
        }) { e ->
            raise(DomainError.ValidationError("Failed to save: ${e.message}"))
        }
    }
}
```

---

## Event Sourcing Guidelines

### Event Store Pattern

All aggregate state changes are stored as events:

```kotlin
// 1. Aggregate produces events
val order = Order.create(customerId, items)
// order.events = [OrderCreatedEvent]

// 2. Events are persisted
eventStore.save(order.id, order.events)

// 3. Aggregate can be reconstituted
val events = eventStore.load(orderId)
val order = Order.fromEvents(events)
```

### Event Naming Conventions

- Use past tense: `OrderCreated`, `PaymentProcessed`, `ItemShipped`
- Include aggregate ID
- Include timestamp
- Be specific: `OrderConfirmed` not `OrderUpdated`

### Domain Events vs Integration Events

**Domain Events** - Internal to aggregate:
```kotlin
data class OrderCreatedEvent(
    override val eventId: UUID,
    override val occurredAt: Instant,
    override val aggregateId: UUID,
    val orderId: OrderId,
    val customerId: CustomerId,
    val items: List<OrderItem>
) : OrderDomainEvent
```

**Integration Events** - Cross-module communication:
```kotlin
data class OrderPlacedIntegrationEvent(
    override val eventId: UUID,
    override val occurredAt: Instant,
    val orderId: OrderId,
    val customerId: CustomerId,
    val items: List<OrderItemDto> // DTOs, not domain objects
) : IntegrationEvent {
    override val eventType = "OrderPlaced"
}
```

### Event Reconstitution

```kotlin
companion object {
    fun fromEvents(events: List<OrderDomainEvent>): Either<OrderError, Order> = either {
        ensure(events.isNotEmpty()) { OrderError.NoEventsToReconstitute }

        var order: Order? = null
        var version = 0L

        events.forEach { event ->
            version++
            order = when (event) {
                is OrderCreatedEvent -> PendingOrder(...)
                is OrderConfirmedEvent -> (order as? PendingOrder)?.let {
                    ConfirmedOrder(...)
                }
                // ... other events
            } ?: raise(OrderError.InvalidEventSequence)
        }

        order ?: raise(OrderError.FailedToReconstitute)
    }
}
```

---

## Coding Standards

### Value Objects

Use `@JvmInline value class` for type-safe IDs and values:

```kotlin
@JvmInline
value class OrderId(override val value: UUID) : EntityId(value) {
    companion object {
        fun generate() = OrderId(UUID.randomUUID())

        fun from(value: String): Either<DomainError, OrderId> =
            Either.catch { OrderId(UUID.fromString(value)) }
                .mapLeft { DomainError.ValidationError("Invalid OrderId") }
    }
}
```

### Money Type

Always use the `Money` value object:

```kotlin
// ❌ Bad
val price: BigDecimal = BigDecimal("99.99")
val total = price * quantity

// ✅ Good
val price: Money = Money.of("99.99").getOrNull()!!
val total = price * quantity
```

### Sealed Classes for State Machines

```kotlin
sealed class Order {
    data class PendingOrder(...) : Order()
    data class ConfirmedOrder(...) : Order()
    data class PaidOrder(...) : Order()
    data class CompletedOrder(...) : Order()
}

// Type-safe pattern matching
when (order) {
    is Order.PendingOrder -> order.confirm()
    is Order.ConfirmedOrder -> order.pay()
    is Order.PaidOrder -> order.fulfill()
    is Order.CompletedOrder -> /* already complete */
}
```

### Coroutines for Async Operations

```kotlin
suspend fun processOrder(orderId: OrderId): Either<DomainError, Unit> = either {
    val order = orderRepository.findById(orderId).bind()

    // Use withContext for blocking operations
    val result = withContext(Dispatchers.IO) {
        externalApi.process(order)
    }

    result.bind()
}
```

---

## Testing Strategy

### Unit Tests - Domain Layer

```kotlin
class OrderTest {
    @Test
    fun `should create pending order with valid items`() {
        val customerId = CustomerId.generate()
        val items = nonEmptyListOf(
            OrderItem.create(productId, "Laptop", Quantity(1), Money.of("999.99"))
        )

        val result = Order.create(customerId, items)

        result.shouldBeRight { order ->
            order.shouldBeInstanceOf<Order.PendingOrder>()
            order.items.size shouldBe 1
            order.events.shouldContainExactly(OrderCreatedEvent::class)
        }
    }

    @Test
    fun `should fail to create order with empty items`() {
        val result = Order.create(customerId, emptyList())

        result.shouldBeLeft(OrderError.EmptyOrder)
    }
}
```

### Integration Tests - Event Store

```kotlin
@Testcontainers
class EventStoreIntegrationTest {
    @Container
    val postgres = PostgreSQLContainer<Nothing>("postgres:16-alpine")

    @Test
    fun `should save and load events`() = runBlocking {
        val events = listOf(OrderCreatedEvent(...))

        eventStore.save(orderId, "Order", events, 0)
            .shouldBeRight()

        val loaded = eventStore.load(orderId, "Order")

        loaded.shouldBeRight { loadedEvents ->
            loadedEvents.size shouldBe 1
            loadedEvents.first() shouldBe events.first()
        }
    }
}
```

---

## Common Patterns

### Pattern 1: Command Handler

```kotlin
suspend fun handleCommand(command: Command): Either<DomainError, Result> = either {
    // 1. Validate
    val validated = validate(command).bind()

    // 2. Load aggregate (if needed)
    val aggregate = repository.findById(id).bind()

    // 3. Execute business logic
    val updated = aggregate.doSomething(validated).bind()

    // 4. Save events
    repository.save(updated).bind()

    // 5. Publish integration events
    eventPublisher.publish(IntegrationEvent(...)).bind()

    // 6. Return result
    updated.id
}
```

### Pattern 2: Query Handler

```kotlin
suspend fun handleQuery(query: Query): Either<DomainError, DTO> = either {
    val aggregate = repository.findById(query.id).bind()
        ?: raise(DomainError.NotFoundError("Order", query.id))

    DTO.from(aggregate)
}
```

### Pattern 3: Event Listener

```kotlin
@Service
class PaymentEventListener(
    private val paymentService: PaymentService
) {
    @EventListener
    suspend fun on(event: OrderPlacedIntegrationEvent): Either<DomainError, Unit> = either {
        val payment = Payment.create(event.orderId, event.totalAmount).bind()

        val processed = payment.process().bind()

        eventPublisher.publish(PaymentProcessedEvent(...)).bind()
    }
}
```

---

## Anti-Patterns to Avoid

### ❌ Don't Use Nulls for Error Cases

```kotlin
// ❌ Bad
fun findOrder(id: OrderId): Order? {
    return repository.findById(id) // What if DB error?
}

// ✅ Good
suspend fun findOrder(id: OrderId): Either<DomainError, Order?> = either {
    repository.findById(id).bind()
}
```

### ❌ Don't Swallow Errors

```kotlin
// ❌ Bad
try {
    repository.save(order)
} catch (e: Exception) {
    logger.error("Failed to save", e)
    // Error is lost!
}

// ✅ Good
catch({
    repository.save(order)
}) { e ->
    raise(DomainError.ValidationError("Failed to save: ${e.message}"))
}
```

### ❌ Don't Use Exceptions for Control Flow

```kotlin
// ❌ Bad
try {
    validateOrder(order)
    processOrder(order)
} catch (e: ValidationException) {
    return Response.invalid()
}

// ✅ Good
suspend fun processOrder(order: Order): Either<OrderError, Unit> = either {
    validateOrder(order).bind()
    processOrderLogic(order).bind()
}
```

### ❌ Don't Mix Exceptions and Either

```kotlin
// ❌ Bad - Inconsistent error handling
suspend fun createOrder(cmd: CreateOrderCommand): Either<OrderError, OrderId> = either {
    val order = Order.create(cmd.customerId, cmd.items).bind()
    repository.save(order) // Throws exception - not caught!
    order.id
}

// ✅ Good - Consistent Either
suspend fun createOrder(cmd: CreateOrderCommand): Either<OrderError, OrderId> = either {
    val order = Order.create(cmd.customerId, cmd.items).bind()
    repository.save(order).bind() // Returns Either
    order.id
}
```

---

## Temporal Workflow Considerations

### Exception Handling in Workflows

**⚠️ Special Case:** Temporal workflows are deterministic and run in a special execution environment. They require try-catch for proper compensation logic:

```kotlin
override fun fulfillOrder(input: OrderFulfillmentInput): OrderFulfillmentResult {
    return try {
        val paymentProcessed = paymentActivity.processPayment(...)

        val inventoryReserved = try {
            fulfillmentActivity.reserveInventory(...)
        } catch (e: Exception) {
            // Compensate: refund payment
            paymentActivity.refundPayment(...)
            return OrderFulfillmentResult.Failed(...)
        }

        OrderFulfillmentResult.Success(...)

    } catch (e: Exception) {
        OrderFulfillmentResult.Failed(...)
    }
}
```

### Why Try-Catch in Workflows?

1. **Determinism**: Temporal workflows must be deterministic
2. **Replay**: Workflows can be replayed from history
3. **Compensation**: Need explicit compensation logic
4. **Activity Exceptions**: Activities can throw ActivityFailureException

### Activities Should Use Either

```kotlin
// Activity implementation uses Either
@Component
class PaymentActivityImpl : PaymentActivity {
    override fun processPayment(orderId: UUID, amount: Money): Boolean {
        return paymentService.process(orderId, amount)
            .fold(
                { false },
                { true }
            )
    }
}
```

---

## Module Communication

### Internal Communication (Modulith Mode)

Use Spring Application Events:

```kotlin
@Service
class OrderService(
    private val eventPublisher: EventPublisher // Spring Events
) {
    suspend fun createOrder(...) = either {
        // ...
        eventPublisher.publish(OrderCreatedEvent(...)).bind()
    }
}

@Service
class PaymentService {
    @EventListener
    suspend fun on(event: OrderCreatedEvent) {
        // Handle event
    }
}
```

### External Communication (Microservices Mode)

Use Kafka:

```kotlin
// Same code! Just configure messaging.mode=kafka
@Service
class OrderService(
    private val eventPublisher: EventPublisher // Kafka
) {
    // Exact same code as above
}
```

---

## Summary Checklist

When writing code for this project:

- [ ] Use `Either<DomainError, T>` for all operations that can fail
- [ ] Use `either {}` DSL for sequential operations
- [ ] Use `catch {}` at infrastructure boundaries
- [ ] Use `raise()` to short-circuit with errors
- [ ] Never use try-catch in domain or application layers
- [ ] Use value objects for domain primitives
- [ ] Follow hexagonal architecture layers
- [ ] Keep domain layer pure (no frameworks)
- [ ] Use sealed classes for state machines
- [ ] Write tests using Arrow-kt assertions
- [ ] Document why, not what (code is self-documenting)

---

## Resources

- [Arrow-kt Documentation](https://arrow-kt.io/)
- [Domain-Driven Design](https://martinfowler.com/tags/domain%20driven%20design.html)
- [Event Sourcing Pattern](https://martinfowler.com/eaaDev/EventSourcing.html)
- [Hexagonal Architecture](https://alistair.cockburn.us/hexagonal-architecture/)
- [Temporal.io Workflows](https://docs.temporal.io/workflows)

---

**Remember:** This project prioritizes type safety, explicitness, and functional error handling. When in doubt, use `Either` and make errors explicit!
