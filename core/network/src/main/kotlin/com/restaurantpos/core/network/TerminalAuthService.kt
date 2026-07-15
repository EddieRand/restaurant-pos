package com.restaurantpos.core.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Exchanges a terminalId for a server-issued JWT.
 * Called once at app startup; token is cached in [TokenStore].
 */
@Singleton
class TerminalAuthService @Inject constructor(
    private val baseUrl: String,
    private val client: OkHttpClient,
    private val tokenStore: TokenStore,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val mediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun loginTerminal(terminalId: String): Result<String> = withContext(Dispatchers.IO) {
        val body = json.encodeToString(TerminalLoginBody(terminalId))
        val request = Request.Builder()
            .url("$baseUrl/auth/login/terminal")
            .post(body.toRequestBody(mediaType))
            .build()
        try {
            val resp = client.newCall(request).execute()
            resp.use { r ->
                if (r.isSuccessful) {
                    val responseBody = r.body?.string() ?: return@use Result.failure(IOException("Empty body"))
                    val parsed = json.decodeFromString<LoginTokenResponse>(responseBody)
                    tokenStore.saveToken(parsed.token)
                    Result.success(parsed.token)
                } else {
                    Result.failure(IOException("HTTP ${r.code}"))
                }
            }
        } catch (e: IOException) {
            Result.failure(e)
        }
    }
}

@Serializable
private data class TerminalLoginBody(val terminalId: String)

@Serializable
private data class LoginTokenResponse(
    val token: String,
    val userId: String = "",
    val role: String = "",
    val displayName: String = "",
)
