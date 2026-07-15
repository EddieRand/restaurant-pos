package com.restaurantpos.core.hardware

import android.util.Log

/**
 * Mock printer for simulator/unit tests. Logs output instead of printing.
 * Use this in all non-hardware test scenarios — never fake SUNMI SDK calls.
 */
class MockPrinter : PrinterPort {

    val printedReceipts = mutableListOf<ReceiptData>()
    val printedTickets = mutableListOf<KitchenTicketData>()

    override suspend fun printReceipt(data: ReceiptData): PrintResult {
        printedReceipts.add(data)
        Log.d("MockPrinter", buildReceiptLog(data))
        return PrintResult.Success
    }

    override suspend fun printKitchenTicket(data: KitchenTicketData): PrintResult {
        printedTickets.add(data)
        Log.d("MockPrinter", buildKitchenLog(data))
        return PrintResult.Success
    }

    override suspend fun isConnected(): Boolean = true

    fun clearHistory() {
        printedReceipts.clear()
        printedTickets.clear()
    }

    private fun buildReceiptLog(data: ReceiptData): String = buildString {
        appendLine("=== RECEIPT [${data.orderId}] ===")
        data.headerLines.forEach { appendLine("  $it") }
        if (data.taxId != null) appendLine("  TAX ID: ${data.taxId}")
        data.lines.forEach { appendLine("  ${it.label}: ${data.currencySymbol}${it.amountMinorUnit}") }
        appendLine("  TOTAL: ${data.currencySymbol}${data.totalMinorUnit}")
        data.footerLines.forEach { appendLine("  $it") }
        appendLine("==========================")
    }

    private fun buildKitchenLog(data: KitchenTicketData): String = buildString {
        appendLine("=== KITCHEN [${data.orderId}] table=${data.tableId} course=${data.course} station=${data.stationId} ===")
        data.items.forEach { appendLine("  ${it.quantity}x ${it.name} ${if (it.notes.isNotBlank()) "(${it.notes})" else ""}") }
        appendLine("==========================================")
    }
}
