# Spring Modulith with Temporal.io - Event-Driven Sample Application

A comprehensive sample application demonstrating modern architectural patterns for building scalable, maintainable event-driven systems in Kotlin.

## 🎯 Overview

This project showcases a production-ready architecture that combines:

- **Spring Modulith** - Modular monolith with enforced boundaries
- **Hexagonal Architecture** - Clean separation of domain, application, and infrastructure
- **Domain-Driven Design (DDD)** - Rich domain models with aggregates and value objects
- **Event Sourcing** - Complete audit trail and temporal queries
- **Event-Driven Architecture** - Loose coupling between modules
- **Temporal.io** - Reliable workflow orchestration and saga pattern
- **Arrow-kt** - Functional error handling and type safety
- **Kotlin Coroutines** - Reactive/async programming

### 🚀 Key Features

- **Deployment Flexibility**: Run as a single modulith OR as independent microservices
- **Event Bus Abstraction**: Switch between Spring Events (modulith) and Kafka (microservices)
- **Event Store**: Full event sourcing with event replay capabilities
- **Saga Orchestration**: Temporal workflows with automatic compensation
- **Type-Safe Errors**: Arrow-kt Either and Validated for error handling
- **Module Boundaries**: Compile-time enforcement via Spring Modulith

## 📋 Table of Contents

