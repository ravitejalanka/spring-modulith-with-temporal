# 📁 Project Folder Structure Explained

This document explains the folder structure of this Spring Modulith + Temporal project in a beginner-friendly way.

## 🌳 High-Level Structure

```
spring-modulith-with-temporal/
├── modules/              ← Domain modules (business logic)
├── infrastructure/       ← Technical infrastructure (messaging, databases, etc.)
├── applications/         ← Runnable applications (modulith OR microservices)
├── docker/              ← Docker compose files
├── CLAUDE.md            ← Development guide
└── README.md            ← Project documentation
```

---

## 📦 1. `modules/` - Business Logic (Domain Modules)

**Purpose:** Contains all your business domains/bounded contexts

```
modules/
├── shared-kernel/         ← Shared domain primitives (Money, Quantity, Email)
├── order-management/      ← Order domain (CREATE, CONFIRM, CANCEL orders)
├── payment/              ← Payment domain (PROCESS payments)
├── fulfillment/          ← Fulfillment domain (SHIP orders)
└── temporal-workflows/   ← Temporal orchestration workflows
```

### 🔍 Why separate modules?
- **Each module is a bounded context** (DDD concept)
- Can be deployed together (modulith) OR separately (microservices)
- Clear boundaries between business domains

---

## 🏛️ Inside Each Domain Module (Hexagonal Architecture)

Let's look at **`order-management/`** as an example:

```
modules/order-management/
└── src/main/kotlin/com/example/modulith/order/
    ├── domain/              ← 🟢 PURE business logic (no frameworks)
    │   ├── model/          ← Aggregates, Entities, Value Objects
    │   │   ├── Order.kt           (Aggregate root - old style)
    │   │   ├── OrderItem.kt       (Entity)
    │   │   └── OrderId.kt         (Value object)
    │   ├── command/        ← 🆕 Commands (f{model} pattern)
    │   │   └── OrderCommand.kt    (CreateOrder, ConfirmOrder, etc.)
    │   ├── event/          ← Domain Events
    │   │   ├── OrderEvents.kt     (OrderCreatedEvent, etc.)
    │   │   └── OrderIntegrationEvents.kt
    │   ├── state/          ← 🆕 State representation (f{model})
    │   │   └── OrderState.kt      (Pending, Confirmed, Paid, etc.)
    │   └── decider/        ← 🆕 Pure decision logic (f{model})
    │       └── OrderDecider.kt    (decide(), evolve())
    │
    ├── application/         ← 🟡 Use cases & orchestration
    │   ├── usecase/        ← Application services
    │   │   ├── CreateOrderUseCase.kt
    │   │   └── GetOrderUseCase.kt
    │   ├── handler/        ← 🆕 Command handlers
    │   │   └── OrderCommandHandler.kt
    │   └── port/           ← Interfaces (hexagonal architecture)
    │       └── OrderPorts.kt      (OrderRepository, EventPublisher)
    │
    ├── infrastructure/      ← 🔴 Technical implementations
    │   └── persistence/    ← Repository implementations
    │       └── TemporalOrderRepository.kt
    │
    └── api/                ← 🔵 REST controllers (HTTP layer)
        └── OrderController.kt
```

### 🎯 Layer Explanation:

