package com.restaurantpos.server.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

class DeepSeekAiPriceIntentClient(
    private val apiKey: String,
    private val baseUrl: String,
    private val model: String,
    private val httpClient: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5))
        .build(),
) : AiPriceIntentClient {
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun parse(
        instruction: String,
        locale: String,
        currencyCode: String,
        minorUnitDigits: Int,
    ): AiPriceIntent = withContext(Dispatchers.IO) {
        val prompt = """
            Structure one restaurant menu price-change instruction. Do not choose a menu item and do not calculate a final price.
            Return JSON with exactly: targetName, operation, amountMajorUnit, percentage.
            operation must be SET, INCREASE, or DECREASE.
            Preserve amounts and percentages as decimal strings, never JSON numbers.
            Exactly one of amountMajorUnit or percentage must be a decimal string; the other must be null.
            For SET, amountMajorUnit is required. Do not infer missing amounts.
            Locale: $locale. Currency: $currencyCode. Currency minor digits: $minorUnitDigits.
            User instruction: $instruction
        """.trimIndent()
        val payload = buildJsonObject {
            put("model", model)
            put("temperature", 0)
            put("max_tokens", 250)
            put("stream", false)
            put("thinking", buildJsonObject { put("type", "disabled") })
            put("response_format", buildJsonObject { put("type", "json_object") })
            putJsonArray("messages") {
                add(buildJsonObject { put("role", "system"); put("content", "Return valid JSON only.") })
                add(buildJsonObject { put("role", "user"); put("content", prompt) })
            }
        }.toString()
        val request = HttpRequest.newBuilder()
            .uri(URI.create("${baseUrl.trimEnd('/')}/chat/completions"))
            .timeout(Duration.ofSeconds(17))
            .header("Authorization", "Bearer $apiKey")
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(payload))
            .build()
        val response = try {
            httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        } catch (_: Exception) {
            throw AiProviderException("AI_PROVIDER_UNAVAILABLE", "DeepSeek request failed", true)
        }
        when (response.statusCode()) {
            401, 403 -> throw AiProviderException("AI_AUTH_FAILED", "DeepSeek credentials are invalid", false)
            402 -> throw AiProviderException("AI_QUOTA_EXCEEDED", "DeepSeek quota is insufficient", false)
            429 -> throw AiProviderException("AI_RATE_LIMITED", "DeepSeek rate limit reached", true)
            in 500..599 -> throw AiProviderException("AI_PROVIDER_UNAVAILABLE", "DeepSeek is temporarily unavailable", true)
        }
        if (response.statusCode() !in 200..299) {
            throw AiProviderException("AI_PROVIDER_ERROR", "DeepSeek rejected the request", false)
        }
        val content = runCatching {
            json.parseToJsonElement(response.body()).jsonObject["choices"]
                ?.jsonArray?.firstOrNull()?.jsonObject
                ?.get("message")?.jsonObject
                ?.get("content")?.jsonPrimitive?.content
        }.getOrNull()?.trim()
        if (content.isNullOrEmpty()) {
            throw AiProviderException("AI_INVALID_RESPONSE", "DeepSeek returned empty content", true)
        }
        val intent = runCatching { json.decodeFromString<AiPriceIntent>(content) }
            .getOrElse { throw AiProviderException("AI_INVALID_RESPONSE", "DeepSeek returned invalid intent JSON", true) }
        if (intent.targetName.isBlank() || intent.operation.isBlank()) {
            throw AiProviderException("AI_INVALID_RESPONSE", "DeepSeek intent is incomplete", true)
        }
        intent
    }
}
