package com.restaurantpos.server.routes

import com.restaurantpos.server.ai.*
import com.restaurantpos.server.auth.JwtConfig
import com.restaurantpos.server.auth.hasPermission
import com.restaurantpos.server.model.*
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.ContentTransformationException
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.*
import kotlinx.coroutines.TimeoutCancellationException

fun Route.adminAiGrowthRoutes(service: AiGrowthService, workspaceService: AiWorkspaceService? = null) {
    authenticate("jwt") {
        route("/admin/ai/growth") {
            get("/briefings/today") {
                if (!call.hasPermission(AiGrowthPermissions.CAMPAIGN_MANAGE)) return@get call.growthForbidden()
                call.respondGrowthErrors { call.respond(service.today()) }
            }
            post("/proposals") {
                if (!call.hasPermission(AiGrowthPermissions.CAMPAIGN_MANAGE)) return@post call.growthForbidden()
                call.respondGrowthErrors { call.respond(service.createProposal(call.actorId(), call.receive<CreateAiGrowthProposalRequest>())) }
            }
            post("/proposals/{proposalId}/revise") {
                if (!call.hasPermission(AiGrowthPermissions.CAMPAIGN_MANAGE)) return@post call.growthForbidden()
                call.respondGrowthErrors {
                    call.respond(service.revise(call.actorId(), call.parameters["proposalId"].orEmpty(), call.receive<ReviseAiGrowthProposalRequest>()))
                }
            }
            post("/proposals/{proposalId}/execute") {
                if (!call.hasPermission(AiGrowthPermissions.CAMPAIGN_MANAGE)) return@post call.growthForbidden()
                call.respondGrowthErrors {
                    val actorId = call.actorId()
                    val response = service.execute(actorId, call.parameters["proposalId"].orEmpty(), call.receive<ExecuteAiGrowthProposalRequest>())
                    runCatching { workspaceService?.markGrowthProposalExecuted(actorId, response) }
                        .onFailure { call.application.environment.log.error("Failed to link AI growth execution", it) }
                    call.respond(response)
                }
            }
        }
    }
}

private fun io.ktor.server.application.ApplicationCall.actorId(): String =
    principal<JWTPrincipal>()!!.payload.getClaim(JwtConfig.CLAIM_USER_ID).asString()

private suspend fun io.ktor.server.application.ApplicationCall.growthForbidden() =
    respond(HttpStatusCode.Forbidden, AiGrowthErrorResponse(AiGrowthErrorCodes.PERMISSION_DENIED, "缺少 crm.campaign.manage 权限"))

private suspend fun io.ktor.server.application.ApplicationCall.respondGrowthErrors(block: suspend () -> Unit) {
    try {
        block()
    } catch (e: AiGrowthException) {
        val status = when (e.code) {
            AiGrowthErrorCodes.PERMISSION_DENIED -> HttpStatusCode.Forbidden
            AiGrowthErrorCodes.NOT_FOUND -> HttpStatusCode.NotFound
            AiGrowthErrorCodes.EXPIRED -> HttpStatusCode.Gone
            AiGrowthErrorCodes.STALE, AiGrowthErrorCodes.IDEMPOTENCY_CONFLICT, AiGrowthErrorCodes.ALREADY_EXECUTED -> HttpStatusCode.Conflict
            AiGrowthErrorCodes.INVALID_PARAMS -> HttpStatusCode.UnprocessableEntity
            else -> HttpStatusCode.BadRequest
        }
        respond(status, AiGrowthErrorResponse(e.code, e.message, e.retryable))
    } catch (_: AiNotConfiguredException) {
        respond(HttpStatusCode.ServiceUnavailable, AiGrowthErrorResponse("AI_NOT_CONFIGURED", "DeepSeek API is not configured"))
    } catch (e: AiProviderException) {
        val status = when (e.code) {
            "AI_QUOTA_EXCEEDED" -> HttpStatusCode.PaymentRequired
            "AI_RATE_LIMITED" -> HttpStatusCode.TooManyRequests
            "AI_PROVIDER_UNAVAILABLE" -> HttpStatusCode.ServiceUnavailable
            else -> HttpStatusCode.BadGateway
        }
        respond(status, AiGrowthErrorResponse(e.code, e.message ?: "AI provider error", e.retryable))
    } catch (_: TimeoutCancellationException) {
        respond(HttpStatusCode.GatewayTimeout, AiGrowthErrorResponse("AI_TIMEOUT", "AI request timed out", true))
    } catch (_: BadRequestException) {
        respond(HttpStatusCode.BadRequest, AiGrowthErrorResponse(AiGrowthErrorCodes.INVALID_PARAMS, "请求体无效"))
    } catch (_: ContentTransformationException) {
        respond(HttpStatusCode.BadRequest, AiGrowthErrorResponse(AiGrowthErrorCodes.INVALID_PARAMS, "请求体无效"))
    }
}
