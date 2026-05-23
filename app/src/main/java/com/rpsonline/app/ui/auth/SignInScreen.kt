package com.rpsonline.app.ui.auth

import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rpsonline.app.ui.components.AutofillTextField
import com.rpsonline.app.ui.components.excludeFromAutofill
import com.rpsonline.app.ui.components.rpsScreenPadding
import com.rpsonline.app.viewmodel.EmailAuthMode
import com.rpsonline.app.viewmodel.SignInViewModel

@Composable
fun SignInScreen(
    onSignedIn: () -> Unit,
    viewModel: SignInViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(uiState.profile) {
        if (uiState.profile != null) {
            onSignedIn()
        }
    }

    if (uiState.profile != null) {
        return
    }

    Column(
        modifier = Modifier
            .rpsScreenPadding()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "RPS Online",
            style = MaterialTheme.typography.headlineLarge,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Ranked rock-paper-scissors.\nBest of 3. ELO matchmaking.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(24.dp))

        if (uiState.isLoading) {
            CircularProgressIndicator()
        } else {
            AuthButtons(
                onGoogle = { viewModel.signInWithGoogle(context) },
                onGuest = viewModel::signInAnonymously,
            )
            Spacer(modifier = Modifier.height(24.dp))
            HorizontalDivider(modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(24.dp))
            EmailAuthSection(
                email = uiState.email,
                password = uiState.password,
                displayName = uiState.displayName,
                mode = uiState.emailMode,
                onEmailChange = viewModel::updateEmail,
                onPasswordChange = viewModel::updatePassword,
                onDisplayNameChange = viewModel::updateDisplayName,
                onModeChange = viewModel::setEmailMode,
                onSubmit = { viewModel.submitEmailAuth(context) },
            )
        }

        uiState.error?.let { error ->
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = error,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun AuthButtons(
    onGoogle: () -> Unit,
    onGuest: () -> Unit,
) {
    Button(
        onClick = onGoogle,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text("Sign in with Google")
    }
    Spacer(modifier = Modifier.height(8.dp))
    OutlinedButton(
        onClick = onGuest,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text("Continue as guest")
    }
}

@Composable
private fun EmailAuthSection(
    email: String,
    password: String,
    displayName: String,
    mode: EmailAuthMode,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onDisplayNameChange: (String) -> Unit,
    onModeChange: (EmailAuthMode) -> Unit,
    onSubmit: () -> Unit,
) {
    Text(
        text = "Email",
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(modifier = Modifier.height(8.dp))

    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        SegmentedButton(
            selected = mode == EmailAuthMode.SIGN_IN,
            onClick = { onModeChange(EmailAuthMode.SIGN_IN) },
            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
            modifier = Modifier.weight(1f),
        ) {
            Text("Sign in")
        }
        SegmentedButton(
            selected = mode == EmailAuthMode.REGISTER,
            onClick = { onModeChange(EmailAuthMode.REGISTER) },
            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
            modifier = Modifier.weight(1f),
        ) {
            Text("Register")
        }
    }

    Spacer(modifier = Modifier.height(12.dp))

    val keyboardController = LocalSoftwareKeyboardController.current
    val isRegister = mode == EmailAuthMode.REGISTER
    val submitFromKeyboard: () -> Unit = {
        keyboardController?.hide()
        onSubmit()
    }

    val emailHints = if (isRegister) {
        arrayOf(View.AUTOFILL_HINT_USERNAME)
    } else {
        arrayOf(View.AUTOFILL_HINT_EMAIL_ADDRESS)
    }
    val passwordHints = if (isRegister) {
        arrayOf("newPassword")
    } else {
        arrayOf(View.AUTOFILL_HINT_PASSWORD)
    }

    AutofillTextField(
        value = email,
        onValueChange = onEmailChange,
        label = "Email",
        autofillHints = emailHints,
        imeAction = EditorInfo.IME_ACTION_NEXT,
    )
    Spacer(modifier = Modifier.height(12.dp))
    AutofillTextField(
        value = password,
        onValueChange = onPasswordChange,
        label = "Password",
        autofillHints = passwordHints,
        isPassword = true,
        imeAction = if (isRegister) EditorInfo.IME_ACTION_NEXT else EditorInfo.IME_ACTION_DONE,
        onImeAction = { submitFromKeyboard() },
    )

    if (isRegister) {
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(
            value = displayName,
            onValueChange = onDisplayNameChange,
            label = { Text("Display name (optional)") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                imeAction = ImeAction.Done,
                autoCorrectEnabled = false,
            ),
            keyboardActions = KeyboardActions(
                onDone = { submitFromKeyboard() },
            ),
            modifier = Modifier
                .fillMaxWidth()
                .excludeFromAutofill(),
        )
    }

    Spacer(modifier = Modifier.height(12.dp))
    Button(
        onClick = onSubmit,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(if (mode == EmailAuthMode.SIGN_IN) "Sign in with email" else "Create account")
    }
}
