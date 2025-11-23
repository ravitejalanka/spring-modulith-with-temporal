package com.example.modulith.payment.domain

import arrow.core.Either
import arrow.core.raise.either
import com.example.modulith.order.domain.model.OrderId
import com.example.modulith.order.domain.model.PaymentId
import com.example.modulith.shared.domain.DomainError
import com.example.modulith.shared.domain.Money

/**
 * Payment aggregate (simplified)
 */
sealed class Payment(
    open val id: PaymentId,
    open val orderId: OrderId,
    open val amount: Money
) {
    data class PendingPayment(
        override val id: PaymentId,
        override val orderId: OrderId,
        override val amount: Money
    ) : Payment(id, orderId, amount) {

        fun process(): Either<PaymentError, ProcessedPayment> = either {
            // Simulate payment processing
            ProcessedPayment(id, orderId, amount, "txn_${id.value}")
        }

        fun fail(reason: String): FailedPayment {
            return FailedPayment(id, orderId, amount, reason)
        }
    }

    data class ProcessedPayment(
        override val id: PaymentId,
        override val orderId: OrderId,
        override val amount: Money,
        val transactionId: String
    ) : Payment(id, orderId, amount)

    data class FailedPayment(
        override val id: PaymentId,
        override val orderId: OrderId,
        override val amount: Money,
        val reason: String
    ) : Payment(id, orderId, amount)

    companion object {
        fun create(
            orderId: OrderId,
            amount: Money
        ): Either<PaymentError, PendingPayment> = either {
            PendingPayment(
                id = PaymentId.generate(),
                orderId = orderId,
                amount = amount
            )
        }
    }
}

sealed interface PaymentError : DomainError {
    data object InsufficientFunds : PaymentError {
        override val message = "Insufficient funds"
    }

    data object PaymentGatewayError : PaymentError {
        override val message = "Payment gateway error"
    }
}
