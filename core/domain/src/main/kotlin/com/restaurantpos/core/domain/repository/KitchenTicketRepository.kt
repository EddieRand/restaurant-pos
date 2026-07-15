package com.restaurantpos.core.domain.repository

import com.restaurantpos.core.model.KitchenTicket
import com.restaurantpos.core.model.KitchenTicketStatus
import kotlinx.coroutines.flow.Flow

interface KitchenTicketRepository {
    /** Live stream of all active (non-DONE) tickets, ordered by createdAt. */
    fun observeActive(): Flow<List<KitchenTicket>>
    /** Live stream for a specific station — used by individual KDS screens. */
    fun observeByStation(stationId: String): Flow<List<KitchenTicket>>
    suspend fun getById(id: String): KitchenTicket?
    suspend fun getByOrder(orderId: String): List<KitchenTicket>
    suspend fun save(ticket: KitchenTicket)
    suspend fun saveAll(tickets: List<KitchenTicket>)
    suspend fun updateStatus(id: String, status: KitchenTicketStatus, bumpedAt: Long? = null)
    /** Apply tickets pulled from the server (last-write-wins) without re-enqueuing an outbound sync push. */
    suspend fun applyRemote(tickets: List<KitchenTicket>)
}
