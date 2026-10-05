package com.pashurakshak.app.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pashurakshak.app.ui.components.AppSpacing
import com.pashurakshak.app.ui.components.AppTextField
import com.pashurakshak.app.ui.components.LanguageSelector
import com.pashurakshak.app.ui.components.PrimaryButton

@Composable
fun RegisterScreen(
    onRegistered: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RegisterViewModel = viewModel { RegisterViewModel() },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(state.registered) {
        if (state.registered) onRegistered()
    }

    val stepIndex = when (state.step) {
        RegisterUiState.Step.CONTACT -> 0
        RegisterUiState.Step.OTP -> 1
        RegisterUiState.Step.DETAILS -> 2
    }
    val stepTitle = when (state.step) {
        RegisterUiState.Step.CONTACT -> "Phone and email"
        RegisterUiState.Step.OTP -> "Enter the code"
        RegisterUiState.Step.DETAILS -> "Your details"
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(horizontal = AppSpacing.Screen),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.height(48.dp))
        AuthBranding(
            title = "Create your account",
            subtitle = "Step ${stepIndex + 1} of 3 — $stepTitle",
        )
        Spacer(modifier = Modifier.height(AppSpacing.LG))
        AuthStepIndicator(currentStep = stepIndex, totalSteps = 3)
        Spacer(modifier = Modifier.height(AppSpacing.LG))

        AuthFormCard {
            when (state.step) {
                RegisterUiState.Step.CONTACT -> {
                    AppTextField(
                        value = state.phone,
                        onValueChange = viewModel::onPhoneChanged,
                        label = "Phone number",
                        prefix = "+91 ",
                        enabled = !state.isLoading,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    )
                    AppTextField(
                        value = state.email,
                        onValueChange = viewModel::onEmailChanged,
                        label = "Email",
                        enabled = !state.isLoading,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        supportingText = "We'll send a 6-digit code here",
                    )
                    state.error?.let { AuthErrorText(it) }
                    PrimaryButton(
                        text = if (state.resendCooldown > 0) {
                            "Resend in ${state.resendCooldown}s"
                        } else {
                            "Send code"
                        },
                        onClick = viewModel::sendOtp,
                        enabled = state.canSendOtp,
                        loading = state.isLoading,
                    )
                }

                RegisterUiState.Step.OTP -> {
                    Text(
                        text = "Code sent to ${state.maskedEmail ?: state.email}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    AppTextField(
                        value = state.otpCode,
                        onValueChange = viewModel::onOtpChanged,
                        label = "6-digit code",
                        enabled = !state.isLoading,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    )
                    state.error?.let { AuthErrorText(it) }
                    PrimaryButton(
                        text = "Verify code",
                        onClick = viewModel::verifyOtp,
                        enabled = state.canVerifyOtp,
                        loading = state.isLoading,
                    )
                    TextButton(
                        onClick = viewModel::backToContact,
                        enabled = !state.isLoading,
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    ) {
                        Text("Change phone or email")
                    }
                }

                RegisterUiState.Step.DETAILS -> {
                    AppTextField(
                        value = state.name,
                        onValueChange = viewModel::onNameChanged,
                        label = "Full name",
                        enabled = !state.isLoading,
                    )
                    AppTextField(
                        value = state.pin,
                        onValueChange = viewModel::onPinChanged,
                        label = "Choose a 6-digit PIN",
                        enabled = !state.isLoading,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    )
                    AppTextField(
                        value = state.pinConfirm,
                        onValueChange = viewModel::onPinConfirmChanged,
                        label = "Confirm PIN",
                        enabled = !state.isLoading,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    )
                    AppTextField(
                        value = state.animalCountText,
                        onValueChange = viewModel::onAnimalCountChanged,
                        label = "Number of animals",
                        enabled = !state.isLoading,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                    AppTextField(
                        value = state.village,
                        onValueChange = viewModel::onVillageChanged,
                        label = "Village",
                        enabled = !state.isLoading,
                    )
                    AppTextField(
                        value = state.pincode,
                        onValueChange = viewModel::onPincodeChanged,
                        label = "Pincode",
                        enabled = !state.isLoading,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        supportingText = "6 digits — vets in your district see reports here",
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.SM)) {
                        Text(
                            text = "Preferred language",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        LanguageSelector(
                            selected = state.language,
                            onSelect = viewModel::onLanguageChanged,
                        )
                    }
                    state.error?.let { AuthErrorText(it) }
                    PrimaryButton(
                        text = "Create account",
                        onClick = viewModel::register,
                        enabled = state.canRegister,
                        loading = state.isLoading,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(AppSpacing.LG))
        TextButton(onClick = onBack, enabled = !state.isLoading) {
            Text("Back to sign in")
        }
        Spacer(modifier = Modifier.height(AppSpacing.LG))
    }
}
