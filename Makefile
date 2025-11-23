.PHONY: help build test run-modulith run-microservices docker-up docker-down clean

help: ## Show this help message
	@echo 'Usage: make [target]'
	@echo ''
	@echo 'Available targets:'
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | sort | awk 'BEGIN {FS = ":.*?## "}; {printf "  \033[36m%-20s\033[0m %s\n", $$1, $$2}'

build: ## Build the project
	./gradlew build

test: ## Run all tests
	./gradlew test

clean: ## Clean build artifacts
	./gradlew clean

# Infrastructure
docker-up: ## Start all infrastructure (PostgreSQL, Kafka, Temporal)
	cd docker && docker-compose up -d

docker-up-dev: ## Start minimal infrastructure for dev (PostgreSQL, Temporal)
	cd docker && docker-compose -f docker-compose-dev.yml up -d

docker-down: ## Stop all infrastructure
	cd docker && docker-compose down

docker-logs: ## View docker logs
	cd docker && docker-compose logs -f

# Applications
run-modulith: ## Run as modulith (single application)
	./gradlew :applications:modulith-app:bootRun

run-microservices: ## Run as microservices mode (with Kafka)
	./gradlew :applications:modulith-app:bootRun --args='--spring.profiles.active=microservices'

run-order-service: ## Run order service independently
	./gradlew :applications:order-service:bootRun

# Development
create-order: ## Create a sample order
	@curl -X POST http://localhost:8080/api/orders \
		-H "Content-Type: application/json" \
		-d '{"customerId": "123e4567-e89b-12d3-a456-426614174000", "items": [{"productId": "789e4567-e89b-12d3-a456-426614174000", "productName": "Laptop", "quantity": 1, "unitPrice": "1299.99"}]}' \
		| jq '.'

temporal-ui: ## Open Temporal UI in browser
	@echo "Opening Temporal UI at http://localhost:8080"
	@open http://localhost:8080 || xdg-open http://localhost:8080 || echo "Please open http://localhost:8080 in your browser"

# Full setup
setup: docker-up-dev build ## Full setup (infrastructure + build)
	@echo "Setup complete! Run 'make run-modulith' to start the application"

quick-start: setup run-modulith ## Quick start (setup + run modulith)
