package com.pashurakshak.app.ui.farmer

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.navigation.NavController
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.rememberNavController
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.pashurakshak.app.data.SessionManager
import com.pashurakshak.app.data.local.SymptomReport
import com.pashurakshak.app.di.ServiceLocator
import com.pashurakshak.app.navigation.Screen
import com.pashurakshak.app.ui.components.AppDialog
import com.pashurakshak.app.ui.components.AppDropdownField
import com.pashurakshak.app.ui.components.AppSpacing
import com.pashurakshak.app.ui.components.AppTextField
import com.pashurakshak.app.ui.components.FadeInContent
import androidx.compose.material3.Scaffold
import com.pashurakshak.app.ui.components.PrimaryButton
import com.pashurakshak.app.ui.components.ReportPhoto
import com.pashurakshak.app.ui.components.ScreenHeader
import com.pashurakshak.app.ui.components.SectionCard
import com.pashurakshak.app.ui.components.StatusPill
import com.pashurakshak.app.ui.theme.AppColors
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ReportSickAnimalScreen(
    onSubmitted: (String?, String?) -> Unit,
    modifier: Modifier = Modifier,
    navController: NavController = rememberNavController(),
    viewModel: ReportSickAnimalViewModel = viewModel {
        ReportSickAnimalViewModel(
            animalRepository = ServiceLocator.animalRepository,
            reportRepository = ServiceLocator.reportRepository,
            alertRepository = ServiceLocator.alertRepository,
            farmerId = SessionManager.farmerId,
        )
    },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }

    var pendingPhotoPath by remember { mutableStateOf<String?>(null) }
    var cameraAttempt by remember { mutableIntStateOf(0) }
    var showAiDialog by remember { mutableStateOf(false) }
    var showPhotoSourceDialog by remember { mutableStateOf(false) }

    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture(),
    ) { success ->
        val path = pendingPhotoPath
        if (success && path != null) {
            viewModel.onPhotoAdded(path)
        } else {
            pendingPhotoPath = null
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent(),
    ) { uri ->
        if (uri != null) copyPickedPhoto(context, uri, viewModel)
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            launchCamera(context, pendingPhotoPath, cameraLauncher)
        } else {
            pendingPhotoPath = null
            cameraAttempt++
            viewModel.onLocationError("Camera permission denied — photo skipped")
        }
    }

    fun startCameraCapture() {
        val photosDir = File(context.filesDir, "photos").apply { mkdirs() }
        val file = File(photosDir, "report_${System.currentTimeMillis()}.jpg")
        runCatching { file.createNewFile() }
        pendingPhotoPath = file.absolutePath
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA,
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            launchCamera(context, pendingPhotoPath, cameraLauncher)
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            fetchLocation(fusedLocationClient, viewModel)
        } else {
            viewModel.onLocationError("Location permission denied — enter latitude/longitude manually")
        }
    }

    LaunchedEffect(state.submitted) {
        if (state.submitted) {
            showAiDialog = true
        }
    }
    LaunchedEffect(state.error) {
        state.error?.let { error ->
            snackbarHostState.showSnackbar(error)
            viewModel.clearError()
        }
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = {
            SnackbarHost(snackbarHostState, modifier = Modifier.padding(bottom = AppSpacing.FloatingBar))
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(top = AppSpacing.SM)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.LG),
        ) {
            ScreenHeader(
                title = "Report Sick Animal",
                subtitle = "Describe symptoms — we'll estimate risk and alert a vet.",
            )

            FadeInContent {
                SectionCard(
                    title = "Animal",
                    modifier = Modifier.padding(horizontal = AppSpacing.Screen),
                ) {
                    if (state.isLoadingAnimals) {
                        CircularProgressIndicator(modifier = Modifier.size(28.dp))
                    } else if (state.animals.isEmpty()) {
                        Text(
                            text = "No animals yet — add one in My Animals first.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        AppDropdownField(
                            label = "Select animal",
                            options = state.animals,
                            selected = state.animals.find { it.id == state.selectedAnimalId },
                            optionLabel = { "${it.name} (${it.species})" },
                            onSelect = { viewModel.onAnimalSelected(it.id) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }

            FadeInContent(delayMillis = 40) {
                SectionCard(
                    title = "Symptoms",
                    modifier = Modifier.padding(horizontal = AppSpacing.Screen),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.MD)) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(AppSpacing.SM),
                            verticalArrangement = Arrangement.spacedBy(AppSpacing.SM),
                        ) {
                            Symptom.entries.forEach { symptom ->
                                val selected = symptom in state.selectedSymptoms
                                FilterChip(
                                    selected = selected,
                                    onClick = { viewModel.onSymptomToggled(symptom) },
                                    label = { Text(symptom.label) },
                                    shape = MaterialTheme.shapes.small,
                                    leadingIcon = if (selected) {
                                        {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp),
                                            )
                                        }
                                    } else {
                                        null
                                    },
                                    colors = FilterChipDefaults.filterChipColors(
                                        containerColor = MaterialTheme.colorScheme.surface,
                                        labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                                    ),
                                    border = FilterChipDefaults.filterChipBorder(
                                        enabled = true,
                                        selected = selected,
                                        borderColor = MaterialTheme.colorScheme.outline,
                                        selectedBorderColor = MaterialTheme.colorScheme.primary,
                                    ),
                                )
                            }
                        }
                        Text(
                            text = if (state.selectedSymptoms.isEmpty()) {
                                "Select at least one symptom you can see"
                            } else {
                                "${state.selectedSymptoms.size} selected"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )

                        if (state.hasOtherSelected) {
                            AppTextField(
                                value = state.customDiseaseText,
                                onValueChange = viewModel::onCustomDiseaseChanged,
                                label = "Disease name",
                                supportingText = if (state.customDiseaseText.isBlank()) {
                                    "Required when you pick Other"
                                } else {
                                    null
                                },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }

            FadeInContent(delayMillis = 50) {
                SectionCard(
                    title = "Disease details",
                    modifier = Modifier.padding(horizontal = AppSpacing.Screen),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.MD)) {
                        OutlinedTextField(
                            value = state.diseaseDescription,
                            onValueChange = viewModel::onDiseaseDescriptionChanged,
                            label = { Text("Describe the disease in detail (optional)") },
                            minLines = 3,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(
                            text = "The AI reads your description first, then shows a score that is combined with the rule-based estimate.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        PrimaryButton(
                            text = if (state.aiScore != null) "Re-analyze with AI" else "Analyze with AI",
                            onClick = { viewModel.analyzeWithAi() },
                            enabled = state.selectedSymptoms.isNotEmpty() ||
                                state.diseaseDescription.isNotBlank() ||
                                state.hasSomeDiseaseDetails,
                            loading = state.isAnalyzingAi,
                        )
                        state.aiAnalysis?.let { analysis ->
                            Surface(
                                shape = MaterialTheme.shapes.small,
                                color = AppColors.SoftBlue,
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(AppSpacing.MD),
                                    verticalArrangement = Arrangement.spacedBy(AppSpacing.XS),
                                ) {
                                    state.aiLikelyCondition?.let { condition ->
                                        Text(
                                            text = condition,
                                            style = MaterialTheme.typography.titleSmall,
                                            color = AppColors.Info,
                                        )
                                    }
                                    Text(
                                        text = analysis,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                    Text(
                                        text = "AI score: ${state.aiScore}",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = AppColors.Info,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            FadeInContent(delayMillis = 60) {
                SectionCard(
                    title = if (state.photoPaths.isEmpty()) "Photos (optional)"
                    else "Photos (${state.photoPaths.size}/${SymptomReport.MAX_PHOTOS})",
                    modifier = Modifier.padding(horizontal = AppSpacing.Screen),
                ) {
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(AppSpacing.MD),
                    ) {
                        AddPhotoTile(
                            enabled = state.photoPaths.size < SymptomReport.MAX_PHOTOS,
                            onClick = { showPhotoSourceDialog = true },
                        )

                        state.photoPaths.forEachIndexed { index, path ->
                            Box(modifier = Modifier.size(110.dp)) {
                                ReportPhoto(
                                    localPath = path,
                                    modifier = Modifier
                                        .size(110.dp)
                                        .clip(MaterialTheme.shapes.small),
                                    contentDescription = "Photo ${index + 1} of the animal",
                                )
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(4.dp)
                                        .size(26.dp)
                                        .clip(androidx.compose.foundation.shape.CircleShape)
                                        .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.65f))
                                        .clickable { viewModel.onPhotoRemovedAt(index) },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Remove photo ${index + 1}",
                                        tint = androidx.compose.ui.graphics.Color.White,
                                        modifier = Modifier.size(16.dp),
                                    )
                                }
                            }
                        }
                    }

                    Text(
                        text = "A clear photo of the affected area helps the vet",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            FadeInContent(delayMillis = 80) {
                SectionCard(
                    title = "Location",
                    modifier = Modifier.padding(horizontal = AppSpacing.Screen),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.MD)) {
                        AppTextField(
                            value = state.villageText,
                            onValueChange = viewModel::onVillageChanged,
                            label = "Village",
                            supportingText = if (state.villageText.isBlank()) {
                                "Required — the vet needs to find you"
                            } else {
                                null
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedButton(
                            onClick = {
                                val fine = ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                ) == PackageManager.PERMISSION_GRANTED
                                val coarse = ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.ACCESS_COARSE_LOCATION,
                                ) == PackageManager.PERMISSION_GRANTED
                                if (fine || coarse) {
                                    fetchLocation(fusedLocationClient, viewModel)
                                } else {
                                    locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.medium,
                        ) {
                            Icon(Icons.Default.LocationOn, contentDescription = null)
                            Spacer(modifier = Modifier.width(AppSpacing.SM))
                            Text("Use current location")
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(AppSpacing.MD),
                        ) {
                            AppTextField(
                                value = state.latitudeText,
                                onValueChange = viewModel::onLatitudeChanged,
                                label = "Latitude",
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                supportingText = if (state.latitude == null) "Required" else null,
                                modifier = Modifier.weight(1f),
                            )
                            AppTextField(
                                value = state.longitudeText,
                                onValueChange = viewModel::onLongitudeChanged,
                                label = "Longitude",
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                supportingText = if (state.longitude == null) "Required" else null,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }

            FadeInContent(delayMillis = 100) {
                SectionCard(
                    title = "Review",
                    modifier = Modifier.padding(horizontal = AppSpacing.Screen),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.MD)) {
                        val (riskBand, riskColor) = when {
                            state.riskScore >= 60 -> "High risk" to AppColors.Danger
                            state.riskScore >= 30 -> "Medium risk" to AppColors.Warning
                            else -> "Low risk" to AppColors.Healthy
                        }
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = riskColor.copy(alpha = 0.10f),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = AppSpacing.MD, vertical = AppSpacing.SM),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = riskColor,
                                    modifier = Modifier.size(22.dp),
                                )
                                Spacer(modifier = Modifier.width(AppSpacing.SM))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Estimated risk",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Text(
                                        text = "$riskBand - score ${state.riskScore}",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = riskColor,
                                    )
                                    if (state.aiScore != null) {
                                        Text(
                                            text = "AI analyzed: ${state.aiScore} · rule-based: ${state.ruleBasedScore}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }

                        val missing = buildList {
                            if (state.selectedAnimalId == null) add("Select the sick animal")
                            if (state.selectedSymptoms.isEmpty()) add("Pick at least one symptom")
                            if (state.villageText.isBlank()) add("Enter your village")
                            if (state.latitude == null || state.longitude == null) {
                                add("Add the location — use current location or type coordinates")
                            }
                        }
                        if (missing.isEmpty()) {
                            RequirementRow("All required details are filled", met = true)
                        } else {
                            Text(
                                text = "Still needed before you can submit",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            missing.forEach { RequirementRow(it, met = false) }
                        }

                        PrimaryButton(
                            text = "Submit report",
                            onClick = { viewModel.submit() },
                            enabled = state.canSubmit,
                            loading = state.isSubmitting,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(AppSpacing.FloatingBar))
        }
    }

    if (showPhotoSourceDialog) {
        AppDialog(
            onDismissRequest = { showPhotoSourceDialog = false },
            title = "Add photo",
            icon = Icons.Default.Image,
            confirmText = "",
            onConfirm = {},
            showConfirm = false,
            dismissText = "Cancel",
            onCancel = { showPhotoSourceDialog = false },
            content = {
                PhotoSourceOption(
                    icon = Icons.Default.PhotoCamera,
                    title = "Take a photo",
                    subtitle = "Use the camera",
                    onClick = {
                        showPhotoSourceDialog = false
                        startCameraCapture()
                    },
                )
                PhotoSourceOption(
                    icon = Icons.Default.Image,
                    title = "Choose from gallery",
                    subtitle = "Pick an existing photo",
                    onClick = {
                        showPhotoSourceDialog = false
                        galleryLauncher.launch("image/*")
                    },
                )
            },
        )
    }

    if (showAiDialog) {
        val reportId = state.lastReportId
        val advisory = state.aiAdvisory
        AppDialog(
            onDismissRequest = {},
            title = "AI Insight Ready",
            message = "Your report has been submitted successfully! Would you like to generate an AI health insight about your animal?",
            icon = Icons.Default.AutoAwesome,
            confirmText = "Generate",
            onConfirm = {
                showAiDialog = false
                if (reportId != null) {
                    navController.navigate(Screen.AiInsight.withReportId(reportId, advisory ?: ""))
                }
                viewModel.resetAfterSubmit()
            },
            dismissText = "Close",
            onCancel = {
                showAiDialog = false
                viewModel.resetAfterSubmit()
            },
        )
    }
}

@Composable
private fun RequirementRow(label: String, met: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.SM),
    ) {
        Icon(
            imageVector = if (met) Icons.Default.Check else Icons.Default.Close,
            contentDescription = null,
            tint = if (met) AppColors.Healthy else MaterialTheme.colorScheme.outline,
            modifier = Modifier.size(16.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = if (met) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}

@Composable
private fun AddPhotoTile(
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .size(110.dp)
            .clip(MaterialTheme.shapes.small)
            .border(
                width = 1.dp,
                color = if (enabled) {
                    MaterialTheme.colorScheme.outline
                } else {
                    MaterialTheme.colorScheme.outlineVariant
                },
                shape = MaterialTheme.shapes.small,
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(AppSpacing.SM),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(MaterialTheme.shapes.small)
                .background(
                    if (enabled) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = null,
                tint = if (enabled) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.outline
                },
                modifier = Modifier.size(24.dp),
            )
        }
        Spacer(modifier = Modifier.height(AppSpacing.SM))
        Text(
            text = if (enabled) "Add photo" else "Max 5 photos",
            style = MaterialTheme.typography.labelLarge,
            color = if (enabled) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}

@Composable
private fun PhotoSourceOption(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .padding(vertical = AppSpacing.SM),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.MD),
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(MaterialTheme.shapes.small)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(22.dp),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.titleSmall)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Copies a picked gallery image into the app's photos dir as a JPEG: content URIs
 * lose read access after a restart (breaking offline sync), and recompressing
 * handles HEIC/WebP sources. Runs on IO with a bounded decode to avoid OOM.
 */
private fun copyPickedPhoto(
    context: android.content.Context,
    uri: android.net.Uri,
    viewModel: ReportSickAnimalViewModel,
) {
    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
        try {
            val options = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                android.graphics.BitmapFactory.decodeStream(stream, null, options)
            }
            var sample = 1
            val longest = maxOf(options.outWidth, options.outHeight)
            while (longest / sample > 2048) sample *= 2
            val decodeOptions = android.graphics.BitmapFactory.Options().apply { inSampleSize = sample }
            val bitmap = context.contentResolver.openInputStream(uri)?.use { stream ->
                android.graphics.BitmapFactory.decodeStream(stream, null, decodeOptions)
            } ?: return@launch
            val photosDir = File(context.filesDir, "photos").apply { mkdirs() }
            val dest = File(photosDir, "report_${System.currentTimeMillis()}.jpg")
            FileOutputStream(dest).use { out ->
                bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, out)
            }
            bitmap.recycle()
            viewModel.onPhotoAdded(dest.absolutePath)
        } catch (error: Exception) {
            android.util.Log.e("ReportSickAnimal", "Gallery photo copy failed", error)
            viewModel.onLocationError("Could not read that image — try another one")
        }
    }
}

private fun launchCamera(
    context: android.content.Context,
    path: String?,
    launcher: androidx.activity.result.ActivityResultLauncher<android.net.Uri>,
) {
    if (path.isNullOrBlank()) return
    try {
        val file = File(path)
        file.parentFile?.mkdirs()
        if (!file.exists()) file.createNewFile()
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        launcher.launch(uri)
    } catch (error: Exception) {
        android.util.Log.e("ReportSickAnimal", "Camera launch failed", error)
    }
}

@SuppressLint("MissingPermission")
private fun fetchLocation(
    client: FusedLocationProviderClient,
    viewModel: ReportSickAnimalViewModel,
) {
    client.getCurrentLocation(
        Priority.PRIORITY_BALANCED_POWER_ACCURACY,
        CancellationTokenSource().token,
    ).addOnSuccessListener { location ->
        if (location != null) {
            viewModel.onLocationFetched(location.latitude, location.longitude)
        } else {
            viewModel.onLocationError("Location unavailable — enter latitude/longitude manually")
        }
    }.addOnFailureListener { error ->
        viewModel.onLocationError("Location failed: ${error.message} — enter manually")
    }
}
