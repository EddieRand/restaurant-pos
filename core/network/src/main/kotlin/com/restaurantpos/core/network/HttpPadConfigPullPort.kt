package com.restaurantpos.core.network

import com.restaurantpos.core.config.PadConfig
import com.restaurantpos.core.sync.PadConfigPullPort
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

/**
 * Real HTTP implementation of [PadConfigPullPort].
 *
 * Web Admin persists the whole `RegionConfig` (including the nested `padConfig`
 * sub-object) as a single JSON blob under the generic settings key `regionConfig`
 * (legacy fallback `region-config`) — see `useRegionConfig`/`getRegionConfig` in
 * `web/admin/src/api/admin.ts`. There is no standalone `padConfig` settings key,
 * so we fetch the `regionConfig` blob and extract the nested `padConfig` field.
 */
class HttpPadConfigPullPort(
    private val baseUrl: String,
    private val client: OkHttpClient,
    private val authToken: () -> String,
) : PadConfigPullPort {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun pullPadConfig(): PadConfig? = withContext(Dispatchers.IO) {
        val raw = fetchSettingValue("regionConfig") ?: fetchSettingValue("region-config") ?: return@withContext null
        try {
            val root = json.parseToJsonElement(raw).jsonObject
            val padConfigElement = root["padConfig"] ?: return@withContext null
            json.decodeFromJsonElement<PadConfig>(padConfigElement)
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
                json.decodeFromString<SettingDto>(body).value
            }
        } catch (e: IOException) {
            null
        } catch (e: SerializationException) {
            null
        }
    }
}

@Serializable
private data class SettingDto(val key: String, val value: String)
