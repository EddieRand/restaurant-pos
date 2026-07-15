package com.restaurantpos.server.ai

import com.restaurantpos.server.model.AiInsightActionDto
import com.restaurantpos.server.model.AiInsightObservationDto
import com.restaurantpos.server.model.AiInsightPeriodDto
import com.restaurantpos.server.model.AiInsightSnapshotDto
import com.restaurantpos.server.model.AiOperatingInsightRequest
import com.restaurantpos.server.model.AiOperatingInsightResponse
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

class AiInsightService(
    private val dataSource: AiInsightDataSource,
    private val client: AiInsightClient?,
    val model: String,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private data class CacheEntry(val createdAt: Long, val response: AiOperatingInsightResponse)
    private val cache = ConcurrentHashMap<String, CacheEntry>()
    private val json = Json { encodeDefaults = true }

    suspend fun generate(request: AiOperatingInsightRequest): AiOperatingInsightResponse {
        require(request.toMs > request.fromMs) { "toMs must be greater than fromMs" }
        require(request.toMs - request.fromMs <= MAX_PERIOD_MS) { "Date range must not exceed 366 days" }
        require(request.locale.matches(LOCALE_PATTERN)) { "locale is invalid" }
        val configuredClient = client ?: throw AiNotConfiguredException()
        val metrics = dataSource.load(request.fromMs, request.toMs, request.locale)
        val fingerprint = sha256(json.encodeToString(metrics) + "|" + request.locale + "|" + model)
        cache[fingerprint]?.takeIf { now() - it.createdAt < CACHE_TTL_MS }?.let { return it.response }

        val generated = withTimeout(TOTAL_TIMEOUT_MS) {
            var last: AiProviderException? = null
            repeat(2) { attempt ->
                try {
                    return@withTimeout configuredClient.generate(metrics, request.locale)
                } catch (e: AiProviderException) {
                    last = e
                    if (!e.retryable || attempt == 1) throw e
                    delay(150)
                }
            }
            throw last ?: AiProviderException("AI_PROVIDER_ERROR", "AI generation failed", false)
        }
        val response = try {
            mapResponse(metrics, generated)
        } catch (_: IllegalArgumentException) {
            throw AiProviderException("AI_INVALID_RESPONSE", "DeepSeek response failed validation", true)
        }
        cache.entries.removeIf { now() - it.value.createdAt >= CACHE_TTL_MS }
        cache[fingerprint] = CacheEntry(now(), response)
        return response
    }

    private fun mapResponse(metrics: AiInsightMetrics, generated: AiGeneratedInsight): AiOperatingInsightResponse {
        require(generated.headline.isNotBlank() && generated.summary.isNotBlank()) { "AI response is missing required text" }
        val observations = generated.observations.take(5).map {
            require(it.title.isNotBlank() && it.detail.isNotBlank()) { "AI observation is invalid" }
            AiInsightObservationDto(
                severity = it.severity.lowercase().takeIf(ALLOWED_SEVERITIES::contains) ?: "neutral",
                title = it.title.trim(),
                detail = it.detail.trim(),
                evidenceKeys = it.evidenceKeys.filter(ALLOWED_EVIDENCE_KEYS::contains).distinct(),
            )
        }
        val actions = generated.actions.take(3).map {
            require(it.title.isNotBlank() && it.reason.isNotBlank()) { "AI action is invalid" }
            AiInsightActionDto(
                priority = it.priority.lowercase().takeIf(ALLOWED_PRIORITIES::contains) ?: "medium",
                title = it.title.trim(),
                reason = it.reason.trim(),
            )
        }
        require(actions.size == 3) { "AI response must contain three actions" }
        return AiOperatingInsightResponse(
            generatedAt = now(),
            model = model,
            period = AiInsightPeriodDto(metrics.fromMs, metrics.toMs),
            snapshot = AiInsightSnapshotDto(
                orderCount = metrics.orderCount,
                netRevenueMinorUnit = metrics.netRevenueMinorUnit,
                averageOrderValueMinorUnit = metrics.averageOrderValueMinorUnit,
                guestCount = metrics.guestCount,
            ),
            headline = generated.headline.trim(),
            summary = generated.summary.trim(),
            observations = observations,
            actions = actions,
        )
    }

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray())
        .joinToString("") { "%02x".format(it) }

    companion object {
        private const val TOTAL_TIMEOUT_MS = 20_000L
        private const val CACHE_TTL_MS = 5 * 60_000L
        private const val MAX_PERIOD_MS = 366L * 24 * 60 * 60 * 1000
        private val LOCALE_PATTERN = Regex("^[A-Za-z]{2,3}(-[A-Za-z0-9]{2,8})?$")
        private val ALLOWED_SEVERITIES = setOf("positive", "neutral", "warning")
        private val ALLOWED_PRIORITIES = setOf("high", "medium", "low")
        private val ALLOWED_EVIDENCE_KEYS = setOf(
            "orderCount", "grossRevenue", "netRevenue", "averageOrderValue", "guestCount",
            "discount", "refund", "paymentMethods", "topItems", "peakHours", "periodComparison",
        )

        fun fromEnvironment(): AiInsightService {
            val apiKey = System.getenv("DEEPSEEK_API_KEY")?.trim().orEmpty()
            val baseUrl = System.getenv("DEEPSEEK_BASE_URL")?.trim().takeUnless { it.isNullOrEmpty() }
                ?: "https://api.deepseek.com"
            val model = System.getenv("DEEPSEEK_MODEL")?.trim().takeUnless { it.isNullOrEmpty() }
                ?: "deepseek-v4-flash"
            val client = apiKey.takeIf(String::isNotEmpty)?.let {
                DeepSeekAiInsightClient(apiKey = it, baseUrl = baseUrl, model = model)
            }
            return AiInsightService(DatabaseAiInsightDataSource(), client, model)
        }
    }
}