- [Architecture](#architecture)
- [Project Structure](#project-structure)
- [Getting Started](#getting-started)
- [Running the Application](#running-the-application)
- [API Usage](#api-usage)
- [Key Concepts](#key-concepts)
- [Technology Stack](#technology-stack)
- [References](#references)

## 🏗️ Architecture

See [ARCHITECTURE.md](ARCHITECTURE.md) for detailed architectural documentation.

### Deployment Modes

#### Modulith Mode (Default)
```
┌─────────────────────────────────────────┐
│         Single Application              │
│  ┌──────────┬──────────┬──────────────┐ │
│  │  Order   │ Payment  │ Fulfillment  │ │
│  │  Module  │  Module  │   Module     │ │
│  └──────────┴──────────┴──────────────┘ │
│         Spring Application Events        │
└─────────────────────────────────────────┘
```

#### Microservices Mode
```
┌──────────────┐    ┌──────────────┐    ┌──────────────┐
│    Order     │    │   Payment    │    │ Fulfillment  │
│   Service    │    │   Service    │    │   Service    │
└──────┬───────┘    └──────┬───────┘    └──────┬───────┘
       │                   │                    │
       └───────────────────┴────────────────────┘
                      Kafka Events
```

### Module Structure

Each module follows **Hexagonal Architecture**:

```
module/
├── domain/           # Pure business logic
│   ├── model/       # Aggregates, Entities, Value Objects
│   └── event/       # Domain Events
├── application/      # Use cases (Ports)
│   ├── usecase/     # Command/Query handlers
│   └── port/        # Input/Output ports
├── infrastructure/   # Adapters
│   ├── persistence/ # Repository implementations
│   └── messaging/   # Event publishers
└── api/             # REST controllers
```

## 📁 Project Structure

```
spring-modulith-temporal/
├── modules/
│   ├── shared-kernel/        # Common domain primitives
│   ├── order-management/     # Order bounded context
│   ├── payment/              # Payment bounded context
│   ├── fulfillment/          # Fulfillment bounded context
│   └── temporal-workflows/   # Workflow orchestration
├── infrastructure/
│   ├── event-store/          # Event sourcing infrastructure
│   └── messaging/            # Event bus abstraction
├── applications/
│   ├── modulith-app/         # Monolith runner
│   ├── order-service/        # Order microservice
│   ├── payment-service/      # Payment microservice (template)
│   └── fulfillment-service/  # Fulfillment microservice (template)
├── docker/
│   ├── docker-compose.yml         # Full infrastructure
│   └── docker-compose-dev.yml     # Dev infrastructure
├── ARCHITECTURE.md
└── README.md
```

## 🚦 Getting Started

### Prerequisites

- **JDK 21** or higher
- **Docker** and **Docker Compose**
- **Gradle** 8+ (or use included wrapper)

### 1. Start Infrastructure

For modulith mode (PostgreSQL + Temporal only):
```bash
cd docker
docker-compose -f docker-compose-dev.yml up -d
```

For microservices mode (includes Kafka):
```bash
cd docker
docker-compose up -d
```

This starts:
- PostgreSQL (port 5432)
- Temporal Server (port 7233)
- Temporal UI (http://localhost:8080)
- Kafka + Zookeeper (microservices mode only)
- Kafka UI (http://localhost:8090, microservices mode only)

### 2. Build the Project

```bash
./gradlew build
```

## 🏃 Running the Application

### Modulith Mode (Default)

Run all modules in a single JVM:

```bash
./gradlew :applications:modulith-app:bootRun
```

The application starts on **http://localhost:8080**

### Microservices Mode

Run with Kafka messaging:

```bash
./gradlew :applications:modulith-app:bootRun --args='--spring.profiles.active=microservices'
```

### Individual Microservices

Run each service independently:

```bash
# Terminal 1 - Order Service
./gradlew :applications:order-service:bootRun

# Terminal 2 - Payment Service
./gradlew :applications:payment-service:bootRun

# Terminal 3 - Fulfillment Service
./gradlew :applications:fulfillment-service:bootRun
```

## 📡 API Usage

### Create an Order

```bash
curl -X POST http://localhost:8080/api/orders \
  -H "Content-Type: application/json" \
  -d '{
    "customerId": "123e4567-e89b-12d3-a456-426614174000",
    "items": [
      {
        "productId": "789e4567-e89b-12d3-a456-426614174000",
        "productName": "Laptop",
        "quantity": 1,
        "unitPrice": "1299.99"
      }
    ]
  }'
```

Response:
```json
{
  "orderId": "550e8400-e29b-41d4-a716-446655440000"
}
```

### Get Order Details

```bash
curl http://localhost:8080/api/orders/550e8400-e29b-41d4-a716-446655440000
```

Response:
```json
{
  "orderId": "550e8400-e29b-41d4-a716-446655440000",
  "customerId": "123e4567-e89b-12d3-a456-426614174000",
  "status": "CONFIRMED",
  "items": [
    {
      "productId": "789e4567-e89b-12d3-a456-426614174000",
      "productName": "Laptop",
      "quantity": 1,
      "unitPrice": 1299.99,
      "totalPrice": 1299.99
    }
  ],
  "totalAmount": 1299.99
}
```

### Monitor Temporal Workflows

Visit **http://localhost:8080** to see the Temporal UI with running workflows.

## 💡 Key Concepts

### 1. Event Sourcing

All order state changes are stored as events:

```kotlin
// Events are the source of truth
val events = listOf(
    OrderCreatedEvent(...),
    OrderConfirmedEvent(...),
    OrderPaidEvent(...)
)

// Aggregate is reconstituted from events
val order = Order.fromEvents(events)
```

### 2. Arrow-kt Error Handling

Type-safe error handling without exceptions:

```kotlin
suspend fun createOrder(command: CreateOrderCommand): Either<OrderError, OrderId> = either {
    val customerId = CustomerId(command.customerId)
    val items = validateItems(command.items).bind()

    val order = Order.create(customerId, items).bind()

    eventStore.save(order.events).bind()

    order.id
}
```

### 3. Temporal Workflows

Reliable saga orchestration with automatic compensation:

```kotlin
override fun fulfillOrder(input: OrderFulfillmentInput): OrderFulfillmentResult {
    val paymentProcessed = paymentActivity.processPayment(input.orderId, input.totalAmount)

    val inventoryReserved = try {
        fulfillmentActivity.reserveInventory(input.orderId)
    } catch (e: Exception) {
        // Automatic compensation
        paymentActivity.refundPayment(input.orderId)
        return OrderFulfillmentResult.Failed(input.orderId, "Inventory reservation failed")
    }

    // Continue workflow...
}
```

### 4. Module Boundaries

Spring Modulith enforces boundaries at compile time:

```kotlin
@Modulith(
    systemName = "Order Management System",
    sharedModules = ["shared-kernel"]
)
@SpringBootApplication
class ModulithApplication
```

## 🛠️ Technology Stack

### Core
- **Kotlin** 2.0
- **Spring Boot** 3.2
- **Spring Modulith** 1.1
- **Arrow-kt** 1.2
- **Kotlin Coroutines** 1.8

### Infrastructure
- **PostgreSQL** - Database and event store
- **Apache Kafka** - Event streaming (microservices mode)
- **Temporal.io** - Workflow orchestration
- **Flyway** - Database migrations

### Build & Runtime
- **Gradle** 8 (Kotlin DSL)
- **Docker** - Containerization
- **Docker Compose** - Local infrastructure

## 📚 Module Descriptions

### Order Management
- **Responsibility**: Order lifecycle management
- **Domain Model**: Order aggregate with states (Pending, Confirmed, Paid, Fulfilling, Completed, Cancelled)
- **Events**: OrderCreated, OrderConfirmed, OrderPaid, OrderCompleted, OrderCancelled
- **API**: REST endpoints for creating and querying orders

### Payment
- **Responsibility**: Payment processing
- **Integration**: Listens to OrderPlaced events
- **Events**: OrderPaid (publishes after successful payment)
- **Domain Model**: Payment aggregate (simplified)

### Fulfillment
- **Responsibility**: Inventory and shipping management
- **Integration**: Listens to OrderPaid events
- **Events**: OrderCompleted (publishes after fulfillment)
- **Domain Model**: Fulfillment process (simplified)

### Temporal Workflows
- **OrderFulfillmentWorkflow**: Orchestrates payment → inventory → shipping
- **Activities**: OrderActivity, PaymentActivity, FulfillmentActivity
- **Compensation**: Automatic rollback on failure

## 🧪 Testing

### Run All Tests
```bash
./gradlew test
```

### Module Verification Test
```bash
./gradlew :applications:modulith-app:test --tests "*ModulithTest"
```

This verifies module boundaries are properly enforced.

## 📖 Learning Path

1. **Start with Domain Models**: Explore `modules/order-management/src/main/kotlin/com/example/modulith/order/domain/`
2. **Understand Event Sourcing**: Look at `infrastructure/event-store/`
3. **See Hexagonal Architecture**: Check the layering in order-management module
4. **Explore Temporal Workflows**: Review `modules/temporal-workflows/`
5. **Study Event Abstraction**: See how `infrastructure/messaging/` switches between Spring Events and Kafka

## 🤝 Contributing

This is a sample/educational project. Feel free to fork and adapt for your needs.

## 📄 License

MIT License - feel free to use this as a template for your projects.

## 🔗 References

- [Spring Modulith Documentation](https://spring.io/projects/spring-modulith)
- [Temporal.io Documentation](https://docs.temporal.io/)
- [Arrow-kt Documentation](https://arrow-kt.io/)
- [Domain-Driven Design](https://martinfowler.com/tags/domain%20driven%20design.html)
- [Event Sourcing Pattern](https://martinfowler.com/eaaDev/EventSourcing.html)
- [Hexagonal Architecture](https://alistair.cockburn.us/hexagonal-architecture/)

## 🎓 Key Takeaways

This project demonstrates that modern applications can:

1. **Start as a modulith** and evolve to microservices when needed
2. **Use event sourcing** for complete audit trails and flexibility
3. **Apply DDD patterns** for rich, maintainable domain models
4. **Leverage Temporal** for reliable distributed workflows
5. **Use functional programming** (Arrow-kt) for type-safe error handling
6. **Maintain clean architecture** with hexagonal/ports-and-adapters pattern

The industry is indeed moving toward this hybrid approach - modular monoliths that can be split into microservices when business requirements demand it!
