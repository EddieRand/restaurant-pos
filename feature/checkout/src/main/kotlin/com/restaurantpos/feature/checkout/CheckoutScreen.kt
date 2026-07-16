package com.restaurantpos.feature.checkout

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.restaurantpos.core.config.AmountFormatter
import com.restaurantpos.core.model.Customer
import com.restaurantpos.core.model.PaymentMethod
import com.restaurantpos.core.model.PaymentStatus

@Composable
fun CheckoutScreen(
    onPaymentComplete: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CheckoutViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val formatter = remember(uiState.regionConfig) { AmountFormatter(uiState.regionConfig) }

    LaunchedEffect(uiState.finished) {
        if (uiState.finished) onPaymentComplete()
    }

    // Print error dialog
    if (uiState.printError != null) {
        AlertDialog(
            onDismissRequest = viewModel::dismissPrintError,
            title = { Text(stringResource(R.string.checkout_print_error)) },
            text = { Text(uiState.printError!!) },
            confirmButton = {
                TextButton(onClick = viewModel::dismissPrintError) { Text(stringResource(R.string.checkout_ok)) }
            },
        )
    }

    // Permission denied dialog
    if (uiState.permissionDeniedAction != null) {
        AlertDialog(
            onDismissRequest = viewModel::dismissPermissionDenied,
            title = { Text(stringResource(R.string.permission_denied_title)) },
            text = { Text(stringResource(R.string.permission_denied_body, uiState.permissionDeniedAction!!)) },
            confirmButton = {
                TextButton(onClick = viewModel::dismissPermissionDenied) {
                    Text(stringResource(R.string.permission_denied_ok))
                }
            },
        )
    }

    Row(modifier = modifier.fillMaxSize()) {
        // Left panel — order summary + adjustments
        Column(
            modifier = Modifier.weight(1f).fillMaxHeight().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.checkout_title), style = MaterialTheme.typography.headlineMedium)

            OrderTotalsCard(uiState, formatter)

            if (uiState.regionConfig.tipConfig.enabled) {
                TipRow(
                    tipConfig = uiState.regionConfig.tipConfig,
                    subtotalMinorUnit = uiState.subtotalMinorUnit,
                    taxMinorUnit = uiState.taxTotalMinorUnit,
                    currentTipMinorUnit = uiState.tipMinorUnit,
                    onApplyTip = viewModel::applyTip,
                )
            }

            MemberLookupRow(
                phone = uiState.memberPhone,
                member = uiState.boundMember,
                earnPreview = uiState.earnPointsPreview,
                isLoading = uiState.memberSearchLoading,
                onPhoneChange = viewModel::setMemberPhone,
                onLookup = viewModel::lookupMemberByPhone,
                onClear = viewModel::clearMember,
            )

            CouponRow(
                couponCode = uiState.couponCode,
                appliedCouponCode = uiState.appliedCouponCode,
                onCodeChange = viewModel::setCouponCode,
                onApply = viewModel::applyCoupon,
            )

            DiscountServiceChargeRow(
                onDiscount = viewModel::applyOrderDiscount,
                onServiceCharge = viewModel::applyServiceCharge,
            )

            if (uiState.payments.isNotEmpty()) {
                PaymentHistoryCard(
                    payments = uiState.payments,
                    formatter = formatter,
                    onRefund = viewModel::refundPayment,
                )
            }
        }

        // Right panel — payment actions
        Column(
            modifier = Modifier.weight(1f).fillMaxHeight().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = formatter.format(uiState.remainingMinorUnit),
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                stringResource(R.string.checkout_remaining),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(8.dp))

            uiState.errorMessage?.let { err ->
                Text(err, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = viewModel::dismissError) { Text(stringResource(android.R.string.ok)) }
            }

            // Tip step — payment is gated behind it; cashier sets the tip on the left, then continues.
            if (uiState.step == CheckoutStep.TIP && !uiState.isSettled) {
                Text(
                    stringResource(R.string.checkout_tip_prompt),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(
                    onClick = viewModel::proceedToPayment,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) { Text(stringResource(R.string.checkout_continue_payment), style = MaterialTheme.typography.titleMedium) }
            } else if (uiState.isSettled && uiState.step == CheckoutStep.RECEIPT) {
                // Receipt step — customer display shows receipt options.
                Text(stringResource(R.string.checkout_receipt_step), style = MaterialTheme.typography.titleLarge)
                Text(
                    stringResource(R.string.checkout_receipt_prompt),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(onClick = viewModel::printReceipt, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.checkout_reprint_receipt))
                }
                Button(
                    onClick = viewModel::finishCheckout,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) { Text(stringResource(R.string.checkout_done), style = MaterialTheme.typography.titleMedium) }
            } else if (uiState.isSettled) {
                // Payment complete — confirm, then offer receipt or finish.
                Text(stringResource(R.string.checkout_payment_successful), style = MaterialTheme.typography.titleLarge)
                Button(
                    onClick = viewModel::showReceiptStep,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) { Text(stringResource(R.string.checkout_send_receipt), style = MaterialTheme.typography.titleMedium) }
                OutlinedButton(onClick = viewModel::finishCheckout, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.checkout_done))
                }
            } else if (!uiState.isSplitMode) {
                PaymentMethod.entries.filter { it != PaymentMethod.GIFT_CARD }.forEach { method ->
                    Button(
                        onClick = { viewModel.payWithMethod(method) },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        enabled = !uiState.isLoading && uiState.remainingMinorUnit > 0,
                    ) {
                        if (uiState.isLoading) {
                            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Text(paymentMethodLabel(method), style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = uiState.giftCardCode,
                        onValueChange = viewModel::setGiftCardCode,
                        label = { Text(stringResource(R.string.checkout_gift_card_label)) },
                        placeholder = { Text(stringResource(R.string.checkout_gift_card_hint)) },
                        singleLine = true,
                        enabled = uiState.isOnline,
                        modifier = Modifier.weight(1f),
                    )
                    Button(
                        onClick = { viewModel.payWithGiftCard() },
                        enabled = !uiState.isLoading && uiState.isOnline && uiState.remainingMinorUnit > 0 && uiState.giftCardCode.isNotBlank(),
                        modifier = Modifier.height(56.dp),
                    ) { Text(stringResource(R.string.checkout_gift_card_pay)) }
                }
                if (!uiState.isOnline) {
                    Text(
                        text = stringResource(R.string.checkout_gift_card_offline_hint),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                OutlinedButton(
                    onClick = { viewModel.activateSplitMode(2) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = uiState.remainingMinorUnit > 0,
                ) { Text(stringResource(R.string.checkout_split) + " (×2)") }

                OutlinedButton(
                    onClick = { viewModel.activateSplitMode(3) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = uiState.remainingMinorUnit > 0,
                ) { Text(stringResource(R.string.checkout_split) + " (×3)") }
            } else {
                Text(stringResource(R.string.checkout_split), style = MaterialTheme.typography.titleLarge)
                uiState.splitShares.forEach { share ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp)) {
                            Text("${share.index + 1}: ${formatter.format(share.amountMinorUnit)}")
                            Spacer(Modifier.height(4.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                PaymentMethod.entries.take(2).forEach { method ->
                                    OutlinedButton(
                                        onClick = { viewModel.payShareWithMethod(share.index, method) },
                                        modifier = Modifier.weight(1f),
                                        enabled = uiState.remainingMinorUnit > 0,
                                    ) { Text(paymentMethodLabel(method)) }
                                }
                            }
                        }
                    }
                }
                TextButton(onClick = viewModel::deactivateSplitMode) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        }
    }
}

@Composable
private fun OrderTotalsCard(uiState: CheckoutUiState, formatter: AmountFormatter) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            TotalRow(stringResource(R.string.checkout_subtotal), uiState.subtotalMinorUnit, formatter)
            if (uiState.taxTotalMinorUnit != 0L)
                TotalRow(stringResource(R.string.checkout_tax), uiState.taxTotalMinorUnit, formatter)
            if (uiState.serviceChargeMinorUnit != 0L)
                TotalRow(stringResource(R.string.checkout_service_charge), uiState.serviceChargeMinorUnit, formatter)
            if (uiState.tipMinorUnit != 0L)
                TotalRow(stringResource(R.string.checkout_tip), uiState.tipMinorUnit, formatter)
            if (uiState.discountMinorUnit != 0L)
                TotalRow(stringResource(R.string.checkout_discount), -uiState.discountMinorUnit, formatter)
            HorizontalDivider()
            TotalRow(
                stringResource(R.string.checkout_total),
                uiState.totalMinorUnit,
                formatter,
                style = MaterialTheme.typography.titleLarge,
            )
            if (uiState.alreadyPaidMinorUnit > 0L)
                TotalRow(stringResource(R.string.checkout_paid), uiState.alreadyPaidMinorUnit, formatter)
        }
    }
}

