package com.pashurakshak.app.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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

/**
 * Farmer registration: phone + email → OTP → profile + 6-digit PIN.
 * Completing this establishes a session and saves the profile to the backend.
 */
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

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Create your account",
            style = MaterialTheme.typography.headlineSmall,
        )

        when (state.step) {
            RegisterUiState.Step.CONTACT -> {
                Text(
                    text = "Step 1 of 3 — phone and email",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = state.phone,
                    onValueChange = viewModel::onPhoneChanged,
                    label = { Text("Phone number") },
                    prefix = { Text("+91 ") },
                    singleLine = true,
                    enabled = !state.isLoading,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = state.email,
                    onValueChange = viewModel::onEmailChanged,
                    label = { Text("Email") },
                    singleLine = true,
                    enabled = !state.isLoading,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    supportingText = { Text("We'll send a 6-digit code here") },
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

            RegisterUiState.Step.OTP -> {
                Text(
                    text = "Step 2 of 3 — enter the code",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
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
                state.error?.let { ErrorText(it) }
                Button(
                    onClick = viewModel::verifyOtp,
                    enabled = state.canVerifyOtp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                ) {
                    if (state.isLoading) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                    } else {
                        Text("Verify code", style = MaterialTheme.typography.titleMedium)
                    }
                }
                TextButton(onClick = viewModel::backToContact, enabled = !state.isLoading) {
                    Text("Change phone or email")
                }
            }

            RegisterUiState.Step.DETAILS -> {
                Text(
                    text = "Step 3 of 3 — your details",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = state.name,
                    onValueChange = viewModel::onNameChanged,
                    label = { Text("Full name") },
                    singleLine = true,
                    enabled = !state.isLoading,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = state.pin,
                    onValueChange = viewModel::onPinChanged,
                    label = { Text("Choose a 6-digit PIN") },
                    singleLine = true,
                    enabled = !state.isLoading,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = state.pinConfirm,
                    onValueChange = viewModel::onPinConfirmChanged,
                    label = { Text("Confirm PIN") },
                    singleLine = true,
                    enabled = !state.isLoading,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = state.animalCountText,
                    onValueChange = viewModel::onAnimalCountChanged,
                    label = { Text("Number of animals") },
                    singleLine = true,
                    enabled = !state.isLoading,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = state.village,
                    onValueChange = viewModel::onVillageChanged,
                    label = { Text("Village") },
                    singleLine = true,
                    enabled = !state.isLoading,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = state.pincode,
                    onValueChange = viewModel::onPincodeChanged,
                    label = { Text("Pincode") },
                    singleLine = true,
                    enabled = !state.isLoading,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    supportingText = { Text("6 digits — vets in your district see reports here") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Language:", style = MaterialTheme.typography.bodyMedium)
                    RadioButton(selected = state.language == "hi", onClick = { viewModel.onLanguageChanged("hi") })
                    Text("Hindi")
                    RadioButton(selected = state.language == "en", onClick = { viewModel.onLanguageChanged("en") })
                    Text("English")
                }
                state.error?.let { ErrorText(it) }
                Button(
                    onClick = viewModel::register,
                    enabled = state.canRegister,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                ) {
                    if (state.isLoading) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                    } else {
                        Text("Create account", style = MaterialTheme.typography.titleMedium)
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
