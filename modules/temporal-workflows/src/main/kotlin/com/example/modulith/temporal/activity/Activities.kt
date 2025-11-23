package com.example.modulith.temporal.activity

import com.example.modulith.shared.domain.Money
import io.temporal.activity.ActivityInterface
import io.temporal.activity.ActivityMethod
import java.util.UUID

/**
 * Activity for order operations
 */
@ActivityInterface
interface OrderActivity {

    @ActivityMethod
    fun validateOrder(orderId: UUID): Boolean

    @ActivityMethod
    fun completeOrder(orderId: UUID): Boolean
}

/**
 * Activity for payment operations
 */
@ActivityInterface
interface PaymentActivity {

    @ActivityMethod
    fun processPayment(orderId: UUID, amount: Money): Boolean

    @ActivityMethod
    fun refundPayment(orderId: UUID): Boolean
}

/**
 * Activity for fulfillment operations
 */
@ActivityInterface
interface FulfillmentActivity {

    @ActivityMethod
    fun reserveInventory(orderId: UUID): Boolean

    @ActivityMethod
    fun releaseInventory(orderId: UUID): Boolean

    @ActivityMethod
    fun initiateShipping(orderId: UUID): Boolean
}
