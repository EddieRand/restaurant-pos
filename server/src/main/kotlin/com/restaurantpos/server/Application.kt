package com.restaurantpos.server

import com.restaurantpos.server.auth.JwtConfig
import com.restaurantpos.server.auth.requirePermission
import com.restaurantpos.server.ai.AiInsightService
import com.restaurantpos.server.ai.AiPriceAgentService
import com.restaurantpos.server.ai.AiWorkspaceService
import com.restaurantpos.server.ai.AiGrowthService
import com.restaurantpos.server.db.DatabaseFactory
import com.restaurantpos.server.model.ErrorResponse
import com.restaurantpos.server.model.AiAgentErrorResponse
import com.restaurantpos.server.routes.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.calllogging.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.http.content.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.Json

fun main() {
    val port = System.getenv("PORT")?.toIntOrNull() ?: 8080
    val jdbcUrl = System.getenv("DATABASE_URL")
    val dbUser = System.getenv("DB_USER") ?: "postgres"
    val dbPassword = System.getenv("DB_PASSWORD") ?: "postgres"
    val jwtSecret = System.getenv("JWT_SECRET") ?: "dev-secret-change-in-production"

    if (jdbcUrl != null) {
        DatabaseFactory.init(jdbcUrl, dbUser, dbPassword)
    } else {
        // Dev fallback: H2 in-memory (no PostgreSQL required)
        DatabaseFactory.initWithUrl("jdbc:h2:mem:posdev;DB_CLOSE_DELAY=-1;MODE=PostgreSQL")
    }
    JwtConfig.init(jwtSecret)

    embeddedServer(Netty, port = port) {
        configurePlugins()
        configureAuth()
        configureRouting()
    }.start(wait = true)
}

fun Application.configurePlugins() {
    install(ContentNegotiation) {
        json(Json {
            prettyPrint = false
            isLenient = true
            ignoreUnknownKeys = true
        })
    }
    install(CallLogging) { }
    install(CORS) {
        anyHost()
        allowHeader(HttpHeaders.ContentType)
        allowHeader(HttpHeaders.Authorization)
        allowMethod(HttpMethod.Options)
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Patch)
        allowMethod(HttpMethod.Delete)
    }
    install(StatusPages) {
        exception<IllegalArgumentException> { call, cause ->
            call.respond(HttpStatusCode.BadRequest, ErrorResponse(cause.message ?: "Bad request"))
        }
        exception<Throwable> { call, cause ->
            call.application.log.error("Unhandled error", cause)
            call.respond(HttpStatusCode.InternalServerError, ErrorResponse("Internal server error"))
        }
    }
}

fun Application.configureAuth() {
    install(Authentication) {
        jwt("jwt") {
            verifier(JwtConfig.verifier())
            validate { credential ->
                val userId = credential.payload.getClaim(JwtConfig.CLAIM_USER_ID).asString()
                if (userId.isNullOrBlank()) null
                else JWTPrincipal(credential.payload)
            }
            challenge { _, _ ->
                if (call.request.uri.startsWith("/admin/ai/")) {
                    call.respond(HttpStatusCode.Unauthorized, AiAgentErrorResponse("AI_UNAUTHORIZED", "Missing or invalid token", false))
                } else {
                    call.respond(HttpStatusCode.Unauthorized, ErrorResponse("Missing or invalid token"))
                }
            }
        }
    }
}

fun Application.configureRouting(
    aiInsightService: AiInsightService = AiInsightService.fromEnvironment(),
    aiPriceAgentService: AiPriceAgentService = AiPriceAgentService.fromEnvironment(),
    aiWorkspaceService: AiWorkspaceService = AiWorkspaceService.fromEnvironment(aiInsightService, aiPriceAgentService),
    aiGrowthService: AiGrowthService = AiGrowthService.fromEnvironment(),
) {
    routing {
        get("/health") {
            call.respond(mapOf("status" to "ok"))
        }
        authRoutes()
        syncRoutes()
        publicOrderingRoutes()
        cdsStateRoutes()
        adminChannelRoutes()
        adminMenuCategoryRoutes()
        adminPaymentMethodRoutes()
        adminMenuRoutes()
        adminMenuProfileRoutes()
        adminOrderRoutes()
        adminReportRoutes()
        adminAiRoutes(aiInsightService)
        adminAiPriceRoutes(aiPriceAgentService, aiWorkspaceService)
        adminAiWorkspaceRoutes(aiWorkspaceService)
        adminAiGrowthRoutes(aiGrowthService, aiWorkspaceService)
        adminUserRoutes()
        adminRoleRoutes()
        adminSettingsRoutes()
        adminQrOrderingRoutes()
        adminTableRoutes()
        adminModifierGroupRoutes()
        adminCouponRoutes()
        adminComboRoutes()
        waiterCallRoutes()
        adminCrmRoutes()
        adminInventoryRoutes()
        adminReservationRoutes()
        adminCashierShiftRoutes()
        adminTimecardRoutes()
        adminScheduleRoutes()
        adminGiftCardRoutes()
        groupBuyingVoucherRoutes()
        devSeedRoutes()
        get("/qr") { call.respondCustomerQrIndex() }
        get("/qr/") { call.respondCustomerQrIndex() }
        get("/qr/index.html") { call.respondCustomerQrIndex() }
        get("/qr/assets/{file}") {
            call.respondCustomerQrAsset(call.parameters["file"] ?: "")
        }
        // Serve React web admin SPA from classpath resources/static
        staticResources("/", "static") {
            default("index.html")
        }
    }
}

private suspend fun ApplicationCall.respondCustomerQrIndex() {
    val html = Thread.currentThread().contextClassLoader
        .getResource("customer/index.html")
        ?.readText()
        ?: error("Customer QR index.html not found")
    respondText(html, ContentType.Text.Html)
}

private suspend fun ApplicationCall.respondCustomerQrAsset(file: String) {
    require(file.matches(Regex("[A-Za-z0-9._-]+"))) { "Invalid asset path" }
    val resourcePath = "customer/assets/$file"
    val bytes = Thread.currentThread().contextClassLoader
        .getResource(resourcePath)
        ?.readBytes()
        ?: run {
            respond(HttpStatusCode.NotFound)
            return
        }
    val contentType = when {
        file.endsWith(".js") -> ContentType.Application.JavaScript
        file.endsWith(".css") -> ContentType.Text.CSS
        else -> ContentType.Application.OctetStream
    }
    respondBytes(bytes, contentType)
}
