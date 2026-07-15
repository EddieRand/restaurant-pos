package com.restaurantpos.core.network

import com.restaurantpos.core.domain.repository.TimeclockRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class HttpTimeclockApi(
    private val baseUrl: String,
    private val client: OkHttpClient,
    private val authToken: () -> String,
) : TimeclockRepository {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val mediaType = "application/json".toMediaType()

    @Serializable
    private data class ActiveTimecardDto(val id: String)

    @Serializable
    private data class ClockInReq(
        val operatorId: String,
        val operatorName: String,
        val terminalId: String,
    )

    @Serializable
    private data class ClockInResp(val id: String)

    @Serializable
    private data class ClockOutReq(val note: String = "")

    override suspend fun activeTimecardId(operatorId: String): String? = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder()
                .url("$baseUrl/admin/timecards/active")
                .header("Authorization", "Bearer ${authToken()}")
                .get()
                .build()
            val response = client.newCall(req).execute()
            if (!response.isSuccessful) return@withContext null
            val body = response.body?.string() ?: return@withContext null
            val list = json.decodeFromString<List<ActiveTimecardDto>>(body)
            list.firstOrNull { it.id.isNotBlank() }?.id
        } catch (_: Exception) {
            null
        }
    }

    override suspend fun clockIn(operatorId: String, operatorName: String, terminalId: String): String =
        withContext(Dispatchers.IO) {
            val body = json.encodeToString(ClockInReq(operatorId, operatorName, terminalId))
                .toRequestBody(mediaType)
            val req = Request.Builder()
                .url("$baseUrl/admin/timecards/clockin")
                .header("Authorization", "Bearer ${authToken()}")
                .post(body)
                .build()
            val response = client.newCall(req).execute()
            check(response.isSuccessful) { "Clock-in failed: ${response.code}" }
            val resp = json.decodeFromString<ClockInResp>(response.body!!.string())
            resp.id
        }

    override suspend fun clockOut(timecardId: String): Unit = withContext(Dispatchers.IO) {
        val body = json.encodeToString(ClockOutReq()).toRequestBody(mediaType)
        val req = Request.Builder()
            .url("$baseUrl/admin/timecards/$timecardId/clockout")
            .header("Authorization", "Bearer ${authToken()}")
            .post(body)
            .build()
        client.newCall(req).execute()
    }
}