@Composable
private fun TotalRow(
    label: String,
    amountMinorUnit: Long,
    formatter: AmountFormatter,
    style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.bodyMedium,
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = style)
        Text(formatter.format(amountMinorUnit), style = style)
    }
}

@Composable
private fun MemberLookupRow(
    phone: String,
    member: Customer?,
    earnPreview: Long,
    isLoading: Boolean,
    onPhoneChange: (String) -> Unit,
    onLookup: () -> Unit,
    onClear: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (member == null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = phone,
                    onValueChange = onPhoneChange,
                    label = { Text(stringResource(R.string.checkout_member_phone)) },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    placeholder = { Text(stringResource(R.string.checkout_member_phone_hint)) },
                )
                Button(onClick = onLookup, enabled = phone.isNotBlank() && !isLoading) {
                    if (isLoading) CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    else Text(stringResource(R.string.checkout_member_find))
                }
            }
        } else {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column {
                        Text(member.name, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "${member.loyaltyPoints} pts  •  +$earnPreview pts this visit",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    }
                    TextButton(onClick = onClear) { Text(stringResource(R.string.checkout_member_clear)) }
                }
            }
        }
    }
}

@Composable
private fun CouponRow(
    couponCode: String,
    appliedCouponCode: String?,
    onCodeChange: (String) -> Unit,
    onApply: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = couponCode,
                onValueChange = onCodeChange,
                label = { Text(stringResource(R.string.checkout_coupon_label)) },
                modifier = Modifier.weight(1f),
                singleLine = true,
                placeholder = { Text(stringResource(R.string.checkout_coupon_hint)) },
            )
            Button(onClick = onApply, enabled = couponCode.isNotBlank()) {
                Text(stringResource(R.string.checkout_coupon_apply))
            }
        }
        if (appliedCouponCode != null) {
            Text(
                text = stringResource(R.string.checkout_coupon_applied, appliedCouponCode),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.tertiary,
            )
        }
    }
}

