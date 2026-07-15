package com.restaurantpos.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.restaurantpos.core.model.Payment
import com.restaurantpos.core.model.PaymentMethod
import com.restaurantpos.core.model.PaymentStatus

@Entity(
    tableName = "payments",
    foreignKeys = [ForeignKey(
        entity = OrderEntity::class,
        parentColumns = ["id"],
        childColumns = ["orderId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("orderId")],
)
data class PaymentEntity(
    @PrimaryKey val id: String,
    val orderId: String,
    val amountMinorUnit: Long,
    val method: PaymentMethod,
    val status: PaymentStatus,
    val operatorId: String,
    val refundedPaymentId: String?,
    val createdAt: Long,
) {
    fun toDomain() = Payment(
        id = id, orderId = orderId, amountMinorUnit = amountMinorUnit,
        method = method, status = status, operatorId = operatorId,
        refundedPaymentId = refundedPaymentId, createdAt = createdAt,
    )

    companion object {
        fun fromDomain(p: Payment) = PaymentEntity(
            id = p.id, orderId = p.orderId, amountMinorUnit = p.amountMinorUnit,
            method = p.method, status = p.status, operatorId = p.operatorId,
            refundedPaymentId = p.refundedPaymentId, createdAt = p.createdAt,
        )
    }
}
