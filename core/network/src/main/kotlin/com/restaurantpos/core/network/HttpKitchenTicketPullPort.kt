package com.restaurantpos.core.network

import com.restaurantpos.core.model.KitchenTicket
import com.restaurantpos.core.model.KitchenTicketStatus
import com.restaurantpos.core.sync.KitchenTicketPullPort
import com.restaurantpos.core.sync.KitchenTicketPullResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

/**
 * Real HTTP implementation of [KitchenTicketPullPort].
 *
 * Protocol: GET [baseUrl]/sync/pull?since=<watermark> with JWT auth.
 * Returns 200 with `{ serverTime, kitchenTickets: [...] }` — only tickets whose
 * server `updatedAt` is strictly greater than `since`.
 */
class HttpKitchenTicketPullPort(
    private val baseUrl: String,
    private val client: OkHttpClient,
    private val authToken: () -> String,
) : KitchenTicketPullPort {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun pullKitchenTickets(since: Long): KitchenTicketPullResult? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$baseUrl/sync/pull?since=$since")
            .header("Authorization", "Bearer ${authToken()}")
            .get()
            .build()

        try {
            client.newCall(request).execute().use { res ->
                if (!res.isSuccessful) return@withContext null
                val body = res.body?.string() ?: return@withContext null
                val dto = json.decodeFromString<SyncPullKitchenTicketsResponseDto>(body)
                KitchenTicketPullResult(
                    serverTime = dto.serverTime,
                    tickets = dto.kitchenTickets.map { it.toDomain() },
                )
            }
        } catch (e: IOException) {
            null
        }
    }
}

@Serializable
private data class SyncPullKitchenTicketsResponseDto(
    val serverTime: Long,
    val kitchenTickets: List<KitchenTicketDto> = emptyList(),
)

@Serializable
private data class KitchenTicketDto(
    val id: String,
    val orderId: String,
    val orderItemIds: List<String>,
    val stationId: String,
    val course: Int = 1,
    val status: String = "NEW",
    val createdAt: Long,
    val bumpedAt: Long? = null,
    val updatedAt: Long,
) {
    fun toDomain() = KitchenTicket(
        id = id,
        orderId = orderId,
        orderItemIds = orderItemIds,
        stationId = stationId,
        course = course,
        status = runCatching { KitchenTicketStatus.valueOf(status) }.getOrDefault(KitchenTicketStatus.NEW),
        createdAt = createdAt,
        bumpedAt = bumpedAt,
        updatedAt = updatedAt,
    )
}
