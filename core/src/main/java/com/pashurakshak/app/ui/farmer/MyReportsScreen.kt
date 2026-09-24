package com.pashurakshak.app.ui.farmer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import com.pashurakshak.app.ui.components.FadeInContent
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
    bottomBar: @Composable () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: MyReportsViewModel = viewModel {
        MyReportsViewModel(
            reportRepository = ServiceLocator.reportRepository,
            animalRepository = ServiceLocator.animalRepository,
            farmerId = com.pashurakshak.app.data.SessionManager.farmerId,
        )
    },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.refresh()
    }

    Scaffold(
        modifier = modifier,
        bottomBar = bottomBar,
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            ScreenHeader(
                title = "My Reports",
                subtitle = if (state.items.isNotEmpty()) {
                    "${state.items.size} report${if (state.items.size == 1) "" else "s"}"
                } else {
                    "AI risk + vet visit history"
                },
            )
            state.error?.let { error ->
                ErrorBanner(
                    message = error,
                    onRetry = { viewModel.refresh() },
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
            when {
                state.isLoading -> LoadingState()
                state.items.isEmpty() -> EmptyState(
                    icon = Icons.AutoMirrored.Filled.ReceiptLong,
                    title = "No reports yet",
                    message = "Report a sick animal from the Report tab to get AI risk scoring and vet support.",
                )

                else -> {
                    LazyColumn(
                        contentPadding = PaddingValues(
                            start = AppSpacing.Screen,
                            end = AppSpacing.Screen,
                            top = 4.dp,
                            bottom = AppSpacing.ListBottom,
                        ),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(state.items, key = { it.report.id }) { item ->
                            FadeInContent {
                                ReportCard(item = item, onReportClick = onReportClick)
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun ReportCard(item: MyReportItem, onReportClick: (String) -> Unit) {
    val risk = riskLevelFor(item.report.riskScore)
    val levelColor = when (risk) {
        RiskLevel.HIGH -> RiskColors.highRed
        RiskLevel.MEDIUM -> RiskColors.mediumOrange
        else -> RiskColors.lowGreen
    }
    Card(
        onClick = { onReportClick(item.report.id) },
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
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
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val statusChip = when {
                    item.isResolved -> "Resolved"
                    item.vetVisited -> "Vet visited · ${prettifyStatus(item.report.status.dbValue)}"
                    else -> "Awaiting vet · ${prettifyStatus(item.report.status.dbValue)}"
                }
                StatusPill(
                    text = statusChip,
                    container = if (item.vetVisited || item.isResolved) {
                        AppColors.SoftGreen
                    } else {
                        AppColors.SoftAmber
                    },
                    content = if (item.vetVisited || item.isResolved) {
                        AppColors.Healthy
                    } else {
                        AppColors.Warning
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

            val photoLocal = item.report.photoLocalPath
            val photoRemote = item.report.photoRemoteUrl
            if (!photoLocal.isNullOrBlank() || !photoRemote.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                com.pashurakshak.app.ui.components.ReportPhoto(
                    report = item.report,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                        .clip(MaterialTheme.shapes.small),
                )
            }
        }
    }
}
