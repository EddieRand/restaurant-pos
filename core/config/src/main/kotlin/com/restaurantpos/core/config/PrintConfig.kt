package com.restaurantpos.core.config

import com.restaurantpos.core.model.TicketType
import kotlinx.serialization.Serializable

/**
 * Top-level print configuration: printer stations + per-ticket-type templates.
 * Replaces the flat fields in [ReceiptConfig] for new functionality;
 * [ReceiptConfig] is kept for backward-compat with Batch 4 hardware layer.
 */
@Serializable
data class PrintConfig(
    val stations: List<PrinterStation> = DefaultPrinterStations,
    val templates: Map<TicketType, TicketTemplateConfig> = defaultTicketTemplates(),
    /** Global font scale applied to all ticket types (1.0 = normal) */
    val fontSizeScale: Float = 1.0f,
    val printLogo: Boolean = false,
    val openCashDrawerOnReceipt: Boolean = false,
    val printDensity: Int = 80,
    val feedLinesAfterPrint: Int = 3,
) {
    fun templateFor(type: TicketType): TicketTemplateConfig =
        templates[type] ?: TicketTemplateConfig(ticketType = type)

    fun stationById(id: String): PrinterStation? = stations.find { it.id == id }

    fun withTemplate(config: TicketTemplateConfig): PrintConfig =
        copy(templates = templates + (config.ticketType to config))

    fun withStation(station: PrinterStation): PrintConfig =
        copy(stations = stations.map { if (it.id == station.id) station else it }
            .let { if (it.none { s -> s.id == station.id }) it + station else it })
}
