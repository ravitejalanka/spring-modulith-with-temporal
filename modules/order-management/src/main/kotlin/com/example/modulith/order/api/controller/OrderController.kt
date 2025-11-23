package com.example.modulith.order.api.controller

import com.example.modulith.order.api.dto.CreateOrderRequest
import com.example.modulith.order.api.dto.OrderResponse
import com.example.modulith.order.application.usecase.CreateOrderCommand
import com.example.modulith.order.application.usecase.CreateOrderUseCase
import com.example.modulith.order.application.usecase.GetOrderQuery
import com.example.modulith.order.application.usecase.GetOrderUseCase
import kotlinx.coroutines.runBlocking
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.util.UUID

/**
 * REST API controller for orders
 */
@RestController
@RequestMapping("/api/orders")
class OrderController(
    private val createOrderUseCase: CreateOrderUseCase,
    private val getOrderUseCase: GetOrderUseCase
) {

    @PostMapping
    fun createOrder(@RequestBody request: CreateOrderRequest): ResponseEntity<OrderResponse> = runBlocking {
        val command = CreateOrderCommand(
            customerId = request.customerId,
            items = request.items
        )

        createOrderUseCase.execute(command)
            .fold(
                { error ->
                    ResponseEntity
                        .badRequest()
                        .body(OrderResponse.Error(error.message))
                },
                { orderId ->
                    ResponseEntity
                        .status(HttpStatus.CREATED)
                        .body(OrderResponse.Created(orderId.value))
                }
            )
    }

    @GetMapping("/{orderId}")
    fun getOrder(@PathVariable orderId: UUID): ResponseEntity<OrderResponse> = runBlocking {
        val query = GetOrderQuery(orderId)

        getOrderUseCase.execute(query)
            .fold(
                { error ->
                    if (error.message.contains("not found")) {
                        ResponseEntity.notFound().build()
                    } else {
                        ResponseEntity
                            .badRequest()
                            .body(OrderResponse.Error(error.message))
                    }
                },
                { order ->
                    ResponseEntity.ok(OrderResponse.from(order))
                }
            )
    }
}
