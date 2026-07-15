package com.restaurantpos.core.database.repository

import com.restaurantpos.core.database.dao.PaymentDao
import com.restaurantpos.core.database.entity.PaymentEntity
import com.restaurantpos.core.domain.repository.PaymentRepository
import com.restaurantpos.core.model.Payment
import com.restaurantpos.core.model.PaymentStatus
import com.restaurantpos.core.sync.SyncEntityType
import com.restaurantpos.core.sync.SyncOperation
import com.restaurantpos.core.sync.SyncWriter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomPaymentRepository(
    private val paymentDao: PaymentDao,
    private val syncWriter: SyncWriter,
) : PaymentRepository {

    override fun observeByOrder(orderId: String): Flow<List<Payment>> =
        paymentDao.observeByOrder(orderId).map { list -> list.map { it.toDomain() } }

    override suspend fun getByOrder(orderId: String): List<Payment> =
        paymentDao.getByOrder(orderId).map { it.toDomain() }

    override suspend fun getById(id: String): Payment? =
        paymentDao.getById(id)?.toDomain()

    override suspend fun save(payment: Payment) {
        paymentDao.upsert(PaymentEntity.fromDomain(payment))
        syncWriter.enqueue(
            entityType = SyncEntityType.PAYMENT,
            entityId = payment.id,
            operation = SyncOperation.CREATE,
            payload = """{"id":"${payment.id}","orderId":"${payment.orderId}","amountMinorUnit":${payment.amountMinorUnit},"method":"${payment.method}","status":"${payment.status}","operatorId":"${payment.operatorId}","createdAt":${payment.createdAt}}""",
        )
    }

    override suspend fun updateStatus(id: String, status: PaymentStatus) {
        paymentDao.updateStatus(id, status)
        syncWriter.enqueue(
            entityType = SyncEntityType.PAYMENT,
            entityId = id,
            operation = SyncOperation.UPDATE,
            payload = """{"id":"$id","status":"$status"}""",
        )
    }
}
