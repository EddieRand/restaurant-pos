package com.restaurantpos.core.network

import com.restaurantpos.core.model.Allergen
import com.restaurantpos.core.model.Order
import com.restaurantpos.core.model.OrderFulfillmentStatus
import com.restaurantpos.core.model.OrderItem
import com.restaurantpos.core.model.OrderItemStatus
import com.restaurantpos.core.model.OrderStatus
import com.restaurantpos.core.model.OrderType
import com.restaurantpos.core.sync.OrderPullPort
import com.restaurantpos.core.sync.OrderPullResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

/**
 * Real HTTP implementation of [OrderPullPort].
 *
 * Protocol: GET [baseUrl]/sync/pull?since=<watermark> with JWT auth.
 * Returns 200 with `{ serverTime, orders: [...], orderItems: [...] }` — only orders whose
 * server `updatedAt` is strictly greater than `since` (their items ride along).
 */
class HttpOrderPullPort(
    private val baseUrl: String,
    private val client: OkHttpClient,
    private val authToken: () -> String,
) : OrderPullPort {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun pullOrders(since: Long): OrderPullResult? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$baseUrl/sync/pull?since=$since")
            .header("Authorization", "Bearer ${authToken()}")
            .get()
            .build()

        try {
            client.newCall(request).execute().use { res ->
                if (!res.isSuccessful) return@withContext null
                val body = res.body?.string() ?: return@withContext null
                val dto = json.decodeFromString<SyncPullOrdersResponseDto>(body)
                OrderPullResult(
                    serverTime = dto.serverTime,
                    orders = dto.orders.map { it.toDomain() },
                    orderItems = dto.orderItems.map { it.toDomain() },
                )
            }
        } catch (e: IOException) {
            null
        }
    }
}

@Serializable
private data class SyncPullOrdersResponseDto(
    val serverTime: Long,
    val orders: List<OrderPullDto> = emptyList(),
    val orderItems: List<OrderItemPullDto> = emptyList(),
)

@Serializable
private data class OrderPullDto(
    val id: String,
    val type: String = "DINE_IN",
    val tableId: String? = null,
    val guestCount: Int = 1,
    val sourceTerminalId: String = "",
    val operatorId: String = "",
    val subtotalMinorUnit: Long = 0,
    val taxTotalMinorUnit: Long = 0,
    val serviceChargeMinorUnit: Long = 0,
    val tipMinorUnit: Long = 0,
    val discountMinorUnit: Long = 0,
    val status: String = "DRAFT",
    val orderNotes: String = "",
    val createdAt: Long,
    val updatedAt: Long,
    val pickupCode: String? = null,
    val fulfillmentStatus: String = "NOT_READY",
) {
    fun toDomain() = Order(
        id = id,
        type = runCatching { OrderType.valueOf(type) }.getOrDefault(OrderType.DINE_IN),
        tableId = tableId,
        guestCount = guestCount,
        sourceTerminalId = sourceTerminalId,
        operatorId = operatorId,
        subtotalMinorUnit = subtotalMinorUnit,
        taxTotalMinorUnit = taxTotalMinorUnit,
        serviceChargeMinorUnit = serviceChargeMinorUnit,
        tipMinorUnit = tipMinorUnit,
        discountMinorUnit = discountMinorUnit,
        status = runCatching { OrderStatus.valueOf(status) }.getOrDefault(OrderStatus.DRAFT),
        orderNotes = orderNotes,
        createdAt = createdAt,
        updatedAt = updatedAt,
        pickupCode = pickupCode,
        fulfillmentStatus = runCatching { OrderFulfillmentStatus.valueOf(fulfillmentStatus) }
            .getOrDefault(OrderFulfillmentStatus.NOT_READY),
    )
}

@Serializable
private data class OrderItemPullDto(
    val id: String,
    val orderId: String,
    val menuItemId: String,
    val menuItemNameSnapshot: String = "{}",
    val quantity: Int = 1,
    val unitPriceMinorUnit: Long = 0,
    val taxRateId: String? = null,
    val course: Int = 1,
    val status: String = "PENDING",
    val notes: String = "",
    val categoryId: String? = null,
    val allergenSnapshot: String = "",
    val comboId: String? = null,
) {
    fun toDomain() = OrderItem(
        id = id,
        orderId = orderId,
        menuItemId = menuItemId,
        menuItemNameSnapshot = runCatching {
            Json.decodeFromString<Map<String, String>>(menuItemNameSnapshot)
        }.getOrDefault(emptyMap()),
        quantity = quantity,
        unitPriceMinorUnit = unitPriceMinorUnit,
        taxRateId = taxRateId,
        course = course,
        status = runCatching { OrderItemStatus.valueOf(status) }.getOrDefault(OrderItemStatus.PENDING),
        notes = notes,
        categoryId = categoryId,
        // 客户端约定用 "|" 分隔（见 OrderItemEntity），与 push 侧保持对称
        allergenSnapshot = allergenSnapshot.split("|")
            .mapNotNull { token -> token.trim().takeIf { it.isNotEmpty() } }
            .mapNotNull { runCatching { Allergen.valueOf(it) }.getOrNull() }
            .toSet(),
        comboId = comboId,
    )
}
