package com.rpsonline.app.ui.auth

import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import com.rpsonline.app.BuildConfig
import com.rpsonline.app.R
import com.rpsonline.app.ui.components.AppUpdateDialogs
import com.rpsonline.app.ui.components.AutofillTextField
import com.rpsonline.app.ui.components.RpsLoadingColumn
import com.rpsonline.app.ui.components.excludeFromAutofill
import com.rpsonline.app.ui.components.rpsScreenPadding
import com.rpsonline.app.ui.home.HomeAppInfoFooter
import com.rpsonline.app.ui.util.NetworkUtils
import com.rpsonline.app.ui.util.findActivity
import com.rpsonline.app.viewmodel.AppUpdateViewModel
import com.rpsonline.app.viewmodel.EmailAuthMode
import com.rpsonline.app.viewmodel.SignInViewModel

@Composable
fun SignInScreen(
    onSignedIn: () -> Unit,
    onChangelog: () -> Unit = {},
    viewModel: SignInViewModel = viewModel(),
    updateViewModel: AppUpdateViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val updateState by updateViewModel.uiState.collectAsState()
    val context = LocalContext.current
    val activity = context.findActivity()

    LaunchedEffect(Unit) {
        updateViewModel.onScreenVisible(context)
    }

    LaunchedEffect(uiState.profile) {
        if (uiState.profile != null) {
            onSignedIn()
        }
    }

    AppUpdateDialogs(
        updateState = updateState,
        activity = activity,
        viewModel = updateViewModel,
    )

    if (uiState.profile != null) {
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .rpsScreenPadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.app_tagline),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(24.dp))

        if (uiState.isLoading) {
            SignInLoadingState(isRestoringSession = uiState.isRestoringSession)
        } else {
            if (uiState.isCheckingFirebase) {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Checking Firebase availability...",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(12.dp))
            } else if (!uiState.isFirebaseAvailable) {
                Text(
                    text = "Waiting for Firebase connection...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(12.dp))
            }
            AuthButtons(
                onGoogle = { viewModel.signInWithGoogle(context) },
                onGuest = viewModel::signInAnonymously,
                enabled = uiState.isFirebaseAvailable && !uiState.isCheckingFirebase,
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
                enabled = uiState.isFirebaseAvailable && !uiState.isCheckingFirebase,
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

        Spacer(modifier = Modifier.height(16.dp))
        HomeAppInfoFooter(
            versionName = updateState.versionName,
            updatesEnabled = BuildConfig.GITHUB_UPDATES_ENABLED,
            availableUpdate = updateState.availableUpdate,
            isCheckingForUpdate = updateState.isCheckingForUpdate,
            isDownloadingUpdate = updateState.isDownloadingUpdate,
            updateMessage = updateState.updateMessage,
            onCheckForUpdate = { updateViewModel.checkForUpdate(context) },
            onInstallUpdate = {
                activity?.let { updateViewModel.downloadAndInstallUpdate(it) }
                    ?: updateViewModel.showUpdatePrompt()
            },
            onVersionClick = onChangelog,
        )
    }
}

@Composable
private fun SignInLoadingState(isRestoringSession: Boolean) {
    val context = LocalContext.current
    var isOnline by remember { mutableStateOf(NetworkUtils.isOnline(context)) }

    LaunchedEffect(Unit) {
        while (true) {
            isOnline = NetworkUtils.isOnline(context)
            delay(2_000)
        }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        RpsLoadingColumn(
            message = if (isRestoringSession) stringResource(R.string.restoring_session) else stringResource(R.string.signing_in),
        )
        if (!isOnline) {
            Text(
                text = stringResource(R.string.no_internet_try_again),
                style = MaterialTheme.typography.bodyMedium,
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
    enabled: Boolean,
) {
    Button(
        onClick = onGoogle,
        modifier = Modifier.fillMaxWidth(),
        enabled = enabled,
    ) {
        Text(stringResource(R.string.sign_in_with_google))
    }
    Spacer(modifier = Modifier.height(8.dp))
    OutlinedButton(
        onClick = onGuest,
        modifier = Modifier.fillMaxWidth(),
        enabled = enabled,
    ) {
        Text(stringResource(R.string.continue_as_guest))
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
    enabled: Boolean,
) {
    Text(
        text = stringResource(R.string.email),
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
            enabled = enabled,
        ) {
            Text(stringResource(R.string.sign_in))
        }
        SegmentedButton(
            selected = mode == EmailAuthMode.REGISTER,
            onClick = { onModeChange(EmailAuthMode.REGISTER) },
            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
            modifier = Modifier.weight(1f),
            enabled = enabled,
        ) {
            Text(stringResource(R.string.register))
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
        label = stringResource(R.string.email),
        autofillHints = emailHints,
        imeAction = EditorInfo.IME_ACTION_NEXT,
        enabled = enabled,
    )
    Spacer(modifier = Modifier.height(12.dp))
    AutofillTextField(
        value = password,
        onValueChange = onPasswordChange,
        label = stringResource(R.string.password),
        autofillHints = passwordHints,
        isPassword = true,
        imeAction = if (isRegister) EditorInfo.IME_ACTION_NEXT else EditorInfo.IME_ACTION_DONE,
        onImeAction = { submitFromKeyboard() },
        enabled = enabled,
    )

    if (isRegister) {
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(
            value = displayName,
            onValueChange = onDisplayNameChange,
            label = { Text(stringResource(R.string.display_name_optional)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                imeAction = ImeAction.Done,
                autoCorrectEnabled = false,
            ),
            keyboardActions = KeyboardActions(
                onDone = { submitFromKeyboard() },
            ),
            enabled = enabled,
            modifier = Modifier
                .fillMaxWidth()
                .excludeFromAutofill(),
        )
    }

    Spacer(modifier = Modifier.height(12.dp))
    Button(
        onClick = onSubmit,
        modifier = Modifier.fillMaxWidth(),
        enabled = enabled,
    ) {
        Text(
            if (mode == EmailAuthMode.SIGN_IN) {
                stringResource(R.string.sign_in_with_email)
            } else {
                stringResource(R.string.create_account)
            },
        )
    }
}
