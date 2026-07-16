package com.restaurantpos.server.ai

import com.restaurantpos.server.db.tables.AiWorkspaceEventsTable
import com.restaurantpos.server.db.tables.AiWorkspaceMessagesTable
import com.restaurantpos.server.db.tables.AiWorkspaceRunsTable
import com.restaurantpos.server.db.tables.AiWorkspaceRunStepsTable
import com.restaurantpos.server.db.tables.AiWorkspaceSessionsTable
import com.restaurantpos.server.db.tables.AdminUsersTable
import com.restaurantpos.server.db.tables.RolePermissionsTable
import com.restaurantpos.server.model.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import java.util.UUID

class AiWorkspaceService(
    private val modelClient: AiWorkspaceModelClient?,
    private val insightService: AiInsightService,
    private val priceService: AiPriceAgentService,
    private val enabled: Boolean,
    private val growthService: AiGrowthService? = null,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    private val permissionChecker: (String, String) -> Boolean = ::workspaceActorHasPermission,
    private val now: () -> Long = System::currentTimeMillis,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }
    private val reportService by lazy { AiWorkspaceReportQueryService(requireModel()) }
    private val howToService by lazy { AiWorkspaceHowToService(requireModel()) }

    fun createSession(actorId: String, request: CreateAiWorkspaceSessionRequest): AiWorkspaceSessionDto {
        ensureEnabled()
        requireLocale(request.locale)
        val timestamp = now()
        val id = newId()
        transaction {
            AiWorkspaceSessionsTable.insert {
                it[AiWorkspaceSessionsTable.id] = id
                it[AiWorkspaceSessionsTable.actorId] = actorId
                it[title] = "新会话"
                it[expert] = request.expert.name
                it[locale] = request.locale
                it[createdAt] = timestamp
                it[updatedAt] = timestamp
            }
        }
        return getSession(actorId, id)
    }

    fun listSessions(actorId: String): AiWorkspaceSessionListResponse {
        ensureEnabled()
        return transaction {
            AiWorkspaceSessionListResponse(
                AiWorkspaceSessionsTable.selectAll()
                    .where { AiWorkspaceSessionsTable.actorId eq actorId }
                    .orderBy(AiWorkspaceSessionsTable.updatedAt to SortOrder.DESC)
                    .map { it.toSessionSummary() },
            )
        }
    }

    fun getSession(actorId: String, sessionId: String): AiWorkspaceSessionDto {
        ensureEnabled()
        return transaction {
            val session = ownedSession(actorId, sessionId)
            val messages = AiWorkspaceMessagesTable.selectAll()
                .where { AiWorkspaceMessagesTable.sessionId eq sessionId }
                .orderBy(AiWorkspaceMessagesTable.createdAt to SortOrder.ASC)
                .map { it.toMessage() }
            val runs = AiWorkspaceRunsTable.selectAll()
                .where { AiWorkspaceRunsTable.sessionId eq sessionId }
                .orderBy(AiWorkspaceRunsTable.createdAt to SortOrder.ASC)
                .map { run ->
                    val runId = run[AiWorkspaceRunsTable.id]
                    AiWorkspaceRunDto(
                        runId = runId,
                        messageId = run[AiWorkspaceRunsTable.messageId],
                        status = run[AiWorkspaceRunsTable.status],
                        createdAt = run[AiWorkspaceRunsTable.createdAt],
                        completedAt = run[AiWorkspaceRunsTable.completedAt],
                        steps = loadSteps(runId),
                        error = run.runError(),
                        clarification = run.runClarification(),
                    )
                }
            AiWorkspaceSessionDto(
                sessionId = session[AiWorkspaceSessionsTable.id],
                title = session[AiWorkspaceSessionsTable.title],
                expert = AiWorkspaceExpert.valueOf(session[AiWorkspaceSessionsTable.expert]),
                locale = session[AiWorkspaceSessionsTable.locale],
                createdAt = session[AiWorkspaceSessionsTable.createdAt],
                updatedAt = session[AiWorkspaceSessionsTable.updatedAt],
                messages = messages,
                runs = runs,
            )
        }
    }

    fun acceptMessage(
        actorId: String,
        pathSessionId: String,
        request: AiWorkspaceMessageRequest,
        allowedTools: Set<String>,
    ): AiWorkspaceMessageAcceptedResponse {
        ensureEnabled()
        require(pathSessionId == request.sessionId) { "Path and body session IDs must match" }
        require(request.message.trim().length in 1..2_000) { "message must contain 1 to 2000 characters" }
        requireLocale(request.locale)
        val (fromMs, toMs) = resolvePeriod(request.message, request.context)
        val expectedTools = toolsForExpert(request.expert).intersect(allowedTools)
        val timestamp = now()
        val messageId = newId()
        val runId = newId()
        transaction {
            val session = ownedSession(actorId, pathSessionId)
            val active = AiWorkspaceRunsTable.selectAll().where {
                (AiWorkspaceRunsTable.sessionId eq pathSessionId) and
                    (AiWorkspaceRunsTable.status inList listOf("QUEUED", "RUNNING"))
            }.count() > 0
            if (active) throw AiWorkspaceException("AI_RUN_IN_PROGRESS", "该会话已有任务正在运行")
            AiWorkspaceMessagesTable.insert {
                it[id] = messageId
                it[sessionId] = pathSessionId
                it[role] = "USER"
                it[content] = request.message.trim()
                it[createdAt] = timestamp
            }
            AiWorkspaceRunsTable.insert {
                it[id] = runId
                it[sessionId] = pathSessionId
                it[AiWorkspaceRunsTable.messageId] = messageId
                it[status] = "QUEUED"
                it[createdAt] = timestamp
                it[completedAt] = null
                it[errorCode] = null
                it[errorMessage] = null
                it[errorRetryable] = false
            }
            AiWorkspaceSessionsTable.update({ AiWorkspaceSessionsTable.id eq pathSessionId }) {
                it[expert] = request.expert.name
                it[locale] = request.locale
                it[updatedAt] = timestamp
                if (session[AiWorkspaceSessionsTable.title] == "新会话") {
                    it[title] = request.message.trim().take(40)
                }
            }
            appendEvent(runId, "message.accepted", runStatus = "QUEUED")
        }
        scope.launch {
            processRun(actorId, runId, request, expectedTools, fromMs, toMs)
        }
        return AiWorkspaceMessageAcceptedResponse(pathSessionId, messageId, runId, "QUEUED")
    }

    fun events(actorId: String, runId: String, afterSequence: Long): List<AiWorkspaceEventDto> {
        require(afterSequence >= 0) { "afterSequence must not be negative" }
        transaction { ownedRun(actorId, runId) }
        return transaction {
            AiWorkspaceEventsTable.selectAll().where {
                (AiWorkspaceEventsTable.runId eq runId) and
                    (AiWorkspaceEventsTable.sequence greater afterSequence)
            }.orderBy(AiWorkspaceEventsTable.sequence to SortOrder.ASC).map { it.toEvent() }
        }
    }

    fun isTerminal(actorId: String, runId: String): Boolean = transaction {
        ownedRun(actorId, runId)[AiWorkspaceRunsTable.status] in TERMINAL_RUN_STATUSES
    }

    fun markProposalExecuted(actorId: String, response: ExecuteAiPriceProposalResponse) {
        transaction {
            val stepRow = AiWorkspaceRunStepsTable.selectAll()
                .where { AiWorkspaceRunStepsTable.proposalId eq response.proposalId }
                .firstOrNull() ?: return@transaction
            val runId = stepRow[AiWorkspaceRunStepsTable.runId]
            ownedRun(actorId, runId)
            val prior = stepRow[AiWorkspaceRunStepsTable.resultJson]
                ?.let { json.decodeFromString<AiWorkspaceStepResultDto>(it) }
                ?: AiWorkspaceStepResultDto()
            val result = prior.copy(execution = response)
            AiWorkspaceRunStepsTable.update({ AiWorkspaceRunStepsTable.id eq stepRow[AiWorkspaceRunStepsTable.id] }) {
                it[status] = AiWorkspaceStepStatus.EXECUTED.name
                it[resultJson] = json.encodeToString(result)
            }
            appendEvent(runId, "step.executed", loadStep(stepRow[AiWorkspaceRunStepsTable.id]))
        }
    }

    fun markGrowthProposalExecuted(actorId: String, response: ExecuteAiGrowthProposalResponse) {
        transaction {
            val stepRow = AiWorkspaceRunStepsTable.selectAll()
                .where { AiWorkspaceRunStepsTable.proposalId eq response.proposalId }
                .firstOrNull() ?: return@transaction
            val runId = stepRow[AiWorkspaceRunStepsTable.runId]
            ownedRun(actorId, runId)
            val prior = stepRow[AiWorkspaceRunStepsTable.resultJson]
                ?.let { json.decodeFromString<AiWorkspaceStepResultDto>(it) }
                ?: AiWorkspaceStepResultDto()
            val result = prior.copy(growthExecution = response)
            AiWorkspaceRunStepsTable.update({ AiWorkspaceRunStepsTable.id eq stepRow[AiWorkspaceRunStepsTable.id] }) {
                it[status] = AiWorkspaceStepStatus.EXECUTED.name
                it[resultJson] = json.encodeToString(result)
            }
            appendEvent(runId, "step.executed", loadStep(stepRow[AiWorkspaceRunStepsTable.id]))
        }
    }

    private suspend fun processRun(
        actorId: String,
        runId: String,
        request: AiWorkspaceMessageRequest,
        allowedTools: Set<String>,
        fromMs: Long,
        toMs: Long,
    ) {
        try {
            updateRun(runId, "RUNNING")
            val plan = withTimeout(20_000) {
                requireModel().plan(request.message.trim(), request.expert, request.context, allowedTools)
            }
            val clarification = validatePlan(plan, allowedTools, request)
            if (clarification != null) {
                awaitClarification(runId, clarification.toDto())
                return
            }
            val stepIds = plan.steps.map { newId() }
            transaction {
                plan.steps.forEachIndexed { index, planned ->
                    AiWorkspaceRunStepsTable.insert {
                        it[id] = stepIds[index]
                        it[AiWorkspaceRunStepsTable.runId] = runId
                        it[position] = index
                        it[tool] = planned.tool
                        it[kind] = kindFor(planned.tool).name
                        it[status] = AiWorkspaceStepStatus.QUEUED.name
                        it[dependsOnJson] = json.encodeToString(planned.dependsOn.map { stepIds[it - 1] })
                        it[displayTitle] = planned.displayTitle.trim().take(256)
                        it[instruction] = planned.instruction.trim()
                        it[queryType] = planned.queryType
                    }
                }
                appendEvent(runId, "plan.created", runStatus = "RUNNING")
            }
            plan.steps.forEachIndexed { index, planned ->
                val stepId = stepIds[index]
                val blocked = planned.dependsOn.any { dependency ->
                    val status = transaction { loadStep(stepIds[dependency - 1]).status }
                    status in setOf(AiWorkspaceStepStatus.FAILED, AiWorkspaceStepStatus.SKIPPED)
                }
                if (blocked) {
                    updateStep(stepId, AiWorkspaceStepStatus.SKIPPED)
                    appendStepEvent(runId, "step.failed", stepId)
                    return@forEachIndexed
                }
                updateStep(stepId, AiWorkspaceStepStatus.RUNNING)
                appendStepEvent(runId, "step.started", stepId)
                try {
                    val result = when (planned.tool) {
                        AiWorkspaceTools.OPERATING_INSIGHT -> AiWorkspaceStepResultDto(
                            insight = requireToolPermission(actorId, "report.daily").let {
                                insightService.generate(AiOperatingInsightRequest(fromMs, toMs, request.locale))
                            },
                        )
                        AiWorkspaceTools.REPORT_QUERY -> AiWorkspaceStepResultDto(
                            query = requireToolPermission(actorId, "report.daily").let {
                                reportService.query(planned.instruction, planned.queryType, fromMs, toMs)
                            },
                        )
                        AiWorkspaceTools.HOW_TO_SEARCH -> AiWorkspaceStepResultDto(
                            howTo = howToService.answer(planned.instruction),
                        )
                        AiWorkspaceTools.MENU_UPDATE_PRICE -> AiWorkspaceStepResultDto(
                            priceProposal = requireToolPermission(actorId, "menu.edit").let {
                                priceService.createProposal(actorId, AiPriceProposalRequest(planned.instruction, request.locale))
                            },
                        )
                        AiWorkspaceTools.GROWTH_DAILY_BRIEFING,
                        AiWorkspaceTools.GROWTH_CONTENT_DRAFT,
                        -> AiWorkspaceStepResultDto(
                            growthBriefing = requireToolPermission(actorId, AiGrowthPermissions.CAMPAIGN_MANAGE).let {
                                requireGrowthService().today()
                            },
                        )
                        AiWorkspaceTools.CRM_COUPON_CAMPAIGN_PROPOSAL -> AiWorkspaceStepResultDto(
                            growthProposal = requireToolPermission(actorId, AiGrowthPermissions.CAMPAIGN_MANAGE).let {
                                requireGrowthService().createProposal(actorId, growthProposalRequest(planned.instruction))
                            },
                        )
                        else -> throw AiWorkspaceException("AI_UNSUPPORTED_INTENT", "工具不受支持")
                    }
                    val proposalId = result.priceProposal?.proposalId ?: result.growthProposal?.proposalId
                    val status = if (proposalId == null) AiWorkspaceStepStatus.SUCCEEDED else AiWorkspaceStepStatus.AWAITING_CONFIRMATION
                    updateStep(stepId, status, result, proposalId = proposalId)
                    appendStepEvent(runId, if (proposalId == null) "step.completed" else "step.awaiting_confirmation", stepId)
                } catch (cause: Throwable) {
                    val error = cause.toWorkspaceError()
                    updateStep(stepId, AiWorkspaceStepStatus.FAILED, error = error)
                    appendStepEvent(runId, "step.failed", stepId)
                }
            }
            val anySuccess = transaction { loadSteps(runId) }.any {
                it.status in setOf(AiWorkspaceStepStatus.SUCCEEDED, AiWorkspaceStepStatus.AWAITING_CONFIRMATION, AiWorkspaceStepStatus.EXECUTED)
            }
            finishRun(runId, if (anySuccess) "COMPLETED" else "FAILED")
        } catch (cause: Throwable) {
            finishRun(runId, "FAILED", cause.toWorkspaceError())
        }
    }

    private fun validatePlan(
        plan: AiWorkspacePlan,
        allowedTools: Set<String>,
        request: AiWorkspaceMessageRequest,
    ): AiWorkspaceClarification? {
        val clarification = plan.clarification
            ?: missingPeriodClarification(plan, request)
            ?: missingGrowthClarification(plan)
        if (clarification != null) {
            require(clarification.question.trim().length in 1..256) { "clarification question is invalid" }
            require(clarification.options.size in 2..4) { "clarification must contain 2 to 4 options" }
            require(clarification.options.map { it.id }.distinct().size == clarification.options.size) { "clarification option IDs must be unique" }
            clarification.options.forEach {
                require(it.id.matches(Regex("[a-z0-9_-]{1,40}"))) { "clarification option ID is invalid" }
                require(it.label.trim().length in 1..80 && it.value.trim().length in 1..256) { "clarification option is invalid" }
            }
            require(plan.steps.isEmpty() || plan.clarification == null) { "clarification plan must not contain executable steps" }
            return clarification
        }
        if (plan.steps.isEmpty()) throw AiWorkspaceException("AI_UNSUPPORTED_INTENT", "没有可执行的已注册能力")
        if (plan.steps.size > 5) throw AiWorkspaceException("AI_INVALID_RESPONSE", "AI plan exceeds five steps", true)
        plan.steps.forEachIndexed { index, step ->
            if (step.tool !in AiWorkspaceTools.all || step.tool !in allowedTools) {
                throw AiWorkspaceException("AI_PERMISSION_DENIED", "当前专家或账号无权使用 ${step.tool}")
            }
            if (step.displayTitle.isBlank() || step.instruction.isBlank()) {
                throw AiWorkspaceException("AI_INVALID_RESPONSE", "AI plan contains an empty step", true)
            }
            if (step.dependsOn.any { it !in 1..index }) {
                throw AiWorkspaceException("AI_INVALID_RESPONSE", "AI plan dependency is invalid", true)
            }
            if (step.tool == AiWorkspaceTools.REPORT_QUERY && step.queryType !in QUERY_TYPES) {
                throw AiWorkspaceException("AI_INVALID_RESPONSE", "AI report query type is invalid", true)
            }
        }
        return null
    }

    private fun missingPeriodClarification(
        plan: AiWorkspacePlan,
        request: AiWorkspaceMessageRequest,
    ): AiWorkspaceClarification? {
        val usesReports = plan.steps.any { it.tool == AiWorkspaceTools.OPERATING_INSIGHT || it.tool == AiWorkspaceTools.REPORT_QUERY }
        val hasPageRange = request.context.fromMs != null && request.context.toMs != null
        if (!usesReports || hasPageRange || hasRecognizedPeriod(request.message)) return null
        return AiWorkspaceClarification(
            question = "你希望分析哪个时间范围？",
            options = listOf(
                AiWorkspaceClarificationOption("today", "今天", "日期范围：今天"),
                AiWorkspaceClarificationOption("yesterday", "昨天", "日期范围：昨天"),
                AiWorkspaceClarificationOption("last_7_days", "近 7 天", "日期范围：近 7 天"),
                AiWorkspaceClarificationOption("this_month", "本月", "日期范围：本月"),
            ),
        )
    }

    private fun missingGrowthClarification(plan: AiWorkspacePlan): AiWorkspaceClarification? {
        val instruction = plan.steps.firstOrNull { it.tool == AiWorkspaceTools.CRM_COUPON_CAMPAIGN_PROPOSAL }?.instruction ?: return null
        if (!Regex("(\\d{1,4})\\s*元").containsMatchIn(instruction)) {
            return AiWorkspaceClarification(
                "你希望固定优惠多少钱？",
                listOf(
                    AiWorkspaceClarificationOption("amount_5", "5 元", "固定优惠金额：5 元"),
                    AiWorkspaceClarificationOption("amount_8", "8 元", "固定优惠金额：8 元"),
                    AiWorkspaceClarificationOption("amount_10", "10 元", "固定优惠金额：10 元"),
                ),
            )
        }
        if (!Regex("(\\d{1,2})\\s*天").containsMatchIn(instruction)) {
            return AiWorkspaceClarification(
                "优惠券有效期多久？",
                listOf(
                    AiWorkspaceClarificationOption("days_7", "7 天", "有效期：7 天"),
                    AiWorkspaceClarificationOption("days_14", "14 天", "有效期：14 天"),
                    AiWorkspaceClarificationOption("days_30", "30 天", "有效期：30 天"),
                ),
            )
        }
        val hasSegment = listOf("全部顾客", "所有顾客", "全量顾客", "30天未到店", "三十天未到店", "沉睡", "流失", "高价值", "高消费", "VIP")
            .any(instruction::contains)
        if (!hasSegment) {
            return AiWorkspaceClarification(
                "这次活动面向哪类顾客？",
                listOf(
                    AiWorkspaceClarificationOption("segment_inactive", "30 天未到店", "目标客群：30 天未到店顾客"),
                    AiWorkspaceClarificationOption("segment_high_value", "高价值顾客", "目标客群：高价值顾客"),
                    AiWorkspaceClarificationOption("segment_all", "全部顾客", "目标客群：全部顾客"),
                ),
            )
        }
        return null
    }

    private fun hasRecognizedPeriod(message: String): Boolean {
        val normalized = message.replace(" ", "")
        return listOf(
            "今天", "今日", "昨天", "昨日", "近7天", "最近7天", "过去7天", "近七天", "最近七天", "过去七天",
            "近30天", "最近30天", "过去30天", "近三十天", "最近三十天", "过去三十天",
            "本周", "这周", "本星期", "这个星期", "本月", "这个月", "当月",
        ).any(normalized::contains)
    }

    private fun toolsForExpert(expert: AiWorkspaceExpert): Set<String> = when (expert) {
        AiWorkspaceExpert.AUTO -> AiWorkspaceTools.all
        AiWorkspaceExpert.OPERATIONS -> setOf(AiWorkspaceTools.OPERATING_INSIGHT, AiWorkspaceTools.REPORT_QUERY)
        AiWorkspaceExpert.PRODUCT_HELP -> setOf(AiWorkspaceTools.HOW_TO_SEARCH)
        AiWorkspaceExpert.MENU -> setOf(AiWorkspaceTools.REPORT_QUERY, AiWorkspaceTools.MENU_UPDATE_PRICE)
        AiWorkspaceExpert.GROWTH -> setOf(
            AiWorkspaceTools.GROWTH_DAILY_BRIEFING,
            AiWorkspaceTools.GROWTH_CONTENT_DRAFT,
            AiWorkspaceTools.CRM_COUPON_CAMPAIGN_PROPOSAL,
        )
    }

    private fun kindFor(tool: String): AiWorkspaceStepKind = when (tool) {
        AiWorkspaceTools.HOW_TO_SEARCH -> AiWorkspaceStepKind.HOW_TO
        AiWorkspaceTools.MENU_UPDATE_PRICE,
        AiWorkspaceTools.CRM_COUPON_CAMPAIGN_PROPOSAL,
        -> AiWorkspaceStepKind.ACTION
        else -> AiWorkspaceStepKind.ANALYSIS
    }

    private fun requireGrowthService(): AiGrowthService = growthService
        ?: throw AiWorkspaceException("AI_GROWTH_NOT_CONFIGURED", "增长参谋服务未配置")

    private fun growthProposalRequest(instruction: String): CreateAiGrowthProposalRequest {
        val amountYuan = Regex("(\\d{1,4})\\s*元").find(instruction)?.groupValues?.get(1)?.toLongOrNull()
            ?: throw AiWorkspaceException("AI_NEEDS_CLARIFICATION", "请明确固定优惠金额")
        val validDays = Regex("(\\d{1,2})\\s*天").find(instruction)?.groupValues?.get(1)?.toIntOrNull()
            ?: throw AiWorkspaceException("AI_NEEDS_CLARIFICATION", "请明确有效天数")
        val segment = when {
            listOf("30天未到店", "三十天未到店", "沉睡", "流失").any(instruction::contains) -> "INACTIVE_30_DAYS"
            listOf("高价值", "高消费", "VIP").any(instruction::contains) -> "HIGH_VALUE"
            listOf("全部顾客", "所有顾客", "全量顾客").any(instruction::contains) -> "ALL"
            else -> throw AiWorkspaceException("AI_NEEDS_CLARIFICATION", "请选择目标客群")
        }
        return CreateAiGrowthProposalRequest(amountYuan * 100, validDays, segment)
    }

    private fun resolvePeriod(message: String, context: AiWorkspaceContextDto): Pair<Long, Long> {
        val instant = Instant.ofEpochMilli(now())
        val zone = ZoneId.systemDefault()
        val today = instant.atZone(zone).toLocalDate()
        val current = instant.toEpochMilli()
        fun startOf(date: LocalDate) = date.atStartOfDay(zone).toInstant().toEpochMilli()

        // Natural-language periods override the optional page context. The input box is
        // the primary interaction: operators should be able to say “昨天” or “近 7 天”
        // without first configuring a separate date control.
        val normalized = message.replace(" ", "")
        when {
            listOf("昨天", "昨日").any(normalized::contains) -> {
                val yesterday = today.minusDays(1)
                return startOf(yesterday) to startOf(today) - 1
            }
            listOf("近7天", "最近7天", "过去7天", "近七天", "最近七天", "过去七天").any(normalized::contains) ->
                return startOf(today.minusDays(6)) to current
            listOf("近30天", "最近30天", "过去30天", "近三十天", "最近三十天", "过去三十天").any(normalized::contains) ->
                return startOf(today.minusDays(29)) to current
            listOf("本周", "这周", "本星期", "这个星期").any(normalized::contains) -> {
                val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                return startOf(monday) to current
            }
            listOf("本月", "这个月", "当月").any(normalized::contains) ->
                return startOf(today.withDayOfMonth(1)) to current
            listOf("今天", "今日").any(normalized::contains) ->
                return startOf(today) to current
        }
        if (context.fromMs != null || context.toMs != null) {
            val from = context.fromMs ?: throw IllegalArgumentException("fromMs is required with toMs")
            val to = context.toMs ?: throw IllegalArgumentException("toMs is required with fromMs")
            require(to > from) { "toMs must be greater than fromMs" }
            require(to - from <= MAX_RANGE_MS) { "Date range must not exceed 90 days" }
            return from to to
        }
        return startOf(today) to current
    }

    private fun requireLocale(locale: String) {
        require(locale == "zh-CN") { "Only zh-CN is supported" }
    }

    private fun requireModel(): AiWorkspaceModelClient = modelClient ?: throw AiNotConfiguredException()

    private fun requireToolPermission(actorId: String, permission: String) {
        if (!permissionChecker(actorId, permission)) {
            throw AiWorkspaceException("AI_PERMISSION_DENIED", "$permission permission is required")
        }
    }

    private fun ensureEnabled() {
        if (!enabled) throw AiWorkspaceException("AI_WORKSPACE_DISABLED", "AI 工作台未启用")
    }

    private fun updateRun(runId: String, status: String) = transaction {
        AiWorkspaceRunsTable.update({ AiWorkspaceRunsTable.id eq runId }) { it[AiWorkspaceRunsTable.status] = status }
    }

    private fun finishRun(runId: String, status: String, error: AiWorkspaceStepErrorDto? = null) = transaction {
        AiWorkspaceRunsTable.update({ AiWorkspaceRunsTable.id eq runId }) {
            it[AiWorkspaceRunsTable.status] = status
            it[completedAt] = now()
            it[errorCode] = error?.code
            it[errorMessage] = error?.message
            it[errorRetryable] = error?.retryable ?: false
        }
        appendEvent(runId, "run.completed", runStatus = status, error = error)
    }

    private fun awaitClarification(runId: String, clarification: AiWorkspaceClarificationDto) = transaction {
        AiWorkspaceRunsTable.update({ AiWorkspaceRunsTable.id eq runId }) {
            it[status] = "AWAITING_CLARIFICATION"
            it[completedAt] = now()
            it[clarificationJson] = json.encodeToString(clarification)
        }
        appendEvent(runId, "run.awaiting_clarification", runStatus = "AWAITING_CLARIFICATION", clarification = clarification)
    }

    private fun updateStep(
        stepId: String,
        status: AiWorkspaceStepStatus,
        result: AiWorkspaceStepResultDto? = null,
        proposalId: String? = null,
        error: AiWorkspaceStepErrorDto? = null,
    ) = transaction {
        AiWorkspaceRunStepsTable.update({ AiWorkspaceRunStepsTable.id eq stepId }) {
            it[AiWorkspaceRunStepsTable.status] = status.name
            if (result != null) it[resultJson] = json.encodeToString(result)
            if (proposalId != null) it[AiWorkspaceRunStepsTable.proposalId] = proposalId
            it[errorCode] = error?.code
            it[errorMessage] = error?.message
            it[errorRetryable] = error?.retryable ?: false
        }
    }

    private fun appendStepEvent(runId: String, type: String, stepId: String) = transaction {
        appendEvent(runId, type, loadStep(stepId))
    }

    private fun appendEvent(
        runId: String,
        type: String,
        step: AiWorkspaceStepDto? = null,
        runStatus: String? = null,
        error: AiWorkspaceStepErrorDto? = null,
        clarification: AiWorkspaceClarificationDto? = null,
    ) {
        val next = (AiWorkspaceEventsTable.selectAll()
            .where { AiWorkspaceEventsTable.runId eq runId }
            .maxOfOrNull { it[AiWorkspaceEventsTable.sequence] } ?: 0L) + 1
        AiWorkspaceEventsTable.insert {
            it[AiWorkspaceEventsTable.runId] = runId
            it[sequence] = next
            it[AiWorkspaceEventsTable.type] = type
            it[occurredAt] = now()
            it[stepJson] = step?.let { value -> json.encodeToString(value) }
            it[AiWorkspaceEventsTable.runStatus] = runStatus
            it[errorCode] = error?.code
            it[errorMessage] = error?.message
            it[errorRetryable] = error?.retryable ?: false
            it[clarificationJson] = clarification?.let { value -> json.encodeToString(value) }
        }
    }

    private fun loadSteps(runId: String): List<AiWorkspaceStepDto> = AiWorkspaceRunStepsTable.selectAll()
        .where { AiWorkspaceRunStepsTable.runId eq runId }
        .orderBy(AiWorkspaceRunStepsTable.position to SortOrder.ASC)
        .map { it.toStep() }

    private fun loadStep(stepId: String): AiWorkspaceStepDto = AiWorkspaceRunStepsTable.selectAll()
        .where { AiWorkspaceRunStepsTable.id eq stepId }.single().toStep()

    private fun ownedSession(actorId: String, sessionId: String): ResultRow = AiWorkspaceSessionsTable.selectAll().where {
        (AiWorkspaceSessionsTable.id eq sessionId) and (AiWorkspaceSessionsTable.actorId eq actorId)
    }.firstOrNull() ?: throw AiWorkspaceException("AI_SESSION_NOT_FOUND", "AI 会话不存在")

    private fun ownedRun(actorId: String, runId: String): ResultRow {
        val run = AiWorkspaceRunsTable.selectAll().where { AiWorkspaceRunsTable.id eq runId }.firstOrNull()
            ?: throw AiWorkspaceException("AI_RUN_NOT_FOUND", "AI 运行不存在")
        ownedSession(actorId, run[AiWorkspaceRunsTable.sessionId])
        return run
    }

    private fun ResultRow.toSessionSummary() = AiWorkspaceSessionSummaryDto(
        sessionId = this[AiWorkspaceSessionsTable.id],
        title = this[AiWorkspaceSessionsTable.title],
        expert = AiWorkspaceExpert.valueOf(this[AiWorkspaceSessionsTable.expert]),
        locale = this[AiWorkspaceSessionsTable.locale],
        createdAt = this[AiWorkspaceSessionsTable.createdAt],
        updatedAt = this[AiWorkspaceSessionsTable.updatedAt],
    )

    private fun ResultRow.toMessage() = AiWorkspaceMessageDto(
        messageId = this[AiWorkspaceMessagesTable.id],
        role = this[AiWorkspaceMessagesTable.role],
        content = this[AiWorkspaceMessagesTable.content],
        createdAt = this[AiWorkspaceMessagesTable.createdAt],
    )

    private fun ResultRow.toStep(): AiWorkspaceStepDto {
        val error = this[AiWorkspaceRunStepsTable.errorCode]?.let {
            AiWorkspaceStepErrorDto(it, this[AiWorkspaceRunStepsTable.errorMessage].orEmpty(), this[AiWorkspaceRunStepsTable.errorRetryable])
        }
        return AiWorkspaceStepDto(
            stepId = this[AiWorkspaceRunStepsTable.id],
            tool = this[AiWorkspaceRunStepsTable.tool],
            kind = AiWorkspaceStepKind.valueOf(this[AiWorkspaceRunStepsTable.kind]),
            status = AiWorkspaceStepStatus.valueOf(this[AiWorkspaceRunStepsTable.status]),
            dependsOn = json.decodeFromString(this[AiWorkspaceRunStepsTable.dependsOnJson]),
            displayTitle = this[AiWorkspaceRunStepsTable.displayTitle],
            result = this[AiWorkspaceRunStepsTable.resultJson]?.let { json.decodeFromString(it) },
            proposalId = this[AiWorkspaceRunStepsTable.proposalId],
            error = error,
        )
    }

    private fun ResultRow.toEvent() = AiWorkspaceEventDto(
        sequence = this[AiWorkspaceEventsTable.sequence],
        type = this[AiWorkspaceEventsTable.type],
        occurredAt = this[AiWorkspaceEventsTable.occurredAt],
        runId = this[AiWorkspaceEventsTable.runId],
        step = this[AiWorkspaceEventsTable.stepJson]?.let { json.decodeFromString(it) },
        runStatus = this[AiWorkspaceEventsTable.runStatus],
        error = this.eventError(),
        clarification = this[AiWorkspaceEventsTable.clarificationJson]?.let { json.decodeFromString(it) },
    )

    private fun ResultRow.runError(): AiWorkspaceStepErrorDto? = this[AiWorkspaceRunsTable.errorCode]?.let {
        AiWorkspaceStepErrorDto(it, this[AiWorkspaceRunsTable.errorMessage].orEmpty(), this[AiWorkspaceRunsTable.errorRetryable])
    }

    private fun ResultRow.runClarification(): AiWorkspaceClarificationDto? =
        this[AiWorkspaceRunsTable.clarificationJson]?.let { json.decodeFromString(it) }

    private fun AiWorkspaceClarification.toDto() = AiWorkspaceClarificationDto(
        question = question.trim(),
        options = options.map { AiWorkspaceClarificationOptionDto(it.id, it.label.trim(), it.value.trim()) },
    )

    private fun ResultRow.eventError(): AiWorkspaceStepErrorDto? = this[AiWorkspaceEventsTable.errorCode]?.let {
        AiWorkspaceStepErrorDto(it, this[AiWorkspaceEventsTable.errorMessage].orEmpty(), this[AiWorkspaceEventsTable.errorRetryable])
    }

    private fun Throwable.toWorkspaceError(): AiWorkspaceStepErrorDto = when (this) {
        is AiWorkspaceException -> AiWorkspaceStepErrorDto(code, message, retryable)
        is AiAgentException -> AiWorkspaceStepErrorDto(code, message, retryable)
        is AiGrowthException -> AiWorkspaceStepErrorDto(code, message, retryable)
        is AiProviderException -> AiWorkspaceStepErrorDto(code, message ?: "AI provider error", retryable)
        is AiNotConfiguredException -> AiWorkspaceStepErrorDto("AI_NOT_CONFIGURED", "DeepSeek API is not configured")
        is TimeoutCancellationException -> AiWorkspaceStepErrorDto("AI_TIMEOUT", "AI request timed out", true)
        else -> AiWorkspaceStepErrorDto("AI_STEP_FAILED", message ?: "AI step failed")
    }

    companion object {
        private const val MAX_RANGE_MS = 90L * 24 * 60 * 60 * 1000
        private val TERMINAL_RUN_STATUSES = setOf("COMPLETED", "FAILED", "AWAITING_CLARIFICATION")
        private val QUERY_TYPES = setOf("SUMMARY", "TOP_ITEMS", "PEAK_HOURS", "PAYMENT_METHODS", "TREND")

        fun fromEnvironment(
            insightService: AiInsightService,
            priceService: AiPriceAgentService,
            growthService: AiGrowthService = AiGrowthService.fromEnvironment(),
        ): AiWorkspaceService {
            val apiKey = System.getenv("DEEPSEEK_API_KEY")?.trim().orEmpty()
            val baseUrl = System.getenv("DEEPSEEK_BASE_URL")?.trim().takeUnless { it.isNullOrEmpty() }
                ?: "https://api.deepseek.com"
            val model = System.getenv("DEEPSEEK_MODEL")?.trim().takeUnless { it.isNullOrEmpty() }
                ?: "deepseek-v4-flash"
            val client = apiKey.takeIf(String::isNotEmpty)?.let { DeepSeekAiWorkspaceClient(it, baseUrl, model) }
            val enabled = System.getenv("AI_WORKSPACE_ENABLED")?.toBooleanStrictOrNull()
                ?: System.getenv("AI_AGENT_ENABLED").toBoolean()
            return AiWorkspaceService(client, insightService, priceService, enabled, growthService)
        }
    }
}

private fun workspaceActorHasPermission(actorId: String, permission: String): Boolean = transaction {
    val role = AdminUsersTable.selectAll()
        .where { (AdminUsersTable.id eq actorId) and (AdminUsersTable.isActive eq true) }
        .firstOrNull()?.get(AdminUsersTable.role)?.lowercase()
        ?: return@transaction false
    RolePermissionsTable.selectAll().where {
        (RolePermissionsTable.roleId eq role) and (RolePermissionsTable.permissionKey eq permission)
    }.count() > 0
}
