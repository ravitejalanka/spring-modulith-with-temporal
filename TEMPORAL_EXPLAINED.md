# 🕐 Temporal Workers & Workflows Explained (Simple Guide)

This document explains Temporal concepts in the simplest way possible, using real-world analogies.

---

## 🤔 The Confusion

When people first see Temporal, they ask:
- "What is a Workflow?"
- "What is a Worker?"
- "How do they work together?"
- "Why do I need both?"

Let's answer these with simple analogies!

---

## 🎭 Analogy 1: Restaurant Kitchen

Imagine a restaurant:

### 📋 **Workflow = Recipe (Instructions)**

A workflow is like a **recipe** - it's a set of instructions:

```kotlin
// This is a RECIPE for making an order
@WorkflowInterface
interface OrderWorkflow {
    @WorkflowMethod
    fun processOrder(order: Order): Result
}

class OrderWorkflowImpl : OrderWorkflow {
    override fun processOrder(order: Order): Result {
        // Step 1: Validate order
        validateOrder(order)

        // Step 2: Charge payment
        chargePayment(order)

        // Step 3: Ship order
        shipOrder(order)

        // Done!
        return Result.Success
    }
}
```

**Recipe says:**
1. First, validate the order
2. Then, charge payment
3. Finally, ship the order

### 👨‍🍳 **Worker = Chef (Does the work)**

A worker is like a **chef** - it executes the recipe:

```kotlin
val worker = workerFactory.newWorker("kitchen-queue")
worker.registerWorkflowImplementationTypes(OrderWorkflowImpl::class.java)
workerFactory.start() // Chef starts working!
```

**Chef:**
- Reads the recipe (workflow code)
- Actually cooks the food (executes the steps)
- Can be stopped and restarted
- Multiple chefs can work on different recipes

### 🔔 **Workflow Client = Customer (Starts the order)**

```kotlin
val workflow = workflowClient.newWorkflowStub(OrderWorkflow::class.java)
workflow.processOrder(myOrder) // Customer places order
```

---

## 🏭 Analogy 2: Factory Assembly Line

### 📐 **Workflow = Blueprint**

A workflow is a **blueprint** showing how to assemble a car:

```
Step 1: Install engine
Step 2: Attach wheels
Step 3: Paint car
Step 4: Quality check
```

### 🤖 **Worker = Robot on Assembly Line**

A worker is a **robot** that follows the blueprint:
- Reads the blueprint
- Performs each step
- Can be powered off/on
- Multiple robots can work on different cars

---

## 💡 In Our Project

Let me show you exactly how we use Workers and Workflows:

### Example 1: Order Aggregate Workflow (Event Store)

#### The Workflow (Recipe):
```kotlin
@WorkflowInterface
interface OrderAggregateWorkflow {
    @WorkflowMethod
    fun execute(command: OrderCommand): OrderCommandResult

    @QueryMethod
    fun getEvents(): List<OrderDomainEvent>
}
```

**Translation:**
- "This is a recipe for handling order commands"
- "You can also query what events happened"

#### The Worker (Chef):
```kotlin
@Bean("aggregateWorker")
fun aggregateWorker(
    workerFactory: WorkerFactory,
    properties: TemporalProperties
): Worker {
    val worker = workerFactory.newWorker(properties.aggregateTaskQueue)

    // Register the recipe with this chef
    worker.registerWorkflowImplementationTypes(OrderAggregateWorkflowImpl::class.java)

    return worker
}
```

**Translation:**
- "Create a chef named 'aggregateWorker'"
- "Tell this chef to work on queue 'order-aggregate-task-queue'"
- "Teach the chef the OrderAggregateWorkflow recipe"

---

## 🔄 Complete Flow Diagram

