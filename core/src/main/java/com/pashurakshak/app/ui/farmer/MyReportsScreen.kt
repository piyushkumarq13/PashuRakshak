package com.pashurakshak.app.ui.farmer

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pashurakshak.app.di.ServiceLocator
import com.pashurakshak.app.ui.components.AppSpacing
import com.pashurakshak.app.ui.components.EmptyState
import com.pashurakshak.app.ui.components.ErrorBanner
import androidx.compose.material3.Scaffold
import com.pashurakshak.app.ui.components.LoadingState
import com.pashurakshak.app.ui.components.ScreenHeader
import com.pashurakshak.app.ui.components.StatusPill
import com.pashurakshak.app.ui.theme.AppColors
import com.pashurakshak.app.ui.vet.RiskColors
import com.pashurakshak.app.ui.vet.RiskLevel
import com.pashurakshak.app.ui.vet.label
import com.pashurakshak.app.ui.vet.riskLevelFor

@Composable
fun MyReportsScreen(
    onReportClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    filter: String = "all",
    viewModel: MyReportsViewModel = viewModel {
        MyReportsViewModel(
            reportRepository = ServiceLocator.reportRepository,
            animalRepository = ServiceLocator.animalRepository,
            farmerId = com.pashurakshak.app.data.SessionManager.farmerId,
        )
    },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var qrDialogReport by remember { mutableStateOf<MyReportItem?>(null) }
    var selectedFilter by remember(filter) { mutableStateOf(filter) }

    val filteredItems = remember(state.items, selectedFilter) {
        when (selectedFilter) {
            "ongoing" -> state.items.filter {
                it.report.status == com.pashurakshak.app.data.local.ReportStatus.REPORTED ||
                    it.report.status == com.pashurakshak.app.data.local.ReportStatus.VET_ASSIGNED
            }
            "examined" -> state.items.filter { it.vetVisited && !it.isResolved }
            "resolved" -> state.items.filter { it.isResolved }
            else -> state.items
        }
    }

    LaunchedEffect(Unit) {
        viewModel.refresh()
    }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            ScreenHeader(
                title = when (selectedFilter) {
                    "ongoing" -> "Active Cases"
                    "examined" -> "Examined Cases"
                    "resolved" -> "Resolved Cases"
                    else -> "My Reports"
                },
                subtitle = when (selectedFilter) {
                    "ongoing" -> "Out for triage: ${filteredItems.size} ongoing"
                    "examined" -> "${filteredItems.size} case${if (filteredItems.size == 1) "" else "s"} examined by a vet"
                    "resolved" -> "${filteredItems.size} verified or resolved case${if (filteredItems.size == 1) "" else "s"}"
                    else -> if (state.items.isNotEmpty()) {
                        "${state.items.size} report${if (state.items.size == 1) "" else "s"}"
                    } else {
                        "AI risk + vet visit history"
                    }
                },
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = AppSpacing.Screen),
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.SM),
            ) {
                listOf(
                    "all" to "All",
                    "ongoing" to "Active",
                    "examined" to "Examined",
                    "resolved" to "Resolved",
                ).forEach { (key, label) ->
                    FilterChip(
                        selected = selectedFilter == key,
                        onClick = { selectedFilter = key },
                        label = { Text(label) },
                    )
                }
            }
            state.error?.let { error ->
                ErrorBanner(
                    message = error,
                    onRetry = { viewModel.refresh() },
                    modifier = Modifier.padding(bottom = AppSpacing.SM),
                )
            }
            when {
                state.isLoading -> LoadingState()
                filteredItems.isEmpty() -> EmptyState(
                    icon = Icons.AutoMirrored.Filled.ReceiptLong,
                    title = when (selectedFilter) {
                        "ongoing" -> "No active cases"
                        "examined" -> "No examined cases"
                        "resolved" -> "No resolved cases"
                        else -> "No reports yet"
                    },
                    message = when (selectedFilter) {
                        "ongoing" -> "Your ongoing reports will appear here until a vet examines them."
                        "examined" -> "Reports a vet has examined will appear here."
                        "resolved" -> "Verified and closed cases will appear here."
                        else -> "Report a sick animal from the Report tab to get AI risk scoring and vet support."
                    },
                )

                else -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentPadding = PaddingValues(
                            start = AppSpacing.Screen,
                            end = AppSpacing.Screen,
                            top = AppSpacing.XS,
                            bottom = AppSpacing.ListBottom,
                        ),
                        verticalArrangement = Arrangement.spacedBy(AppSpacing.MD),
                    ) {
                        items(filteredItems, key = { it.report.id }) { item ->
                            ReportCard(
                                item = item,
                                onReportClick = onReportClick,
                                onShowQr = { qrDialogReport = item },
                            )
                        }
                    }
                }
            }
        }
    }

    if (qrDialogReport != null) {
        ReportQrDialog(item = qrDialogReport!!) { qrDialogReport = null }
    }
}

