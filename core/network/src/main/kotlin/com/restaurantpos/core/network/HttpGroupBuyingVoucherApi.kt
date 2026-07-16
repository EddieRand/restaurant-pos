package com.restaurantpos.core.network

import com.restaurantpos.core.domain.repository.GroupBuyingVoucherRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class HttpGroupBuyingVoucherApi(
    private val baseUrl: String,
    private val client: OkHttpClient,
    private val authToken: () -> String,
) : GroupBuyingVoucherRepository {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val mediaType = "application/json".toMediaType()

    @Serializable private data class ValidateReq(val provider: String, val code: String)
    @Serializable private data class RedeemReq(
        val provider: String,
        val code: String,
        val orderId: String,
        val operatorId: String,
        val requestedAmountMinorUnit: Long,
        val idempotencyKey: String,
    )
    @Serializable private data class VoucherResp(
        val provider: String,
        val maskedCode: String,
        val title: String,
        val faceValueMinorUnit: Long,
        val expiresAt: Long,
        val status: String,
        val demo: Boolean = false,
    )
    @Serializable private data class RedeemResp(
        val redemptionId: String,
        val redeemedAmountMinorUnit: Long,
        val alreadyRedeemed: Boolean = false,
    )
    @Serializable private data class ErrorResp(val code: String = "VOUCHER_ERROR", val message: String = "")

    override suspend fun validate(
        provider: GroupBuyingVoucherRepository.Provider,
        code: String,
    ): GroupBuyingVoucherRepository.ValidateResult = withContext(Dispatchers.IO) {
        when (val result = post("$baseUrl/pos/group-buying-vouchers/validate", ValidateReq(provider.name, code)) { body ->
            val response = json.decodeFromString<VoucherResp>(body)
            GroupBuyingVoucherRepository.ValidateResult.Success(response.toDomain())
        }) {
            is PostResult.Success -> result.value
            is PostResult.Error -> GroupBuyingVoucherRepository.ValidateResult.Error(result.code, result.message)
        }
    }

    override suspend fun redeem(
        provider: GroupBuyingVoucherRepository.Provider,
        code: String,
        orderId: String,
        operatorId: String,
        requestedAmountMinorUnit: Long,
        idempotencyKey: String,
    ): GroupBuyingVoucherRepository.RedeemResult = withContext(Dispatchers.IO) {
        when (val result = post(
            "$baseUrl/pos/group-buying-vouchers/redeem",
            RedeemReq(provider.name, code, orderId, operatorId, requestedAmountMinorUnit, idempotencyKey),
        ) { body ->
            val response = json.decodeFromString<RedeemResp>(body)
            GroupBuyingVoucherRepository.RedeemResult.Success(
                response.redemptionId,
                response.redeemedAmountMinorUnit,
                response.alreadyRedeemed,
            )
        }) {
            is PostResult.Success -> result.value
            is PostResult.Error -> GroupBuyingVoucherRepository.RedeemResult.Error(result.code, result.message)
        }
    }

    private sealed interface PostResult<out T> {
        data class Success<T>(val value: T) : PostResult<T>
        data class Error(val code: String, val message: String) : PostResult<Nothing>
    }

    private inline fun <reified T, R> post(url: String, payload: T, decode: (String) -> R): PostResult<R> {
        return try {
            val request = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer ${authToken()}")
                .post(json.encodeToString(payload).toRequestBody(mediaType))
                .build()
            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    val error = runCatching { json.decodeFromString<ErrorResp>(body) }.getOrNull()
                    PostResult.Error(
                        code = error?.code ?: "VOUCHER_HTTP_${response.code}",
                        message = error?.message?.takeIf { it.isNotBlank() } ?: "团购券服务异常 (${response.code})",
                    )
                } else PostResult.Success(decode(body))
            }
        } catch (e: Exception) {
            PostResult.Error("VOUCHER_NETWORK_ERROR", e.message ?: "网络连接失败，请重试")
        }
    }

    private fun VoucherResp.toDomain() = GroupBuyingVoucherRepository.Voucher(
        provider = GroupBuyingVoucherRepository.Provider.valueOf(provider),
        maskedCode = maskedCode,
        title = title,
        faceValueMinorUnit = faceValueMinorUnit,
        expiresAt = expiresAt,
        status = status,
        demo = demo,
    )
}