```
┌─────────────────────────────────────────────────────────────┐
│                     YOUR APPLICATION                         │
│                                                              │
│  Controller -> UseCase -> CommandHandler                    │
│                              |                               │
│                              | Send command                  │
│                              ↓                               │
│                      ┌───────────────┐                       │
│                      │ Workflow      │ ← (Recipe/Blueprint) │
│                      │ Client        │                       │
│                      └───────┬───────┘                       │
│                              |                               │
└──────────────────────────────┼───────────────────────────────┘
                               |
                               | Sends to Task Queue
                               ↓
                    ┌──────────────────────┐
                    │  TEMPORAL SERVER     │
                    │  (Task Queue)        │
                    │                      │
                    │  "order-aggregate-   │
                    │   task-queue"        │
                    └──────────┬───────────┘
                               |
                               | Worker polls for work
                               ↓
┌─────────────────────────────────────────────────────────────┐
│                      WORKER                                  │
│                    (The Chef)                                │
│                                                              │
│  ┌──────────────────────────────────────┐                   │
│  │  OrderAggregateWorkflowImpl          │                   │
│  │  (Recipe Implementation)             │                   │
│  │                                      │                   │
│  │  1. Load current state               │                   │
│  │  2. Use Decider to decide events     │                   │
│  │  3. Store events                     │                   │
│  │  4. Return result                    │                   │
│  └──────────────────────────────────────┘                   │
│                                                              │
└──────────────────────────────────────────────────────────────┘
```

---

## 📝 Step-by-Step Example

Let's trace what happens when you create an order:

### Step 1: User makes request
```kotlin
POST /orders
{
  "customerId": "123",
  "items": [...]
}
```

### Step 2: Controller → UseCase → CommandHandler
```kotlin
// CreateOrderUseCase.kt
commandHandler.handle(OrderCommand.CreateOrder(...))
```

### Step 3: CommandHandler creates workflow stub
```kotlin
// OrderCommandHandler.kt
val workflow = workflowClient.newWorkflowStub(
    OrderAggregateWorkflow::class.java,
    WorkflowOptions.newBuilder()
        .setTaskQueue("order-aggregate-task-queue")  // Put on THIS queue
        .build()
)
```

**Translation:**
- "Hey Temporal, I want to use the OrderAggregateWorkflow recipe"
- "Put this work on the 'order-aggregate-task-queue' queue"

### Step 4: Temporal Server receives the request
Temporal server says:
- "Got it! I'll put this on the 'order-aggregate-task-queue'"
- "Now I'll wait for a worker to pick it up"

### Step 5: Worker polls for work
```kotlin
// Worker constantly asks: "Any work for me?"
while (true) {
    task = temporal.poll("order-aggregate-task-queue")
    if (task != null) {
        executeWorkflow(task)
    }
}
```

**Translation:**
- Worker is like a chef checking the order board
- "Any new orders? Any new orders?"
- When it sees one: "Got it! I'll make this!"

### Step 6: Worker executes the workflow
```kotlin
// OrderAggregateWorkflowImpl.kt
override fun execute(command: OrderCommand): OrderCommandResult {
    val domainCommand = mapToDomainCommand(command)
    val events = OrderDecider.decide(currentState, domainCommand)

    // Store events in Temporal's history
    events.forEach { event -> this.events.add(event) }

    // Evolve state
    currentState = OrderDecider.rehydrate(events)

    return OrderCommandResult.Success(...)
}
```

### Step 7: Result returns to caller
```
Worker → Temporal Server → Workflow Client → CommandHandler → UseCase → Controller → User
```

---

## 🎯 Key Concepts

### 1. **Workflow = Recipe (Code)**
- It's just **code** - a class implementing an interface
- Describes **WHAT** to do
- **Deterministic** - same input = same output
- Can run for seconds, days, or months!

```kotlin
@WorkflowInterface
interface MyWorkflow {
    @WorkflowMethod
    fun doSomething(): Result
}
```

### 2. **Worker = Executor (Process)**
- It's a **running process** (like your Spring Boot app)
- **Executes** the workflow code
- Polls Temporal server for work
- Can be stopped/started anytime

```kotlin
val worker = workerFactory.newWorker("my-queue")
worker.registerWorkflowImplementationTypes(MyWorkflowImpl::class.java)
```

### 3. **Task Queue = Work Queue**
- Named queue where work is placed
- Workers listen to specific queues
- Like a "to-do list" for workers

```
Queue: "order-aggregate-task-queue"
- Task 1: Process order ABC
- Task 2: Process order XYZ
- Task 3: Process order 123
```

### 4. **Workflow Client = Starter**
- How you **start** a workflow
- Like placing an order with a restaurant

```kotlin
val workflow = workflowClient.newWorkflowStub(MyWorkflow::class.java)
workflow.doSomething() // Start the workflow!
```