@Composable
fun ReportQrDialog(item: MyReportItem, onDismiss: () -> Unit) {
    val qrResult by produceState<Result<android.graphics.Bitmap>?>(
        initialValue = null,
        item.report.qrCodeId,
    ) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            runCatching {
                com.journeyapps.barcodescanner.BarcodeEncoder().encodeBitmap(
                    com.pashurakshak.app.data.remote.QrPayloads.sign(item.report.qrCodeId),
                    com.google.zxing.BarcodeFormat.QR_CODE,
                    640,
                    640,
                )
            }
        }
    }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Report QR code") },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(AppSpacing.SM),
            ) {
                val bitmap = qrResult?.getOrNull()
                if (bitmap != null) {
                    androidx.compose.foundation.Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "QR code for report ${item.report.id.take(8)}",
                        modifier = Modifier.size(220.dp),
                    )
                } else {
                    Text("Could not generate QR code")
                }
                Text(
                    text = item.report.qrCodeId,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "Show this to the vet during the visit so they can verify this case.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) { Text("Close") }
        },
    )
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun ReportCard(item: MyReportItem, onReportClick: (String) -> Unit, onShowQr: () -> Unit) {
    val risk = riskLevelFor(item.report.riskScore)
    val levelColor = when (risk) {
        RiskLevel.HIGH -> RiskColors.highRed
        RiskLevel.MEDIUM -> RiskColors.mediumOrange
        else -> RiskColors.lowGreen
    }
    Surface(
        onClick = { onReportClick(item.report.id) },
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        shadowElevation = 2.dp,
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .width(5.dp)
                    .fillMaxHeight()
                    .background(levelColor),
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(AppSpacing.LG),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.SM),
            ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = item.animal?.let { "${it.name} (${it.species})" }
                        ?: "Animal ${item.report.animalId.take(8)}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                StatusPill(
                    text = risk.label().uppercase(),
                    container = levelColor.copy(alpha = 0.14f),
                    content = levelColor,
                )
                if (!item.verifiedVisit && !item.isResolved) {
                androidx.compose.material3.IconButton(
                    onClick = onShowQr,
                    modifier = Modifier.size(36.dp),
                ) {
                    androidx.compose.material3.Icon(
                        imageVector = Icons.Default.QrCodeScanner,
                        contentDescription = "Show report QR",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.SM),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val statusChip = when {
                    item.isResolved && item.verifiedVisit -> "RESOLVED • VERIFIED"
                    item.isResolved -> "RESOLVED"
                    item.verifiedVisit -> "VERIFIED"
                    item.vetVisited -> prettifyStatus(item.report.status.dbValue).uppercase()
                    else -> "AWAITING VET"
                }
                val isPositive = item.vetVisited || item.isResolved || item.verifiedVisit
                StatusPill(
                    text = statusChip,
                    container = if (isPositive) AppColors.SoftGreen else AppColors.SoftAmber,
                    content = if (isPositive) AppColors.Healthy else AppColors.Warning,
                    icon = when {
                        item.isResolved -> Icons.Default.CheckCircle
                        item.verifiedVisit -> Icons.Default.Verified
                        item.vetVisited -> Icons.Default.CheckCircle
                        else -> Icons.Default.Schedule
                    },
                )
                Text(
                    text = formatDateMillis(item.report.createdAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Text(
                text = item.report.symptoms.joinToString(", "),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (item.isResolved) {
                Text(
                    text = item.report.resolvedAt?.let {
                        "Resolved ${formatDateTimeMillis(it)}"
                    } ?: "This case is resolved.",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = AppColors.Healthy,
                )
                if (!item.report.resolutionNote.isNullOrBlank()) {
                    Text(
                        text = "Resolution note: ${item.report.resolutionNote}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
            if (item.verifiedVisit) {
                Text(
                    text = item.report.verifiedAt?.let {
                        "QR verified ${formatDateTimeMillis(it)} — no longer needed."
                    } ?: "QR verification completed for this case.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else if (item.vetVisited) {
                Text(
                    text = "Examined by a vet (not QR verified).",
                    style = MaterialTheme.typography.bodySmall,
                    color = AppColors.Warning,
                )
            }
            if (!item.report.vetAssessment.isNullOrBlank()) {
                Text(
                    text = "Vet note: ${item.report.vetAssessment}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            if (item.report.displayPhotoCount > 0) {
                Spacer(modifier = Modifier.height(AppSpacing.XS))
                com.pashurakshak.app.ui.components.ReportPhoto(
                    remoteUrl = item.report.displayPhotoUrl,
                    localPath = item.report.photoLocalPaths.firstOrNull().orEmpty(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                        .clip(MaterialTheme.shapes.small),
                )
                if (item.report.displayPhotoCount > 1) {
                    Text(
                        text = "${item.report.displayPhotoCount} photos",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            }
        }
    }
}
