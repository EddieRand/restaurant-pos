package com.restaurantpos.core.config

import kotlinx.serialization.Serializable

@Serializable
enum class PrinterConnectionType { BUILT_IN, USB, NETWORK, BLUETOOTH }

/**
 * A named physical or virtual printer destination.
 * Tickets are routed to a station by ID.
 */
@Serializable
data class PrinterStation(
    val id: String,
    val name: String,
    val connectionType: PrinterConnectionType = PrinterConnectionType.BUILT_IN,
    /** IP address for NETWORK; device path for USB; MAC for BLUETOOTH; empty for BUILT_IN */
    val address: String = "",
    val paperWidthMm: Int = 58,
)

val DefaultPrinterStations = listOf(
    PrinterStation(id = "main", name = "Main Printer", connectionType = PrinterConnectionType.BUILT_IN),
    PrinterStation(id = "kitchen", name = "Kitchen Printer", connectionType = PrinterConnectionType.BUILT_IN),
)
