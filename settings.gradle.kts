rootProject.name = "spring-modulith-temporal"

// Modules
include(
    "modules:shared-kernel",
    "modules:order-management",
    "modules:payment",
    "modules:fulfillment",
    "modules:temporal-workflows"
)

// Infrastructure
include(
    "infrastructure:messaging"
)

// Applications
include(
    "applications:modulith-app",
    "applications:order-service",
    "applications:payment-service",
    "applications:fulfillment-service"
)
