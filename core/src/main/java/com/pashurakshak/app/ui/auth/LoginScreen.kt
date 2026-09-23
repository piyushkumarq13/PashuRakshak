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
 * Sign-in: phone + PIN (farmer app) or phone + PIN/password (vet app).
 * Vets register on the web — approved accounts only can sign in here.
 */
@Composable
fun LoginScreen(
    onLoggedIn: (needsOnboarding: Boolean) -> Unit,
    onGoRegister: () -> Unit,
    onGoForgotPin: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LoginViewModel = viewModel { LoginViewModel() },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val isVetApp = SessionManager.appRole == SessionManager.Role.VET

    LaunchedEffect(state.loggedIn) {
        if (state.loggedIn) {
            viewModel.consumedLoggedIn()
            onLoggedIn(!SessionManager.profileComplete)
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
            text = "PashuRakshak",
            style = MaterialTheme.typography.headlineLarge,
        )
        Text(
            text = if (isVetApp) "Veterinarian sign in" else "Sign in to your account",
            style = MaterialTheme.typography.bodyLarge,
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
            supportingText = { Text("e.g. 98765 43210") },
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = state.credential,
            onValueChange = viewModel::onCredentialChanged,
            label = { Text(if (isVetApp) "PIN or password" else "6-digit PIN") },
            singleLine = true,
            enabled = !state.isLoading,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth(),
        )
        state.error?.let { error ->
            Text(
                text = error,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Button(
            onClick = viewModel::submit,
            enabled = state.canSubmit,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
        ) {
            if (state.isLoading) {
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp,
                )
            } else {
                Text("Sign in", style = MaterialTheme.typography.titleMedium)
            }
        }
        if (!isVetApp) {
            TextButton(onClick = onGoRegister, enabled = !state.isLoading) {
                Text("New here? Create account")
            }
        } else {
            Text(
                text = "Not registered? Sign up on the vet registration website.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(onClick = onGoForgotPin, enabled = !state.isLoading) {
            Text(if (isVetApp) "Forgot PIN or password?" else "Forgot PIN?")
        }
    }
}
