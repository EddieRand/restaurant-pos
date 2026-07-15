package com.restaurantpos.core.network

import com.restaurantpos.core.model.Role
import com.restaurantpos.core.model.RolePermission
import com.restaurantpos.core.sync.PermissionPullPort
import com.restaurantpos.core.sync.PermissionPullResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

/**
 * Real HTTP implementation of [PermissionPullPort].
 *
 * Protocol: GET [baseUrl]/sync/permissions with JWT auth.
 * Returns 200 with `{ serverTime, roles: [...], rolePermissions: [...] }`.
 */
class HttpPermissionPullPort(
    private val baseUrl: String,
    private val client: OkHttpClient,
    private val authToken: () -> String,
) : PermissionPullPort {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun pullPermissions(): PermissionPullResult? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$baseUrl/sync/permissions")
            .header("Authorization", "Bearer ${authToken()}")
            .get()
            .build()

        try {
            client.newCall(request).execute().use { res ->
                if (!res.isSuccessful) return@withContext null
                val body = res.body?.string() ?: return@withContext null
                val dto = json.decodeFromString<PermissionSyncPullResponseDto>(body)
                PermissionPullResult(
                    serverTime = dto.serverTime,
                    roles = dto.roles.map { it.toDomain() },
                    rolePermissions = dto.rolePermissions.map { it.toDomain() },
                )
            }
        } catch (e: IOException) {
            null
        }
    }
}

@Serializable
private data class PermissionSyncPullResponseDto(
    val serverTime: Long,
    val roles: List<RoleDto> = emptyList(),
    val rolePermissions: List<RolePermissionDto> = emptyList(),
)

@Serializable
private data class RoleDto(
    val id: String,
    val displayName: String,
    val isBuiltin: Boolean = false,
    val sortOrder: Int = 0,
) {
    fun toDomain() = Role(id, displayName, isBuiltin, sortOrder)
}

@Serializable
private data class RolePermissionDto(
    val roleId: String,
    val permissionKey: String,
) {
    fun toDomain() = RolePermission(roleId, permissionKey)
}
