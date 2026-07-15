package com.restaurantpos.server.auth

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import java.util.Date

object JwtConfig {
    private lateinit var secret: String
    private lateinit var algorithm: Algorithm
    const val CLAIM_USER_ID = "userId"
    const val CLAIM_ROLE = "role"
    private const val VALIDITY_MS = 24 * 60 * 60 * 1_000L // 24 hours

    fun init(secret: String) {
        this.secret = secret
        this.algorithm = Algorithm.HMAC256(secret)
    }

    fun issueToken(userId: String, role: String): String = JWT.create()
        .withClaim(CLAIM_USER_ID, userId)
        .withClaim(CLAIM_ROLE, role.lowercase())
        .withExpiresAt(Date(System.currentTimeMillis() + VALIDITY_MS))
        .sign(algorithm)

    fun verifier() = JWT.require(algorithm).build()!!
}
