# 🏗️ Architecture Decisions & Patterns

This document explains key architectural decisions, patterns, and answers common questions about our design.

---

## Question 1: Event Projections - Building Read Models

### ❓ Problem
"We need projections on events generated, so we can have our custom logic written in a decoupling manner"

### ✅ Solution: Projection Pattern

We implement the **Projection Pattern** (also known as **Event Handler Pattern**):

```
Commands (Write Side)                     Events                    Projections (Read Side)
     ↓                                      ↓                              ↓
CreateOrder → Decider → OrderCreatedEvent → Publish → OrderReadModelProjection
                                                    → OrderAnalyticsProjection
                                                    → OrderNotificationProjection
                                                    → [Add any projection you want]
```

### 📁 Implementation

**1. Domain Events** (already exist)
```kotlin
// domain/event/OrderEvents.kt
sealed interface OrderDomainEvent : DomainEvent

data class OrderCreatedEvent(...)
data class OrderConfirmedEvent(...)
data class OrderPaidEvent(...)
```

**2. Projection Interface** (NEW)
```kotlin
// application/projection/OrderEventHandler.kt
interface OrderProjection {
    suspend fun project(event: OrderDomainEvent): Either<DomainError, Unit>
}
```

**3. Read Model Projection** (NEW)
```kotlin
// application/projection/OrderReadModelProjection.kt
@Component
class OrderReadModelProjection : OrderProjection {
    private val readModels = ConcurrentHashMap<UUID, OrderReadModel>()

    override suspend fun project(event: OrderDomainEvent): Either<DomainError, Unit> = either {
        when (event) {
            is OrderCreatedEvent -> projectOrderCreated(event)
            is OrderPaidEvent -> projectOrderPaid(event)
            // ... handle each event
        }
    }

    // Query methods
    fun findById(orderId: UUID): OrderReadModel?
    fun findByCustomerId(customerId: UUID): List<OrderReadModel>
    fun findUnpaidOrders(): List<OrderReadModel>
}
```

**4. Event Listener** (NEW)
```kotlin
// infrastructure/projection/OrderEventListener.kt
@Component
class OrderEventListener(
    private val projections: List<OrderProjection> // Auto-wired!
) {
    @EventListener
    fun onOrderEvent(event: OrderDomainEvent) {
        projections.forEach { projection ->
            projection.project(event) // Each projection updates independently
        }
    }
}
```

**5. Command Handler publishes events** (UPDATED)
```kotlin
// application/handler/OrderCommandHandler.kt
class OrderCommandHandler(
    private val applicationEventPublisher: ApplicationEventPublisher // Added!
) {
    suspend fun handle(command: OrderCommand): Either<DomainError, OrderState> = either {
        val events = OrderDecider.decide(currentState, command).bind()

        storeEvents(events).bind()

        publishDomainEvents(events) // ← Projections listen here!

        publishIntegrationEvents(events).bind()
    }
}
```

### 🎯 Benefits

1. **Decoupled** - Projections don't know about commands or command handlers
2. **Multiple Views** - Can have many projections for different use cases:
   - `OrderReadModelProjection` - For queries
   - `OrderAnalyticsProjection` - For analytics dashboard
   - `OrderNotificationProjection` - For sending emails
   - `OrderSearchIndexProjection` - For Elasticsearch
3. **Independent Evolution** - Add/remove projections without changing domain
4. **CQRS** - Separate write model (OrderState) from read model (OrderReadModel)
5. **Scalable** - Each projection can be optimized for its use case

### 📊 Flow Diagram

