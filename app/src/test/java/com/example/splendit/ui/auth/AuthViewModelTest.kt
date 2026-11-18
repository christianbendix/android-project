package com.example.splendit.ui.auth

import com.example.splendit.R
import com.example.splendit.auth.AuthRepository
import com.example.splendit.auth.AuthResult
import com.example.splendit.auth.FakeSessionStore
import com.example.splendit.auth.Session
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val repository = FakeAuthRepository()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.viewModel() = AuthViewModel(repository).also { advanceUntilIdle() }

    private fun AuthViewModel.fill(vararg values: Pair<AuthField, String>) =
        values.forEach { (field, value) -> onChange(field, value) }

    @Test
    fun invalidInput_blocksRequest() = runTest(dispatcher) {
        val vm = viewModel()
        vm.fill(AuthField.Email to "not-an-email", AuthField.Password to "short")

        vm.submit()
        advanceUntilIdle()

        assertEquals(
            mapOf(
                AuthField.Email to R.string.error_email_invalid,
                AuthField.Password to R.string.error_password_too_short
            ),
            vm.form.value.fieldErrors
        )
        assertEquals(0, repository.calls)
    }

    @Test
    fun signUp_mismatchedConfirm_blocksRequest() = runTest(dispatcher) {
        val vm = viewModel()
        vm.showMode(AuthMode.SignUp)
        vm.fill(
            AuthField.Email to "a@b.co",
            AuthField.Password to "password123",
            AuthField.PasswordConfirm to "password124",
            AuthField.Name to "x".repeat(101)
        )

        vm.submit()
        advanceUntilIdle()

        assertEquals(
            R.string.error_password_mismatch,
            vm.form.value.fieldErrors[AuthField.PasswordConfirm]
        )
        assertEquals(R.string.error_name_too_long, vm.form.value.fieldErrors[AuthField.Name])
        assertEquals(0, repository.calls)
    }

    @Test
    fun passwordOver72_blocksRequest() = runTest(dispatcher) {
        val vm = viewModel()
        vm.fill(AuthField.Email to "a@b.co", AuthField.Password to "p".repeat(73))

        vm.submit()

        assertEquals(
            R.string.error_password_too_long,
            vm.form.value.fieldErrors[AuthField.Password]
        )
        assertEquals(0, repository.calls)
    }

    @Test
    fun submit_togglesLoading_trimsEmail_andClearsPasswordOnFailure() = runTest(dispatcher) {
        val vm = viewModel()
        val gate = CompletableDeferred<AuthResult>()
        repository.next = gate
        vm.fill(AuthField.Email to "  demo@demo.local ", AuthField.Password to "wrongpass")

        vm.submit()
        runCurrent()
        assertTrue(vm.form.value.loading)
        vm.submit() // ignored while in flight: no double submit
        runCurrent()
        assertEquals(1, repository.calls)
        assertEquals("demo@demo.local", repository.lastEmail)

        gate.complete(AuthResult.InvalidCredentials)
        advanceUntilIdle()

        with(vm.form.value) {
            assertFalse(loading)
            assertEquals("", password)
            assertEquals(R.string.error_invalid_credentials, generalError)
        }
    }

    @Test
    fun success_clearsForm() = runTest(dispatcher) {
        val vm = viewModel()
        repository.next = CompletableDeferred(AuthResult.Success(SESSION))
        vm.fill(AuthField.Email to "demo@demo.local", AuthField.Password to "demo12345")

        vm.submit()
        advanceUntilIdle()

        assertEquals(AuthFormState(), vm.form.value)
    }

    @Test
    fun signUp_serverFieldError_showsUnderField() = runTest(dispatcher) {
        val vm = viewModel()
        repository.next =
            CompletableDeferred(AuthResult.FieldErrors(mapOf("email" to "validation_not_unique")))
        vm.showMode(AuthMode.SignUp)
        vm.fill(
            AuthField.Email to "demo@demo.local",
            AuthField.Password to "password123",
            AuthField.PasswordConfirm to "password123"
        )

        vm.submit()
        advanceUntilIdle()

        with(vm.form.value) {
            assertEquals(mapOf(AuthField.Email to R.string.error_email_taken), fieldErrors)
            assertNull(generalError)
            assertEquals("", password)
            assertEquals("", passwordConfirm)
        }
    }

    @Test
    fun resultMessages() = runTest(dispatcher) {
        val expected = mapOf(
            AuthResult.RateLimited to R.string.error_rate_limited,
            AuthResult.SignupDisabled to R.string.error_signup_disabled,
            AuthResult.Network to R.string.error_network,
            AuthResult.Unknown to R.string.error_generic
        )
        val vm = viewModel()
        for ((result, message) in expected) {
            repository.next = CompletableDeferred(result)
            vm.fill(AuthField.Email to "a@b.co", AuthField.Password to "password123")
            vm.submit()
            advanceUntilIdle()
            assertEquals(message, vm.form.value.generalError)
        }
    }

    @Test
    fun switchingMode_clearsPasswords() = runTest(dispatcher) {
        val vm = viewModel()
        vm.fill(AuthField.Email to "a@b.co", AuthField.Password to "secret123")

        vm.showMode(AuthMode.SignUp)

        assertEquals("", vm.form.value.password)
        assertEquals("a@b.co", vm.form.value.email)
    }

    @Test
    fun sessionGate_loadingUntilRestored_thenSignedInOrOut() = runTest(dispatcher) {
        repository.store.session.value = SESSION
        val restore = CompletableDeferred<Unit>()
        repository.restoreGate = restore
        val vm = AuthViewModel(repository)
        runCurrent()
        assertEquals(SessionState.Loading, vm.sessionState.value)

        restore.complete(Unit)
        advanceUntilIdle()
        assertEquals(SessionState.SignedIn(SESSION), vm.sessionState.value)

        vm.logout()
        advanceUntilIdle()
        assertEquals(SessionState.SignedOut, vm.sessionState.value)
    }

    private class FakeAuthRepository : AuthRepository {
        val store = FakeSessionStore()
        override val session = store.session
        var next = CompletableDeferred<AuthResult>(AuthResult.Unknown)
        var restoreGate = CompletableDeferred(Unit)
        var calls = 0
        var lastEmail: String? = null

        override suspend fun signUp(email: String, password: String, name: String) = respond(email)
        override suspend fun login(email: String, password: String) = respond(email)

        private suspend fun respond(email: String): AuthResult {
            calls++
            lastEmail = email
            return next.await().also { if (it is AuthResult.Success) store.save(it.session) }
        }

        override suspend fun restoreSession(): AuthResult? {
            restoreGate.await()
            return null
        }

        override suspend fun logout() = store.clear()
    }

    private companion object {
        val SESSION = Session("tok", "qqw7x1psxmeuq72", "demo@demo.local", "Demo User")
    }
}
