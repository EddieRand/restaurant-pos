package com.restaurantpos.core.network

import com.restaurantpos.core.model.User
import com.restaurantpos.core.sync.UserPullPort
import com.restaurantpos.core.sync.UserPullResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

/**
 * Real HTTP implementation of [UserPullPort].
 *
 * Protocol: GET [baseUrl]/sync/users with JWT auth.
 * Returns 200 with `{ serverTime, users: [...] }` — the full staff set including pinHash.
 */
class HttpUserPullPort(
    private val baseUrl: String,
    private val client: OkHttpClient,
    private val authToken: () -> String,
) : UserPullPort {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun pullUsers(): UserPullResult? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$baseUrl/sync/users")
            .header("Authorization", "Bearer ${authToken()}")
            .get()
            .build()

        try {
            client.newCall(request).execute().use { res ->
                if (!res.isSuccessful) return@withContext null
                val body = res.body?.string() ?: return@withContext null
                val dto = json.decodeFromString<UserSyncPullResponseDto>(body)
                UserPullResult(
                    serverTime = dto.serverTime,
                    users = dto.users.map { it.toDomain() },
                )
            }
        } catch (e: IOException) {
            null
        }
    }
}

@Serializable
private data class UserSyncPullResponseDto(
    val serverTime: Long,
    val users: List<UserSyncDto> = emptyList(),
)

@Serializable
private data class UserSyncDto(
    val id: String,
    val displayName: String,
    val roleId: String,
    val pinHash: String,
    val isActive: Boolean = true,
    val createdAt: Long = 0,
) {
    fun toDomain() = User(
        id = id,
        displayName = displayName,
        roleId = roleId,
        pinHash = pinHash,
        isActive = isActive,
        createdAt = createdAt,
    )
}
