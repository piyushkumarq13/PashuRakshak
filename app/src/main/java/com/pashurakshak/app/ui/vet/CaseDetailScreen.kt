package com.pashurakshak.app.ui.vet

import android.content.Intent
import android.app.Activity
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.zxing.integration.android.IntentIntegrator
import com.pashurakshak.app.di.ServiceLocator
import com.pashurakshak.app.ui.components.ReportPhoto
import com.pashurakshak.app.ui.farmer.formatDateMillis
import com.pashurakshak.app.ui.farmer.prettifyStatus
import androidx.compose.foundation.layout.Box

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CaseDetailScreen(
    reportId: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CaseDetailViewModel = viewModel {
        CaseDetailViewModel(
            reportId = reportId,
            reportRepository = ServiceLocator.reportRepository,
            animalRepository = ServiceLocator.animalRepository,
            alertRepository = ServiceLocator.alertRepository,
        )
    },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showFieldCheck by rememberSaveable { mutableStateOf(false) }
    var showVisitDialog by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }

    val barcodeLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val intentResult = IntentIntegrator.parseActivityResult(result.resultCode, result.data)
        if (intentResult != null && intentResult.contents != null) {
            viewModel.onQrScanned(intentResult.contents)
        }
    }

    LaunchedEffect(state.notice) {
        state.notice?.let { notice ->
            showFieldCheck = false
            snackbarHostState.showSnackbar(notice)
            viewModel.clearNotice()
        }
    }

    LaunchedEffect(state.error) {
        state.error?.let { error ->
            snackbarHostState.showSnackbar(error)
            viewModel.clearNotice()
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Case Detail") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        val report = state.report
        when {
            state.isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }

            report == null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = state.error ?: "Report not found",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }

            else -> {
                val riskLevel = riskLevelFor(report.riskScore)
                val levelColor = riskLevel.color()

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    // Risk score header
                    Card {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = "Risk score",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Text(
                                    text = report.riskScore.toString(),
                                    style = MaterialTheme.typography.displaySmall,
                                    color = levelColor,
                                    fontWeight = FontWeight.Bold,
                                )
                                RiskBadge(level = riskLevel)
                            }
                            Text(
                                text = "Status: ${prettifyStatus(report.status.dbValue)}",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }

                    // Animal
                    DetailSection(title = "Animal") {
                        Text(
                            text = state.animal?.let { "${it.name} (${it.species})" }
                                ?: "Animal ${report.animalId.take(8)}",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }

                    // Symptoms
                    DetailSection(title = "Symptoms") {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            report.symptoms.forEach { symptom ->
                                Surface(
                                    color = MaterialTheme.colorScheme.secondaryContainer,
                                    shape = RoundedCornerShape(50),
                                ) {
                                    Text(
                                        text = symptom,
                                        style = MaterialTheme.typography.labelMedium,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    )
                                }
                            }
                        }
                    }

                    // Meta details
                    DetailSection(title = "Details") {
                        DetailRow("Reported by", report.farmerId)
                        DetailRow(
                            label = "Location",
                            value = "%.4f, %.4f".format(report.latitude, report.longitude),
                        )
                        DetailRow("Captured", formatDateMillis(report.createdAt))
                    }

                    // Photo
                    DetailSection(title = "Photo") {
                        ReportPhoto(
                            report = report,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    // Why this score
                    WhyThisScoreCard(
                        totalScore = report.riskScore,
                        breakdown = report.riskBreakdown,
                        levelColor = levelColor,
                    )

                    // QR Scan
                    if (state.canMarkExamined) {
                        Button(
                            onClick = {
                                val activity = context as android.app.Activity
                                val intent = Intent(activity, com.journeyapps.barcodescanner.CaptureActivity::class.java)
                                intent.putExtra("MODE", IntentIntegrator.QR_CODE)
                                intent.putExtra("PROMPT_MESSAGE", "Scan animal QR to verify visit")
                                barcodeLauncher.launch(intent)
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Scan QR to Verify Visit")
                        }
                    }

                    // Scanned QR status
                    if (state.isQrScanned) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(50),
                        ) {
                            Text(
                                text = "QR verified: ${state.scannedQrCodeId?.take(8)}...",
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            )
                        }
                    }

                    // Assessment options (unlocked after QR scan)
                    if (state.isQrScanned) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Assessment", style = MaterialTheme.typography.titleSmall)
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                listOf("risky", "moderate", "mild").forEach { level ->
                                    FilterChip(
                                        selected = state.assessment == level,
                                        onClick = { viewModel.onAssessmentSelected(level) },
                                        label = { Text(level) },
                                    )
                                }
                            }
                        }
                    }

                    // Location
                    if (state.isQrScanned) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Location", style = MaterialTheme.typography.titleSmall)
                            Button(
                                onClick = {
                                    val fine = ContextCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.ACCESS_FINE_LOCATION,
                                    ) == PackageManager.PERMISSION_GRANTED
                                    if (fine) {
                                        fetchLocation(fusedLocationClient, viewModel)
                                    }
                                },
                            ) {
                                Text("Get current location")
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
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

                    // Mark Examined
                    if (state.canMarkExamined) {
                        Button(
                            onClick = { showFieldCheck = true },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Mark Examined")
                        }
                    } else {
                        Text(
                            text = "Field check completed — status: ${prettifyStatus(report.status.dbValue)}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    // Submit Visit
                    if (state.canSubmitVisit) {
                        Button(
                            onClick = { showVisitDialog = true },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            if (state.isSubmittingVisit) {
                                CircularProgressIndicator(
                                    modifier = Modifier.padding(end = 8.dp),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                )
                            } else {
                                Text("Submit Visit Verification")
                            }
                        }
                    }
                }
            }
        }
    }

    if (showFieldCheck) {
        FieldCheckDialog(
            isSubmitting = state.isSubmittingFieldCheck,
            onSubmit = { sampleRequired -> viewModel.submitFieldCheck(sampleRequired) },
            onDismiss = { showFieldCheck = false },
        )
    }

    if (showVisitDialog) {
        AlertDialog(
            onDismissRequest = { showVisitDialog = false },
            title = { Text("Verify Visit") },
            text = {
                Text(
                    text = "Submit visit verification for report ${state.report?.id?.take(8)}? " +
                        "Assessment: ${state.assessment}",
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showVisitDialog = false
                        viewModel.submitVisit()
                    },
                    enabled = !state.isSubmittingVisit,
                ) {
                    if (state.isSubmittingVisit) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Text("Confirm")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showVisitDialog = false }, enabled = !state.isSubmittingVisit) {
                    Text("Cancel")
                }
            },
        )
    }
}

