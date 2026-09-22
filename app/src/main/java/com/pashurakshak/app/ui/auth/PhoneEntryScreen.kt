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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pashurakshak.app.MainActivity

/**
 * Step 1 of Firebase Phone Auth: phone number entry → send OTP.
 * On instant verification the user skips the OTP screen entirely.
 */
@Composable
fun PhoneEntryScreen(
    onCodeSent: (verificationId: String, phoneE164: String) -> Unit,
    onVerified: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PhoneEntryViewModel = viewModel { PhoneEntryViewModel() },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(state.verificationId) {
        state.verificationId?.let { verificationId ->
            state.e164?.let { phone -> onCodeSent(verificationId, phone) }
            viewModel.consumedVerificationId()
        }
    }

    LaunchedEffect(state.autoSignedIn) {
        if (state.autoSignedIn) {
            onVerified()
            viewModel.consumedAutoSignIn()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "PashuRakshak",
            style = MaterialTheme.typography.headlineLarge,
        )
        Text(
            text = "Sign in with your phone number",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = state.digits,
            onValueChange = viewModel::onDigitsChanged,
            label = { Text("Phone number") },
            prefix = { if (!state.digits.startsWith("+")) Text("+91 ") },
            singleLine = true,
            enabled = !state.isLoading,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            supportingText = { Text("e.g. 98765 43210 — or enter a full number with country code") },
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
            onClick = {
                val activity = context as? MainActivity
                if (activity != null) viewModel.sendCode(activity)
            },
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
                Text("Send OTP", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}
