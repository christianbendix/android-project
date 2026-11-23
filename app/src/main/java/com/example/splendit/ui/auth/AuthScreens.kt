package com.example.splendit.ui.auth

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Brush
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
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.splendit.R
import com.example.splendit.domain.Country
import com.example.splendit.ui.components.PrimaryButton
import com.example.splendit.ui.components.SwIcon
import com.example.splendit.ui.components.SwTextField
import com.example.splendit.ui.theme.SplenditTheme
import com.example.splendit.ui.theme.Sw

private val PASSWORD_MASK = PasswordVisualTransformation()
private val FIELD_HEIGHT = 48.dp

/** Shows a loading state while the session is restored, then login / sign-up or [signedIn]. */
@Composable
fun AuthGate(
    viewModel: AuthViewModel = viewModel(factory = AuthViewModel.Factory),
    signedIn: @Composable () -> Unit
) {
    val sessionState by viewModel.sessionState.collectAsStateWithLifecycle()
    val form by viewModel.form.collectAsStateWithLifecycle()
    when (sessionState) {
        SessionState.Loading -> Box(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = Sw.colors.accent400)
        }
        is SessionState.SignedIn -> signedIn()
        SessionState.SignedOut -> {
            BackHandler(enabled = form.mode == AuthMode.SignUp) {
                viewModel.showMode(AuthMode.Login)
            }
            AuthContent(
                form = form,
                onChange = viewModel::onChange,
                onCountryChange = viewModel::onCountryChange,
                onShowMode = viewModel::showMode,
                onSubmit = viewModel::submit
            )
        }
    }
}

/** Login and sign-up share one form; sign-up adds the name, phone and confirm-password fields. */
@Composable
fun AuthContent(
    form: AuthFormState,
    onChange: (AuthField, String) -> Unit,
    onCountryChange: (Country) -> Unit,
    onShowMode: (AuthMode) -> Unit,
    onSubmit: () -> Unit
) {
    val focusManager = LocalFocusManager.current
    val next = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) })
    val submit = KeyboardActions(onDone = {
        focusManager.clearFocus()
        onSubmit()
    })
    val signUp = form.mode == AuthMode.SignUp

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .systemBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Header(
            title = if (signUp) R.string.signup_title else R.string.login_title,
            subtitle = if (signUp) R.string.signup_subtitle else R.string.login_subtitle
        )

        if (signUp) {
            Field(R.string.field_name_optional, form.fieldErrors[AuthField.Name]) { modifier ->
                SwTextField(
                    value = form.name,
                    onValueChange = { onChange(AuthField.Name, it) },
                    placeholder = stringResource(R.string.placeholder_name),
                    enabled = !form.loading,
                    isError = form.fieldErrors[AuthField.Name] != null,
                    height = FIELD_HEIGHT,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Words,
                        imeAction = ImeAction.Next
                    ),
                    keyboardActions = next,
                    modifier = modifier.semantics { contentType = ContentType.PersonFullName }
                )
            }
        }
        Field(R.string.field_email, form.fieldErrors[AuthField.Email]) { modifier ->
            SwTextField(
                value = form.email,
                onValueChange = { onChange(AuthField.Email, it) },
                placeholder = stringResource(R.string.placeholder_email),
                enabled = !form.loading,
                isError = form.fieldErrors[AuthField.Email] != null,
                height = FIELD_HEIGHT,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    autoCorrectEnabled = false,
                    imeAction = ImeAction.Next
                ),
                keyboardActions = next,
                modifier = modifier.semantics {
                    contentType = if (signUp) {
                        ContentType.EmailAddress + ContentType.NewUsername
                    } else {
                        ContentType.EmailAddress + ContentType.Username
                    }
                }
            )
        }
        if (signUp) {
            Field(R.string.field_phone, form.fieldErrors[AuthField.Phone]) { modifier ->
                Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CountryPicker(form.country, enabled = !form.loading, onSelect = onCountryChange)
                    SwTextField(
                        value = form.phone,
                        onValueChange = { onChange(AuthField.Phone, it) },
                        placeholder = stringResource(R.string.placeholder_phone),
                        enabled = !form.loading,
                        isError = form.fieldErrors[AuthField.Phone] != null,
                        height = FIELD_HEIGHT,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Phone,
                            imeAction = ImeAction.Next
                        ),
                        keyboardActions = next,
                        modifier = Modifier
                            .weight(1f)
                            .semantics { contentType = ContentType.PhoneNumberNational }
                    )
                }
            }
        }
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
            Text(stringResource(it), fontSize = 13.sp, color = MaterialTheme.colorScheme.error)
        }

        PrimaryButton(
            text = stringResource(
                if (signUp) R.string.action_create_account else R.string.action_login
            ),
            onClick = onSubmit,
            loading = form.loading
        )
        TextButton(
            onClick = { onShowMode(if (signUp) AuthMode.Login else AuthMode.SignUp) },
            enabled = !form.loading,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        ) {
            Text(
                stringResource(if (signUp) R.string.link_to_login else R.string.link_to_signup),
                fontSize = 13.5.sp,
                color = Sw.colors.accent
            )
        }
    }
}

