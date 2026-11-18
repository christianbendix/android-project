package com.example.splendit.ui.auth

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.splendit.R
import com.example.splendit.SplenditApplication
import com.example.splendit.auth.AuthRepository
import com.example.splendit.auth.AuthResult
import com.example.splendit.auth.Session
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class AuthMode { Login, SignUp }

enum class AuthField { Name, Email, Password, PasswordConfirm }

data class AuthFormState(
    val mode: AuthMode = AuthMode.Login,
    val name: String = "",
    val email: String = "",
    val password: String = "",
    val passwordConfirm: String = "",
    val fieldErrors: Map<AuthField, Int> = emptyMap(),
    @StringRes val generalError: Int? = null,
    val loading: Boolean = false
)

sealed interface SessionState {
    data object Loading : SessionState
    data object SignedOut : SessionState
    data class SignedIn(val session: Session) : SessionState
}

class AuthViewModel(private val repository: AuthRepository) : ViewModel() {

    private val restored = MutableStateFlow(false)

    /** Drives top-level routing: loading gate, login/sign-up, or the signed-in screen. */
    val sessionState: StateFlow<SessionState> =
        combine(restored, repository.session) { restored, session ->
            when {
                !restored -> SessionState.Loading
                session == null -> SessionState.SignedOut
                else -> SessionState.SignedIn(session)
            }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, SessionState.Loading)

    private val _form = MutableStateFlow(AuthFormState())
    val form: StateFlow<AuthFormState> = _form.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                repository.restoreSession()
            } finally {
                restored.value = true
            }
        }
    }

    fun onChange(field: AuthField, value: String) = _form.update {
        val errors = it.fieldErrors - field
        when (field) {
            AuthField.Name -> it.copy(name = value, fieldErrors = errors)
            AuthField.Email -> it.copy(email = value, fieldErrors = errors)
            AuthField.Password -> it.copy(password = value, fieldErrors = errors)
            AuthField.PasswordConfirm -> it.copy(passwordConfirm = value, fieldErrors = errors)
        }
    }

    /** Switches between login and sign-up. Passwords and errors don't carry over. */
    fun showMode(mode: AuthMode) = _form.update {
        if (it.loading) it else AuthFormState(mode = mode, email = it.email)
    }

    fun submit() {
        val state = _form.value
        if (state.loading) return
        val errors = validate(state)
        if (errors.isNotEmpty()) {
            _form.value = state.copy(fieldErrors = errors, generalError = null)
            return
        }
        _form.value = state.copy(loading = true, fieldErrors = emptyMap(), generalError = null)
        viewModelScope.launch {
            val email = state.email.trim()
            val result = when (state.mode) {
                AuthMode.Login -> repository.login(email, state.password)
                AuthMode.SignUp -> repository.signUp(email, state.password, state.name.trim())
            }
            _form.update {
                val cleared = it.copy(loading = false, password = "", passwordConfirm = "")
                when (result) {
                    is AuthResult.Success -> AuthFormState()
                    is AuthResult.FieldErrors -> cleared.withServerErrors(result.codes)
                    else -> cleared.copy(generalError = messageFor(result))
                }
            }
        }
    }

    fun logout() {
        viewModelScope.launch { repository.logout() }
    }

    companion object {
        private const val MIN_PASSWORD = 8
        private const val MAX_PASSWORD = 72
        private const val MAX_NAME = 100
        private val EMAIL = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")

        val Factory = viewModelFactory {
            initializer {
                AuthViewModel((this[APPLICATION_KEY] as SplenditApplication).authRepository)
            }
        }

        private fun validate(state: AuthFormState): Map<AuthField, Int> = buildMap {
            if (!EMAIL.matches(
                    state.email.trim()
                )
            ) {
                put(AuthField.Email, R.string.error_email_invalid)
            }
            when {
                state.password.isEmpty() -> put(AuthField.Password, R.string.error_required)
                state.password.length < MIN_PASSWORD ->
                    put(AuthField.Password, R.string.error_password_too_short)
                state.password.length > MAX_PASSWORD ->
                    put(AuthField.Password, R.string.error_password_too_long)
            }
            if (state.mode == AuthMode.SignUp) {
                if (state.name.trim().length >
                    MAX_NAME
                ) {
                    put(AuthField.Name, R.string.error_name_too_long)
                }
                if (state.passwordConfirm != state.password) {
                    put(AuthField.PasswordConfirm, R.string.error_password_mismatch)
                }
            }
        }

        private fun AuthFormState.withServerErrors(codes: Map<String, String>): AuthFormState {
            val errors = codes.mapNotNull { (key, code) ->
                val field = when (key) {
                    "name" -> AuthField.Name
                    "email" -> AuthField.Email
                    "password" -> AuthField.Password
                    "passwordConfirm" -> AuthField.PasswordConfirm
                    else -> return@mapNotNull null
                }
                field to messageForCode(field, code)
            }.toMap()
            // Errors on fields the form doesn't show get the generic message.
            val general = if (errors.size < codes.size) R.string.error_generic else null
            return copy(fieldErrors = errors, generalError = general)
        }

        @StringRes
        private fun messageForCode(field: AuthField, code: String): Int = when (code) {
            "validation_not_unique" ->
                if (field ==
                    AuthField.Email
                ) {
                    R.string.error_email_taken
                } else {
                    R.string.error_invalid_value
                }
            "validation_required" -> R.string.error_required
            "validation_is_email", "validation_invalid_email" -> R.string.error_email_invalid
            "validation_min_text_constraint" -> R.string.error_password_too_short
            "validation_max_text_constraint" ->
                if (field ==
                    AuthField.Name
                ) {
                    R.string.error_name_too_long
                } else {
                    R.string.error_password_too_long
                }
            "validation_values_mismatch" -> R.string.error_password_mismatch
            else -> R.string.error_invalid_value
        }

        @StringRes
        private fun messageFor(result: AuthResult): Int = when (result) {
            AuthResult.InvalidCredentials -> R.string.error_invalid_credentials
            AuthResult.RateLimited -> R.string.error_rate_limited
            AuthResult.SignupDisabled -> R.string.error_signup_disabled
            AuthResult.Network -> R.string.error_network
            else -> R.string.error_generic
        }
    }
}
