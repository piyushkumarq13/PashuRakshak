package com.pashurakshak.app.ui.auth

import androidx.compose.foundation.layout.Arrangement
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
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(horizontal = AppSpacing.Screen),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.height(56.dp))
        AuthBranding(
            title = "PashuRakshak",
            subtitle = if (isVetApp) "Veterinarian sign in" else "Sign in to your account",
        )
        Spacer(modifier = Modifier.height(AppSpacing.XL))
        AuthFormCard {
            AppTextField(
                value = state.phone,
                onValueChange = viewModel::onPhoneChanged,
                label = "Phone number",
                prefix = "+91 ",
                enabled = !state.isLoading,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                supportingText = "e.g. 98765 43210",
            )
            AppTextField(
                value = state.credential,
                onValueChange = viewModel::onCredentialChanged,
                label = if (isVetApp) "PIN or password" else "6-digit PIN",
                enabled = !state.isLoading,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            )
            state.error?.let { AuthErrorText(it) }
        }
        Spacer(modifier = Modifier.height(AppSpacing.XL))
        PrimaryButton(
            text = "Sign in",
            onClick = viewModel::submit,
            enabled = state.canSubmit,
            loading = state.isLoading,
        )
        Spacer(modifier = Modifier.height(AppSpacing.SM))
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
        Spacer(modifier = Modifier.height(AppSpacing.LG))
    }
}
