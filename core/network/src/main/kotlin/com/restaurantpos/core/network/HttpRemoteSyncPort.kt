package com.restaurantpos.core.network

import com.restaurantpos.core.sync.RemoteSyncPort
import com.restaurantpos.core.sync.SyncRecord
import com.restaurantpos.core.sync.SyncResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

/**
 * Real HTTP implementation of [RemoteSyncPort].
 *
 * Protocol:
 * - POST [baseUrl]/sync/push with JSON body: { entityType, entityId, operation, payload, updatedAt }
 * - 200 → Accepted
 * - 409 → Conflict (server returns newer version in body)
 * - 410 → PermanentError (entity deleted/invalid on server)
 * - 401/403 → PermanentError (auth failure; caller should re-auth)
 * - 4xx (other) → PermanentError
 * - 5xx / IOException → NetworkError (retry)
 */
class HttpRemoteSyncPort(
    private val baseUrl: String,
    private val client: OkHttpClient,
    private val authToken: () -> String,
) : RemoteSyncPort {

    private val json = Json { ignoreUnknownKeys = true }
    private val mediaType = "application/json; charset=utf-8".toMediaType()

    override suspend fun push(record: SyncRecord): SyncResponse = withContext(Dispatchers.IO) {
        val body = buildBody(record)
        val request = Request.Builder()
            .url("$baseUrl/sync/push")
            .header("Authorization", "Bearer ${authToken()}")
            .header("Content-Type", "application/json")
            .post(body.toRequestBody(mediaType))
            .build()

        try {
            val response = client.newCall(request).execute()
            response.use { res ->
                when (res.code) {
                    200, 201, 204 -> SyncResponse.Accepted
                    409 -> {
                        val serverPayload = res.body?.string() ?: ""
                        SyncResponse.Conflict(serverPayload)
                    }
                    401, 403, 410 -> SyncResponse.PermanentError("HTTP ${res.code}")
                    in 400..499 -> SyncResponse.PermanentError("HTTP ${res.code}")
                    else -> SyncResponse.NetworkError(IOException("HTTP ${res.code}"))
                }
            }
        } catch (e: IOException) {
            SyncResponse.NetworkError(e)
        }
    }

    private fun buildBody(record: SyncRecord): String = json.encodeToString(
        SyncPushRequest(
            id = record.id,
            entityType = record.entityType.name,
            entityId = record.entityId,
            operation = record.operation.name,
            payload = record.payload,
            updatedAt = record.updatedAt,
        )
    )
}

@kotlinx.serialization.Serializable
private data class SyncPushRequest(
    val id: String,
    val entityType: String,
    val entityId: String,
    val operation: String,
    val payload: String,
    val updatedAt: Long,
)
