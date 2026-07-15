package com.restaurantpos.core.network

import com.restaurantpos.core.domain.repository.GiftCardRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class HttpGiftCardApi(
    private val baseUrl: String,
    private val client: OkHttpClient,
    private val authToken: () -> String,
) : GiftCardRepository {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val mediaType = "application/json".toMediaType()

    @Serializable
    private data class RedeemReq(
        val code: String,
        val amountMinorUnit: Long,
        val orderId: String? = null,
        val operatorId: String = "",
    )

    @Serializable
    private data class RedeemResp(val ok: Boolean, val remainingBalanceMinorUnit: Long = 0L)

    @Serializable
    private data class ErrorResp(val error: String = "")

    override suspend fun redeem(
        code: String,
        amountMinorUnit: Long,
        orderId: String?,
        operatorId: String,
    ): GiftCardRepository.RedeemResult = withContext(Dispatchers.IO) {
        try {
            val body = json.encodeToString(RedeemReq(code, amountMinorUnit, orderId, operatorId))
                .toRequestBody(mediaType)
            val req = Request.Builder()
                .url("$baseUrl/admin/gift-cards/redeem")
                .header("Authorization", "Bearer ${authToken()}")
                .post(body)
                .build()
            val response = client.newCall(req).execute()
            val responseBody = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                val message = runCatching { json.decodeFromString<ErrorResp>(responseBody).error }
                    .getOrNull()
                    ?.takeIf { it.isNotBlank() }
                    ?: "Gift card redeem failed (${response.code})"
                return@withContext GiftCardRepository.RedeemResult.Error(message)
            }
            val resp = json.decodeFromString<RedeemResp>(responseBody)
            GiftCardRepository.RedeemResult.Success(resp.remainingBalanceMinorUnit)
        } catch (e: Exception) {
            GiftCardRepository.RedeemResult.Error(e.message ?: "Network error")
        }
    }
}
