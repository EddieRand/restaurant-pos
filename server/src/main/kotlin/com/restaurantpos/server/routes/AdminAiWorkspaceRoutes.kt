package com.restaurantpos.server.routes

import com.restaurantpos.server.ai.AiNotConfiguredException
import com.restaurantpos.server.ai.AiProviderException
import com.restaurantpos.server.ai.AiWorkspaceException
import com.restaurantpos.server.ai.AiWorkspaceService
import com.restaurantpos.server.auth.JwtConfig
import com.restaurantpos.server.auth.hasPermission
import com.restaurantpos.server.model.*
import io.ktor.http.CacheControl
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.ContentTransformationException
import io.ktor.server.request.receive
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondTextWriter
import io.ktor.server.routing.*
import kotlinx.coroutines.delay
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

fun Route.adminAiWorkspaceRoutes(service: AiWorkspaceService) {
    val json = Json { encodeDefaults = true }
    authenticate("jwt") {
        route("/admin/ai/workspace") {
            post("/sessions") {
                call.respondWorkspaceErrors {
                    call.respond(service.createSession(call.actorId(), call.receive<CreateAiWorkspaceSessionRequest>()))
                }
            }
            get("/sessions") {
                call.respondWorkspaceErrors { call.respond(service.listSessions(call.actorId())) }
            }
            get("/sessions/{sessionId}") {
                call.respondWorkspaceErrors {
                    call.respond(service.getSession(call.actorId(), call.parameters["sessionId"].orEmpty()))
                }
            }
            post("/sessions/{sessionId}/messages") {
                call.respondWorkspaceErrors {
                    val allowed = buildSet {
                        add(AiWorkspaceTools.HOW_TO_SEARCH)
                        if (call.hasPermission("report.daily")) {
                            add(AiWorkspaceTools.OPERATING_INSIGHT)
                            add(AiWorkspaceTools.REPORT_QUERY)
                        }
                        if (call.hasPermission("menu.edit")) add(AiWorkspaceTools.MENU_UPDATE_PRICE)
                    }
                    val response = service.acceptMessage(
                        call.actorId(),
                        call.parameters["sessionId"].orEmpty(),
                        call.receive<AiWorkspaceMessageRequest>(),
                        allowed,
                    )
                    call.respond(HttpStatusCode.Accepted, response)
                }
            }
            get("/runs/{runId}/events") {
                call.respondWorkspaceErrors {
                    val actorId = call.actorId()
                    val runId = call.parameters["runId"].orEmpty()
                    var cursor = call.request.queryParameters["afterSequence"]?.toLongOrNull() ?: 0L
                    require(cursor >= 0) { "afterSequence must not be negative" }
                    service.events(actorId, runId, cursor)
                    call.response.header(HttpHeaders.CacheControl, CacheControl.NoCache(null).toString())
                    call.respondTextWriter(ContentType.Text.EventStream, HttpStatusCode.OK) {
                        while (true) {
                            val events = service.events(actorId, runId, cursor)
                            events.forEach { event ->
                                write("id: ${event.sequence}\n")
                                write("event: ${event.type}\n")
                                write("data: ${json.encodeToString(event)}\n\n")
                                flush()
                                cursor = event.sequence
                            }
                            if (events.isEmpty() && service.isTerminal(actorId, runId)) break
                            delay(250)
                        }
                    }
                }
            }
        }
    }
}

private fun io.ktor.server.application.ApplicationCall.actorId(): String =
    principal<JWTPrincipal>()!!.payload.getClaim(JwtConfig.CLAIM_USER_ID).asString()

private suspend fun io.ktor.server.application.ApplicationCall.respondWorkspaceErrors(block: suspend () -> Unit) {
    try {
        block()
    } catch (e: AiWorkspaceException) {
        val status = when (e.code) {
            "AI_INVALID_REQUEST" -> HttpStatusCode.BadRequest
            "AI_PERMISSION_DENIED" -> HttpStatusCode.Forbidden
            "AI_SESSION_NOT_FOUND", "AI_RUN_NOT_FOUND" -> HttpStatusCode.NotFound
            "AI_RUN_IN_PROGRESS" -> HttpStatusCode.Conflict
            "AI_CLARIFICATION_REQUIRED", "AI_UNSUPPORTED_INTENT" -> HttpStatusCode.UnprocessableEntity
            "AI_WORKSPACE_DISABLED" -> HttpStatusCode.ServiceUnavailable
            else -> HttpStatusCode.BadRequest
        }
        respond(status, AiWorkspaceErrorResponse(e.code, e.message, e.retryable))
    } catch (_: AiNotConfiguredException) {
        respond(HttpStatusCode.ServiceUnavailable, AiWorkspaceErrorResponse("AI_NOT_CONFIGURED", "DeepSeek API is not configured"))
    } catch (e: AiProviderException) {
        val status = when (e.code) {
            "AI_QUOTA_EXCEEDED" -> HttpStatusCode.PaymentRequired
            "AI_RATE_LIMITED" -> HttpStatusCode.TooManyRequests
            "AI_PROVIDER_UNAVAILABLE" -> HttpStatusCode.ServiceUnavailable
            else -> HttpStatusCode.BadGateway
        }
        respond(status, AiWorkspaceErrorResponse(e.code, e.message ?: "AI provider error", e.retryable))
    } catch (_: BadRequestException) {
        respond(HttpStatusCode.BadRequest, AiWorkspaceErrorResponse("AI_INVALID_REQUEST", "Request body is invalid"))
    } catch (_: ContentTransformationException) {
        respond(HttpStatusCode.BadRequest, AiWorkspaceErrorResponse("AI_INVALID_REQUEST", "Request body is invalid"))
    } catch (e: IllegalArgumentException) {
        respond(HttpStatusCode.BadRequest, AiWorkspaceErrorResponse("AI_INVALID_REQUEST", e.message ?: "Invalid request"))
    }
}
