package com.restaurantpos.server.routes

import com.restaurantpos.server.auth.JwtConfig
import com.restaurantpos.server.db.tables.AdminUsersTable
import com.restaurantpos.server.db.tables.UsersTable
import com.restaurantpos.server.model.ErrorResponse
import com.restaurantpos.server.model.LoginResponse
import com.restaurantpos.server.model.PasswordLoginRequest
import com.restaurantpos.server.model.PinLoginRequest
import com.restaurantpos.server.model.TerminalLoginRequest
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.security.MessageDigest

fun Route.authRoutes() {
    route("/auth") {
        // Android POS staff login with PIN
        post("/login/pin") {
            val req = call.receive<PinLoginRequest>()
            val pinHash = sha256(req.pin)

            val user = transaction {
                UsersTable.selectAll()
                    .where { UsersTable.pinHash eq pinHash }
                    .firstOrNull()
            }

            if (user == null || !user[UsersTable.isActive]) {
                call.respond(HttpStatusCode.Unauthorized, ErrorResponse("Invalid PIN"))
                return@post
            }

            val token = JwtConfig.issueToken(user[UsersTable.id], user[UsersTable.role])
            call.respond(
                LoginResponse(
                    token = token,
                    userId = user[UsersTable.id],
                    role = user[UsersTable.role].lowercase(),
                    displayName = user[UsersTable.displayName],
                )
            )
        }

        // Android terminal device login — grants TERMINAL role JWT for sync
        post("/login/terminal") {
            val req = call.receive<TerminalLoginRequest>()
            if (req.terminalId.isBlank()) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("terminalId required"))
                return@post
            }
            val token = JwtConfig.issueToken(req.terminalId, "TERMINAL")
            call.respond(
                LoginResponse(
                    token = token,
                    userId = req.terminalId,
                    role = "TERMINAL",
                    displayName = req.terminalId,
                )
            )
        }

        // Web admin login with email + password
        post("/login/password") {
            val req = call.receive<PasswordLoginRequest>()
            val passwordHash = sha256(req.password)

            val user = transaction {
                AdminUsersTable.selectAll()
                    .where { AdminUsersTable.email eq req.email }
                    .firstOrNull()
            }

            if (user == null || !user[AdminUsersTable.isActive] || user[AdminUsersTable.passwordHash] != passwordHash) {
                call.respond(HttpStatusCode.Unauthorized, ErrorResponse("Invalid credentials"))
                return@post
            }

            val token = JwtConfig.issueToken(user[AdminUsersTable.id], user[AdminUsersTable.role])
            call.respond(
                LoginResponse(
                    token = token,
                    userId = user[AdminUsersTable.id],
                    role = user[AdminUsersTable.role].lowercase(),
                    displayName = user[AdminUsersTable.email],
                )
            )
        }
    }
}

private fun sha256(input: String): String {
    val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
    return bytes.joinToString("") { "%02x".format(it) }
}