---

## 🏗️ In Our Project - Two Workers

We have **TWO** workers for different purposes:

### Worker 1: Aggregate Worker (Event Store)
```kotlin
@Bean("aggregateWorker")
fun aggregateWorker(workerFactory: WorkerFactory): Worker {
    val worker = workerFactory.newWorker("order-aggregate-task-queue")
    worker.registerWorkflowImplementationTypes(OrderAggregateWorkflowImpl::class.java)
    return worker
}
```

**Purpose:** Store events in Temporal (replaces PostgreSQL event store)

**Workflows it handles:**
- `OrderAggregateWorkflow` - Stores domain events

**When it runs:**
- Every time you create/update an order

---

### Worker 2: Fulfillment Worker (Saga Orchestration)
```kotlin
@Bean("fulfillmentWorker")
fun fulfillmentWorker(workerFactory: WorkerFactory): Worker {
    val worker = workerFactory.newWorker("order-fulfillment-task-queue")
    worker.registerWorkflowImplementationTypes(OrderFulfillmentWorkflowImpl::class.java)
    worker.registerActivitiesImplementations(orderActivity, paymentActivity, ...)
    return worker
}
```

**Purpose:** Orchestrate long-running sagas

**Workflows it handles:**
- `OrderFulfillmentWorkflow` - Coordinates order → payment → fulfillment

**When it runs:**
- For complex multi-step processes

---

## 🤯 Common Confusions Explained

### Q1: "Do I need a worker running?"
**A:** YES! Without a worker, your workflows won't execute.

Think of it this way:
- Workflow = Recipe written in a cookbook
- Worker = Chef who reads and cooks the recipe

Without a chef, the recipe just sits there!

---

### Q2: "Can I have multiple workers?"
**A:** YES! You can have many workers for scalability.

```
Worker 1 ─┐
Worker 2 ─┼──→ "order-aggregate-task-queue" ──→ Temporal
Worker 3 ─┘
```

All three workers can process orders from the same queue. This is how you scale!

---

### Q3: "Where does the worker run?"
**A:** In your Spring Boot application!

When you start your app:
```bash
./gradlew bootRun
```

Temporal workers start automatically:
```
Starting worker: aggregateWorker on queue order-aggregate-task-queue
Starting worker: fulfillmentWorker on queue order-fulfillment-task-queue
Workers started and polling for tasks!
```

---

### Q4: "What if my worker crashes?"
**A:** No problem! Temporal is fault-tolerant.

**Scenario:**
1. Worker is processing order ABC
2. Worker crashes (power outage, out of memory, etc.)
3. Restart your application
4. Worker resumes from where it left off!

**Temporal remembers:**
- Which step you were on
- All previous results
- Continues from there

This is the **MAGIC** of Temporal! 🎩✨

---

### Q5: "What's the difference between Workflow and Activity?"

Great question!

| Workflow | Activity |
|----------|----------|
| Long-running (days/months) | Short-lived (seconds/minutes) |
| Deterministic (pure logic) | Can do I/O (database, API calls) |
| Must be replay-safe | Can have side effects |
| Orchestrates | Does actual work |

**Example:**

```kotlin
// WORKFLOW - Orchestrates the process
class OrderWorkflowImpl : OrderWorkflow {
    private val paymentActivity = Workflow.newActivityStub(PaymentActivity::class.java)

    override fun processOrder(order: Order): Result {
        // Workflow orchestrates
        val paymentResult = paymentActivity.chargeCard(order.amount)  // Calls activity
        val shipResult = shippingActivity.shipOrder(order.id)         // Calls activity

        return Result.Success
    }
}

// ACTIVITY - Does the actual work
class PaymentActivityImpl : PaymentActivity {
    override fun chargeCard(amount: Money): PaymentResult {
        // This actually calls Stripe API
        return stripeClient.charge(amount)
    }
}
```

**In our project:**
- We use workflows as event stores (storing state)
- We could use activities to call external APIs

---

## 📊 Visual Summary

