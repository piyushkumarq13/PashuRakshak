package com.pashurakshak.app.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pashurakshak.app.ui.components.AppDialog
import com.pashurakshak.app.ui.components.AppSpacing
import com.pashurakshak.app.ui.components.AppTextField
import com.pashurakshak.app.ui.components.FadeInContent
import com.pashurakshak.app.ui.components.LanguageSelector
import com.pashurakshak.app.ui.components.SectionCard
import com.pashurakshak.app.ui.theme.AppColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    onBack: () -> Unit,
    onLoggedOut: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ProfileViewModel = viewModel { ProfileViewModel() },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showLogoutDialog by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.load()
    }

    LaunchedEffect(state.notice) {
        state.notice?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearNotice()
        }
    }

    LaunchedEffect(state.error) {
        state.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    LaunchedEffect(state.loggedOut) {
        if (state.loggedOut) onLoggedOut()
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = {
            SnackbarHost(snackbarHostState, modifier = Modifier.padding(bottom = AppSpacing.FloatingBar))
        },
        topBar = {
            TopAppBar(
                title = { Text(if (state.editing) "Edit profile" else "My profile") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (!state.editing) {
                        IconButton(onClick = { viewModel.startEditing() }) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit details")
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = AppSpacing.Screen)
                .padding(bottom = AppSpacing.ListBottom),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.MD),
        ) {
            FadeInContent {
                ProfileAvatar(name = state.name, phone = state.phone)
            }

            FadeInContent(delayMillis = 40) {
                SectionCard(title = if (state.editing) "Edit your details" else "Account details") {
                    if (state.editing) {
                        Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.MD)) {
                            AppTextField(
                                value = state.name,
                                onValueChange = viewModel::onNameChanged,
                                label = "Full name",
                            )
                            AppTextField(
                                value = state.phone,
                                onValueChange = {},
                                label = "Phone (cannot be changed)",
                                enabled = false,
                            )
                            AppTextField(
                                value = state.email,
                                onValueChange = { if (!state.isVet) viewModel.onEmailChanged(it) },
                                label = if (state.isVet) "Email (cannot be changed)" else "Email",
                                enabled = !state.isVet,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                            )
                            if (!state.isVet) {
                                AppTextField(
                                    value = state.village,
                                    onValueChange = viewModel::onVillageChanged,
                                    label = "Village",
                                )
                                AppTextField(
                                    value = state.pincode,
                                    onValueChange = viewModel::onPincodeChanged,
                                    label = "Pincode",
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                )
                                Column {
                                    Text(
                                        text = "Number of animals",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(bottom = AppSpacing.XS),
                                    )
                                    Text(
                                        text = state.animalCountText.ifBlank { "0" },
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                    Text(
                                        text = "Counted from your animals list",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            Column {
                                Text(
                                    text = "Preferred language",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(bottom = AppSpacing.SM),
                                )
                                LanguageSelector(
                                    selected = state.language,
                                    onSelect = viewModel::onLanguageChanged,
                                )
                            }
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.MD)) {
                            ProfileRow(
                                icon = Icons.Default.Person,
                                label = "Name",
                                value = state.name.ifBlank { "Not set" },
                            )
                            ProfileRow(
                                icon = Icons.Default.Phone,
                                label = "Phone",
                                value = state.phone.ifBlank { "—" },
                                prefix = "+91 ",
                            )
                            ProfileRow(
                                icon = Icons.Default.Email,
                                label = "Email",
                                value = state.email.ifBlank { "Not set" },
                            )
                            if (!state.isVet) {
                                ProfileRow(
                                    icon = Icons.Default.LocationOn,
                                    label = "Village",
                                    value = state.village.ifBlank { "Not set" },
                                )
                                ProfileRow(
                                    icon = Icons.Default.Pets,
                                    label = "Animals",
                                    value = state.animalCountText.ifBlank { "0" },
                                )
                            }
                            ProfileRow(
                                icon = Icons.Default.Language,
                                label = "Language",
                                value = if (state.language == "en") "English" else "Hindi",
                            )
                        }
                    }
                }
            }

            if (state.editing) {
                FadeInContent(delayMillis = 60) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(AppSpacing.MD),
                    ) {
                        OutlinedButton(
                            onClick = { viewModel.cancelEditing() },
                            modifier = Modifier.weight(1f),
                            enabled = !state.isSaving,
                        ) {
                            Text("Cancel")
                        }
                        Button(
                            onClick = { viewModel.save() },
                            modifier = Modifier.weight(1f),
                            enabled = state.canSave && !state.isSaving,
                        ) {
                            if (state.isSaving) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                )
                            } else {
                                Icon(Icons.Default.Check, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Save")
                            }
                        }
                    }
                }
            } else {
                FadeInContent(delayMillis = 80) {
                    Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.SM)) {
                        OutlinedButton(
                            onClick = { viewModel.startEditing() },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = null)
                            Spacer(modifier = Modifier.width(AppSpacing.SM))
                            Text("Edit details")
                        }
                        OutlinedButton(
                            onClick = { showLogoutDialog = true },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.Logout,
                                contentDescription = null,
                                tint = AppColors.Danger,
                            )
                            Spacer(modifier = Modifier.width(AppSpacing.SM))
                            Text("Sign out", color = AppColors.Danger)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(AppSpacing.SM))
        }
    }

    if (showLogoutDialog) {
        AppDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = "Sign out?",
            message = "You will need your phone number and PIN to sign in again.",
            icon = Icons.AutoMirrored.Filled.Logout,
            danger = true,
            confirmText = "Sign out",
            onConfirm = {
                showLogoutDialog = false
                viewModel.logout()
            },
        )
    }
}

@Composable
private fun ProfileAvatar(name: String, phone: String) {
    val initials = name.trim()
        .split(Regex("\\s+"))
        .filter { it.isNotBlank() }
        .take(2)
        .map { it.first().uppercaseChar() }
        .joinToString("")
        .ifBlank { phone.take(2).uppercase() ?: "?" }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = AppSpacing.SM),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(CircleShape)
                .background(AppColors.SoftGreen),
            contentAlignment = Alignment.Center,
        ) {
            if (initials.isBlank() || initials == "?") {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    tint = AppColors.Primary,
                    modifier = Modifier.size(48.dp),
                )
            } else {
                Text(
                    text = initials,
                    style = MaterialTheme.typography.headlineMedium,
                    color = AppColors.Primary,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        Spacer(modifier = Modifier.height(AppSpacing.MD))
        Text(
            text = name.ifBlank { "Farmer" },
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        if (phone.isNotBlank()) {
            Text(
                text = "+91 $phone",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ProfileRow(
    icon: ImageVector,
    label: String,
    value: String,
    prefix: String = "",
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(AppColors.Primary.copy(alpha = 0.10f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = AppColors.Primary,
                modifier = Modifier.size(17.dp),
            )
        }
        Spacer(modifier = Modifier.width(AppSpacing.MD))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = prefix + value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
