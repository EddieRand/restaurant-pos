package com.restaurantpos.server.ai

import com.restaurantpos.server.db.tables.*
import com.restaurantpos.server.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.security.MessageDigest
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

class AiGrowthException(val code: String, override val message: String, val retryable: Boolean = false) : RuntimeException(message)

@Serializable
data class GrowthGeneratedCopy(
    val headline: String,
    val summary: String,
    val suggestions: List<String>,
    val contentDraft: String,
)

fun interface AiGrowthCopyGenerator {
    suspend fun generate(businessDate: String, evidence: List<AiGrowthEvidenceDto>): GrowthGeneratedCopy
}

class AiGrowthService(
    private val generator: AiGrowthCopyGenerator?,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    suspend fun today(): AiGrowthBriefingResponse {
        val snapshot = snapshot()
        val cached = transaction {
            AiGrowthBriefingsTable.selectAll().where {
                (AiGrowthBriefingsTable.businessDate eq snapshot.businessDate) and
                    (AiGrowthBriefingsTable.dataFingerprint eq snapshot.fingerprint)
            }.firstOrNull()?.let { json.decodeFromString<AiGrowthBriefingResponse>(it[AiGrowthBriefingsTable.payloadJson]) }
        }
        if (cached != null) return cached
        val copy = generator?.generate(snapshot.businessDate, snapshot.evidence)
            ?: throw AiNotConfiguredException()
        val generatedAt = now()
        val response = AiGrowthBriefingResponse(
            briefingId = UUID.randomUUID().toString(),
            businessDate = snapshot.businessDate,
            generatedAt = generatedAt,
            dataFingerprint = snapshot.fingerprint,
            headline = copy.headline,
            summary = copy.summary,
            evidence = snapshot.evidence + demoSignals(),
            suggestions = copy.suggestions.take(3),
            contentDraft = copy.contentDraft,
            demoSignalNotice = "演示信号，不代表抖音官方数据",
        )
        transaction {
            AiGrowthBriefingsTable.insertIgnore {
                it[id] = response.briefingId
                it[businessDate] = response.businessDate
                it[dataFingerprint] = response.dataFingerprint
                it[payloadJson] = json.encodeToString(response)
                it[AiGrowthBriefingsTable.generatedAt] = generatedAt
            }
        }
        // insertIgnore protects the unique business-date/fingerprint key. Re-read
        // the winner so concurrent first visits always receive the same briefing ID.
        return transaction {
            AiGrowthBriefingsTable.selectAll().where {
                (AiGrowthBriefingsTable.businessDate eq snapshot.businessDate) and
                    (AiGrowthBriefingsTable.dataFingerprint eq snapshot.fingerprint)
            }.first().let { json.decodeFromString(it[AiGrowthBriefingsTable.payloadJson]) }
        }
    }

    fun createProposal(actorId: String, request: CreateAiGrowthProposalRequest): AiGrowthProposalResponse {
        validateParams(request.fixedAmountMinorUnit, request.validDays, request.targetSegment)
        val snapshot = snapshot()
        return insertProposal(
            actorId = actorId,
            evidence = snapshot.evidence,
            fingerprint = snapshot.fingerprint,
            params = AiGrowthEditableParamsDto(request.fixedAmountMinorUnit, request.validDays, request.targetSegment),
            version = 1,
            parentId = null,
        )
    }

    fun revise(actorId: String, proposalId: String, request: ReviseAiGrowthProposalRequest): AiGrowthProposalResponse = transaction {
        val row = proposalRow(actorId, proposalId)
        ensureExecutable(row)
        val old = json.decodeFromString<AiGrowthEditableParamsDto>(row[AiGrowthProposalsTable.editableParamsJson])
        val params = AiGrowthEditableParamsDto(
            request.fixedAmountMinorUnit ?: old.fixedAmountMinorUnit,
            request.validDays ?: old.validDays,
            request.targetSegment ?: old.targetSegment,
        )
        validateParams(params.fixedAmountMinorUnit, params.validDays, params.targetSegment)
        AiGrowthProposalsTable.update({ AiGrowthProposalsTable.id eq proposalId }) { it[status] = "SUPERSEDED" }
        insertProposal(
            actorId,
            json.decodeFromString(row[AiGrowthProposalsTable.evidenceJson]),
            row[AiGrowthProposalsTable.dataFingerprint],
            params,
            row[AiGrowthProposalsTable.version] + 1,
            proposalId,
        )
    }

    fun execute(actorId: String, proposalId: String, request: ExecuteAiGrowthProposalRequest): ExecuteAiGrowthProposalResponse = transaction {
        if (!request.confirmed || request.idempotencyKey.isBlank()) {
            throw AiGrowthException(AiGrowthErrorCodes.INVALID_PARAMS, "必须明确确认并提供幂等键")
        }
        AiActionAuditsTable.selectAll().where { AiActionAuditsTable.idempotencyKey eq request.idempotencyKey }.firstOrNull()?.let {
            if (it[AiActionAuditsTable.proposalId] != proposalId) {
                throw AiGrowthException(AiGrowthErrorCodes.IDEMPOTENCY_CONFLICT, "该幂等键已用于其他方案")
            }
            return@transaction it.toExecution(true)
        }
        AiActionAuditsTable.selectAll().where { AiActionAuditsTable.proposalId eq proposalId }.firstOrNull()?.let {
            throw AiGrowthException(AiGrowthErrorCodes.ALREADY_EXECUTED, "方案已执行")
        }
        val row = proposalRow(actorId, proposalId)
        ensureExecutable(row)
        val params = json.decodeFromString<AiGrowthEditableParamsDto>(row[AiGrowthProposalsTable.editableParamsJson])
        validateParams(params.fixedAmountMinorUnit, params.validDays, params.targetSegment)
        val timestamp = now()
        val couponId = "ai-coupon-${UUID.randomUUID()}"
        val campaignId = "ai-campaign-${UUID.randomUUID()}"
        val auditId = "ai-audit-${UUID.randomUUID()}"
        CouponsTable.insert {
            it[id] = couponId
            it[code] = "AI-${UUID.randomUUID().toString().replace("-", "").take(10).uppercase()}"
            it[type] = "FIXED"
            it[value] = params.fixedAmountMinorUnit
            it[expiresAt] = timestamp + params.validDays * 86_400_000L
            it[isActive] = true
        }
        CampaignsTable.insert {
            it[id] = campaignId
            it[name] = "AI 增长优惠券活动"
            it[type] = "COUPON_PUSH"
            it[targetSegment] = params.targetSegment
            it[targetTierId] = null
            it[CampaignsTable.couponId] = couponId
            it[message] = "到店专享优惠，活动详情以门店说明为准。"
            it[scheduledAt] = null
            it[status] = "DRAFT"
            it[sentCount] = 0
            it[createdAt] = timestamp
        }
        AiActionAuditsTable.insert {
            it[id] = auditId
            it[actionType] = "CRM_COUPON_CAMPAIGN_CREATE"
            it[AiActionAuditsTable.proposalId] = proposalId
            it[AiActionAuditsTable.actorId] = actorId
            it[executedParamsJson] = json.encodeToString(params)
            it[AiActionAuditsTable.couponId] = couponId
            it[AiActionAuditsTable.campaignId] = campaignId
            it[idempotencyKey] = request.idempotencyKey
            it[createdAt] = timestamp
        }
        AiGrowthProposalsTable.update({ AiGrowthProposalsTable.id eq proposalId }) { it[status] = "EXECUTED" }
        ExecuteAiGrowthProposalResponse(proposalId, auditId, couponId, campaignId, false, timestamp)
    }

    private fun insertProposal(
        actorId: String,
        evidence: List<AiGrowthEvidenceDto>,
        fingerprint: String,
        params: AiGrowthEditableParamsDto,
        version: Int,
        parentId: String?,
    ): AiGrowthProposalResponse {
        val id = UUID.randomUUID().toString()
        val expiresAt = now() + 15 * 60_000L
        val impact = AiGrowthExpectedImpactDto(
            "召回未到店客群",
            "预期影响为 AI 推测，实际效果取决于活动触达与顾客响应。",
            AiGrowthDataMode.AI_GENERATED,
        )
        transaction {
            AiGrowthProposalsTable.insert {
                it[AiGrowthProposalsTable.id] = id
                it[AiGrowthProposalsTable.actorId] = actorId
                it[type] = AiGrowthProposalType.COUPON_CAMPAIGN.name
                it[dataMode] = AiGrowthDataMode.AI_GENERATED.name
                it[evidenceJson] = json.encodeToString(evidence)
                it[expectedImpactJson] = json.encodeToString(impact)
                it[editableParamsJson] = json.encodeToString(params)
                it[dataFingerprint] = fingerprint
                it[status] = "ACTIVE"
                it[AiGrowthProposalsTable.version] = version
                it[parentProposalId] = parentId
                it[AiGrowthProposalsTable.expiresAt] = expiresAt
                it[createdAt] = now()
            }
            AiGrowthProposalVersionsTable.insert {
                it[proposalId] = id
                it[AiGrowthProposalVersionsTable.version] = version
                it[editableParamsJson] = json.encodeToString(params)
                it[createdAt] = now()
            }
        }
        return AiGrowthProposalResponse(id, AiGrowthProposalType.COUPON_CAMPAIGN, AiGrowthDataMode.AI_GENERATED, evidence, impact, params, expiresAt, true, version)
    }

    private fun proposalRow(actorId: String, proposalId: String): ResultRow =
        AiGrowthProposalsTable.selectAll().where {
            (AiGrowthProposalsTable.id eq proposalId) and (AiGrowthProposalsTable.actorId eq actorId)
        }.firstOrNull() ?: throw AiGrowthException(AiGrowthErrorCodes.NOT_FOUND, "增长方案不存在")

    private fun ensureExecutable(row: ResultRow) {
        if (row[AiGrowthProposalsTable.status] != "ACTIVE") throw AiGrowthException(AiGrowthErrorCodes.STALE, "增长方案已失效")
        if (row[AiGrowthProposalsTable.expiresAt] <= now()) throw AiGrowthException(AiGrowthErrorCodes.EXPIRED, "增长方案已过期")
        if (row[AiGrowthProposalsTable.dataFingerprint] != snapshot().fingerprint) {
            throw AiGrowthException(AiGrowthErrorCodes.STALE, "经营数据已变化，请重新生成方案")
        }
    }

    private data class Snapshot(val businessDate: String, val fingerprint: String, val evidence: List<AiGrowthEvidenceDto>)

    private fun snapshot(): Snapshot = transaction {
        val zone = ZoneId.systemDefault()
        val date = java.time.Instant.ofEpochMilli(now()).atZone(zone).toLocalDate()
        val from = date.atStartOfDay(zone).toInstant().toEpochMilli()
        val to = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val orders = OrdersTable.selectAll().where {
            (OrdersTable.createdAt greaterEq from) and
                (OrdersTable.createdAt less to) and
                (OrdersTable.status eq "CLOSED")
        }.toList()
        val revenue = orders.sumOf { it[OrdersTable.subtotalMinorUnit] + it[OrdersTable.taxTotalMinorUnit] + it[OrdersTable.serviceChargeMinorUnit] - it[OrdersTable.discountMinorUnit] }
        val guests = orders.sumOf { it[OrdersTable.guestCount].toLong() }
        val redemptions = GroupBuyingRedemptionsTable.selectAll().where { (GroupBuyingRedemptionsTable.createdAt greaterEq from) and (GroupBuyingRedemptionsTable.createdAt less to) }.toList()
        val customerCount = CustomersTable.selectAll().count()
        val inactiveCutoff = now() - 30L * 86_400_000L
        val inactiveCount = CustomersTable.selectAll().where { CustomersTable.lastVisitAt less inactiveCutoff }.count()
        val orderIds = orders.map { it[OrdersTable.id] }
        val topItemRows = if (orderIds.isEmpty()) emptyList() else OrderItemsTable.selectAll()
            .where { OrderItemsTable.orderId inList orderIds }.toList()
        val topItem = topItemRows.groupBy { it[OrderItemsTable.menuItemNameSnapshot] }
            .maxByOrNull { (_, rows) -> rows.sumOf { it[OrderItemsTable.quantity] } }?.key
            ?.let(::localizedName) ?: "暂无"
        val evidence = listOf(
            evidence("orderCount", "订单量", orders.size.toLong(), "COUNT", "POS_REPORT"),
            evidence("netRevenue", "营业额", revenue, "MINOR_UNIT", "POS_REPORT"),
            evidence("guestCount", "客流", guests, "COUNT", "POS_REPORT"),
            AiGrowthEvidenceDto("topItem", "热销菜品", textValue = topItem, unit = "TEXT", dataMode = AiGrowthDataMode.REAL, source = "POS_REPORT"),
            evidence("customerCount", "CRM 顾客数", customerCount, "COUNT", "CRM"),
            evidence("inactiveCustomerCount", "30 天未到店顾客", inactiveCount, "COUNT", "CRM"),
            evidence("groupBuyingRedemptionCount", "团购核销笔数", redemptions.size.toLong(), "COUNT", "GROUP_BUYING_LEDGER"),
            evidence("groupBuyingRedemptionAmount", "团购核销金额", redemptions.sumOf { it[GroupBuyingRedemptionsTable.redeemedAmountMinorUnit] }, "MINOR_UNIT", "GROUP_BUYING_LEDGER"),
        )
        val raw = evidence.joinToString("|") { "${it.key}:${it.numericValue}:${it.textValue}" }
        Snapshot(date.toString(), sha256(raw), evidence)
    }

    private fun evidence(key: String, label: String, value: Long, unit: String, source: String) =
        AiGrowthEvidenceDto(key, label, numericValue = value, unit = unit, dataMode = AiGrowthDataMode.REAL, source = source)

    private fun demoSignals() = listOf(
        AiGrowthEvidenceDto("douyinTrendRank", "抖音热点排名", numericValue = 3, unit = "RANK", dataMode = AiGrowthDataMode.DEMO_SIGNAL, source = "DEMO_ADAPTER"),
        AiGrowthEvidenceDto("adRoiBasisPoints", "广告归因 ROI", numericValue = 18500, unit = "BASIS_POINTS", dataMode = AiGrowthDataMode.DEMO_SIGNAL, source = "DEMO_ADAPTER"),
    )

    private fun localizedName(raw: String): String = runCatching {
        val obj = json.parseToJsonElement(raw).jsonObject
        obj["zh-CN"]?.jsonPrimitive?.content ?: obj["zh"]?.jsonPrimitive?.content ?: obj.values.first().jsonPrimitive.content
    }.getOrElse { raw.substringBefore('|').substringAfter('=') }

    private fun validateParams(amount: Long, days: Int, segment: String) {
        if (amount !in 100..100_000 || days !in 1..90 || segment !in setOf("ALL", "INACTIVE_30_DAYS", "HIGH_VALUE")) {
            throw AiGrowthException(AiGrowthErrorCodes.INVALID_PARAMS, "优惠金额、有效天数或目标客群无效")
        }
    }

    private fun ResultRow.toExecution(replay: Boolean) = ExecuteAiGrowthProposalResponse(
        this[AiActionAuditsTable.proposalId], this[AiActionAuditsTable.id],
        this[AiActionAuditsTable.couponId]!!, this[AiActionAuditsTable.campaignId]!!,
        replay, this[AiActionAuditsTable.createdAt],
    )

    private fun sha256(value: String) = MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString("") { "%02x".format(it) }

    companion object {
        fun fromEnvironment(): AiGrowthService {
            val key = System.getenv("DEEPSEEK_API_KEY")?.takeIf(String::isNotBlank)
            return AiGrowthService(key?.let {
                DeepSeekAiGrowthClient(it, System.getenv("DEEPSEEK_BASE_URL") ?: "https://api.deepseek.com", System.getenv("DEEPSEEK_MODEL") ?: "deepseek-v4-flash")
            })
        }
    }
}

