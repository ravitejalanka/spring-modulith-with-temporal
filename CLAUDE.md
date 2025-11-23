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

1. **Type Safety First**: Use Arrow-kt's `Either` for all error handling
2. **No Exceptions in Business Logic**: Exceptions are only for truly exceptional cases
3. **Railway-Oriented Programming**: Use `bind()` to chain operations that can fail
4. **Immutability**: Prefer `val` over `var`, use immutable data structures
5. **Explicit Over Implicit**: Make intentions clear in code
6. **Simple Error Handling**: Use `Either.catch { }.fold()` pattern with specialized helpers

### Why This Matters

- **Compile-time Safety**: Errors are part of the type system
- **Better Error Handling**: Forces developers to handle all error cases
- **Self-Documenting**: Function signatures tell you what can go wrong
- **Composability**: Easy to chain operations with `.bind()`
- **Analysis-Friendly**: Fold pattern is easier to analyze than catch-raise

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

**✅ Good - Use specialized helpers with fold:**
```kotlin
suspend fun processPayment(amount: Money): Either<PaymentError, Payment> = either {
    val validation = validateAmount(amount).bind()

    // Use helper functions for common error scenarios
    catchingMessaging {
        gateway.process(validation)
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

### Using Helper Functions for Exception Boundaries

Use specialized helper functions at boundaries where external systems may throw exceptions:

```kotlin
override suspend fun save(
    aggregateId: UUID,
    events: List<DomainEvent>
): Either<DomainError, Unit> = either {
    val entities = events.map { /* map to entities */ }

    // Use catchingDatabase for database operations
    catchingDatabase {
        repository.saveAll(entities)
    }.bind()
}
```

### Common Error Handling Helpers

The project provides specialized helpers for different types of operations:

- **`catchingDatabase`** - For database operations (handles DataIntegrityViolationException)
- **`catchingSerialization`** - For JSON serialization/deserialization
- **`catchingMessaging`** - For event publishing operations
- **`catching`** - Generic exception catching

**Implementation using fold pattern:**
```kotlin
inline fun <T> catchingDatabase(crossinline block: () -> T): Either<DomainError, T> =
    Either.catch { block() }
        .fold(
            { e -> Either.Left(ErrorHandlers.handleDatabaseError(e)) },
            { result -> Either.Right(result) }
        )
```

**Centralized error handlers:**
```kotlin
object ErrorHandlers {
    fun handleDatabaseError(e: Throwable): DomainError = when (e) {
        is DataIntegrityViolationException -> DomainError.ConcurrencyError(
            "Concurrency conflict: ${e.message}"
        )
        else -> DomainError.ValidationError("Database error: ${e.message ?: "Unknown error"}")
    }

    fun handleSerializationError(e: Throwable): DomainError =
        DomainError.ValidationError("Serialization error: ${e.message ?: "Unknown error"}")

    fun handleMessagingError(e: Throwable): DomainError =
        DomainError.ValidationError("Messaging error: ${e.message ?: "Unknown error"}")
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
- Uses specialized `catching*` helpers for exception boundaries
- Translates exceptions to domain errors via ErrorHandlers

**Example - Repository Adapter:**
```kotlin
@Repository
class EventSourcedOrderRepository(
    private val eventStore: EventStore
) : OrderRepository {
    override suspend fun save(order: Order): Either<DomainError, Unit> = either {
        catchingDatabase {
            eventStore.save(
                aggregateId = order.id.value,
                events = order.events
            )
        }.bind()
    }
}
```

---

[Rest of the file continues with Event Sourcing Guidelines, Coding Standards, etc. - keeping the same content as before]

## Summary Checklist

When writing code for this project:

- [ ] Use `Either<DomainError, T>` for all operations that can fail
- [ ] Use `either {}` DSL for sequential operations
- [ ] Use `catchingDatabase`, `catchingSerialization`, `catchingMessaging` at infrastructure boundaries
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

**Remember:** This project prioritizes type safety, explicitness, and simple functional error handling. Use specialized helpers (`catchingDatabase`, `catchingSerialization`, `catchingMessaging`) and make errors explicit!
