# Temporal Event Storage vs. Explicit Event Store

## Your Question: Why Both?

You're absolutely right to question this! **Temporal.io DOES have built-in event storage**, so having a separate event store creates redundancy. Let's analyze the options:

---

## Temporal's Built-In Event Storage

### What Temporal Stores

Temporal automatically stores:
- **Workflow Events**: WorkflowStarted, WorkflowCompleted, ActivityScheduled, ActivityCompleted
- **Decision Events**: Timer events, signal events, child workflow events
- **Activity Results**: Input/output data for activities
- **Full History**: Complete audit trail of workflow execution

### Temporal's Event Store Characteristics

```
✅ Durable and replicated
✅ Supports event replay (deterministic execution)
✅ Query workflow state and history
✅ Time-travel queries
✅ Built-in versioning
✅ Retention policies
```

---

## Current Architecture (Redundant)

```
┌─────────────────┐         ┌──────────────────┐
│   Order Service │         │ Temporal Workflow│
└────────┬────────┘         └────────┬─────────┘
         │                           │
         │ Domain Events             │ Workflow Events
         ▼                           ▼
┌─────────────────┐         ┌──────────────────┐
│  Event Store    │         │ Temporal Server  │
│  (PostgreSQL)   │         │ (Event Storage)  │
└─────────────────┘         └──────────────────┘
```

**Problem**: We're storing events TWICE!
- Domain events (OrderCreated, OrderConfirmed) → PostgreSQL Event Store
- Workflow events (ActivityCompleted) → Temporal's event store

---

## Option 1: Use ONLY Temporal (Recommended)

### Architecture

```
┌─────────────────┐
│   Order Service │
└────────┬────────┘
         │
         │ Trigger Workflow
         ▼
┌──────────────────────────────┐
│    Temporal Workflow         │
│  - OrderFulfillmentWorkflow  │
└────────┬─────────────────────┘
         │
         │ Store Domain Events as Signals/Activities
         ▼
┌──────────────────────────────┐
│    Temporal Event Storage    │
│  - Workflow History          │
│  - Domain Events (embedded)  │
│  - Full Audit Trail          │
└──────────────────────────────┘
```

### Implementation

**Store domain events as workflow state:**

```kotlin
@WorkflowInterface
interface OrderWorkflow {
    @WorkflowMethod
    fun processOrder(command: CreateOrderCommand): OrderResult

    @QueryMethod
    fun getEvents(): List<OrderDomainEvent>

    @SignalMethod
    fun applyEvent(event: OrderDomainEvent)
}

@WorkflowImpl
class OrderWorkflowImpl : OrderWorkflow {
    private val events = mutableListOf<OrderDomainEvent>()

    override fun processOrder(command: CreateOrderCommand): OrderResult {
        // Emit domain event
        val orderCreated = OrderCreatedEvent(...)
        events.add(orderCreated)

        // Signal emitted (stored in Temporal's history)
        Workflow.upsertSearchAttributes(...)

        // Continue workflow
        val paymentResult = paymentActivity.process(...)

        return OrderResult.Success(orderId)
    }

    override fun getEvents(): List<OrderDomainEvent> = events.toList()
}
```

### Benefits

✅ **Single Source of Truth**: Temporal is the only event store
✅ **Less Infrastructure**: No PostgreSQL event store needed
✅ **Built-in Replay**: Temporal handles event replay
✅ **Time Travel**: Query state at any point in time
✅ **Simpler**: Fewer moving parts

### Drawbacks

❌ **Unconventional**: Temporal is primarily for orchestration, not event sourcing
❌ **Query Limitations**: Querying domain events requires workflow queries
❌ **Read Models**: Harder to build read model projections
❌ **Coupling**: Domain events coupled to workflow execution

---

## Option 2: Use ONLY Event Store (Traditional)

Remove Temporal workflows entirely and use pure event sourcing with sagas.

### Architecture

```
┌─────────────────┐
│   Order Service │
└────────┬────────┘
         │ Domain Events
         ▼
┌─────────────────┐
│  Event Store    │
│  (PostgreSQL)   │
└────────┬────────┘
         │
         │ Event Subscriptions
         ▼
┌─────────────────┐
│  Saga Manager   │
│  (Process Mgr)  │
└─────────────────┘
```

