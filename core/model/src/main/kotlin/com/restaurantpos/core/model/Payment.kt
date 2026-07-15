package com.restaurantpos.core.model

data class Payment(
    val id: String,
    val orderId: String,
    val amountMinorUnit: Long,
    val method: PaymentMethod,
    val status: PaymentStatus = PaymentStatus.UNPAID,
    val operatorId: String,
    val refundedPaymentId: String? = null,  // points to original if this is a refund
    val createdAt: Long,
)
