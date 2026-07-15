package com.restaurantpos.core.network

import com.restaurantpos.core.model.Customer
import com.restaurantpos.core.sync.CustomerPullPort
import com.restaurantpos.core.sync.CustomerPullResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

/**
 * Real HTTP implementation of [CustomerPullPort].
 * Protocol: GET [baseUrl]/sync/customers?since=<watermark> with JWT auth.
 */
class HttpCustomerPullPort(
    private val baseUrl: String,
    private val client: OkHttpClient,
    private val authToken: () -> String,
) : CustomerPullPort {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun pullCustomers(since: Long): CustomerPullResult? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$baseUrl/sync/customers?since=$since")
            .header("Authorization", "Bearer ${authToken()}")
            .get()
            .build()

        try {
            client.newCall(request).execute().use { res ->
                if (!res.isSuccessful) return@withContext null
                val body = res.body?.string() ?: return@withContext null
                val dto = json.decodeFromString<CustomerSyncPullResponseDto>(body)
                CustomerPullResult(serverTime = dto.serverTime, customers = dto.customers.map { it.toDomain() })
            }
        } catch (e: IOException) {
            null
        }
    }
}

@Serializable
private data class CustomerSyncPullResponseDto(
    val serverTime: Long,
    val customers: List<CustomerSyncDto> = emptyList(),
)

@Serializable
private data class CustomerSyncDto(
    val id: String,
    val name: String,
    val phone: String,
    val email: String? = null,
    val gender: String? = null,
    val birthday: String? = null,
    val tags: String = "",
    val notes: String? = null,
    val totalSpendMinorUnit: Long = 0,
    val loyaltyPoints: Long = 0,
    val membershipTierId: String? = null,
    val totalVisits: Int = 0,
    val lastVisitAt: Long = 0,
    val registeredAt: Long = 0,
    val updatedAt: Long = 0,
) {
    fun toDomain() = Customer(
        id = id,
        name = name,
        phone = phone,
        email = email,
        gender = gender,
        birthday = birthday,
        tags = tags.split("|").filter { it.isNotEmpty() },
        notes = notes,
        totalSpendMinorUnit = totalSpendMinorUnit,
        loyaltyPoints = loyaltyPoints,
        membershipTierId = membershipTierId,
        totalVisits = totalVisits,
        lastVisitAt = lastVisitAt,
        registeredAt = registeredAt,
        updatedAt = updatedAt,
    )
}