### Benefits

✅ **Pure Event Sourcing**: Traditional CQRS/ES pattern
✅ **Flexible Queries**: Easy to build read models
✅ **Decoupled**: Events separate from orchestration

### Drawbacks

❌ **No Temporal Benefits**: Lose durable execution, retries, scheduling
❌ **Manual Saga Management**: Complex compensation logic
❌ **No Workflow Versioning**: Hard to evolve business processes

---

## Option 3: Hybrid (Current - Keep Both)

Use both but understand the separation of concerns.

### Clear Separation

**Event Store** → Domain Events (Event Sourcing)
- OrderCreated, OrderConfirmed, PaymentProcessed
- Aggregate reconstitution
- Read model projections
- Audit trail of domain changes

**Temporal** → Workflow Orchestration (Process Management)
- Long-running business processes
- Saga compensation
- Retries and timeouts
- Cross-service coordination

### When to Use Each

| Concern | Use Event Store | Use Temporal |
|---------|----------------|--------------|
| Aggregate state | ✅ | ❌ |
| Domain events | ✅ | ❌ |
| Read models | ✅ | ❌ |
| Sagas | ❌ | ✅ |
| Long-running processes | ❌ | ✅ |
| Compensation | ❌ | ✅ |
| Retries | ❌ | ✅ |

### Implementation

```kotlin
// Event Store: Domain events
val order = Order.create(customerId, items)
eventStore.save(order.events)  // OrderCreated event

// Temporal: Orchestration only
val workflowId = "order-${order.id}"
workflowClient.start(OrderFulfillmentWorkflow::class, orderId)
```

---

## Recommendation: **Option 1 (Use Only Temporal)**

### Why?

1. **Simpler Architecture**: One event storage system
2. **Temporal's Strengths**: Leverages durable execution, replay, and versioning
3. **Less Infrastructure**: No separate event store database
4. **Audit Trail**: Temporal provides complete history out of the box
5. **Evolution**: Easier to evolve workflows over time

### Migration Path

1. **Store domain events in workflow state** using `@QueryMethod`
2. **Use Search Attributes** for queryable event data
3. **Emit events as Activities** for external subscribers
4. **Remove PostgreSQL event store** infrastructure

### Example Refactored Code

```kotlin
@WorkflowInterface
interface OrderAggregateWorkflow {
    @WorkflowMethod
    fun execute(command: OrderCommand): OrderState

    @QueryMethod
    fun getState(): OrderState

    @QueryMethod
    fun getEvents(): List<OrderDomainEvent>
}

class OrderAggregateWorkflowImpl : OrderAggregateWorkflow {
    private var state: OrderState = OrderState.Initial
    private val events = mutableListOf<OrderDomainEvent>()

    override fun execute(command: OrderCommand): OrderState {
        return when (command) {
            is CreateOrder -> {
                val event = OrderCreatedEvent(...)
                events.add(event)
                state = state.apply(event)

                // Publish to external systems if needed
                publishActivity.publish(event)

                state
            }
        }
    }

    override fun getState(): OrderState = state
    override fun getEvents(): List<OrderDomainEvent> = events.toList()
}
```

---

## Alternative: **Option 3 (Keep Both) with Clear Boundaries**

If you want both, make it clear:

### Event Store: CQRS Read Side
- Build read model projections
- Query historical data
- Analytics and reporting

### Temporal: Write Side + Orchestration
- Command handling
- Workflow orchestration
- Saga coordination

---

## Decision Time

Which approach do you prefer?

1. **Simplify to Temporal only** - Remove event store infrastructure
2. **Keep both** - But clarify separation of concerns
3. **Traditional Event Sourcing** - Remove Temporal, use sagas

I'd recommend **Option 1** for this sample project - it demonstrates Temporal's power while avoiding redundancy. However, in production, **Option 3 (hybrid)** is common when you need both event sourcing capabilities AND workflow orchestration.

What do you think? Should we refactor to use Temporal as the primary event store?