@Composable
private fun DiscountServiceChargeRow(
    onDiscount: (Long) -> Unit,
    onServiceCharge: (Long) -> Unit,
) {
    var showDiscount by remember { mutableStateOf(false) }
    var showServiceCharge by remember { mutableStateOf(false) }
    var discountInput by remember { mutableStateOf("") }
    var serviceInput by remember { mutableStateOf("") }

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { showDiscount = !showDiscount; showServiceCharge = false }) {
            Text(stringResource(R.string.checkout_discount_label))
        }
        OutlinedButton(onClick = { showServiceCharge = !showServiceCharge; showDiscount = false }) {
            Text(stringResource(R.string.checkout_service_charge_label))
        }
    }

    AnimatedVisibility(showDiscount) {
        AmountInputRow(
            label = stringResource(R.string.checkout_discount_label),
            value = discountInput,
            onValueChange = { discountInput = it },
        ) {
            onDiscount(discountInput.toLongOrNull() ?: 0L)
            showDiscount = false
            discountInput = ""
        }
    }
    AnimatedVisibility(showServiceCharge) {
        AmountInputRow(
            label = stringResource(R.string.checkout_service_charge_label),
            value = serviceInput,
            onValueChange = { serviceInput = it },
        ) {
            onServiceCharge(serviceInput.toLongOrNull() ?: 0L)
            showServiceCharge = false
            serviceInput = ""
        }
    }
}

