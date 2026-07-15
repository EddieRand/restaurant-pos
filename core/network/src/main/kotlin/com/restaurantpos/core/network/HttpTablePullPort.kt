package com.restaurantpos.core.network

import com.restaurantpos.core.model.Table
import com.restaurantpos.core.model.TableStatus
import com.restaurantpos.core.sync.TablePullPort
import com.restaurantpos.core.sync.TablePullResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

/**
 * Real HTTP implementation of [TablePullPort].
 * Protocol: GET [baseUrl]/sync/tables?since=<watermark> with JWT auth.
 */
class HttpTablePullPort(
    private val baseUrl: String,
    private val client: OkHttpClient,
    private val authToken: () -> String,
) : TablePullPort {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun pullTables(since: Long): TablePullResult? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$baseUrl/sync/tables?since=$since")
            .header("Authorization", "Bearer ${authToken()}")
            .get()
            .build()

        try {
            client.newCall(request).execute().use { res ->
                if (!res.isSuccessful) return@withContext null
                val body = res.body?.string() ?: return@withContext null
                val dto = json.decodeFromString<TableSyncPullResponseDto>(body)
                TablePullResult(serverTime = dto.serverTime, tables = dto.tables.map { it.toDomain() })
            }
        } catch (e: IOException) {
            null
        }
    }
}

@Serializable
private data class TableSyncPullResponseDto(
    val serverTime: Long,
    val tables: List<TableSyncDto> = emptyList(),
)

@Serializable
private data class TableSyncDto(
    val id: String,
    val name: String,
    val sectionId: String,
    val capacity: Int = 4,
    val currentOrderId: String? = null,
    val status: String = "AVAILABLE",
    val updatedAt: Long = 0,
) {
    fun toDomain() = Table(
        id = id,
        name = name,
        sectionId = sectionId,
        capacity = capacity,
        currentOrderId = currentOrderId,
        status = runCatching { TableStatus.valueOf(status) }.getOrDefault(TableStatus.AVAILABLE),
        updatedAt = updatedAt,
    )
}
