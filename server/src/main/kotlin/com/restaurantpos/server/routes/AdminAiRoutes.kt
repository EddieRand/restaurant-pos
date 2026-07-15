package com.restaurantpos.server.routes

import com.restaurantpos.server.ai.AiInsightService
import com.restaurantpos.server.ai.AiNotConfiguredException
import com.restaurantpos.server.ai.AiProviderException
import com.restaurantpos.server.auth.requirePermission
import com.restaurantpos.server.model.AiInsightErrorResponse
import com.restaurantpos.server.model.AiOperatingInsightRequest
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import kotlinx.coroutines.TimeoutCancellationException

fun Route.adminAiRoutes(service: AiInsightService) {
    authenticate("jwt") {
        route("/admin/ai") {
            post("/operating-insight") {
                if (!call.requirePermission("report.daily")) return@post
                val request = call.receive<AiOperatingInsightRequest>()
                try {
                    call.respond(service.generate(request))
                } catch (e: IllegalArgumentException) {
                    call.respond(HttpStatusCode.BadRequest, AiInsightErrorResponse("AI_INVALID_REQUEST", e.message ?: "Invalid request"))
                } catch (_: AiNotConfiguredException) {
                    call.respond(HttpStatusCode.ServiceUnavailable, AiInsightErrorResponse("AI_NOT_CONFIGURED", "DeepSeek API is not configured"))
                } catch (e: AiProviderException) {
                    val status = when (e.code) {
                        "AI_QUOTA_EXCEEDED" -> HttpStatusCode.PaymentRequired
                        "AI_RATE_LIMITED" -> HttpStatusCode.TooManyRequests
                        "AI_PROVIDER_UNAVAILABLE" -> HttpStatusCode.ServiceUnavailable
                        "AI_AUTH_FAILED", "AI_INVALID_RESPONSE", "AI_PROVIDER_ERROR" -> HttpStatusCode.BadGateway
                        else -> HttpStatusCode.BadGateway
                    }
                    call.respond(status, AiInsightErrorResponse(e.code, e.message ?: "AI provider error"))
                } catch (_: TimeoutCancellationException) {
                    call.respond(HttpStatusCode.GatewayTimeout, AiInsightErrorResponse("AI_TIMEOUT", "AI generation timed out"))
                }
            }
        }
    }
}