| Layer | Color | What it does | Dependencies |
|-------|-------|--------------|--------------|
| **domain/** | 🟢 Green | Pure business logic, NO frameworks | None! |
| **application/** | 🟡 Yellow | Orchestrates domain objects | domain/ only |
| **infrastructure/** | 🔴 Red | Talks to databases, APIs, etc. | Everything |
| **api/** | 🔵 Blue | HTTP endpoints (REST controllers) | application/ |

### 📊 Dependency Rule (CRITICAL):

```
api/ ──→ application/ ──→ domain/
  ↓           ↓
infrastructure/ ←─────────┘
```

**The rule:** Inner layers NEVER depend on outer layers
- ✅ `domain/` depends on NOTHING (pure Kotlin + Arrow-kt)
- ✅ `application/` depends on `domain/`
- ✅ `infrastructure/` depends on `domain/` and `application/`
- ✅ `api/` depends on `application/`

---

## 🔧 2. `infrastructure/` - Technical Plumbing

**Purpose:** Shared technical infrastructure used by multiple modules

```
infrastructure/
└── messaging/           ← Event publishing (Spring Events OR Kafka)
    └── src/main/kotlin/com/example/modulith/infrastructure/messaging/
        ├── EventPublisher.kt           (Interface)
        ├── spring/
        │   └── SpringEventPublisher.kt  (For modulith mode)
        └── kafka/
            └── KafkaEventPublisher.kt   (For microservices mode)
```

### 🤔 Why separate infrastructure?
- Reusable across multiple domain modules
- Can swap implementations (Spring Events ↔ Kafka) without changing domain code

---

## 🚀 3. `applications/` - Runnable Apps

**Purpose:** Different ways to run your code

```
applications/
├── modulith-app/          ← Run ALL modules together (monolith)
├── order-service/         ← Run ONLY order module (microservice)
├── payment-service/       ← Run ONLY payment module (microservice)
└── fulfillment-service/   ← Run ONLY fulfillment module (microservice)
```

### 🎭 Same Code, Different Deployments!

#### Option 1: Modulith (All in one)
```bash
cd applications/modulith-app
./gradlew bootRun
```
- All modules run in ONE JVM
- Uses Spring Events for communication
- Easier to develop and debug

#### Option 2: Microservices (Separate)
```bash
# Terminal 1
cd applications/order-service
./gradlew bootRun

# Terminal 2
cd applications/payment-service
./gradlew bootRun

# Terminal 3
cd applications/fulfillment-service
./gradlew bootRun
```
- Each module runs in separate JVM
- Uses Kafka for communication
- Better for scaling

---

## 🆕 4. f{model} Pattern Structure (NEW)

Here's how the new functional pattern is organized:

```
order-management/domain/
├── command/                  ← What users WANT to do
│   └── OrderCommand.kt
│       ├── CreateOrder       "I want to create an order"
│       ├── ConfirmOrder      "I want to confirm this order"
│       └── MarkAsPaid        "I want to mark as paid"
│
├── event/                    ← What HAPPENED
│   └── OrderEvents.kt
│       ├── OrderCreatedEvent       "Order was created"
│       ├── OrderConfirmedEvent     "Order was confirmed"
│       └── OrderPaidEvent          "Order was paid"
│
├── state/                    ← WHERE we are now
│   └── OrderState.kt
│       ├── Initial           "No order yet"
│       ├── Pending           "Order created, not confirmed"
│       ├── Confirmed         "Order confirmed, waiting payment"
│       └── Paid              "Order paid, ready to ship"
│
└── decider/                  ← HOW we make decisions
    └── OrderDecider.kt
        ├── decide()          (State, Command) → Events
        └── evolve()          (State, Event) → New State
```

### 🔄 Flow Example:

```
1. User Action → Command
   CreateOrder("iPhone", $999)

2. Decider.decide() → Events
   OrderState.Initial + CreateOrder → OrderCreatedEvent

3. Decider.evolve() → New State
   OrderState.Initial + OrderCreatedEvent → OrderState.Pending

4. Store events in Temporal
   Temporal workflow history saves OrderCreatedEvent

5. Publish integration event
   Other modules notified via OrderPlacedIntegrationEvent
```

---

## 📚 5. Complete Module Breakdown

### `shared-kernel/`
**What:** Shared domain primitives
**Contains:**
- `Money` - Money value object with validation
- `Quantity` - Quantity with positive validation
- `Email` - Email with format validation
- `DomainError` - Error types
- Functional helpers (`catchingDatabase`, etc.)

**Used by:** ALL other modules

---

### `order-management/`
**What:** Manages the order lifecycle
**Responsibilities:**
- Create orders
- Confirm orders
- Track payment status
- Manage order state

**Key files:**
- `OrderDecider.kt` - Pure decision logic
- `OrderCommandHandler.kt` - Orchestrates commands
- `OrderAggregateWorkflow` - Temporal workflow (event store)

---

### `payment/`
**What:** Processes payments
**Listens to:** `OrderPlacedIntegrationEvent`
**Publishes:** `OrderPaidIntegrationEvent`

---

### `fulfillment/`
**What:** Ships orders
**Listens to:** `OrderPaidIntegrationEvent`
**Publishes:** `OrderCompletedIntegrationEvent`

---

### `temporal-workflows/`
**What:** Temporal workflow orchestrations
**Contains:**
- `OrderAggregateWorkflow` - Order aggregate as Temporal workflow (event store)
- `OrderFulfillmentWorkflow` - Long-running saga for order fulfillment
- Activities for cross-module operations

---

## 🔗 How Modules Communicate

### Internal Communication (within module)
```
Controller → UseCase → Decider → Events
```

### External Communication (between modules)

#### Modulith Mode (Spring Events)
```
Order Module                    Payment Module
    |                              |
    | OrderPlacedIntegrationEvent  |
    |----------------------------->|
    |                              | Process payment
    |                              |
    | OrderPaidIntegrationEvent    |
    |<-----------------------------|
```

#### Microservice Mode (Kafka)
```
Order Service                   Payment Service
    |                              |
    | OrderPlacedIntegrationEvent  |
    |==========> Kafka ==========>|
    |                              | Process payment
    |                              |
    | OrderPaidIntegrationEvent    |
    |<========== Kafka ===========|
```

---

## 📋 Quick Reference

### Where to find things:

| What | Where |
|------|-------|
| Business rules | `modules/*/domain/decider/` |
| Commands | `modules/*/domain/command/` |
| Events | `modules/*/domain/event/` |
| State | `modules/*/domain/state/` |
| Use cases | `modules/*/application/usecase/` |
| REST endpoints | `modules/*/api/` |
| Database code | `modules/*/infrastructure/persistence/` |
| Shared utilities | `modules/shared-kernel/` |
| Temporal workflows | `modules/temporal-workflows/` |
| Configuration | `applications/*/src/main/resources/` |

---

## 🎓 Learning Path

If you're new to this structure, follow this order:

1. **Start with `shared-kernel/`** - See basic types like `Money`, `Quantity`
2. **Read `order-management/domain/command/OrderCommand.kt`** - See all possible commands
3. **Read `order-management/domain/state/OrderState.kt`** - See all possible states
4. **Read `order-management/domain/decider/OrderDecider.kt`** - See decision logic
5. **Read `order-management/application/handler/OrderCommandHandler.kt`** - See orchestration
6. **Read `order-management/application/usecase/CreateOrderUseCase.kt`** - See a complete use case
7. **Read `CLAUDE.md`** - Understand coding standards

---

## ❓ Common Questions

### Q: Why so many folders?
**A:** Separation of concerns. Business logic (`domain/`) is isolated from technical details (`infrastructure/`).

### Q: Where do I add a new command?
**A:**
1. Add to `domain/command/OrderCommand.kt`
2. Add decision logic in `domain/decider/OrderDecider.kt`
3. Add event in `domain/event/OrderEvents.kt`
4. Handler automatically picks it up!

### Q: Where do I add a new REST endpoint?
**A:** `modules/*/api/` - Add controller method

### Q: Can I run just one module?
**A:** Yes! Use `applications/order-service/` for example

### Q: Where are events stored?
**A:** In Temporal's workflow history (no separate database!)

### Q: How do I switch between modulith and microservices?
**A:**
- Modulith: Run `applications/modulith-app`
- Microservices: Run individual services in `applications/`

---

## 🎯 Summary

```
modules/              → Your BUSINESS LOGIC (domains)
  ├── domain/        → PURE business rules (no frameworks)
  ├── application/   → USE CASES (orchestration)
  ├── infrastructure/→ DATABASE, APIs, etc.
  └── api/          → REST endpoints

infrastructure/      → SHARED technical code
applications/        → RUNNABLE apps (modulith OR microservices)
```

**Key principle:** Business logic (`domain/`) knows NOTHING about HTTP, databases, or frameworks. It's pure Kotlin + Arrow-kt!

---

## 📖 Next Steps

1. Read `CLAUDE.md` for coding standards
2. Read `ARCHITECTURE.md` for architectural decisions
3. Try running `applications/modulith-app`
4. Explore `OrderDecider.kt` to see pure functional logic

Happy coding! 🚀
