package com.restaurantpos.core.network

import com.restaurantpos.core.sync.RegionFormat
import com.restaurantpos.core.sync.RegionFormatPullPort
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

/**
 * Real HTTP implementation of [RegionFormatPullPort].
 *
 * Web Admin persists the whole `RegionConfig` as a single JSON blob under the settings
 * key `regionConfig` (legacy fallback `region-config`). We fetch that blob and read the
 * currency/number-format fields, tolerating the admin's legacy aliases
 * (`minorDigits` for `currencyMinorDigits`, `groupingSeparator` for `thousandsSeparator`).
 */
class HttpRegionFormatPullPort(
    private val baseUrl: String,
    private val client: OkHttpClient,
    private val authToken: () -> String,
) : RegionFormatPullPort {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun pullFormat(): RegionFormat? = withContext(Dispatchers.IO) {
        val raw = fetchSettingValue("regionConfig") ?: fetchSettingValue("region-config") ?: return@withContext null
        try {
            val o: JsonObject = json.parseToJsonElement(raw).jsonObject
            fun str(key: String): String? = o[key]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotEmpty() }
            fun int(key: String): Int? = o[key]?.jsonPrimitive?.intOrNull

            // All fields optional — the admin blob may be partial. Absent fields stay null
            // so the puller keeps the terminal's current value for them.
            RegionFormat(
                currencyCode = str("currencyCode"),
                currencySymbol = str("currencySymbol"),
                currencyMinorDigits = (int("currencyMinorDigits") ?: int("minorDigits"))?.coerceIn(0, 4),
                thousandsSeparator = (str("thousandsSeparator") ?: str("groupingSeparator"))?.firstOrNull(),
                decimalSeparator = str("decimalSeparator")?.firstOrNull(),
                availableTaxRatesJson = o["availableTaxRates"]?.toString(),
                tipPresetsJson = o["tipConfig"]?.toString(),
                serviceChargeRatePermille = int("serviceChargeRatePermille"),
                locale = str("locale"),
                timeZone = str("timeZone"),
            )
        } catch (e: SerializationException) {
            null
        } catch (e: IllegalArgumentException) {
            null
        }
    }

    private fun fetchSettingValue(key: String): String? {
        val request = Request.Builder()
            .url("$baseUrl/admin/settings/$key")
            .header("Authorization", "Bearer ${authToken()}")
            .get()
            .build()

        return try {
            client.newCall(request).execute().use { res ->
                if (!res.isSuccessful) return null
                val body = res.body?.string() ?: return null
                json.decodeFromString<RegionSettingDto>(body).value
            }
        } catch (e: IOException) {
            null
        } catch (e: SerializationException) {
            null
        }
    }
}

@Serializable
private data class RegionSettingDto(val key: String, val value: String)