/** The wheel mark from the bottom bar's create button, over the screen title. */
@Composable
private fun Header(@StringRes title: Int, @StringRes subtitle: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
            Modifier
                .padding(bottom = 14.dp)
                .size(56.dp)
                .shadow(6.dp, CircleShape, ambientColor = Sw.colors.accent400)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        listOf(Sw.colors.accent600, Sw.colors.accent400, Sw.colors.accent300)
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            SwIcon(R.drawable.ic_wheel, size = 28.dp, tint = MaterialTheme.colorScheme.surface)
        }
        Text(stringResource(title), style = MaterialTheme.typography.titleLarge)
        Text(stringResource(subtitle), fontSize = 13.sp, color = Sw.colors.neutral500)
    }
}

/** Small grey label above the input, as in the create-group form, and the error below it. */
@Composable
private fun Field(
    @StringRes label: Int,
    @StringRes error: Int?,
    input: @Composable (Modifier) -> Unit
) {
    Column {
        Text(stringResource(label), fontSize = 12.sp, color = Sw.colors.neutral400)
        input(Modifier.fillMaxWidth().padding(top = 5.dp))
        if (error != null) {
            Text(
                stringResource(error),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 4.dp, start = 4.dp)
            )
        }
    }
}

/** Flag and dialling code; tapping it lists the supported countries. */
@Composable
private fun CountryPicker(country: Country, enabled: Boolean, onSelect: (Country) -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    val shape = RoundedCornerShape(12.dp)
    val description = stringResource(R.string.choose_country)
    Box {
        Text(
            "${country.flag} ${country.dialCode}",
            fontSize = 14.sp,
            modifier = Modifier
                .height(FIELD_HEIGHT)
                .shadow(0.5.dp, shape)
                .clip(shape)
                .background(MaterialTheme.colorScheme.surface)
                .clickable(enabled = enabled, onClickLabel = description) { open = true }
                .padding(horizontal = 12.dp, vertical = 14.dp)
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            Country.entries.forEach { c ->
                DropdownMenuItem(
                    text = { Text("${c.flag}  ${c.label}  ${c.dialCode}") },
                    onClick = {
                        onSelect(c)
                        open = false
                    }
                )
            }
        }
    }
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
    Field(label, error) { modifier ->
        SwTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = "",
            enabled = enabled,
            isError = error != null,
            height = FIELD_HEIGHT,
            visualTransformation = if (visible) VisualTransformation.None else PASSWORD_MASK,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                autoCorrectEnabled = false,
                imeAction = imeAction
            ),
            keyboardActions = keyboardActions,
            modifier = modifier.semantics { this.contentType = contentType },
            trailing = {
                Text(
                    stringResource(if (visible) R.string.hide_password else R.string.show_password),
                    fontSize = 13.sp,
                    color = Sw.colors.accent,
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable { visible = !visible }
                        .padding(horizontal = 6.dp, vertical = 4.dp)
                )
            }
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun LoginPreview() {
    SplenditTheme {
        AuthContent(
            form = AuthFormState(
                email = "someone@example.com",
                generalError = R.string.error_invalid_credentials
            ),
            onChange = { _, _ -> },
            onCountryChange = {},
            onShowMode = {},
            onSubmit = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun SignUpPreview() {
    SplenditTheme {
        AuthContent(
            form = AuthFormState(
                mode = AuthMode.SignUp,
                fieldErrors = mapOf(AuthField.Email to R.string.error_email_taken)
            ),
            onChange = { _, _ -> },
            onCountryChange = {},
            onShowMode = {},
            onSubmit = {}
        )
    }
}