private fun fetchLocation(
    client: FusedLocationProviderClient,
    viewModel: CaseDetailViewModel,
) {
    client.getCurrentLocation(
        Priority.PRIORITY_BALANCED_POWER_ACCURACY,
        CancellationTokenSource().token,
    ).addOnSuccessListener { location ->
        if (location != null) {
            viewModel.onLocationFetched(location.latitude, location.longitude)
        }
    }.addOnFailureListener { error ->
        // Ignore — user can enter manually
    }
}

@Composable
private fun DetailSection(
    title: String,
    content: @Composable () -> Unit,
) {
    Card {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            content()
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun WhyThisScoreCard(
    totalScore: Int,
    breakdown: Map<String, Int>,
    levelColor: Color,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }

    Card {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "Why this score?",
                    style = MaterialTheme.typography.titleMedium,
                )
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (breakdown.isEmpty()) {
                        Text(
                            text = "No breakdown available for this report.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        breakdown.forEach { (factor, points) ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    text = prettifyStatus(factor),
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier.weight(1f),
                                )
                                Surface(
                                    color = levelColor.copy(alpha = 0.15f),
                                    contentColor = levelColor,
                                    shape = RoundedCornerShape(50),
                                ) {
                                    Text(
                                        text = "+$points pts",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                    )
                                }
                            }
                        }
                        HorizontalDivider()
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = "Total",
                                style = MaterialTheme.typography.titleSmall,
                            )
                            Text(
                                text = "$totalScore pts",
                                style = MaterialTheme.typography.titleSmall,
                                color = levelColor,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        Text(
                            text = "Client-side placeholder score — refined server-side later.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FieldCheckDialog(
    isSubmitting: Boolean,
    onSubmit: (sampleRequired: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var symptomsVerified by remember { mutableStateOf<String?>(null) }
    var condition by remember { mutableStateOf<String?>(null) }
    var sampleRequired by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = { if (!isSubmitting) onDismiss() },
        title = { Text("Field Check") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OptionRow(
                    title = "Symptoms verified?",
                    options = listOf("Yes", "No"),
                    selected = symptomsVerified,
                    onSelect = { symptomsVerified = it },
                )
                OptionRow(
                    title = "Condition",
                    options = listOf("Mild", "Serious"),
                    selected = condition,
                    onSelect = { condition = it },
                )
                OptionRow(
                    title = "Sample required?",
                    options = listOf("Yes", "No"),
                    selected = sampleRequired,
                    onSelect = { sampleRequired = it },
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSubmit(sampleRequired == "Yes") },
                enabled = symptomsVerified != null &&
                    condition != null &&
                    sampleRequired != null &&
                    !isSubmitting,
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text("Submit")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSubmitting) {
                Text("Cancel")
            }
        },
    )
}

@Composable
private fun OptionRow(
    title: String,
    options: List<String>,
    selected: String?,
    onSelect: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { option ->
                FilterChip(
                    selected = selected == option,
                    onClick = { onSelect(option) },
                    label = { Text(option) },
                )
            }
        }
    }
}