```
┌──────────────────────────────────────────────────────────────┐
│ WRITE SIDE (Commands)                                        │
│                                                               │
│  CreateOrder Command                                         │
│       ↓                                                      │
│  OrderDecider.decide() → [OrderCreatedEvent]                │
│       ↓                                                      │
│  Store in Temporal (event store)                            │
│       ↓                                                      │
│  applicationEventPublisher.publishEvent(OrderCreatedEvent)  │
└───────────────────────────┬──────────────────────────────────┘
                            │
                            ↓ (Spring Event Bus)
┌──────────────────────────────────────────────────────────────┐
│ READ SIDE (Projections)                                      │
│                                                               │
│  OrderEventListener catches event                            │
│       ↓                                                      │
│  ┌────────────────────────────────────────┐                 │
│  │ OrderReadModelProjection               │                 │
│  │  → Updates in-memory/database          │                 │
│  │  → Fast queries available              │                 │
│  └────────────────────────────────────────┘                 │
│                                                               │
│  ┌────────────────────────────────────────┐                 │
│  │ OrderAnalyticsProjection               │                 │
│  │  → Updates analytics counters          │                 │
│  │  → Calculates metrics                  │                 │
│  └────────────────────────────────────────┘                 │
│                                                               │
│  ┌────────────────────────────────────────┐                 │
│  │ OrderNotificationProjection (future)   │                 │
│  │  → Sends email notifications           │                 │
│  │  → Sends SMS alerts                    │                 │
│  └────────────────────────────────────────┘                 │
└──────────────────────────────────────────────────────────────┘
```

### 💡 Example: Adding a New Projection

Want to send email notifications? Just create a new projection:

```kotlin
@Component
class OrderNotificationProjection(
    private val emailService: EmailService
) : OrderProjection {
    override suspend fun project(event: OrderDomainEvent): Either<DomainError, Unit> = either {
        when (event) {
            is OrderCreatedEvent -> {
                emailService.sendOrderConfirmation(event.customerId, event.orderId)
            }
            is OrderPaidEvent -> {
                emailService.sendPaymentReceipt(event.customerId, event.amount)
            }
            else -> { /* ignore */ }
        }
    }
}
```

**No changes needed anywhere else!** The event listener auto-discovers it via Spring DI.

---

## Question 2: Worker/Workflow Coupling

### ❓ Problem
"Why are we having the worker and workflow logic at the same place? Is that not tight coupling?"

### 🎯 You're Right - There IS Coupling!

Current structure:
```
modules/temporal-workflows/
├── workflow/
│   ├── OrderAggregateWorkflow.kt         ← Interface (Temporal)
│   └── OrderAggregateWorkflowImpl.kt     ← Implementation (uses domain)
└── config/
    └── TemporalConfig.kt                  ← Worker registration
```

**Coupling issues:**
1. `temporal-workflows` module depends on `order-management` domain
2. Workflow implementation mixes Temporal (infrastructure) with domain logic
3. Hard to test domain logic without Temporal
4. Violates "infrastructure depends on domain, not vice versa"

### 🔄 Better Approach: Separate Concerns

There are **3 approaches** we can take:

---

#### **Approach 1: Workflows as Thin Adapters** (Recommended)

Move workflow implementations to domain module's infrastructure layer:

```
modules/order-management/
├── domain/                              ← Pure logic
│   ├── decider/
│   │   └── OrderDecider.kt             ← Pure decision logic
│   └── command/state/event/            ← Domain primitives
│
├── infrastructure/
│   ├── persistence/
│   │   └── TemporalOrderRepository.kt  ← Already here!
│   └── workflow/                        ← NEW: Move here!
│       └── OrderAggregateWorkflowImpl.kt

modules/temporal-workflows/
├── workflow/
│   └── OrderAggregateWorkflow.kt       ← Keep interface only
└── config/
    └── TemporalConfig.kt                ← Worker registration
```

**Benefits:**
- Domain module owns its workflow implementation
- Temporal module only has interfaces and config
- Clear: "This is how Order aggregate is persisted in Temporal"
- Follows hexagonal architecture (workflow impl is an adapter)

**Trade-offs:**
- `order-management` now depends on Temporal SDK
- But it's only in infrastructure layer (acceptable)

---

#### **Approach 2: Separate Worker Service**

Create a separate worker application:

```
modules/
├── order-management/        ← Domain logic only
├── payment/
├── fulfillment/
└── shared-kernel/

applications/
├── modulith-app/            ← Main app (no workers)
├── order-worker-service/    ← NEW: Temporal workers
│   └── Runs workflows that talk to domain via API
├── order-service/
└── payment-service/
```

