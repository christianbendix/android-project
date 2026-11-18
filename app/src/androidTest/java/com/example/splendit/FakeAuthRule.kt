package com.example.splendit

import androidx.test.core.app.ApplicationProvider
import com.example.splendit.auth.AuthRepository
import com.example.splendit.auth.AuthResult
import com.example.splendit.auth.Session
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/**
 * Replaces the app's auth repository with an in-memory one, so UI tests start signed in (or
 * out) without the network. Declare it with a lower `order` than the activity rule so it runs
 * before the activity is launched.
 */
class FakeAuthRule(initial: Session?) : TestWatcher() {
    val repository = FakeAuthRepository(initial)
    private val container =
        ApplicationProvider.getApplicationContext<SplenditApplication>().container
    private lateinit var original: AuthRepository

    override fun starting(description: Description) {
        original = container.authRepository
        container.authRepository = repository
    }

    override fun finished(description: Description) {
        container.authRepository = original
    }

    class FakeAuthRepository(initial: Session?) : AuthRepository {
        override val session = MutableStateFlow(initial)

        override suspend fun signUp(email: String, password: String, name: String) =
            AuthResult.Unknown

        override suspend fun login(email: String, password: String) = AuthResult.InvalidCredentials

        override suspend fun restoreSession(): AuthResult? = null

        override suspend fun logout() {
            session.value = null
        }
    }

    companion object {
        val TEST_USER = Session("tok", "qqw7x1psxmeuq72", "test@example.com", "Test User")
    }
}
