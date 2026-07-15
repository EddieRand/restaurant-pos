package com.restaurantpos.core.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

/**
 * 远程报表 API 客户端 —— 从服务端拉取跨终端聚合的报表数据。
 *
 * 当 Android 终端在线时，通过此 API 获取服务端聚合后的报表
 * （服务端 OrdersTable 包含了所有终端同步上来的数据），
 * 离线时自动降级到本地 Room DB。
 */
class HttpReportApi(
    private val baseUrl: String,
    private val client: OkHttpClient,
    private val authToken: () -> String,
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /**
     * 从服务端拉取 ShiftReport。
     *
     * @param fromMs 开始时间戳
     * @param toMs   结束时间戳
     * @return ShiftReportDto 或 null（网络错误时）
     */
    suspend fun fetchShiftReport(fromMs: Long, toMs: Long): ServerShiftReportDto? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$baseUrl/admin/reports/shift?from=$fromMs&to=$toMs")
                .header("Authorization", "Bearer ${authToken()}")
                .get()
                .build()
            val response = client.newCall(request).execute()
            response.use { res ->
                if (res.isSuccessful) {
                    val body = res.body?.string() ?: return@withContext null
                    json.decodeFromString<ServerShiftReportDto>(body)
                } else null
            }
        } catch (_: IOException) {
            null
        }
    }

    /**
     * 从服务端拉取 TrendReport。
     *
     * @param fromDate    开始日期 "YYYY-MM-DD"
     * @param toDate      结束日期 "YYYY-MM-DD"
     * @param granularity "day" | "week" | "month"
     */
    suspend fun fetchTrendReport(
        fromDate: String,
        toDate: String,
        granularity: String = "day",
    ): ServerTrendReportDto? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$baseUrl/admin/reports/trend?fromDate=$fromDate&toDate=$toDate&granularity=$granularity")
                .header("Authorization", "Bearer ${authToken()}")
                .get()
                .build()
            val response = client.newCall(request).execute()
            response.use { res ->
                if (res.isSuccessful) {
                    val body = res.body?.string() ?: return@withContext null
                    json.decodeFromString<ServerTrendReportDto>(body)
                } else null
            }
        } catch (_: IOException) {
            null
        }
    }
}

// ── Server response DTOs (mirror of server-side AdminModels) ─────────

@Serializable
data class ServerShiftReportDto(
    val fromMs: Long,
    val toMs: Long,
    val orderCount: Int,
    val grossRevenueMinorUnit: Long,
    val netRevenueMinorUnit: Long,
    val totalDiscountMinorUnit: Long,
    val totalTipMinorUnit: Long,
    val totalServiceChargeMinorUnit: Long,
    val totalTaxMinorUnit: Long,
    val totalGuestCount: Int,
    val averageOrderValueMinorUnit: Long,
    val averageSpendPerGuestMinorUnit: Long,
    val paymentMethodBreakdown: Map<String, Long>,
)

@Serializable
data class ServerTrendDataPointDto(
    val date: String,
    val grossRevenueMinorUnit: Long,
    val netRevenueMinorUnit: Long,
    val orderCount: Int,
    val guestCount: Int,
    val averageOrderValueMinorUnit: Long,
    val averageSpendPerGuestMinorUnit: Long,
)

@Serializable
data class ServerTrendSummaryDto(
    val totalGrossRevenue: Long,
    val totalNetRevenue: Long,
    val totalOrderCount: Int,
    val totalGuestCount: Int,
    val avgOrderValue: Long,
    val avgSpendPerGuest: Long,
    val growthFromPrevious: Double? = null,
)

@Serializable
data class ServerTrendReportDto(
    val dataPoints: List<ServerTrendDataPointDto>,
    val summary: ServerTrendSummaryDto,
)
