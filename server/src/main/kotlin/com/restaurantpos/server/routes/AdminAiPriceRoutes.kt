package com.restaurantpos.server.routes

import com.restaurantpos.server.ai.AiAgentException
import com.restaurantpos.server.ai.AiNotConfiguredException
import com.restaurantpos.server.ai.AiPriceAgentService
import com.restaurantpos.server.ai.AiWorkspaceService
import com.restaurantpos.server.ai.AiProviderException
import com.restaurantpos.server.auth.JwtConfig
import com.restaurantpos.server.auth.hasPermission
import com.restaurantpos.server.model.AiAgentErrorResponse
import com.restaurantpos.server.model.AiPriceProposalRequest
import com.restaurantpos.server.model.ExecuteAiPriceProposalRequest
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.plugins.ContentTransformationException
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import kotlinx.coroutines.TimeoutCancellationException

fun Route.adminAiPriceRoutes(service: AiPriceAgentService, workspaceService: AiWorkspaceService? = null) {
    authenticate("jwt") {
        route("/admin/ai/price-proposals") {
            post {
                if (!call.hasPermission("menu.edit")) {
                    call.respond(HttpStatusCode.Forbidden, AiAgentErrorResponse("AI_PERMISSION_DENIED", "menu.edit permission is required", false))
                    return@post
                }
                call.respondAgentErrors {
                    val actorId = call.principal<JWTPrincipal>()!!.payload.getClaim(JwtConfig.CLAIM_USER_ID).asString()
                    call.respond(service.createProposal(actorId, call.receive<AiPriceProposalRequest>()))
                }
            }
            post("/{proposalId}/execute") {
                if (!call.hasPermission("menu.edit")) {
                    call.respond(HttpStatusCode.Forbidden, AiAgentErrorResponse("AI_PERMISSION_DENIED", "menu.edit permission is required", false))
                    return@post
                }
                call.respondAgentErrors {
                    val actorId = call.principal<JWTPrincipal>()!!.payload.getClaim(JwtConfig.CLAIM_USER_ID).asString()
                    val proposalId = call.parameters["proposalId"].orEmpty()
                    val response = service.execute(actorId, proposalId, call.receive<ExecuteAiPriceProposalRequest>())
                    runCatching { workspaceService?.markProposalExecuted(actorId, response) }
                        .onFailure { call.application.environment.log.error("Failed to link AI workspace execution", it) }
                    call.respond(response)
                }
            }
        }
    }
}

private suspend fun io.ktor.server.application.ApplicationCall.respondAgentErrors(
    block: suspend () -> Unit,
) {
    try {
        block()
    } catch (e: AiAgentException) {
        val status = when (e.code) {
            "AI_INVALID_REQUEST" -> HttpStatusCode.BadRequest
            "AI_PERMISSION_DENIED" -> HttpStatusCode.Forbidden
            "AI_PROPOSAL_NOT_FOUND" -> HttpStatusCode.NotFound
            "AI_PROPOSAL_STALE", "AI_PROPOSAL_ALREADY_EXECUTED", "AI_IDEMPOTENCY_CONFLICT" -> HttpStatusCode.Conflict
            "AI_PROPOSAL_EXPIRED" -> HttpStatusCode.Gone
            "AI_TARGET_AMBIGUOUS" -> HttpStatusCode.UnprocessableEntity
            "AI_AGENT_DISABLED" -> HttpStatusCode.ServiceUnavailable
            else -> HttpStatusCode.BadRequest
        }
        respond(status, AiAgentErrorResponse(e.code, e.message, e.retryable))
    } catch (_: AiNotConfiguredException) {
        respond(HttpStatusCode.ServiceUnavailable, AiAgentErrorResponse("AI_NOT_CONFIGURED", "DeepSeek API is not configured", false))
    } catch (e: AiProviderException) {
        val status = when (e.code) {
            "AI_QUOTA_EXCEEDED" -> HttpStatusCode.PaymentRequired
            "AI_RATE_LIMITED" -> HttpStatusCode.TooManyRequests
            "AI_PROVIDER_UNAVAILABLE" -> HttpStatusCode.ServiceUnavailable
            else -> HttpStatusCode.BadGateway
        }
        respond(status, AiAgentErrorResponse(e.code, e.message ?: "AI provider error", e.retryable))
    } catch (_: TimeoutCancellationException) {
        respond(HttpStatusCode.GatewayTimeout, AiAgentErrorResponse("AI_TIMEOUT", "AI request timed out", true))
    } catch (_: BadRequestException) {
        respond(HttpStatusCode.BadRequest, AiAgentErrorResponse("AI_INVALID_REQUEST", "Request body is invalid", false))
    } catch (_: ContentTransformationException) {
        respond(HttpStatusCode.BadRequest, AiAgentErrorResponse("AI_INVALID_REQUEST", "Request body is invalid", false))
    } catch (_: IllegalArgumentException) {
        respond(HttpStatusCode.BadRequest, AiAgentErrorResponse("AI_INVALID_REQUEST", "Request body is invalid", false))
    }
}