**Benefits:**
- Complete separation: workers can scale independently
- Domain module has ZERO Temporal dependencies
- Workers communicate with domain via API calls
- True microservices architecture

**Trade-offs:**
- More complex deployment
- Network calls between worker and domain
- Higher latency

---

#### **Approach 3: Keep As-Is (Current)**

Keep workflows in `temporal-workflows` module:

**Benefits:**
- Simple: All Temporal stuff in one place
- Easy to find all workflows
- Less code movement

**Trade-offs:**
- `temporal-workflows` depends on all domain modules
- Coupling between infrastructure and domain
- Harder to evolve independently

---

### 📊 Comparison Table

| Approach | Coupling | Complexity | Testability | Scalability |
|----------|----------|------------|-------------|-------------|
| **Approach 1** (Thin Adapters) | Low | Medium | High | High |
| **Approach 2** (Separate Service) | None | High | Highest | Highest |
| **Approach 3** (Current) | Medium | Low | Medium | Medium |

---

### ✅ Recommended: Approach 1 (Workflows as Thin Adapters)

**Why:**
- Balances pragmatism with good architecture
- Domain owns its persistence strategy
- Still follows hexagonal architecture
- Easy to migrate to Approach 2 later if needed

**Migration:**
1. Move `OrderAggregateWorkflowImpl` to `order-management/infrastructure/workflow/`
2. Update imports in `TemporalConfig`
3. Keep interface in `temporal-workflows` (for now, or move to shared-kernel)

**Result:**
```
modules/order-management/
├── domain/
│   └── decider/OrderDecider.kt           ← Pure business logic
├── application/
│   └── handler/OrderCommandHandler.kt    ← Orchestration
└── infrastructure/
    ├── workflow/
    │   └── OrderAggregateWorkflowImpl.kt ← Temporal adapter
    └── persistence/
        └── TemporalOrderRepository.kt    ← Repository adapter
```

---

### 🎓 Key Insight

**Workers and Workflows are Infrastructure!**

Think of them as:
- **Database drivers** - You wouldn't put PostgreSQL JDBC code in domain layer
- **Message queues** - You wouldn't put Kafka producers in domain layer
- **Temporal workflows** - Same thing! They're infrastructure adapters

**The domain should be agnostic:**
```kotlin
// Domain doesn't know about Temporal
fun decide(state: OrderState, command: OrderCommand): Either<Error, Events>

// Infrastructure adapts domain to Temporal
class OrderAggregateWorkflowImpl {
    fun execute(command: TemporalCommand) {
        val domainCommand = mapToDomainCommand(command)
        val events = OrderDecider.decide(state, domainCommand) // ← Use domain!
        storeInTemporal(events)
    }
}
```

---

## Summary

### Question 1: Projections
✅ **Added projection pattern** for decoupled read models
- `OrderEventHandler` interface
- `OrderReadModelProjection` for queries
- `OrderAnalyticsProjection` for metrics
- `OrderEventListener` routes events
- Fully decoupled from write side

### Question 2: Worker/Workflow Coupling
✅ **Identified coupling issue**
- Current: Temporal module depends on domain modules
- Better: Move workflow implementations to domain's infrastructure layer
- Best: Separate worker service (for true microservices)
- Recommended: Approach 1 (thin adapters in infrastructure)

---

## Next Steps

1. **Try projections:**
   ```kotlin
   // Events are now published automatically
   // Just query the projection:
   val order = orderReadModelProjection.findById(orderId)
   val unpaid = orderReadModelProjection.findUnpaidOrders()
   ```

2. **Consider refactoring workers:**
   - Move `OrderAggregateWorkflowImpl` to `order-management/infrastructure/workflow/`
   - Update `TemporalConfig` to scan that package
   - Better separation of concerns

3. **Add more projections:**
   - Email notifications
   - Search indexing
   - Analytics dashboards
   - Whatever you need!

The beauty: **Add projections without changing domain logic!** 🎉
