package com.restaurantpos.feature.checkout

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.restaurantpos.core.config.ConfigRepository
import com.restaurantpos.core.config.DefaultRegionConfig
import com.restaurantpos.core.config.RegionConfig
import com.restaurantpos.core.domain.repository.CustomerRepository
import com.restaurantpos.core.domain.repository.GiftCardRepository
import com.restaurantpos.core.domain.repository.GroupBuyingVoucherRepository
import com.restaurantpos.core.domain.repository.OrderRepository
import com.restaurantpos.core.domain.repository.PaymentRepository
import com.restaurantpos.core.domain.repository.SessionRepository
import com.restaurantpos.core.hardware.PrintReceiptUseCase
import com.restaurantpos.core.domain.usecase.ApplyCouponUseCase
import com.restaurantpos.core.domain.usecase.ApplyDiscountUseCase
import com.restaurantpos.core.domain.usecase.ApplyServiceChargeUseCase
import com.restaurantpos.core.domain.usecase.CheckPermissionUseCase
import com.restaurantpos.core.model.PermissionKey
import com.restaurantpos.core.domain.usecase.RefundUseCase
import com.restaurantpos.core.domain.usecase.SettlePaymentUseCase
import com.restaurantpos.core.domain.usecase.SplitBillUseCase
import com.restaurantpos.core.model.*
import com.restaurantpos.core.model.Customer
import com.restaurantpos.core.sync.CdsPhaseBroadcaster
import com.restaurantpos.core.sync.NetworkMonitor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

/**
 * Explicit checkout steps that drive the customer display: an optional tip prompt before
 * payment, the payment step itself, and a post-payment receipt step. Each maps to a CdsPhase.
 */
enum class CheckoutStep { TIP, PAYMENT, RECEIPT }

data class CheckoutUiState(
    val orderId: String = "",
    val step: CheckoutStep = CheckoutStep.PAYMENT,
    /** Set when the cashier closes checkout; the screen then navigates back. */
    val finished: Boolean = false,
    val regionConfig: RegionConfig = DefaultRegionConfig,
    val subtotalMinorUnit: Long = 0L,
    val taxTotalMinorUnit: Long = 0L,
    val serviceChargeMinorUnit: Long = 0L,
    val tipMinorUnit: Long = 0L,
    val discountMinorUnit: Long = 0L,
    val totalMinorUnit: Long = 0L,
    val alreadyPaidMinorUnit: Long = 0L,
    val remainingMinorUnit: Long = 0L,
    val isSettled: Boolean = false,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val couponCode: String = "",
    val appliedCouponCode: String? = null,
    val permissionDeniedAction: String? = null,
    val payments: List<Payment> = emptyList(),
    /** Even-split shares, non-empty when split mode is active. */
    val splitShares: List<SplitBillUseCase.PartyShare> = emptyList(),
    val isSplitMode: Boolean = false,
    val splitPartyCount: Int = 2,
    val printError: String? = null,
    // Member lookup
    val memberPhone: String = "",
    val boundMember: Customer? = null,
    val memberSearchLoading: Boolean = false,
    val earnPointsPreview: Long = 0L,
    // Gift card redemption
    val giftCardCode: String = "",
    // Douyin / Meituan group-buying voucher redemption
    val groupBuyingProvider: GroupBuyingVoucherRepository.Provider = GroupBuyingVoucherRepository.Provider.DOUYIN,
    val groupBuyingCode: String = "",
    val validatedGroupBuyingVoucher: GroupBuyingVoucherRepository.Voucher? = null,
    val groupBuyingIdempotencyKey: String = "",
    val isOnline: Boolean = true,
)

