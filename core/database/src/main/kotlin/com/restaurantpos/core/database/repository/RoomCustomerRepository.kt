package com.restaurantpos.core.database.repository

import com.restaurantpos.core.database.dao.CustomerDao
import com.restaurantpos.core.database.entity.CustomerEntity
import com.restaurantpos.core.database.entity.LoyaltyTransactionEntity
import com.restaurantpos.core.domain.repository.CustomerRepository
import com.restaurantpos.core.model.Customer
import com.restaurantpos.core.model.LoyaltyTransaction
import com.restaurantpos.core.model.LoyaltyTxnType
import com.restaurantpos.core.sync.SyncEntityType
import com.restaurantpos.core.sync.SyncOperation
import com.restaurantpos.core.sync.SyncWriter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONObject
import java.util.UUID

class RoomCustomerRepository(
    private val dao: CustomerDao,
    private val syncWriter: SyncWriter,
) : CustomerRepository {

    override fun observeAll(): Flow<List<Customer>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun search(query: String): List<Customer> =
        dao.search(query).map { it.toDomain() }

    override suspend fun getById(id: String): Customer? =
        dao.getById(id)?.toDomain()

    override suspend fun getByPhone(phone: String): Customer? =
        dao.getByPhone(phone)?.toDomain()

    override suspend fun save(customer: Customer) {
        val stamped = customer.copy(updatedAt = System.currentTimeMillis())
        dao.upsert(CustomerEntity.fromDomain(stamped))
        enqueue(stamped)
    }

    override suspend fun addPoints(
        customerId: String,
        points: Long,
        orderId: String?,
        description: String,
    ) {
        dao.adjustPoints(customerId, points)
        dao.insertTransaction(
            LoyaltyTransactionEntity(
                id = UUID.randomUUID().toString(),
                customerId = customerId,
                orderId = orderId,
                type = if (points >= 0) "EARN" else "REDEEM",
                points = points,
                description = description,
                createdAt = System.currentTimeMillis(),
            )
        )
        // Bump updatedAt + push so the loyalty change propagates cross-device.
        dao.getById(customerId)?.let { entity ->
            val stamped = entity.toDomain().copy(updatedAt = System.currentTimeMillis())
            dao.upsert(CustomerEntity.fromDomain(stamped))
            enqueue(stamped)
        }
    }

    override suspend fun getTransactions(customerId: String): List<LoyaltyTransaction> =
        dao.getTransactions(customerId).map { it.toDomain() }

    /** Last-write-wins by [Customer.updatedAt] so a stale pull never clobbers a local change. */
    override suspend fun applyRemote(customers: List<Customer>) {
        customers.forEach { remote ->
            val local = dao.getById(remote.id)
            if (local == null || remote.updatedAt >= local.updatedAt) {
                dao.upsert(CustomerEntity.fromDomain(remote))
            }
        }
    }

    private suspend fun enqueue(c: Customer) {
        // Field names mirror SyncPushProcessor.processCustomer — keep both sides in sync.
        val payload = JSONObject().apply {
            put("id", c.id)
            put("name", c.name)
            put("phone", c.phone)
            put("email", c.email)
            put("gender", c.gender)
            put("birthday", c.birthday)
            put("tags", c.tags.joinToString("|"))
            put("notes", c.notes)
            put("totalSpendMinorUnit", c.totalSpendMinorUnit)
            put("loyaltyPoints", c.loyaltyPoints)
            put("membershipTierId", c.membershipTierId)
            put("totalVisits", c.totalVisits)
            put("lastVisitAt", c.lastVisitAt)
            put("registeredAt", c.registeredAt)
            put("updatedAt", c.updatedAt)
        }.toString()
        syncWriter.enqueue(
            entityType = SyncEntityType.CUSTOMER,
            entityId = c.id,
            operation = SyncOperation.UPDATE,
            payload = payload,
        )
    }
}

private fun CustomerEntity.toDomain() = Customer(
    id = id, name = name, phone = phone, email = email, gender = gender,
    birthday = birthday, tags = tags.split("|").filter { it.isNotEmpty() },
    notes = notes, totalSpendMinorUnit = totalSpendMinorUnit,
    loyaltyPoints = loyaltyPoints, membershipTierId = membershipTierId,
    totalVisits = totalVisits, lastVisitAt = lastVisitAt, registeredAt = registeredAt,
    updatedAt = updatedAt,
)

private fun CustomerEntity.Companion.fromDomain(c: Customer) = CustomerEntity(
    id = c.id, name = c.name, phone = c.phone, email = c.email, gender = c.gender,
    birthday = c.birthday, tags = c.tags.joinToString("|"), notes = c.notes,
    totalSpendMinorUnit = c.totalSpendMinorUnit, loyaltyPoints = c.loyaltyPoints,
    membershipTierId = c.membershipTierId, totalVisits = c.totalVisits,
    lastVisitAt = c.lastVisitAt, registeredAt = c.registeredAt, updatedAt = c.updatedAt,
)

private fun LoyaltyTransactionEntity.toDomain() = LoyaltyTransaction(
    id = id, customerId = customerId, orderId = orderId,
    type = LoyaltyTxnType.valueOf(type),
    points = points, description = description, createdAt = createdAt,
)
