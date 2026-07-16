package com.restaurantpos.core.domain.repository

/**
 * Online verification and redemption for third-party group-buying vouchers.
 *
 * The server remains authoritative: the Cashier never derives voucher value or marks a
 * voucher as consumed locally. Demo providers use the same contract as production adapters.
 */
interface GroupBuyingVoucherRepository {

    enum class Provider { DOUYIN, MEITUAN }

    data class Voucher(
        val provider: Provider,
        val maskedCode: String,
        val title: String,
        val faceValueMinorUnit: Long,
        val expiresAt: Long,
        val status: String,
        val demo: Boolean,
    )

    sealed class ValidateResult {
        data class Success(val voucher: Voucher) : ValidateResult()
        data class Error(val code: String, val message: String) : ValidateResult()
    }

    sealed class RedeemResult {
        data class Success(
            val redemptionId: String,
            val redeemedAmountMinorUnit: Long,
            val alreadyRedeemed: Boolean,
        ) : RedeemResult()
        data class Error(val code: String, val message: String) : RedeemResult()
    }

    suspend fun validate(provider: Provider, code: String): ValidateResult

    suspend fun redeem(
        provider: Provider,
        code: String,
        orderId: String,
        operatorId: String,
        requestedAmountMinorUnit: Long,
        idempotencyKey: String,
    ): RedeemResult
}
