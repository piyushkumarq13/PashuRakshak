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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.pashurakshak.app.data.SessionManager
import com.pashurakshak.app.di.ServiceLocator
import java.io.File

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ReportSickAnimalScreen(
    onSubmitted: (String?, String?) -> Unit,
    bottomBar: @Composable () -> Unit = {},
    modifier: Modifier = Modifier,
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
            onSubmitted(state.aiAdvisory, state.lastReportId)
            viewModel.resetAfterSubmit()
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
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text(
                text = "Report Sick Animal",
                style = MaterialTheme.typography.headlineSmall,
            )

            // Animal picker
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Animal", style = MaterialTheme.typography.titleSmall)
                if (state.isLoadingAnimals) {
                    CircularProgressIndicator(
                        modifier = Modifier.padding(top = 8.dp),
                    )
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

            // Symptom checklist
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Symptoms", style = MaterialTheme.typography.titleSmall)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Symptom.entries.forEach { symptom ->
                        FilterChip(
                            selected = symptom in state.selectedSymptoms,
                            onClick = { viewModel.onSymptomToggled(symptom) },
                            label = { Text(symptom.label) },
                        )
                    }
                }
            }

            // Photo (optional)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Photo (optional)", style = MaterialTheme.typography.titleSmall)
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
                        Text("  Take photo")
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = "Photo attached",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        TextButton(onClick = { viewModel.onPhotoRemoved() }) {
                            Text("Remove")
                        }
                    }
                }
            }

            // Village
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Village", style = MaterialTheme.typography.titleSmall)
                OutlinedTextField(
                    value = state.villageText,
                    onValueChange = viewModel::onVillageChanged,
                    label = { Text("Village") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
            }

            // Location
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Location", style = MaterialTheme.typography.titleSmall)
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
                    Text("  Use current location")
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

            // Risk preview + submit
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    text = "Estimated risk score: ${state.riskScore}",
                    style = MaterialTheme.typography.titleMedium,
                )
                Button(
                    onClick = { viewModel.submit() },
                    enabled = state.canSubmit,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (state.isSubmitting) {
                        CircularProgressIndicator(
                            modifier = Modifier.padding(end = 8.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    } else {
                        Text("Submit Report")
                    }
                }
            }
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
