package com.restaurantpos.core.hardware

import com.restaurantpos.core.config.RegionConfig
import com.restaurantpos.core.domain.repository.OrderRepository
import com.restaurantpos.core.model.OrderStatus

/**
 * Builds a ReceiptData from the order and delegates printing to PrinterPort.
 *
 * Template:
 *   - One line per item: name × qty  subtotal
 *   - Tax line (if > 0)
 *   - Service charge line (if > 0)
 *   - Discount line (if > 0)
 *   - Total line
 */
class PrintReceiptUseCase(
    private val orderRepo: OrderRepository,
    private val printer: PrinterPort,
    private val regionConfig: RegionConfig,
) {
    sealed class Result {
        object Success : Result()
        data class Error(val reason: String) : Result()
    }

    suspend operator fun invoke(orderId: String, locale: String = "en"): Result {
        val order = orderRepo.getById(orderId)
            ?: return Result.Error("Order $orderId not found")

        if (order.status != OrderStatus.CLOSED && order.status != OrderStatus.READY) {
            return Result.Error("Cannot print receipt for order in status ${order.status}")
        }

        val items = orderRepo.getItemsByOrder(orderId)
        val lines = mutableListOf<ReceiptLine>()

        items.forEach { item ->
            val name = item.menuItemNameSnapshot[locale]
                ?: item.menuItemNameSnapshot.values.firstOrNull()
                ?: "Item"
            val label = "${name} ×${item.quantity}"
            lines.add(ReceiptLine(label = label, amountMinorUnit = item.lineTotalMinorUnit))
        }

        if (order.orderNotes.isNotBlank())
            lines.add(ReceiptLine("Note: ${order.orderNotes}", 0L))

        if (order.taxTotalMinorUnit > 0L)
            lines.add(ReceiptLine("Tax", order.taxTotalMinorUnit))
        if (order.serviceChargeMinorUnit > 0L)
            lines.add(ReceiptLine("Service Charge", order.serviceChargeMinorUnit))
        if (order.discountMinorUnit > 0L)
            lines.add(ReceiptLine("Discount", -order.discountMinorUnit))

        val rc = regionConfig.receiptConfig
        val receiptData = ReceiptData(
            orderId = orderId,
            lines = lines,
            totalMinorUnit = order.totalMinorUnit,
            currencySymbol = regionConfig.currencySymbol,
            currencyMinorDigits = regionConfig.currencyMinorDigits,
            headerLines = rc.headerLines,
            footerLines = rc.footerLines,
            taxId = if (rc.showTaxId && rc.taxId.isNotBlank()) rc.taxId else null,
        )

        return when (val printResult = printer.printReceipt(receiptData)) {
            is PrintResult.Success -> Result.Success
            is PrintResult.Failure -> Result.Error(printResult.reason)
        }
    }
}
