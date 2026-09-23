package com.pashurakshak.app.ui.farmer

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pashurakshak.app.data.local.ReportStatus
import com.pashurakshak.app.di.ServiceLocator
import com.pashurakshak.app.ui.components.ReportPhoto
import com.pashurakshak.app.ui.vet.RiskColors
import com.pashurakshak.app.ui.vet.RiskLevel
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
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            Text(
                text = "My Reports",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )
            state.error?.let { error ->
                Text(
                    text = error,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
            when {
                state.isLoading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                }

                state.items.isEmpty() -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "No reports yet — report a sick animal from the Report tab.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                else -> {
                    LazyColumn(
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 88.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(state.items, key = { it.report.id }) { item ->
                            ReportCard(item = item, onReportClick = onReportClick)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReportCard(item: MyReportItem, onReportClick: (String) -> Unit) {
    val risk = riskLevelFor(item.report.riskScore)
    val levelColor = when (risk) {
        RiskLevel.HIGH -> RiskColors.highRed
        RiskLevel.MEDIUM -> RiskColors.mediumOrange
        else -> RiskColors.lowGreen
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clickable { onReportClick(item.report.id) },
        colors = CardDefaults.cardColors(
            containerColor = levelColor.copy(alpha = 0.06f),
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = item.animal?.let { "${it.name} (${it.species})" }
                        ?: "Animal ${item.report.animalId.take(8)}",
                    style = MaterialTheme.typography.titleMedium,
                )
                Surface(
                    color = levelColor,
                    contentColor = Color.White,
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(50),
                ) {
                    Text(
                        text = "Risk ${item.report.riskScore}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
            }

            val statusChip = when {
                item.isResolved -> "Resolved ✔"
                item.vetVisited -> "Vet visited (${prettifyStatus(item.report.status.dbValue)})"
                else -> "Awaiting vet visit (${prettifyStatus(item.report.status.dbValue)})"
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    color = if (item.vetVisited || item.isResolved) Color(0xFF2E7D32).copy(alpha = 0.15f) else Color(0xFFEF6C00).copy(alpha = 0.15f),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(50),
                ) {
                    Text(
                        text = statusChip,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
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

            ReportPhoto(
                report = item.report,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp)),
            )
        }
    }
}
