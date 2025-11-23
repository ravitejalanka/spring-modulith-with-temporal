package com.example.modulith.app

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication
import org.springframework.modulith.Modulith
import org.springframework.scheduling.annotation.EnableAsync

/**
 * Main application for running all modules as a modulith
 *
 * This application demonstrates:
 * - Spring Modulith for enforcing module boundaries
 * - Event-driven architecture with Spring Application Events
 * - Event sourcing with event store
 * - DDD with rich domain models
 * - Hexagonal architecture
 * - Temporal.io for workflow orchestration
 */
@Modulith(
    systemName = "Order Management System",
    sharedModules = ["shared-kernel"]
)
@SpringBootApplication(
    scanBasePackages = [
        "com.example.modulith.order",
        "com.example.modulith.payment",
        "com.example.modulith.fulfillment",
        "com.example.modulith.temporal",
        "com.example.modulith.infrastructure"
    ]
)
@ConfigurationPropertiesScan
@EnableAsync
class ModulithApplication

fun main(args: Array<String>) {
    runApplication<ModulithApplication>(*args)
}
