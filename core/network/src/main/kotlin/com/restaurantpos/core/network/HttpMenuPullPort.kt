package com.restaurantpos.core.network

import com.restaurantpos.core.model.Allergen
import com.restaurantpos.core.model.MenuItem
import com.restaurantpos.core.sync.MenuPullPort
import com.restaurantpos.core.sync.MenuPullResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

/**
 * Real HTTP implementation of [MenuPullPort].
 *
 * Protocol: GET [baseUrl]/sync/pull?since=<watermark> with JWT auth.
 * Returns 200 with `{ serverTime, menuItems: [...] }` — only items whose
 * server `updatedAt` is strictly greater than `since`.
 */
class HttpMenuPullPort(
    private val baseUrl: String,
    private val client: OkHttpClient,
    private val authToken: () -> String,
) : MenuPullPort {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun pullMenuItems(since: Long): MenuPullResult? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$baseUrl/sync/pull?since=$since")
            .header("Authorization", "Bearer ${authToken()}")
            .get()
            .build()

        try {
            client.newCall(request).execute().use { res ->
                if (!res.isSuccessful) return@withContext null
                val body = res.body?.string() ?: return@withContext null
                val dto = json.decodeFromString<SyncPullResponseDto>(body)
                MenuPullResult(
                    serverTime = dto.serverTime,
                    items = dto.menuItems.map { it.toDomain(json) },
                )
            }
        } catch (e: IOException) {
            null
        }
    }
}

@Serializable
private data class SyncPullResponseDto(
    val serverTime: Long,
    val menuItems: List<MenuItemDto> = emptyList(),
)

@Serializable
private data class MenuItemDto(
    val id: String,
    val names: String,
    val priceMinorUnit: Long,
    val taxRateId: String? = null,
    val categoryId: String,
    val course: Int = 1,
    val isSoldOut: Boolean = false,
    val imageUrl: String? = null,
    val allergens: String = "",
    val stockCount: Long? = null,
    val menuProfileIds: List<String> = emptyList(),
    val updatedAt: Long,
) {
    fun toDomain(json: Json): MenuItem = MenuItem(
        id = id,
        names = runCatching { json.decodeFromString<Map<String, String>>(names) }.getOrDefault(emptyMap()),
        priceMinorUnit = priceMinorUnit,
        taxRateId = taxRateId,
        categoryId = categoryId,
        course = course,
        isSoldOut = isSoldOut,
        imageUrl = imageUrl,
        allergens = if (allergens.isBlank()) emptySet()
                    else allergens.split("|").mapNotNull { runCatching { Allergen.valueOf(it) }.getOrNull() }.toSet(),
        stockCount = stockCount,
        menuProfileIds = menuProfileIds,
        updatedAt = updatedAt,
    )
}
