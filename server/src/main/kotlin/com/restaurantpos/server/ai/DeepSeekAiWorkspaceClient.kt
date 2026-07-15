package com.restaurantpos.server.ai

import com.restaurantpos.server.model.AiWorkspaceContextDto
import com.restaurantpos.server.model.AiWorkspaceEvidenceDto
import com.restaurantpos.server.model.AiWorkspaceExpert
import com.restaurantpos.server.model.AiWorkspacePeriodDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
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

class DeepSeekAiWorkspaceClient(
    private val apiKey: String,
    private val baseUrl: String,
    private val model: String,
    private val httpClient: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5))
        .build(),
) : AiWorkspaceModelClient {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    override suspend fun plan(
        message: String,
        expert: AiWorkspaceExpert,
        context: AiWorkspaceContextDto,
        allowedTools: Set<String>,
    ): AiWorkspacePlan {
        val prompt = """
            你是餐饮 SaaS 的受控任务路由器，只输出 JSON，不回答用户。
            可用工具：${allowedTools.sorted().joinToString(",")}
            专家：${expert.name}
            时间上下文：${json.encodeToString(context)}
            将指令拆为最多 5 个有序步骤。格式：
            {"steps":[{"tool":"...","displayTitle":"中文短标题","instruction":"该工具需要的完整子指令","queryType":null,"dependsOn":[]}],"clarification":null}
            queryType 只允许 SUMMARY、TOP_ITEMS、PEAK_HOURS、PAYMENT_METHODS、TREND。
            经营综合分析用 report.operating_insight；明确问数据用 report.query；产品操作指导用 product.howto_search；明确指定菜品及改价金额/比例才用 menu.update_price。
            不得创造工具。不得依据分析自行决定价格。如果改价没有明确菜品或明确金额/比例，steps 返回空数组并填写 clarification。
            dependsOn 使用之前步骤的一基序号。所有标题用中文。
            用户指令：$message
        """.trimIndent()
        return decode(sendJson(prompt, 1_100))
    }

    override suspend fun explainQuery(
        question: String,
        period: AiWorkspacePeriodDto,
        evidence: List<AiWorkspaceEvidenceDto>,
    ): String {
        val prompt = """
            只根据给出的餐厅聚合证据，用简洁中文回答问题。不得计算、改写或虚构数值，不得提及顾客个人信息。
            返回 JSON：{"answer":"..."}
            问题：$question
            时间：${json.encodeToString(period)}
            证据：${json.encodeToString(evidence)}
        """.trimIndent()
        return sendJson(prompt, 450).jsonObject["answer"]?.jsonPrimitive?.content?.trim()
            ?.takeIf(String::isNotEmpty)
            ?: throw AiProviderException("AI_INVALID_RESPONSE", "DeepSeek query answer is empty", true)
    }

    override suspend fun answerHowTo(
        question: String,
        excerpts: List<AiHowToExcerpt>,
    ): AiWorkspaceHowToAnswer {
        val safeSources = excerpts.map { HowToPromptSource(it.source.title, it.source.section, it.text, it.steps) }
        val prompt = """
            你是产品使用助手。只能依据给出的帮助资料回答，不得发明按钮、页面或流程。
            返回 JSON：{"answer":"...","steps":["..."]}。没有依据时明确说资料中未覆盖。
            问题：$question
            资料：${json.encodeToString(safeSources)}
        """.trimIndent()
        return decode(sendJson(prompt, 700))
    }

    private suspend fun sendJson(prompt: String, maxTokens: Int) = withContext(Dispatchers.IO) {
        val payload = buildJsonObject {
            put("model", model)
            put("temperature", 0.1)
            put("max_tokens", maxTokens)
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
                ?.get("message")?.jsonObject?.get("content")?.jsonPrimitive?.content
        }.getOrNull()?.trim()
        if (content.isNullOrEmpty()) throw AiProviderException("AI_INVALID_RESPONSE", "DeepSeek returned empty content", true)
        runCatching { json.parseToJsonElement(content) }
            .getOrElse { throw AiProviderException("AI_INVALID_RESPONSE", "DeepSeek returned invalid JSON", true) }
    }

    private inline fun <reified T> decode(element: kotlinx.serialization.json.JsonElement): T =
        runCatching { json.decodeFromJsonElement<T>(element) }
            .getOrElse { throw AiProviderException("AI_INVALID_RESPONSE", "DeepSeek response failed validation", true) }

    @Serializable
    private data class HowToPromptSource(
        val title: String,
        val section: String,
        val text: String,
        val steps: List<String>,
    )
}
