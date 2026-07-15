package com.restaurantpos.core.sync

import com.restaurantpos.core.config.ConfigRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Pulls the server-authoritative currency / number-format settings (configured via
 * Web Admin) down into the local [ConfigRepository] so every terminal formats money
 * identically — currency symbol, minor digits, decimal & thousands separators.
 *
 * Mirrors [PadConfigSyncPuller]: the config is a single small blob, so every pull
 * replaces the format fields wholesale on [start] and on each reconnect/poll. The Web
 * Admin is authoritative, so this intentionally overrides any local edits.
 */
class RegionFormatSyncPuller(
    private val port: RegionFormatPullPort,
    private val configRepository: ConfigRepository,
    private val network: NetworkMonitor,
    private val pollIntervalMs: Long = 30_000L,
) {
    private var job: Job? = null

    fun start(scope: CoroutineScope) {
        job = scope.launch {
            pull()
            launch {
                network.isOnline
                    .distinctUntilChanged()
                    .filter { it }
                    .collect { pull() }
            }
            // Poll periodically — admin currency edits must reach devices mid-session.
            while (isActive) {
                delay(pollIntervalMs)
                pull()
            }
        }
    }

    fun stop() { job?.cancel(); job = null }

    /** Fetch and apply the merchant's currency/format + tax/tip/service-charge settings. Best-effort. */
    suspend fun pull() {
        val fmt = runCatching { port.pullFormat() }.getOrNull() ?: return
        val current = configRepository.current()
        // Merge: only override fields the merchant actually set in Web Admin.
        val updatedTaxRates = parseTaxRates(fmt.availableTaxRatesJson) ?: current.availableTaxRates
        val updatedTipConfig = parseTipConfig(fmt.tipPresetsJson) ?: current.tipConfig
        val updated = current.copy(
            currencyCode = fmt.currencyCode ?: current.currencyCode,
            currencySymbol = fmt.currencySymbol ?: current.currencySymbol,
            currencyMinorDigits = fmt.currencyMinorDigits ?: current.currencyMinorDigits,
            thousandsSeparator = fmt.thousandsSeparator ?: current.thousandsSeparator,
            decimalSeparator = fmt.decimalSeparator ?: current.decimalSeparator,
            availableTaxRates = updatedTaxRates,
            tipConfig = updatedTipConfig,
            serviceChargeRatePermille = fmt.serviceChargeRatePermille ?: current.serviceChargeRatePermille,
            locale = fmt.locale ?: current.locale,
            timeZone = fmt.timeZone ?: current.timeZone,
        )
        if (updated != current) configRepository.update(updated)
    }

    private fun parseTaxRates(json: String?): List<com.restaurantpos.core.config.TaxRate>? {
        if (json == null) return null
        return try {
            val rates = mutableListOf<com.restaurantpos.core.config.TaxRate>()
            // Parse JSON array: [{"id":"...","name":"...","ratePermille":...}, ...]
            val jsonStr = json.trim()
            if (!jsonStr.startsWith("[")) return null
            val objPattern = Regex("""\{[^}]+\}""")
            objPattern.findAll(jsonStr).forEach { match ->
                val obj = match.value
                val id = Regex(""""id"\s*:\s*"([^"]+)"""").find(obj)?.groupValues?.get(1) ?: return@forEach
                val name = Regex(""""name"\s*:\s*"([^"]+)"""").find(obj)?.groupValues?.get(1) ?: id
                val ratePermille = Regex(""""ratePermille"\s*:\s*(\d+)""").find(obj)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
                rates.add(com.restaurantpos.core.config.TaxRate(id = id, name = name, ratePermille = ratePermille))
            }
            if (rates.isEmpty()) null else rates
        } catch (_: Exception) { null }
    }

    private fun parseTipConfig(json: String?): com.restaurantpos.core.config.TipConfig? {
        if (json == null) return null
        return try {
            val enabled = Regex(""""enabled"\s*:\s*(true|false)""").find(json)?.groupValues?.get(1) == "true"
            val presets = Regex(""""presets"\s*:\s*\[([^\]]*)\]""").find(json)?.groupValues?.get(1)
                ?.split(",")?.mapNotNull { it.trim().toIntOrNull() } ?: emptyList()
            if (presets.isEmpty() && !enabled) null
            else com.restaurantpos.core.config.TipConfig(enabled = enabled, presets = presets)
        } catch (_: Exception) { null }
    }
}
