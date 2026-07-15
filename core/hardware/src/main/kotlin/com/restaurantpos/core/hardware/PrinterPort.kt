package com.restaurantpos.core.hardware

/**
 * Hardware abstraction for receipt/kitchen printers.
 * SUNMI SDK implementation lives in :core:hardware (prod flavour).
 * MockPrinter is used for simulator testing and unit tests.
 */
interface PrinterPort {
    suspend fun printReceipt(data: ReceiptData): PrintResult
    suspend fun printKitchenTicket(data: KitchenTicketData): PrintResult
    suspend fun isConnected(): Boolean
}

sealed class PrintResult {
    object Success : PrintResult()
    data class Failure(val reason: String) : PrintResult()
}
