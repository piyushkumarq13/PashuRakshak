package com.pashurakshak.app.ui.auth

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import com.pashurakshak.app.data.SessionManager
import com.pashurakshak.app.ui.components.AppSpacing
import com.pashurakshak.app.ui.components.AppTextField
import com.pashurakshak.app.ui.components.PrimaryButton

@Composable
fun ForgotPinScreen(
    onCompleted: (needsOnboarding: Boolean) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ForgotPinViewModel = viewModel { ForgotPinViewModel() },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(state.completed) {
        if (state.completed) {
            onCompleted(!SessionManager.profileComplete)
        }
    }

    val stepIndex = when (state.step) {
        ForgotPinUiState.Step.EMAIL -> 0
        ForgotPinUiState.Step.RESET -> 1
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
            title = "Reset your PIN",
            subtitle = if (state.step == ForgotPinUiState.Step.EMAIL) {
                "Step 1 of 2 — verify your email"
            } else {
                "Step 2 of 2 — choose a new PIN"
            },
        )
        Spacer(modifier = Modifier.height(AppSpacing.LG))
        AuthStepIndicator(currentStep = stepIndex, totalSteps = 2)
        Spacer(modifier = Modifier.height(AppSpacing.LG))

        AuthFormCard {
            when (state.step) {
                ForgotPinUiState.Step.EMAIL -> {
                    Text(
                        text = "Enter the email on your account — we'll send a 6-digit code.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    AppTextField(
                        value = state.email,
                        onValueChange = viewModel::onEmailChanged,
                        label = "Email",
                        enabled = !state.isLoading,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
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

                ForgotPinUiState.Step.RESET -> {
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
                    AppTextField(
                        value = state.newPin,
                        onValueChange = viewModel::onNewPinChanged,
                        label = "New 6-digit PIN",
                        enabled = !state.isLoading,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    )
                    AppTextField(
                        value = state.newPinConfirm,
                        onValueChange = viewModel::onNewPinConfirmChanged,
                        label = "Confirm new PIN",
                        enabled = !state.isLoading,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    )
                    state.error?.let { AuthErrorText(it) }
                    PrimaryButton(
                        text = "Reset PIN",
                        onClick = viewModel::resetPin,
                        enabled = state.canReset,
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