private class DeepSeekAiGrowthClient(
    private val apiKey: String,
    private val baseUrl: String,
    private val model: String,
) : AiGrowthCopyGenerator {
    private val json = Json { ignoreUnknownKeys = true }
    private val http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()

    override suspend fun generate(businessDate: String, evidence: List<AiGrowthEvidenceDto>): GrowthGeneratedCopy = withContext(Dispatchers.IO) {
        val prompt = """
            你是餐饮增长参谋。只根据 REAL 聚合证据生成中文简报和短视频草稿，不得计算或虚构金额、人数、菜品和平台数据。
            返回 JSON：{"headline":"","summary":"","suggestions":["","",""],"contentDraft":""}
            日期：$businessDate
            证据：${json.encodeToString(evidence)}
        """.trimIndent()
        val payload = buildJsonObject {
            put("model", model); put("temperature", 0.2); put("max_tokens", 900); put("stream", false)
            put("thinking", buildJsonObject { put("type", "disabled") })
            put("response_format", buildJsonObject { put("type", "json_object") })
            putJsonArray("messages") {
                add(buildJsonObject { put("role", "system"); put("content", "Return valid JSON only.") })
                add(buildJsonObject { put("role", "user"); put("content", prompt) })
            }
        }.toString()
        val request = HttpRequest.newBuilder().uri(URI.create("${baseUrl.trimEnd('/')}/chat/completions"))
            .timeout(Duration.ofSeconds(17)).header("Authorization", "Bearer $apiKey").header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(payload)).build()
        val response = runCatching { http.send(request, HttpResponse.BodyHandlers.ofString()) }
            .getOrElse { throw AiProviderException("AI_PROVIDER_UNAVAILABLE", "DeepSeek request failed", true) }
        when (response.statusCode()) {
            401, 403 -> throw AiProviderException("AI_AUTH_FAILED", "DeepSeek credentials are invalid", false)
            402 -> throw AiProviderException("AI_QUOTA_EXCEEDED", "DeepSeek quota is insufficient", false)
            429 -> throw AiProviderException("AI_RATE_LIMITED", "DeepSeek rate limit reached", true)
            in 500..599 -> throw AiProviderException("AI_PROVIDER_UNAVAILABLE", "DeepSeek is temporarily unavailable", true)
        }
        if (response.statusCode() !in 200..299) throw AiProviderException("AI_PROVIDER_ERROR", "DeepSeek rejected the request", false)
        val content = runCatching { json.parseToJsonElement(response.body()).jsonObject["choices"]!!.jsonArray.first().jsonObject["message"]!!.jsonObject["content"]!!.jsonPrimitive.content }.getOrNull()
            ?: throw AiProviderException("AI_INVALID_RESPONSE", "DeepSeek returned empty content", true)
        runCatching { json.decodeFromString<GrowthGeneratedCopy>(content) }
            .getOrElse { throw AiProviderException("AI_INVALID_RESPONSE", "DeepSeek returned invalid growth JSON", true) }
    }
}
