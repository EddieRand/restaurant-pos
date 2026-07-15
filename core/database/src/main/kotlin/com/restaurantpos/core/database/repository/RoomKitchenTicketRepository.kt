package com.restaurantpos.core.database.repository

import com.restaurantpos.core.database.dao.KitchenTicketDao
import com.restaurantpos.core.database.entity.KitchenTicketEntity
import com.restaurantpos.core.domain.repository.KitchenTicketRepository
import com.restaurantpos.core.model.KitchenTicket
import com.restaurantpos.core.model.KitchenTicketStatus
import com.restaurantpos.core.sync.SyncEntityType
import com.restaurantpos.core.sync.SyncOperation
import com.restaurantpos.core.sync.SyncWriter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

class RoomKitchenTicketRepository(
    private val dao: KitchenTicketDao,
    private val syncWriter: SyncWriter? = null,
) : KitchenTicketRepository {

    override fun observeActive(): Flow<List<KitchenTicket>> =
        dao.observeActive().map { it.map(KitchenTicketEntity::toDomain) }

    override fun observeByStation(stationId: String): Flow<List<KitchenTicket>> =
        dao.observeByStation(stationId).map { it.map(KitchenTicketEntity::toDomain) }

    override suspend fun getById(id: String): KitchenTicket? =
        dao.getById(id)?.toDomain()

    override suspend fun getByOrder(orderId: String): List<KitchenTicket> =
        dao.getByOrder(orderId).map { it.toDomain() }

    override suspend fun save(ticket: KitchenTicket) {
        dao.upsert(KitchenTicketEntity.fromDomain(ticket))
        enqueueSync(ticket)
    }

    override suspend fun saveAll(tickets: List<KitchenTicket>) {
        dao.upsertAll(tickets.map(KitchenTicketEntity::fromDomain))
        tickets.forEach { enqueueSync(it) }
    }

    override suspend fun updateStatus(id: String, status: KitchenTicketStatus, bumpedAt: Long?) {
        val now = System.currentTimeMillis()
        dao.updateStatus(id, status.name, bumpedAt, now)
        dao.getById(id)?.let { enqueueSync(it.toDomain()) }
    }

    override suspend fun applyRemote(tickets: List<KitchenTicket>) {
        val toApply = tickets.filter { remote ->
            val local = dao.getById(remote.id)
            local == null || remote.updatedAt >= local.updatedAt
        }
        if (toApply.isNotEmpty()) {
            dao.upsertAll(toApply.map(KitchenTicketEntity::fromDomain))
        }
    }

    private suspend fun enqueueSync(ticket: KitchenTicket) {
        syncWriter?.enqueue(
            entityType = SyncEntityType.KITCHEN_TICKET,
            entityId = ticket.id,
            operation = SyncOperation.UPDATE,
            payload = ticketToJson(ticket),
        )
    }

    private fun ticketToJson(t: KitchenTicket): String = JSONObject().apply {
        put("id", t.id)
        put("orderId", t.orderId)
        put("orderItemIds", JSONArray(t.orderItemIds))
        put("stationId", t.stationId)
        put("course", t.course)
        put("status", t.status.name)
        put("createdAt", t.createdAt)
        put("bumpedAt", t.bumpedAt)
        put("updatedAt", t.updatedAt)
    }.toString()
}
