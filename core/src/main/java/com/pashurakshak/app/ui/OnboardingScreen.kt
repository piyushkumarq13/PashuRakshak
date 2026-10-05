package com.pashurakshak.app.ui

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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pashurakshak.app.ui.auth.AuthBranding
import com.pashurakshak.app.ui.auth.AuthErrorText
import com.pashurakshak.app.ui.auth.AuthFormCard
import com.pashurakshak.app.ui.components.AppSpacing
import com.pashurakshak.app.ui.components.AppTextField
import com.pashurakshak.app.ui.components.LanguageSelector
import com.pashurakshak.app.ui.components.PrimaryButton

@Composable
fun OnboardingScreen(
    onSubmitComplete: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: OnboardingViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(state.isSubmitted) {
        if (state.isSubmitted) {
            viewModel.resetSubmitted()
            onSubmitComplete()
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
        Spacer(modifier = Modifier.height(48.dp))
        AuthBranding(
            title = "Welcome, Farmer!",
            subtitle = "Tell us about yourself so we can personalize your experience",
        )
        Spacer(modifier = Modifier.height(AppSpacing.XL))

        AuthFormCard {
            AppTextField(
                value = state.name,
                onValueChange = viewModel::onNameChange,
                label = "Name",
            )
            AppTextField(
                value = state.email,
                onValueChange = viewModel::onEmailChange,
                label = "Email",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            )
            AppTextField(
                value = state.animalCountText,
                onValueChange = viewModel::onAnimalCountChange,
                label = "Number of Animals",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
            AppTextField(
                value = state.village,
                onValueChange = viewModel::onVillageChange,
                label = "Village",
            )
            AppTextField(
                value = state.pincode,
                onValueChange = viewModel::onPincodeChange,
                label = "Pincode",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
            Column {
                Text(
                    text = "Preferred language",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = AppSpacing.SM),
                )
                LanguageSelector(
                    selected = state.language,
                    onSelect = viewModel::onLanguageChange,
                )
            }
            state.error?.let { AuthErrorText(it) }
            PrimaryButton(
                text = "Complete Onboarding",
                onClick = { viewModel.submit() },
                loading = state.isLoading,
            )
        }
        Spacer(modifier = Modifier.height(AppSpacing.XL))
    }
}
