package com.restaurantpos.core.hardware

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.os.RemoteException
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import woyou.aidlservice.jiuiv5.IWoyouService
import kotlin.coroutines.resume

private const val TAG = "SunmiPrinter"
private const val SUNMI_SERVICE_PKG = "woyou.market.factoryservice"
private const val SUNMI_SERVICE_ACTION = "woyou.market.factoryservice.IWoyouService"
private const val BIND_TIMEOUT_MS = 4_000L

/**
 * Production SUNMI printer implementation.
 *
 * Binds to the SUNMI system printer service (InnerPrinterManager AIDL) at construction
 * time. Falls back to [PrintResult.Failure] if the device is not a SUNMI printer or the
 * service is unavailable.
 *
 * ESC/POS formatting is handled by the SDK's own API — we call the high-level methods
 * (printText, printColumnsText, lineWrap, cutPaper) rather than raw byte arrays so the
 * code stays readable and locale-safe.
 *
 * Currency formatting: amounts are in minor units (e.g. cents). We divide by
 * 10^minorDigits to get the display amount.
 */
class SunmiPrinter(private val context: Context) : PrinterPort {

    private var service: IWoyouService? = null
    private val lock = Any()

    init {
        bindService()
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            synchronized(lock) {
                service = IWoyouService.Stub.asInterface(binder)
                Log.i(TAG, "SUNMI printer service connected")
            }
        }

        override fun onServiceDisconnected(name: ComponentName) {
            synchronized(lock) {
                service = null
                Log.w(TAG, "SUNMI printer service disconnected — rebinding")
            }
            bindService()
        }
    }

    private fun bindService() {
        try {
            val intent = Intent(SUNMI_SERVICE_ACTION).apply {
                setPackage(SUNMI_SERVICE_PKG)
            }
            context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to bind SUNMI printer service", e)
        }
    }

    override suspend fun isConnected(): Boolean = withContext(Dispatchers.IO) {
        try {
            synchronized(lock) { service }?.let { svc ->
                svc.printerStatus == 0   // 0 = normal
            } ?: false
        } catch (e: RemoteException) { false }
    }

    override suspend fun printReceipt(data: ReceiptData): PrintResult = withContext(Dispatchers.IO) {
        val svc = awaitService() ?: return@withContext PrintResult.Failure("Printer not available")
        try {
            svc.printerInit(null)

            // Header
            svc.setPrinterStyle(SUNMI_ALIGN_CENTER, null)
            data.headerLines.forEach { svc.printText("$it\n", null) }
            if (data.taxId != null) svc.printText("Tax ID: ${data.taxId}\n", null)
            svc.printText("\n", null)

            // Items
            svc.setPrinterStyle(SUNMI_ALIGN_LEFT, null)
            data.lines.forEach { line ->
                val amtStr = formatAmount(line.amountMinorUnit, data.currencySymbol, data.currencyMinorDigits)
                svc.printColumnsText(
                    arrayOf(line.label, amtStr),
                    intArrayOf(3, 1),
                    intArrayOf(SUNMI_ALIGN_LEFT, SUNMI_ALIGN_RIGHT),
                    null,
                )
            }

            // Divider
            svc.printText("--------------------------------\n", null)

            // Total
            val totalStr = formatAmount(data.totalMinorUnit, data.currencySymbol, data.currencyMinorDigits)
            svc.setPrinterStyle(SUNMI_BOLD, null)
            svc.printColumnsText(
                arrayOf("TOTAL", totalStr),
                intArrayOf(3, 1),
                intArrayOf(SUNMI_ALIGN_LEFT, SUNMI_ALIGN_RIGHT),
                null,
            )
            svc.setPrinterStyle(SUNMI_BOLD_CANCEL, null)

            // Footer
            svc.printText("\n", null)
            svc.setPrinterStyle(SUNMI_ALIGN_CENTER, null)
            data.footerLines.forEach { svc.printText("$it\n", null) }

            // Feed + cut
            svc.lineWrap(3, null)
            svc.cutPaper(null)

            PrintResult.Success
        } catch (e: RemoteException) {
            Log.e(TAG, "printReceipt RemoteException", e)
            PrintResult.Failure(e.message ?: "RemoteException")
        }
    }

    override suspend fun printKitchenTicket(data: KitchenTicketData): PrintResult = withContext(Dispatchers.IO) {
        val svc = awaitService() ?: return@withContext PrintResult.Failure("Printer not available")
        try {
            svc.printerInit(null)

            // Header
            svc.setPrinterStyle(SUNMI_BOLD, null)
            svc.setPrinterStyle(SUNMI_ALIGN_CENTER, null)
            svc.printText("KITCHEN\n", null)
            svc.printText("Table: ${data.tableId ?: "TAKEAWAY"}  Course: ${data.course}\n", null)
            svc.printText("Station: ${data.stationId}\n", null)
            svc.setPrinterStyle(SUNMI_BOLD_CANCEL, null)
            svc.printText("--------------------------------\n", null)

            // Items
            svc.setPrinterStyle(SUNMI_ALIGN_LEFT, null)
            data.items.forEach { item ->
                svc.setPrinterStyle(SUNMI_FONT_SIZE_LARGE, null)
                svc.printText("${item.quantity}x  ${item.name}\n", null)
                svc.setPrinterStyle(SUNMI_FONT_SIZE_NORMAL, null)
                if (item.notes.isNotBlank()) {
                    svc.printText("    >> ${item.notes}\n", null)
                }
            }

            svc.lineWrap(3, null)
            svc.cutPaper(null)

            PrintResult.Success
        } catch (e: RemoteException) {
            Log.e(TAG, "printKitchenTicket RemoteException", e)
            PrintResult.Failure(e.message ?: "RemoteException")
        }
    }

    private suspend fun awaitService(): IWoyouService? {
        // Fast path — already connected
        synchronized(lock) { service }?.let { return it }
        // Wait up to BIND_TIMEOUT_MS for the service to connect
        return withTimeoutOrNull(BIND_TIMEOUT_MS) {
            suspendCancellableCoroutine { cont ->
                val check = object : Runnable {
                    override fun run() {
                        val svc = synchronized(lock) { service }
                        if (svc != null) cont.resume(svc)
                        else android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(this, 200)
                    }
                }
                android.os.Handler(android.os.Looper.getMainLooper()).post(check)
                cont.invokeOnCancellation { /* nothing needed */ }
            }
        }
    }

    private fun formatAmount(minorUnit: Long, symbol: String, minorDigits: Int): String {
        val divisor = Math.pow(10.0, minorDigits.toDouble()).toLong()
        val whole = minorUnit / divisor
        val frac = Math.abs(minorUnit % divisor)
        return if (minorDigits > 0) "$symbol$whole.${frac.toString().padStart(minorDigits, '0')}"
        else "$symbol$whole"
    }

    // SUNMI style constants — mirrors woyou.aidlservice.jiuiv5 constants
    companion object {
        private const val SUNMI_ALIGN_LEFT   = 0
        private const val SUNMI_ALIGN_CENTER = 1
        private const val SUNMI_ALIGN_RIGHT  = 2
        private const val SUNMI_BOLD         = 4     // WoyouConsts.ENABLE_BOLD
        private const val SUNMI_BOLD_CANCEL  = 5     // WoyouConsts.DISABLE_BOLD
        private const val SUNMI_FONT_SIZE_LARGE  = 13 // double height+width
        private const val SUNMI_FONT_SIZE_NORMAL = 14 // reset
    }
}
