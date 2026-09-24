package com.pashurakshak.app.ui.farmer

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.platform.LocalContext
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
import com.pashurakshak.app.di.ServiceLocator
import com.pashurakshak.app.navigation.Screen
import com.pashurakshak.app.ui.components.AppSpacing
import com.pashurakshak.app.ui.components.FadeInContent
import com.pashurakshak.app.ui.components.FieldLabel
import com.pashurakshak.app.ui.components.SectionCard
import com.pashurakshak.app.ui.components.StatusPill
import com.pashurakshak.app.ui.theme.AppColors
import java.io.File

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ReportSickAnimalScreen(
    onSubmitted: (String?, String?) -> Unit,
    bottomBar: @Composable () -> Unit = {},
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

    var animalPickerExpanded by remember { mutableStateOf(false) }
    var pendingPhotoPath by remember { mutableStateOf<String?>(null) }
    var cameraAttempt by remember { mutableIntStateOf(0) }
    var showAiDialog by remember { mutableStateOf(false) }

    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture(),
    ) { success ->
        val path = pendingPhotoPath
        if (success && path != null) {
            viewModel.onPhotoSelected(path)
        } else {
            pendingPhotoPath = null
        }
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
        bottomBar = bottomBar,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = AppSpacing.Screen, vertical = AppSpacing.Card)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column {
                Text(
                    text = "Report Sick Animal",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "Describe symptoms — we'll estimate risk and alert a vet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            FadeInContent {
                SectionCard(title = "1 · Animal") {
                    if (state.isLoadingAnimals) {
                        CircularProgressIndicator(modifier = Modifier.size(28.dp))
                    } else if (state.animals.isEmpty()) {
                        Text(
                            text = "No animals yet — add one in My Animals first.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        ExposedDropdownMenuBox(
                            expanded = animalPickerExpanded,
                            onExpandedChange = { animalPickerExpanded = it },
                        ) {
                            val selected = state.animals.find { it.id == state.selectedAnimalId }
                            OutlinedTextField(
                                value = selected?.let { "${it.name} (${it.species})" } ?: "",
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Select animal") },
                                trailingIcon = {
                                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = animalPickerExpanded)
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                            )
                            ExposedDropdownMenu(
                                expanded = animalPickerExpanded,
                                onDismissRequest = { animalPickerExpanded = false },
                            ) {
                                state.animals.forEach { animal ->
                                    DropdownMenuItem(
                                        text = { Text("${animal.name} (${animal.species})") },
                                        onClick = {
                                            viewModel.onAnimalSelected(animal.id)
                                            animalPickerExpanded = false
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }

            FadeInContent(delayMillis = 40) {
                SectionCard(title = "2 · Symptoms") {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Symptom.entries.forEach { symptom ->
                            val selected = symptom in state.selectedSymptoms
                            FilterChip(
                                selected = selected,
                                onClick = { viewModel.onSymptomToggled(symptom) },
                                label = { Text(symptom.label) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                ),
                            )
                        }
                    }
                }
            }

            FadeInContent(delayMillis = 60) {
                SectionCard(title = "3 · Photo (optional)") {
                    if (state.photoPath == null) {
                        OutlinedButton(
                            onClick = {
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
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Default.PhotoCamera, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Take photo")
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            StatusPill(
                                text = "Photo attached",
                                container = AppColors.SoftGreen,
                                content = AppColors.Healthy,
                            )
                            TextButton(onClick = { viewModel.onPhotoRemoved() }) {
                                Text("Remove")
                            }
                        }
                    }
                }
            }

            FadeInContent(delayMillis = 80) {
                SectionCard(title = "4 · Location") {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = state.villageText,
                            onValueChange = viewModel::onVillageChanged,
                            label = { Text("Village") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                        )
                        Button(
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
                        ) {
                            Icon(Icons.Default.LocationOn, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Use current location")
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            OutlinedTextField(
                                value = state.latitudeText,
                                onValueChange = viewModel::onLatitudeChanged,
                                label = { Text("Latitude") },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                            )
                            OutlinedTextField(
                                value = state.longitudeText,
                                onValueChange = viewModel::onLongitudeChanged,
                                label = { Text("Longitude") },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }

            FadeInContent(delayMillis = 100) {
                SectionCard(title = "5 · Review & submit") {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = when {
                                    state.riskScore >= 60 -> AppColors.Danger
                                    state.riskScore >= 30 -> AppColors.Warning
                                    else -> AppColors.Healthy
                                },
                                modifier = Modifier.size(22.dp),
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Estimated risk score",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    text = state.riskScore.toString(),
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                )
                            }
                        }
                        Button(
                            onClick = { viewModel.submit() },
                            enabled = state.canSubmit,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp),
                        ) {
                            if (state.isSubmitting) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    strokeWidth = 2.dp,
                                )
                            } else {
                                Text("Submit report", style = MaterialTheme.typography.titleMedium)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }

    if (showAiDialog) {
        val reportId = state.lastReportId
        val advisory = state.aiAdvisory
        AlertDialog(
            onDismissRequest = {},
            title = { Text("AI Insight Ready", style = MaterialTheme.typography.titleMedium) },
            text = { Text("Your report has been submitted successfully! Would you like to generate an AI health insight about your animal?", style = MaterialTheme.typography.bodyMedium) },
            confirmButton = {
                TextButton(onClick = {
                    showAiDialog = false
                    if (reportId != null) {
                        navController.navigate(Screen.AiInsight.withReportId(reportId, advisory ?: ""))
                    }
                    viewModel.resetAfterSubmit()
                }) {
                    Text("Generate", style = MaterialTheme.typography.titleMedium)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showAiDialog = false
                    viewModel.resetAfterSubmit()
                }) {
                    Text("Close", style = MaterialTheme.typography.titleMedium)
                }
            },
        )
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
