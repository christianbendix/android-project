package com.example.splendit.auth

import java.io.IOException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONException

sealed interface AuthResult {
    data class Success(val session: Session) : AuthResult
    data object InvalidCredentials : AuthResult

    /** Server-side field errors, e.g. `email -> validation_not_unique`. */
    data class FieldErrors(val codes: Map<String, String>) : AuthResult
    data object RateLimited : AuthResult
    data object SignupDisabled : AuthResult
    data object Network : AuthResult
    data object Unknown : AuthResult
}

interface AuthRepository {
    val session: Flow<Session?>

    /** Creates the account, then logs in with it. [phone] is E.164, e.g. `+4512345678`. */
    suspend fun signUp(email: String, password: String, name: String, phone: String): AuthResult
    suspend fun login(email: String, password: String): AuthResult

    /**
     * Refreshes the stored token. Returns null if there is no stored session.
     * A 401 clears the session; network and server errors keep it so the app
     * still opens offline.
     */
    suspend fun restoreSession(): AuthResult?
    suspend fun logout()
}

class PocketBaseAuthRepository(
    private val api: PocketBaseApi,
    private val store: SessionStore,
    private val io: CoroutineDispatcher = Dispatchers.IO
) : AuthRepository {

    override val session: Flow<Session?> = store.session

    override suspend fun signUp(email: String, password: String, name: String, phone: String) =
        request {
            val response = api.createUser(email.trim(), password, name.trim(), phone)
            when (response.code) {
                200 -> login(email, password)
                400 -> response.body?.fieldErrorCodes()
                    ?.takeIf { it.isNotEmpty() }
                    ?.let { AuthResult.FieldErrors(it) }
                    ?: AuthResult.Unknown
                403 -> AuthResult.SignupDisabled
                429 -> AuthResult.RateLimited
                else -> AuthResult.Unknown
            }
        }

    override suspend fun login(email: String, password: String) = request {
        val response = api.authWithPassword(email.trim(), password)
        when (response.code) {
            200 -> saveSession(response)
            400 -> AuthResult.InvalidCredentials
            429 -> AuthResult.RateLimited
            else -> AuthResult.Unknown
        }
    }

    override suspend fun restoreSession(): AuthResult? {
        val current = store.session.first() ?: return null
        return request {
            val response = api.authRefresh(current.token)
            when (response.code) {
                200 -> saveSession(response)
                401 -> {
                    store.clear()
                    AuthResult.InvalidCredentials
                }
                else -> AuthResult.Unknown
            }
        }
    }

    override suspend fun logout() {
        store.clear()
    }

    private suspend fun saveSession(response: ApiResponse): AuthResult {
        val session = response.body?.toSession() ?: return AuthResult.Unknown
        store.save(session)
        return AuthResult.Success(session)
    }

    private suspend fun request(block: suspend () -> AuthResult): AuthResult = withContext(io) {
        try {
            block()
        } catch (e: IOException) {
            AuthResult.Network
        } catch (e: JSONException) {
            AuthResult.Unknown
        }
    }
}