```
┌─────────────────────────────────────────────────────────┐
│                                                         │
│  YOUR CODE                                              │
│                                                         │
│  commandHandler.handle(CreateOrder)                    │
│        │                                                │
│        │ Creates workflow stub                          │
│        ↓                                                │
│  workflowClient.newWorkflowStub(...)                   │
│        │                                                │
│        │ Sends to Temporal                              │
│        ↓                                                │
└────────┼────────────────────────────────────────────────┘
         │
         ↓
┌─────────────────────────────────────────────────────────┐
│  TEMPORAL SERVER (External Service)                     │
│                                                         │
│  Task Queue: "order-aggregate-task-queue"              │
│  [ Task 1 ] [ Task 2 ] [ Task 3 ]                      │
│                                                         │
└────────┬────────────────────────────────────────────────┘
         │
         │ Worker polls
         ↓
┌─────────────────────────────────────────────────────────┐
│  WORKER (Part of your Spring Boot app)                  │
│                                                         │
│  while (true) {                                         │
│    task = poll("order-aggregate-task-queue")           │
│    if (task) executeWorkflow(task)                     │
│  }                                                      │
│                                                         │
│  Executes: OrderAggregateWorkflowImpl                  │
│    └─> OrderDecider.decide(...)                        │
│    └─> Store events                                    │
│    └─> Return result                                   │
└─────────────────────────────────────────────────────────┘
```

---

## 🎓 Simple Mental Model

**Think of it like Uber:**

1. **You (Client)** - Request a ride
2. **Uber App (Temporal Server)** - Receives request, puts it in a queue
3. **Driver (Worker)** - Polls for ride requests, accepts one
4. **Route (Workflow)** - Step-by-step directions to destination
5. **Driving (Execution)** - Driver follows the route

If the driver's car breaks down (worker crashes):
- Uber remembers where you were
- Sends another driver
- New driver continues from that point

---

## ✅ Quick Checklist

To use Temporal, you need:

- [ ] **Temporal Server running** (Docker Compose)
  ```bash
  docker-compose up -d
  ```

- [ ] **Workflow Interface** (Recipe definition)
  ```kotlin
  @WorkflowInterface
  interface MyWorkflow { ... }
  ```

- [ ] **Workflow Implementation** (Recipe steps)
  ```kotlin
  class MyWorkflowImpl : MyWorkflow { ... }
  ```

- [ ] **Worker configured** (Chef ready to work)
  ```kotlin
  worker.registerWorkflowImplementationTypes(MyWorkflowImpl::class.java)
  ```

- [ ] **Worker started** (Chef starts working)
  ```kotlin
  workerFactory.start()
  ```

- [ ] **Client code** (Start the workflow)
  ```kotlin
  val workflow = workflowClient.newWorkflowStub(MyWorkflow::class.java)
  workflow.execute()
  ```

---

## 🎯 Key Takeaways

1. **Workflow = Instructions** (recipe, blueprint, code)
2. **Worker = Executor** (chef, robot, process that runs the code)
3. **Task Queue = Work inbox** (where work waits to be picked up)
4. **Workflow Client = Starter** (how you begin a workflow)

**The Power:**
- Workflows can run for MONTHS
- Workers can crash and restart - workflow continues
- Temporal tracks EVERYTHING
- Built-in retry, timeout, and error handling

---

## 🚀 Next Steps

1. **See it in action:**
   ```bash
   # Terminal 1: Start Temporal Server
   docker-compose up

   # Terminal 2: Start your app (workers start automatically)
   ./gradlew bootRun

   # Terminal 3: Make a request
   curl -X POST http://localhost:8080/orders -d '{...}'
   ```

2. **Watch the Temporal UI:**
   - Open http://localhost:8080/temporal (Temporal Web UI)
   - See your workflows running in real-time!
   - See event history
   - See which workers are connected

3. **Experiment:**
   - Start a long-running workflow
   - Stop your app (kill the worker)
   - Restart your app
   - Watch the workflow continue! 🎉

---

## 📚 Further Reading

- Our code: `modules/temporal-workflows/src/main/kotlin/.../config/TemporalConfig.kt`
- Worker registration: Look for `@Bean("aggregateWorker")` and `@Bean("fulfillmentWorker")`
- Workflow implementations: `OrderAggregateWorkflowImpl.kt`

**Remember:** Workers and Workflows work together like chefs and recipes. You need both! 👨‍🍳📋

---

Happy learning! If you still have questions, check the examples in our code or read through `TemporalConfig.kt` to see how we register workers. 🎓
