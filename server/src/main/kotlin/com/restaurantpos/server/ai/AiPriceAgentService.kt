package com.restaurantpos.server.ai

import com.restaurantpos.server.db.tables.AiMutationAuditsTable
import com.restaurantpos.server.db.tables.AiPriceProposalChangesTable
import com.restaurantpos.server.db.tables.AiPriceProposalsTable
import com.restaurantpos.server.db.tables.MenuItemsTable
import com.restaurantpos.server.db.tables.SettingsTable
import com.restaurantpos.server.menu.MenuCommandService
import com.restaurantpos.server.model.AiPriceChangeDto
import com.restaurantpos.server.model.AiPriceProposalRequest
import com.restaurantpos.server.model.AiPriceProposalResponse
import com.restaurantpos.server.model.ExecuteAiPriceProposalRequest
import com.restaurantpos.server.model.ExecuteAiPriceProposalResponse
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.or
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.UUID

class AiPriceAgentService(
    private val intentClient: AiPriceIntentClient?,
    private val enabled: Boolean,
    private val priceUpdateEnabled: Boolean,
    private val menuCommandService: MenuCommandService = MenuCommandService(),
    private val now: () -> Long = System::currentTimeMillis,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun createProposal(actorId: String, request: AiPriceProposalRequest): AiPriceProposalResponse {
        ensureEnabled()
        val instruction = request.instruction.trim()
        requireAgent(instruction.isNotEmpty() && instruction.length <= 500, "Instruction must contain 1 to 500 characters")
        requireAgent(request.locale.matches(LOCALE_PATTERN), "locale is invalid")
        val client = intentClient ?: throw AiNotConfiguredException()
        val currency = loadCurrencyConfig()
        val intent = withTimeout(TOTAL_TIMEOUT_MS) {
            var last: AiProviderException? = null
            repeat(2) { attempt ->
                try {
                    return@withTimeout client.parse(instruction, request.locale, currency.code, currency.minorDigits)
                } catch (e: AiProviderException) {
                    last = e
                    if (!e.retryable || attempt == 1) throw e
                    delay(150)
                }
            }
            throw last ?: AiProviderException("AI_PROVIDER_ERROR", "AI intent parsing failed", false)
        }
        val target = resolveTarget(intent.targetName, request.locale)
        val newPrice = calculatePrice(target.priceMinorUnit, intent, currency.minorDigits)
        requireAgent(newPrice != target.priceMinorUnit, "Requested price is unchanged")
        requireAgent(newPrice in 0..MAX_PRICE_MINOR_UNIT, "Requested price is outside the supported range")
        val createdAt = now()
        val proposalId = newId()
        val delta = newPrice - target.priceMinorUnit
        val deltaBasisPoints = target.priceMinorUnit.takeIf { it != 0L }?.let {
            BigDecimal.valueOf(delta).multiply(BigDecimal.valueOf(10_000))
                .divide(BigDecimal.valueOf(it), 0, RoundingMode.HALF_UP).longValueExact()
        }
        transaction {
            AiPriceProposalsTable.insert {
                it[id] = proposalId
                it[AiPriceProposalsTable.actorId] = actorId
                it[AiPriceProposalsTable.instruction] = instruction
                it[locale] = request.locale
                it[status] = STATUS_PROPOSED
                it[tool] = TOOL
                it[currencyCode] = currency.code
                it[minorUnitDigits] = currency.minorDigits
                it[AiPriceProposalsTable.createdAt] = createdAt
                it[expiresAt] = createdAt + PROPOSAL_TTL_MS
                it[executedAt] = null
            }
            AiPriceProposalChangesTable.insert {
                it[AiPriceProposalChangesTable.proposalId] = proposalId
                it[itemId] = target.id
                it[itemName] = target.displayName
                it[oldPriceMinorUnit] = target.priceMinorUnit
                it[newPriceMinorUnit] = newPrice
                it[deltaMinorUnit] = delta
                it[deltaPercentBasisPoints] = deltaBasisPoints
                it[expectedUpdatedAt] = target.updatedAt
            }
        }
        return AiPriceProposalResponse(
            proposalId = proposalId,
            status = STATUS_PROPOSED,
            tool = TOOL,
            createdAt = createdAt,
            expiresAt = createdAt + PROPOSAL_TTL_MS,
            requiresConfirmation = true,
            currencyCode = currency.code,
            minorUnitDigits = currency.minorDigits,
            changes = listOf(target.toChange(newPrice, delta, deltaBasisPoints)),
        )
    }

    fun execute(
        actorId: String,
        proposalId: String,
        request: ExecuteAiPriceProposalRequest,
    ): ExecuteAiPriceProposalResponse {
        ensureEnabled()
        requireAgent(proposalId.isNotBlank(), "proposalId is required")
        requireAgent(request.confirmed, "confirmed must be true")
        val idempotencyKey = request.idempotencyKey.trim()
        requireAgent(idempotencyKey.length in 8..128, "idempotencyKey must contain 8 to 128 characters")
        val executionTime = now()
        return transaction {
            AiMutationAuditsTable.selectAll()
                .where { AiMutationAuditsTable.idempotencyKey eq idempotencyKey }
                .firstOrNull()
                ?.let { audit ->
                    if (audit[AiMutationAuditsTable.proposalId] != proposalId) {
                        throw AiAgentException("AI_IDEMPOTENCY_CONFLICT", "Idempotency key belongs to another proposal")
                    }
                    return@transaction audit.toExecuteResponse(idempotentReplay = true)
                }

            val proposal = AiPriceProposalsTable.selectAll()
                .where { AiPriceProposalsTable.id eq proposalId }
                .firstOrNull()
                ?: throw AiAgentException("AI_PROPOSAL_NOT_FOUND", "Price proposal was not found")
            if (proposal[AiPriceProposalsTable.status] == STATUS_EXECUTED) {
                throw AiAgentException("AI_PROPOSAL_ALREADY_EXECUTED", "Price proposal was already executed")
            }
            if (executionTime >= proposal[AiPriceProposalsTable.expiresAt]) {
                throw AiAgentException("AI_PROPOSAL_EXPIRED", "Price proposal has expired")
            }
            val change = AiPriceProposalChangesTable.selectAll()
                .where { AiPriceProposalChangesTable.proposalId eq proposalId }
                .single()
            val updated = menuCommandService.updatePriceIfVersionInTransaction(
                itemId = change[AiPriceProposalChangesTable.itemId],
                expectedUpdatedAt = change[AiPriceProposalChangesTable.expectedUpdatedAt],
                newPriceMinorUnit = change[AiPriceProposalChangesTable.newPriceMinorUnit],
                mutationTimestamp = executionTime,
            )
            if (!updated) {
                throw AiAgentException("AI_PROPOSAL_STALE", "Menu item changed after this proposal was created")
            }
            val auditId = newId()
            AiMutationAuditsTable.insert {
                it[id] = auditId
                it[AiMutationAuditsTable.actorId] = actorId
                it[AiMutationAuditsTable.proposalId] = proposalId
                it[tool] = TOOL
                it[entityType] = "menu_item"
                it[entityId] = change[AiPriceProposalChangesTable.itemId]
                it[beforeMinorUnit] = change[AiPriceProposalChangesTable.oldPriceMinorUnit]
                it[afterMinorUnit] = change[AiPriceProposalChangesTable.newPriceMinorUnit]
                it[AiMutationAuditsTable.idempotencyKey] = idempotencyKey
                it[createdAt] = executionTime
            }
            AiPriceProposalsTable.update({ AiPriceProposalsTable.id eq proposalId }) {
                it[status] = STATUS_EXECUTED
                it[executedAt] = executionTime
            }
            ExecuteAiPriceProposalResponse(proposalId, STATUS_EXECUTED, executionTime, auditId, false)
        }
    }

    private fun ensureEnabled() {
        if (!enabled || !priceUpdateEnabled) {
            throw AiAgentException("AI_AGENT_DISABLED", "AI price update capability is disabled")
        }
    }

    private fun calculatePrice(oldPrice: Long, intent: AiPriceIntent, minorDigits: Int): Long {
        val operation = intent.operation.trim().uppercase()
        val amount = intent.amountMajorUnit?.trim()?.takeIf(String::isNotEmpty)?.let {
            runCatching { BigDecimal(it).movePointRight(minorDigits).setScale(0, RoundingMode.UNNECESSARY).longValueExact() }
                .getOrElse { throw AiAgentException("AI_INVALID_REQUEST", "Amount has unsupported precision") }
        }
        val percentage = intent.percentage?.trim()?.takeIf(String::isNotEmpty)?.let {
            runCatching { BigDecimal(it) }
                .getOrElse { throw AiAgentException("AI_INVALID_REQUEST", "Percentage is invalid") }
        }
        requireAgent((amount == null) xor (percentage == null), "Specify exactly one amount or percentage")
        val change = amount ?: BigDecimal.valueOf(oldPrice).multiply(percentage)
            .divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP).longValueExact()
        requireAgent(change >= 0, "Price change cannot be negative")
        return try {
            when (operation) {
                "SET" -> {
                    requireAgent(amount != null, "SET requires an amount")
                    amount ?: throw AiAgentException("AI_INVALID_REQUEST", "SET requires an amount")
                }
                "INCREASE" -> Math.addExact(oldPrice, change)
                "DECREASE" -> Math.subtractExact(oldPrice, change)
                else -> throw AiAgentException("AI_INVALID_REQUEST", "Unsupported price operation")
            }
        } catch (_: ArithmeticException) {
            throw AiAgentException("AI_INVALID_REQUEST", "Requested price is outside the supported range")
        }
    }

    private fun resolveTarget(targetName: String, locale: String): MenuTarget {
        val target = normalize(targetName)
        requireAgent(target.isNotEmpty(), "Menu target is missing")
        val items = transaction {
            MenuItemsTable.selectAll().map { row ->
                val names = parseNames(row[MenuItemsTable.names])
                MenuTarget(
                    id = row[MenuItemsTable.id],
                    names = names,
                    displayName = localizedName(names, locale, row[MenuItemsTable.id]),
                    priceMinorUnit = row[MenuItemsTable.priceMinorUnit],
                    updatedAt = row[MenuItemsTable.updatedAt],
                )
            }
        }
        val exact = items.filter { item -> item.id.equals(targetName.trim(), true) || item.names.values.any { normalize(it) == target } }
        val matches = if (exact.isNotEmpty()) exact else items.filter { item ->
            item.names.values.any { name -> normalize(name).let { it.contains(target) || target.contains(it) } }
        }
        if (matches.size != 1) {
            throw AiAgentException("AI_TARGET_AMBIGUOUS", "Menu target must match exactly one item")
        }
        return matches.single()
    }

    private fun parseNames(raw: String): Map<String, String> = runCatching {
        json.parseToJsonElement(raw).jsonObject.mapValues { it.value.jsonPrimitive.content }
    }.getOrElse { mapOf("default" to raw) }

    private fun localizedName(names: Map<String, String>, locale: String, fallback: String): String {
        val language = locale.substringBefore('-').lowercase()
        return names[locale] ?: names[language] ?: names["zh"] ?: names["en"] ?: names.values.firstOrNull() ?: fallback
    }

    private fun loadCurrencyConfig(): CurrencyConfig = transaction {
        val raw = SettingsTable.selectAll()
            .where { (SettingsTable.key eq "regionConfig") or (SettingsTable.key eq "region-config") }
            .firstOrNull()?.get(SettingsTable.value)
        if (raw == null) return@transaction CurrencyConfig("CNY", 2)
        runCatching {
            val objectValue = json.parseToJsonElement(raw).jsonObject
            val code = objectValue["currencyCode"]?.jsonPrimitive?.contentOrNull?.uppercase() ?: "CNY"
            val digits = objectValue["currencyMinorDigits"]?.jsonPrimitive?.intOrNull
                ?: objectValue["minorDigits"]?.jsonPrimitive?.intOrNull
                ?: 2
            CurrencyConfig(code.takeIf { it.matches(Regex("^[A-Z]{3}$")) } ?: "CNY", digits.coerceIn(0, 4))
        }.getOrDefault(CurrencyConfig("CNY", 2))
    }

    private fun ResultRow.toExecuteResponse(idempotentReplay: Boolean) = ExecuteAiPriceProposalResponse(
        proposalId = this[AiMutationAuditsTable.proposalId],
        status = STATUS_EXECUTED,
        executedAt = this[AiMutationAuditsTable.createdAt],
        auditId = this[AiMutationAuditsTable.id],
        idempotentReplay = idempotentReplay,
    )

    private data class CurrencyConfig(val code: String, val minorDigits: Int)
    private data class MenuTarget(
        val id: String,
        val names: Map<String, String>,
        val displayName: String,
        val priceMinorUnit: Long,
        val updatedAt: Long,
    ) {
        fun toChange(newPrice: Long, delta: Long, basisPoints: Long?) = AiPriceChangeDto(
            id, displayName, priceMinorUnit, newPrice, delta, basisPoints,
        )
    }

    private fun normalize(value: String): String = value.trim().lowercase().replace(WHITESPACE, "")

    private fun requireAgent(condition: Boolean, message: String) {
        if (!condition) throw AiAgentException("AI_INVALID_REQUEST", message)
    }

    companion object {
        private const val TOOL = "menu.update_price"
        private const val STATUS_PROPOSED = "PROPOSED"
        private const val STATUS_EXECUTED = "EXECUTED"
        private const val TOTAL_TIMEOUT_MS = 20_000L
        private const val PROPOSAL_TTL_MS = 5 * 60_000L
        private const val MAX_PRICE_MINOR_UNIT = 999_999_999_999L
        private val LOCALE_PATTERN = Regex("^[A-Za-z]{2,3}(-[A-Za-z0-9]{2,8})?$")
        private val WHITESPACE = Regex("\\s+")

        fun fromEnvironment(): AiPriceAgentService {
            val apiKey = System.getenv("DEEPSEEK_API_KEY")?.trim().orEmpty()
            val baseUrl = System.getenv("DEEPSEEK_BASE_URL")?.trim().takeUnless { it.isNullOrEmpty() }
                ?: "https://api.deepseek.com"
            val model = System.getenv("DEEPSEEK_MODEL")?.trim().takeUnless { it.isNullOrEmpty() }
                ?: "deepseek-v4-flash"
            val client = apiKey.takeIf(String::isNotEmpty)?.let { DeepSeekAiPriceIntentClient(it, baseUrl, model) }
            return AiPriceAgentService(
                intentClient = client,
                enabled = System.getenv("AI_AGENT_ENABLED").toBoolean(),
                priceUpdateEnabled = System.getenv("AI_AGENT_PRICE_UPDATE_ENABLED").toBoolean(),
            )
        }
    }
}