@HiltViewModel
class CheckoutViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val orderRepo: OrderRepository,
    private val paymentRepo: PaymentRepository,
    private val settlePaymentUseCase: SettlePaymentUseCase,
    private val applyDiscountUseCase: ApplyDiscountUseCase,
    private val applyServiceChargeUseCase: ApplyServiceChargeUseCase,
    private val splitBillUseCase: SplitBillUseCase,
    private val refundUseCase: RefundUseCase,
    private val configRepo: ConfigRepository,
    private val sessionRepo: SessionRepository,
    private val checkPermission: CheckPermissionUseCase,
    private val applyCouponUseCase: ApplyCouponUseCase,
    private val customerRepo: CustomerRepository,
    private val printReceiptUseCase: PrintReceiptUseCase,
    private val giftCardRepo: GiftCardRepository,
    private val groupBuyingVoucherRepo: GroupBuyingVoucherRepository,
    private val networkMonitor: NetworkMonitor,
    private val cdsPhaseBroadcaster: CdsPhaseBroadcaster,
) : ViewModel() {

    val orderId: String = checkNotNull(savedStateHandle["orderId"])
    private val _uiState = MutableStateFlow(CheckoutUiState(orderId = orderId))
    val uiState: StateFlow<CheckoutUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            configRepo.config.collect { cfg -> _uiState.update { it.copy(regionConfig = cfg) } }
        }
        viewModelScope.launch {
            networkMonitor.isOnline.collect { online -> _uiState.update { it.copy(isOnline = online) } }
        }
        viewModelScope.launch {
            val order = orderRepo.getById(orderId) ?: return@launch
            val payments = paymentRepo.getByOrder(orderId)
            val alreadyPaid = payments.filter { it.status == PaymentStatus.PAID }.sumOf { it.amountMinorUnit }
            // Open on the tip step for a fresh, unpaid order when tipping is enabled.
            val startOnTip = alreadyPaid == 0L && configRepo.current().tipConfig.enabled
            _uiState.update {
                it.copy(
                    step = if (startOnTip) CheckoutStep.TIP else CheckoutStep.PAYMENT,
                    subtotalMinorUnit = order.subtotalMinorUnit,
                    taxTotalMinorUnit = order.taxTotalMinorUnit,
                    serviceChargeMinorUnit = order.serviceChargeMinorUnit,
                    tipMinorUnit = order.tipMinorUnit,
                    discountMinorUnit = order.discountMinorUnit,
                    totalMinorUnit = order.totalMinorUnit,
                    alreadyPaidMinorUnit = alreadyPaid,
                    remainingMinorUnit = order.totalMinorUnit - alreadyPaid,
                    payments = payments,
                )
            }
            // Mirror the checkout to the customer display.
            broadcastCds(if (startOnTip) CdsPhase.TIP else CdsPhase.ORDER)
        }
    }

    /** Tip step → payment step. Any tip is applied via [applyTip] while on the tip step. */
    fun proceedToPayment() {
        _uiState.update { it.copy(step = CheckoutStep.PAYMENT) }
        broadcastCds(CdsPhase.ORDER)
    }

    /** After payment: advance to the receipt step (customer display shows receipt options). */
    fun showReceiptStep() {
        _uiState.update { it.copy(step = CheckoutStep.RECEIPT) }
        broadcastCds(CdsPhase.RECEIPT)
    }

    /** Closes checkout; the screen navigates back and the display returns to WELCOME. */
    fun finishCheckout() {
        _uiState.update { it.copy(finished = true) }
    }

    /** Pushes the current CDS phase to the server so the customer display follows checkout. */
    private fun broadcastCds(phase: CdsPhase) {
        cdsPhaseBroadcaster.broadcast(configRepo.current().terminalId, phase, orderId)
    }

    override fun onCleared() {
        // Leaving checkout returns the customer display to its idle welcome screen.
        cdsPhaseBroadcaster.broadcast(configRepo.current().terminalId, CdsPhase.WELCOME, orderId = null)
        super.onCleared()
    }

    fun payWithMethod(method: PaymentMethod, amountMinorUnit: Long? = null) {
        val amount = amountMinorUnit ?: _uiState.value.remainingMinorUnit
        if (amount <= 0) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            broadcastCds(CdsPhase.PROCESSING)
            val payment = Payment(
                id = UUID.randomUUID().toString(),
                orderId = orderId,
                amountMinorUnit = amount,
                method = method,
                operatorId = sessionRepo.current()?.id ?: configRepo.current().terminalId,
                createdAt = System.currentTimeMillis(),
            )
            when (val result = settlePaymentUseCase(SettlePaymentUseCase.Params(payment))) {
                is SettlePaymentUseCase.Result.Success -> {
                    val order = result.order
                    val payments = paymentRepo.getByOrder(orderId)
                    val alreadyPaid = payments.filter { it.status == PaymentStatus.PAID }.sumOf { it.amountMinorUnit }
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isSettled = order.status == OrderStatus.CLOSED,
                            alreadyPaidMinorUnit = alreadyPaid,
                            remainingMinorUnit = maxOf(0L, order.totalMinorUnit - alreadyPaid),
                            payments = payments,
                            groupBuyingCode = if (method == PaymentMethod.VOUCHER) "" else it.groupBuyingCode,
                            validatedGroupBuyingVoucher = if (method == PaymentMethod.VOUCHER) null else it.validatedGroupBuyingVoucher,
                            groupBuyingIdempotencyKey = if (method == PaymentMethod.VOUCHER) "" else it.groupBuyingIdempotencyKey,
                        )
                    }
                    if (order.status == OrderStatus.CLOSED) {
                        broadcastCds(CdsPhase.SUCCESS)
                        earnMemberPoints(order.totalMinorUnit, orderId)
                        autoPrintReceipt()
                    }
                }
                is SettlePaymentUseCase.Result.Error -> {
                    // Payment failed — return the display to the order review state.
                    broadcastCds(CdsPhase.ORDER)
                    _uiState.update { it.copy(isLoading = false, errorMessage = result.message) }
                }
            }
        }
    }

    fun setGiftCardCode(code: String) = _uiState.update { it.copy(giftCardCode = code) }

    fun setGroupBuyingProvider(provider: GroupBuyingVoucherRepository.Provider) = _uiState.update {
        it.copy(
            groupBuyingProvider = provider,
            validatedGroupBuyingVoucher = null,
            groupBuyingIdempotencyKey = "",
            errorMessage = null,
        )
    }

    fun setGroupBuyingCode(code: String) = _uiState.update {
        it.copy(
            groupBuyingCode = code.take(64),
            validatedGroupBuyingVoucher = null,
            groupBuyingIdempotencyKey = "",
            errorMessage = null,
        )
    }

    fun validateGroupBuyingVoucher() {
        val state = _uiState.value
        val code = state.groupBuyingCode.trim()
        if (!state.isOnline || code.isBlank()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            when (val result = groupBuyingVoucherRepo.validate(state.groupBuyingProvider, code)) {
                is GroupBuyingVoucherRepository.ValidateResult.Error ->
                    _uiState.update { it.copy(isLoading = false, errorMessage = result.message) }
                is GroupBuyingVoucherRepository.ValidateResult.Success ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            validatedGroupBuyingVoucher = result.voucher,
                            groupBuyingIdempotencyKey = UUID.randomUUID().toString(),
                        )
                    }
            }
        }
    }

    fun redeemGroupBuyingVoucher() {
        val state = _uiState.value
        val voucher = state.validatedGroupBuyingVoucher ?: return
        val amount = minOf(voucher.faceValueMinorUnit, state.remainingMinorUnit)
        if (!state.isOnline || amount <= 0 || state.groupBuyingIdempotencyKey.isBlank()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val operatorId = sessionRepo.current()?.id ?: configRepo.current().terminalId
            when (val result = groupBuyingVoucherRepo.redeem(
                provider = state.groupBuyingProvider,
                code = state.groupBuyingCode.trim(),
                orderId = orderId,
                operatorId = operatorId,
                requestedAmountMinorUnit = amount,
                idempotencyKey = state.groupBuyingIdempotencyKey,
            )) {
                is GroupBuyingVoucherRepository.RedeemResult.Error ->
                    _uiState.update { it.copy(isLoading = false, errorMessage = result.message) }
                is GroupBuyingVoucherRepository.RedeemResult.Success -> {
                    _uiState.update { it.copy(isLoading = false) }
                    payWithMethod(PaymentMethod.VOUCHER, result.redeemedAmountMinorUnit)
                }
            }
        }
    }

    /** Redeems balance from a gift card (online, server-authoritative) then settles the payment. */
    fun payWithGiftCard(amountMinorUnit: Long? = null) {
        val amount = amountMinorUnit ?: _uiState.value.remainingMinorUnit
        val code = _uiState.value.giftCardCode.trim()
        if (amount <= 0 || code.isBlank()) return
        if (!_uiState.value.isOnline) {
            _uiState.update { it.copy(errorMessage = "Gift card payment requires a network connection") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            when (val result = giftCardRepo.redeem(code, amount, orderId, sessionRepo.current()?.id ?: configRepo.current().terminalId)) {
                is GiftCardRepository.RedeemResult.Error ->
                    _uiState.update { it.copy(isLoading = false, errorMessage = result.message) }
                is GiftCardRepository.RedeemResult.Success -> {
                    _uiState.update { it.copy(giftCardCode = "") }
                    payWithMethod(PaymentMethod.GIFT_CARD, amount)
                }
            }
        }
    }

    fun applyOrderDiscount(discountMinorUnit: Long) {
        viewModelScope.launch {
            if (checkPermission(sessionRepo.current(), PermissionKey.PAYMENT_DISCOUNT) != CheckPermissionUseCase.Result.Allowed) {
                _uiState.update { it.copy(permissionDeniedAction = "Apply Discount") }
                return@launch
            }
            when (val result = applyDiscountUseCase(orderId, ApplyDiscountUseCase.Target.OrderLevel(discountMinorUnit))) {
                is ApplyDiscountUseCase.Result.Success -> _uiState.update {
                    val o = result.order
                    it.copy(
                        discountMinorUnit = o.discountMinorUnit,
                        totalMinorUnit = o.totalMinorUnit,
                        remainingMinorUnit = maxOf(0L, o.totalMinorUnit - it.alreadyPaidMinorUnit),
                    )
                }
                is ApplyDiscountUseCase.Result.Error ->
                    _uiState.update { it.copy(errorMessage = result.message) }
            }
        }
    }

    fun applyServiceCharge(chargeMinorUnit: Long) {
        viewModelScope.launch {
            when (val result = applyServiceChargeUseCase(orderId, chargeMinorUnit)) {
                is ApplyServiceChargeUseCase.Result.Success -> _uiState.update {
                    val o = result.order
                    it.copy(
                        serviceChargeMinorUnit = o.serviceChargeMinorUnit,
                        totalMinorUnit = o.totalMinorUnit,
                        remainingMinorUnit = maxOf(0L, o.totalMinorUnit - it.alreadyPaidMinorUnit),
                    )
                }
                is ApplyServiceChargeUseCase.Result.Error ->
                    _uiState.update { it.copy(errorMessage = result.message) }
            }
        }
    }

    fun activateSplitMode(partyCount: Int = 2) {
        viewModelScope.launch {
            when (val result = splitBillUseCase(orderId, SplitBillUseCase.Strategy.Even(partyCount))) {
                is SplitBillUseCase.Result.Success ->
                    _uiState.update { it.copy(isSplitMode = true, splitPartyCount = partyCount, splitShares = result.shares) }
                is SplitBillUseCase.Result.Error ->
                    _uiState.update { it.copy(errorMessage = result.message) }
            }
        }
    }

    fun deactivateSplitMode() {
        _uiState.update { it.copy(isSplitMode = false, splitShares = emptyList()) }
    }

    fun payShareWithMethod(shareIndex: Int, method: PaymentMethod) {
        val shares = _uiState.value.splitShares
        if (shareIndex >= shares.size) return
        payWithMethod(method, shares[shareIndex].amountMinorUnit)
    }

    fun refundPayment(paymentId: String) {
        viewModelScope.launch {
            if (checkPermission(sessionRepo.current(), PermissionKey.PAYMENT_REFUND) != CheckPermissionUseCase.Result.Allowed) {
                _uiState.update { it.copy(permissionDeniedAction = "Refund Payment") }
                return@launch
            }
            val payment = paymentRepo.getById(paymentId) ?: return@launch
            when (val result = refundUseCase(
                RefundUseCase.Params(
                    originalPaymentId = paymentId,
                    refundAmountMinorUnit = payment.amountMinorUnit,
                    operatorId = sessionRepo.current()?.id ?: configRepo.current().terminalId,
                )
            )) {
                is RefundUseCase.Result.Success -> {
                    val payments = paymentRepo.getByOrder(orderId)
                    val alreadyPaid = payments.filter { it.status == PaymentStatus.PAID }.sumOf { it.amountMinorUnit }
                    val refundedAmt = payments.filter { it.status == PaymentStatus.REFUNDED }.sumOf { it.amountMinorUnit }
                    _uiState.update {
                        it.copy(
                            isSettled = result.order.status == OrderStatus.CLOSED,
                            alreadyPaidMinorUnit = alreadyPaid - refundedAmt,
                            remainingMinorUnit = maxOf(0L, it.totalMinorUnit - (alreadyPaid - refundedAmt)),
                            payments = payments,
                        )
                    }
                }
                is RefundUseCase.Result.Error ->
                    _uiState.update { it.copy(errorMessage = result.message) }
            }
        }
    }

    fun applyTip(tipMinorUnit: Long) {
        viewModelScope.launch {
            try {
                orderRepo.setTip(orderId, tipMinorUnit)
                val order = orderRepo.getById(orderId) ?: return@launch
                _uiState.update {
                    it.copy(
                        tipMinorUnit = order.tipMinorUnit,
                        totalMinorUnit = order.totalMinorUnit,
                        remainingMinorUnit = maxOf(0L, order.totalMinorUnit - it.alreadyPaidMinorUnit),
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = e.message) }
            }
        }
    }

    fun setCouponCode(code: String) = _uiState.update { it.copy(couponCode = code) }

    fun applyCoupon() {
        val code = _uiState.value.couponCode.trim()
        if (code.isBlank()) return
        viewModelScope.launch {
            when (val result = applyCouponUseCase(orderId, code, System.currentTimeMillis())) {
                is ApplyCouponUseCase.Result.Success -> {
                    val order = orderRepo.getById(orderId) ?: return@launch
                    _uiState.update {
                        it.copy(
                            appliedCouponCode = result.coupon.code,
                            discountMinorUnit = order.discountMinorUnit,
                            totalMinorUnit = order.totalMinorUnit,
                            remainingMinorUnit = maxOf(0L, order.totalMinorUnit - it.alreadyPaidMinorUnit),
                            couponCode = "",
                        )
                    }
                }
                is ApplyCouponUseCase.Result.Error ->
                    _uiState.update { it.copy(errorMessage = result.message) }
            }
        }
    }

    // ── Member lookup ─────────────────────────────────────────────────────────

    fun setMemberPhone(phone: String) {
        _uiState.update { it.copy(memberPhone = phone) }
    }

    fun lookupMemberByPhone() {
        val phone = _uiState.value.memberPhone.trim()
        if (phone.isBlank()) return
        viewModelScope.launch {
            _uiState.update { it.copy(memberSearchLoading = true) }
            val customer = customerRepo.getByPhone(phone)
            val preview = customer?.let { computeEarnPoints(it, _uiState.value.totalMinorUnit) } ?: 0L
            _uiState.update { it.copy(memberSearchLoading = false, boundMember = customer, earnPointsPreview = preview) }
        }
    }

    fun clearMember() = _uiState.update { it.copy(boundMember = null, memberPhone = "", earnPointsPreview = 0L) }

    private suspend fun earnMemberPoints(totalMinorUnit: Long, paidOrderId: String) {
        val member = _uiState.value.boundMember ?: return
        val points = computeEarnPoints(member, totalMinorUnit)
        if (points <= 0L) return
        customerRepo.addPoints(member.id, points, paidOrderId, "Purchase earn")
    }

    private fun computeEarnPoints(customer: Customer, totalMinorUnit: Long): Long =
        totalMinorUnit / 100  // 1 point per dollar; multiplier from tier would go here

    // ── Receipt printing ──────────────────────────────────────────────────────

    fun printReceipt() {
        viewModelScope.launch {
            val locale = configRepo.current().locale.substringBefore('-').lowercase()
            when (val result = printReceiptUseCase(orderId, locale)) {
                is PrintReceiptUseCase.Result.Error ->
                    _uiState.update { it.copy(printError = result.reason) }
                PrintReceiptUseCase.Result.Success -> { /* no-op */ }
            }
        }
    }

    private suspend fun autoPrintReceipt() {
        val locale = configRepo.current().locale.substringBefore('-').lowercase()
        when (val result = printReceiptUseCase(orderId, locale)) {
            is PrintReceiptUseCase.Result.Error ->
                _uiState.update { it.copy(printError = result.reason) }
            PrintReceiptUseCase.Result.Success -> { /* silent success */ }
        }
    }

    fun dismissPrintError() = _uiState.update { it.copy(printError = null) }

    fun dismissError() = _uiState.update { it.copy(errorMessage = null) }
    fun dismissPermissionDenied() = _uiState.update { it.copy(permissionDeniedAction = null) }
}
