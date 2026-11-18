package com.example.splendit.ui.auth

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.splendit.R
import com.example.splendit.ui.theme.SplenditTheme

private val PASSWORD_MASK = PasswordVisualTransformation()

/** Routes between the restore-session gate, login / sign-up, and the signed-in screen. */
@Composable
fun AuthGate(
    sessionState: SessionState,
    form: AuthFormState,
    onChange: (AuthField, String) -> Unit,
    onShowMode: (AuthMode) -> Unit,
    onSubmit: () -> Unit,
    onLogout: () -> Unit
) {
    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        val modifier = Modifier.padding(innerPadding)
        when (sessionState) {
            SessionState.Loading -> Box(
                modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
            is SessionState.SignedIn -> HomeScreen(sessionState.session.email, onLogout, modifier)
            SessionState.SignedOut -> {
                BackHandler(enabled = form.mode == AuthMode.SignUp) { onShowMode(AuthMode.Login) }
                AuthForm(form, onChange, onShowMode, onSubmit, modifier)
            }
        }
    }
}

/** Login and sign-up share one form; sign-up adds the name and confirm-password fields. */
@Composable
fun AuthForm(
    form: AuthFormState,
    onChange: (AuthField, String) -> Unit,
    onShowMode: (AuthMode) -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current
    val next = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) })
    val submit = KeyboardActions(onDone = {
        focusManager.clearFocus()
        onSubmit()
    })
    val signUp = form.mode == AuthMode.SignUp

    Column(
        modifier = modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = stringResource(if (signUp) R.string.signup_title else R.string.login_title),
            style = MaterialTheme.typography.headlineMedium
        )
        Spacer(Modifier.height(8.dp))

        if (signUp) {
            AuthTextField(
                value = form.name,
                onValueChange = { onChange(AuthField.Name, it) },
                label = R.string.field_name_optional,
                error = form.fieldErrors[AuthField.Name],
                enabled = !form.loading,
                contentType = ContentType.PersonFullName,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Words,
                    imeAction = ImeAction.Next
                ),
                keyboardActions = next
            )
        }
        AuthTextField(
            value = form.email,
            onValueChange = { onChange(AuthField.Email, it) },
            label = R.string.field_email,
            error = form.fieldErrors[AuthField.Email],
            enabled = !form.loading,
            contentType = if (signUp) {
                ContentType.EmailAddress + ContentType.NewUsername
            } else {
                ContentType.EmailAddress + ContentType.Username
            },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email,
                autoCorrectEnabled = false,
                imeAction = ImeAction.Next
            ),
            keyboardActions = next
        )
        PasswordField(
            value = form.password,
            onValueChange = { onChange(AuthField.Password, it) },
            label = R.string.field_password,
            error = form.fieldErrors[AuthField.Password],
            enabled = !form.loading,
            contentType = if (signUp) ContentType.NewPassword else ContentType.Password,
            imeAction = if (signUp) ImeAction.Next else ImeAction.Done,
            keyboardActions = if (signUp) next else submit
        )
        if (signUp) {
            PasswordField(
                value = form.passwordConfirm,
                onValueChange = { onChange(AuthField.PasswordConfirm, it) },
                label = R.string.field_password_confirm,
                error = form.fieldErrors[AuthField.PasswordConfirm],
                enabled = !form.loading,
                contentType = ContentType.NewPassword,
                imeAction = ImeAction.Done,
                keyboardActions = submit
            )
        }

        form.generalError?.let {
            Text(
                text = stringResource(it),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium
            )
        }

        Button(
            onClick = onSubmit,
            enabled = !form.loading,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (form.loading) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            } else {
                Text(
                    stringResource(
                        if (signUp) R.string.action_create_account else R.string.action_login
                    )
                )
            }
        }
        TextButton(
            onClick = { onShowMode(if (signUp) AuthMode.Login else AuthMode.SignUp) },
            enabled = !form.loading,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        ) {
            Text(stringResource(if (signUp) R.string.link_to_login else R.string.link_to_signup))
        }
    }
}

@Composable
private fun AuthTextField(
    value: String,
    onValueChange: (String) -> Unit,
    @StringRes label: Int,
    @StringRes error: Int?,
    enabled: Boolean,
    contentType: ContentType,
    keyboardOptions: KeyboardOptions,
    keyboardActions: KeyboardActions,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailingIcon: (@Composable () -> Unit)? = null
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(stringResource(label)) },
        isError = error != null,
        supportingText = error?.let { { Text(stringResource(it)) } },
        enabled = enabled,
        singleLine = true,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        visualTransformation = visualTransformation,
        trailingIcon = trailingIcon,
        modifier = Modifier
            .fillMaxWidth()
            .semantics { this.contentType = contentType }
    )
}

@Composable
private fun PasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    @StringRes label: Int,
    @StringRes error: Int?,
    enabled: Boolean,
    contentType: ContentType,
    imeAction: ImeAction,
    keyboardActions: KeyboardActions
) {
    var visible by rememberSaveable { mutableStateOf(false) }
    AuthTextField(
        value = value,
        onValueChange = onValueChange,
        label = label,
        error = error,
        enabled = enabled,
        contentType = contentType,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Password,
            autoCorrectEnabled = false,
            imeAction = imeAction
        ),
        keyboardActions = keyboardActions,
        visualTransformation = if (visible) VisualTransformation.None else PASSWORD_MASK,
        trailingIcon = {
            TextButton(onClick = { visible = !visible }) {
                Text(
                    stringResource(
                        if (visible) R.string.hide_password else R.string.show_password
                    )
                )
            }
        }
    )
}

@Composable
fun HomeScreen(email: String, onLogout: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(R.string.logged_in_as, email),
            style = MaterialTheme.typography.bodyLarge
        )
        Button(onClick = onLogout) {
            Text(stringResource(R.string.action_logout))
        }
    }
}

@Preview(showBackground = true)
@Composable
fun LoginPreview() {
    SplenditTheme {
        AuthForm(
            form = AuthFormState(
                email = "someone@example.com",
                generalError = R.string.error_invalid_credentials
            ),
            onChange = { _, _ -> },
            onShowMode = {},
            onSubmit = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
fun SignUpPreview() {
    SplenditTheme {
        AuthForm(
            form = AuthFormState(
                mode = AuthMode.SignUp,
                fieldErrors = mapOf(AuthField.Email to R.string.error_email_taken)
            ),
            onChange = { _, _ -> },
            onShowMode = {},
            onSubmit = {}
        )
    }
}
