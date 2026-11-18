package com.example.splendit.auth

import kotlinx.coroutines.flow.StateFlow

/** A signed-in (or signed-out) user with no backend, for screens that only read the session. */
class FakeAuthRepository(initial: Session? = null) : AuthRepository {
    private val store = FakeSessionStore(initial)
    override val session: StateFlow<Session?> = store.session

    override suspend fun signUp(email: String, password: String, name: String) = AuthResult.Unknown

    override suspend fun login(email: String, password: String) = AuthResult.Unknown

    override suspend fun restoreSession(): AuthResult? = null

    override suspend fun logout() = store.clear()
}
