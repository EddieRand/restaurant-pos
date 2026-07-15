package com.restaurantpos.core.domain.repository

/** 礼品卡/储值卡余额核销 — 余额为服务端权威数据，核销需在线进行。 */
interface GiftCardRepository {

    sealed class RedeemResult {
        data class Success(val remainingBalanceMinorUnit: Long) : RedeemResult()
        data class Error(val message: String) : RedeemResult()
    }

    suspend fun redeem(code: String, amountMinorUnit: Long, orderId: String?, operatorId: String): RedeemResult
}
