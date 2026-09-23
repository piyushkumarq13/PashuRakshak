package com.pashurakshak.app.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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

/**
 * Forgot PIN: email → OTP → new PIN. Completing this signs the user in
 * (the reset endpoints return a fresh session token).
 */
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

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Reset your PIN",
            style = MaterialTheme.typography.headlineSmall,
        )

        when (state.step) {
            ForgotPinUiState.Step.EMAIL -> {
                Text(
                    text = "Enter the email on your account — we'll send a 6-digit code.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = state.email,
                    onValueChange = viewModel::onEmailChanged,
                    label = { Text("Email") },
                    singleLine = true,
                    enabled = !state.isLoading,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier.fillMaxWidth(),
                )
                state.error?.let { ErrorText(it) }
                Button(
                    onClick = viewModel::sendOtp,
                    enabled = state.canSendOtp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                ) {
                    if (state.isLoading) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                    } else {
                        Text(
                            text = if (state.resendCooldown > 0) {
                                "Resend in ${state.resendCooldown}s"
                            } else {
                                "Send code"
                            },
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                }
            }

            ForgotPinUiState.Step.RESET -> {
                Text(
                    text = "Code sent to ${state.maskedEmail ?: state.email}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedTextField(
                    value = state.otpCode,
                    onValueChange = viewModel::onOtpChanged,
                    label = { Text("6-digit code") },
                    singleLine = true,
                    enabled = !state.isLoading,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = state.newPin,
                    onValueChange = viewModel::onNewPinChanged,
                    label = { Text("New 6-digit PIN") },
                    singleLine = true,
                    enabled = !state.isLoading,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = state.newPinConfirm,
                    onValueChange = viewModel::onNewPinConfirmChanged,
                    label = { Text("Confirm new PIN") },
                    singleLine = true,
                    enabled = !state.isLoading,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.fillMaxWidth(),
                )
                state.error?.let { ErrorText(it) }
                Button(
                    onClick = viewModel::resetPin,
                    enabled = state.canReset,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                ) {
                    if (state.isLoading) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                    } else {
                        Text("Reset PIN", style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }

        TextButton(onClick = onBack, enabled = !state.isLoading) {
            Text("Back to sign in")
        }
    }
}

@Composable
private fun ErrorText(message: String) {
    Text(
        text = message,
        color = MaterialTheme.colorScheme.error,
        style = MaterialTheme.typography.bodyMedium,
    )
}
