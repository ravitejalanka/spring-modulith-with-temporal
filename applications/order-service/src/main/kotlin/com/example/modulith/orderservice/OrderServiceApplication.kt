package com.example.modulith.orderservice

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

/**
 * Standalone Order microservice
 */
@SpringBootApplication(
    scanBasePackages = [
        "com.example.modulith.order",
        "com.example.modulith.infrastructure"
    ]
)
class OrderServiceApplication

fun main(args: Array<String>) {
    runApplication<OrderServiceApplication>(*args)
}
