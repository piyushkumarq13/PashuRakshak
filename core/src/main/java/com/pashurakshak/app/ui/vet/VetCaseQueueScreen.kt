package com.pashurakshak.app.ui.vet

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pashurakshak.app.di.ServiceLocator
import com.pashurakshak.app.ui.components.AppSpacing
import com.pashurakshak.app.ui.components.EmptyState
import com.pashurakshak.app.ui.components.ErrorBanner
import com.pashurakshak.app.ui.components.FadeInContent
import com.pashurakshak.app.ui.components.LoadingState
import com.pashurakshak.app.ui.components.ScreenHeader
import com.pashurakshak.app.ui.components.StatusPill
import com.pashurakshak.app.ui.farmer.formatDateMillis
import com.pashurakshak.app.ui.farmer.prettifyStatus
import com.pashurakshak.app.ui.theme.AppColors

@Composable
fun VetCaseQueueScreen(
    onReportClick: (reportId: String) -> Unit,
    onQrScanClick: (reportId: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: VetCaseQueueViewModel = viewModel {
        VetCaseQueueViewModel(
            reportRepository = ServiceLocator.reportRepository,
            animalRepository = ServiceLocator.animalRepository,
        )
    },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.refresh()
    }

    LaunchedEffect(state.scanQrReportId) {
        state.scanQrReportId?.let { reportId ->
            viewModel.clearQrScanRequest()
            onQrScanClick(reportId)
        }
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
                title = "Case Queue",
                subtitle = if (state.items.isNotEmpty()) {
                    "${state.items.size} case${if (state.items.size == 1) "" else "s"} awaiting triage"
                } else {
                    "Farmer reports sorted by risk"
                },
            )
            state.error?.let { error ->
                ErrorBanner(
                    message = error,
                    onRetry = { viewModel.refresh() },
                    modifier = Modifier.padding(bottom = AppSpacing.SM),
                )
            }
            when {
                state.isLoading -> {
                    LoadingState()
                }

                state.items.isEmpty() -> {
                    EmptyState(
                        icon = Icons.Default.Pets,
                        title = "All clear",
                        message = "No pending cases right now. New farmer reports will appear here.",
                    )
                }

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
                        items(state.items, key = { it.report.id }) { item ->
                            FadeInContent {
                                QueueCard(
                                    item = item,
                                    onClick = { onReportClick(item.report.id) },
                                    onQrScanClick = { onQrScanClick(item.report.id) },
                                    onMarkExamined = { viewModel.markAsExamined(item.report.id) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QueueCard(
    item: QueueItem,
    onClick: () -> Unit,
    onQrScanClick: () -> Unit,
    onMarkExamined: () -> Unit,
) {
    val levelColor = item.riskLevel.color()
    Surface(
        onClick = onClick,
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
                        text = item.riskLevel.label().uppercase(),
                        container = levelColor.copy(alpha = 0.14f),
                        content = levelColor,
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(AppSpacing.SM),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    StatusPill(
                        text = prettifyStatus(item.report.status.dbValue),
                        container = AppColors.SoftAmber,
                        content = AppColors.Warning,
                    )
                    Text(
                        text = "Risk score ${item.report.riskScore}",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = levelColor,
                    )
                }

                Text(
                    text = item.report.symptoms.joinToString(", ").ifBlank { "No symptoms listed" },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(AppSpacing.XS),
                ) {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp),
                    )
                    Text(
                        text = "%.4f, %.4f".format(item.report.latitude, item.report.longitude),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "• ${formatDateMillis(item.report.createdAt)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = AppSpacing.XS),
                    horizontalArrangement = Arrangement.spacedBy(AppSpacing.SM),
                ) {
                    OutlinedButton(
                        onClick = onQrScanClick,
                        modifier = Modifier.weight(1f),
                        shape = MaterialTheme.shapes.small,
                    ) {
                        Icon(
                            imageVector = Icons.Default.QrCodeScanner,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = "Scan QR", style = MaterialTheme.typography.labelLarge)
                    }
                    Button(
                        onClick = onMarkExamined,
                        modifier = Modifier.weight(1f),
                        shape = MaterialTheme.shapes.small,
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = "Examined", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}

@Composable
internal fun RiskBadge(level: RiskLevel) {
    val color = level.color()
    Surface(
        color = color.copy(alpha = 0.14f),
        contentColor = color,
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Text(
            text = level.label(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}