@Composable
private fun TipRow(
    tipConfig: com.restaurantpos.core.config.TipConfig,
    subtotalMinorUnit: Long,
    taxMinorUnit: Long,
    currentTipMinorUnit: Long,
    onApplyTip: (Long) -> Unit,
) {
    var showCustom by remember { mutableStateOf(false) }
    var customInput by remember { mutableStateOf("") }

    // Base amount for % calculation depends on merchant config (pre-tax vs post-tax)
    val calcBase = if (tipConfig.calcBase == com.restaurantpos.core.config.TipCalcBase.POST_TAX)
        subtotalMinorUnit + taxMinorUnit
    else
        subtotalMinorUnit

    // Always show "No tip" (0%) prepended to merchant presets
    val allPresets = listOf(0) + tipConfig.presets

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.checkout_tip_label),
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(end = 4.dp),
            )
            allPresets.forEach { pct ->
                val tipAmt = if (pct == 0) 0L else calcBase * pct / 100
                val isSelected = !showCustom && currentTipMinorUnit == tipAmt
                FilterChip(
                    selected = isSelected,
                    onClick = {
                        showCustom = false
                        customInput = ""
                        onApplyTip(tipAmt)
                    },
                    label = { Text(if (pct == 0) stringResource(R.string.checkout_tip_no_tip) else "$pct%") },
                )
            }
            FilterChip(
                selected = showCustom,
                onClick = { showCustom = !showCustom },
                label = { Text(stringResource(R.string.checkout_tip_custom)) },
            )
        }
        AnimatedVisibility(showCustom) {
            AmountInputRow(
                label = stringResource(R.string.checkout_tip_label),
                value = customInput,
                onValueChange = { customInput = it },
            ) {
                onApplyTip(customInput.toLongOrNull() ?: 0L)
                showCustom = false
                customInput = ""
            }
        }
    }
}

@Composable
private fun AmountInputRow(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    onConfirm: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            modifier = Modifier.weight(1f),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
        Button(onClick = onConfirm) { Text(stringResource(R.string.checkout_apply)) }
    }
}

@Composable
private fun PaymentHistoryCard(
    payments: List<com.restaurantpos.core.model.Payment>,
    formatter: AmountFormatter,
    onRefund: (String) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(stringResource(R.string.checkout_payment_history), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            payments.filter { it.status == PaymentStatus.PAID }.forEach { payment ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(R.string.checkout_payment_history_item, paymentMethodLabel(payment.method), formatter.format(payment.amountMinorUnit)))
                    TextButton(onClick = { onRefund(payment.id) }) {
                        Text(stringResource(R.string.checkout_refund))
                    }
                }
            }
        }
    }
}

@Composable
private fun paymentMethodLabel(method: PaymentMethod): String = when (method) {
    PaymentMethod.CASH -> stringResource(R.string.checkout_pay_cash)
    PaymentMethod.CARD -> stringResource(R.string.checkout_pay_card)
    PaymentMethod.QR_CODE -> stringResource(R.string.checkout_pay_qr)
    PaymentMethod.VOUCHER -> stringResource(R.string.checkout_pay_voucher)
    PaymentMethod.GIFT_CARD -> stringResource(R.string.checkout_pay_gift_card)
    PaymentMethod.OTHER -> stringResource(R.string.checkout_pay_other)
}
