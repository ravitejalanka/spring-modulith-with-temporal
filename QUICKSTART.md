# Quick Start Guide

Get up and running with the sample application in 5 minutes!

## Prerequisites

- JDK 21+
- Docker and Docker Compose
- curl (for testing)

## Step-by-Step

### 1. Clone and Navigate

```bash
cd spring-modulith-with-temporal
```

### 2. Start Infrastructure

Start PostgreSQL and Temporal:

```bash
cd docker
docker-compose -f docker-compose-dev.yml up -d
cd ..
```

Wait about 30 seconds for services to be ready.

### 3. Build the Project

```bash
./gradlew build
```

### 4. Run the Modulith

```bash
./gradlew :applications:modulith-app:bootRun
```

Wait for the application to start (look for "Started ModulithApplication").

### 5. Create an Order

In a new terminal:

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

You should see a response with an `orderId`.

### 6. Get Order Details

```bash
# Replace ORDER_ID with the ID from step 5
curl http://localhost:8080/api/orders/ORDER_ID
```

### 7. View Temporal Workflows

Open http://localhost:8080 in your browser to see the Temporal UI with running workflows.

## Using the Makefile (Optional)

If you have `make` installed:

```bash
# Full setup and run
make setup
make run-modulith

# Create a sample order
make create-order

# View Temporal UI
make temporal-ui
```

## What Just Happened?

1. **Order Created**: The order was created in the CONFIRMED state
2. **Event Stored**: All events were saved to the event store (PostgreSQL)
3. **Integration Event Published**: An `OrderPlacedIntegrationEvent` was published via Spring Events
4. **Payment Processed**: Payment module listened to the event and processed payment
5. **Fulfillment Initiated**: Fulfillment module listened to payment event and started fulfillment
6. **Temporal Workflow**: (Optional) A Temporal workflow orchestrated the entire saga

All of this happened in a single JVM process using Spring Application Events!

## Next Steps

- Read the [README.md](README.md) for detailed documentation
- Review the [ARCHITECTURE.md](ARCHITECTURE.md) for architectural patterns
- Explore the code starting with `modules/order-management/src/main/kotlin/com/example/modulith/order/domain/model/Order.kt`
- Try running in microservices mode: `make run-microservices`

## Troubleshooting

### Port Already in Use

If port 8080 is already in use, you can change it in:
- `applications/modulith-app/src/main/resources/application.yml`

### Database Connection Issues

Make sure PostgreSQL is running:

```bash
docker ps | grep postgres
```

If not, restart infrastructure:

```bash
cd docker
docker-compose -f docker-compose-dev.yml down
docker-compose -f docker-compose-dev.yml up -d
```

### Build Failures

Clean and rebuild:

```bash
./gradlew clean build
```

## Clean Up

Stop the application (Ctrl+C) and then:

```bash
cd docker
docker-compose -f docker-compose-dev.yml down
```

To remove volumes (database data):

```bash
docker-compose -f docker-compose-dev.yml down -v
```
