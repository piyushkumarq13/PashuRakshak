package com.pashurakshak.app.ui.farmer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import com.pashurakshak.app.data.SessionManager
import com.pashurakshak.app.data.local.Alert
import com.pashurakshak.app.di.ServiceLocator
import com.pashurakshak.app.ui.components.AccentDot
import com.pashurakshak.app.ui.components.AppSpacing
import com.pashurakshak.app.ui.components.EmptyState
import com.pashurakshak.app.ui.components.ErrorBanner
import com.pashurakshak.app.ui.components.FadeInContent
import com.pashurakshak.app.ui.components.LoadingState
import com.pashurakshak.app.ui.components.ScreenHeader
import com.pashurakshak.app.ui.theme.AppColors

@Composable
fun AlertsScreen(
    recipientRole: String = "farmer",
    recipientId: String? = SessionManager.farmerId,
    bottomBar: @Composable () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: AlertsViewModel = viewModel {
        AlertsViewModel(
            alertRepository = ServiceLocator.alertRepository,
            recipientRole = recipientRole,
            recipientId = recipientId,
        )
    },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(recipientRole, recipientId) {
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
                title = "Alerts",
                subtitle = "Vaccinations, reports & updates",
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
                state.alerts.isEmpty() -> EmptyState(
                    icon = Icons.Default.Notifications,
                    title = "No alerts yet",
                    message = "You'll get notified when a vet reviews a report or a vaccination is due.",
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
                        items(state.alerts, key = { it.id }) { alert ->
                            FadeInContent {
                                AlertCard(
                                    alert = alert,
                                    onClick = { viewModel.onAlertClicked(alert) },
                                )
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
private fun AlertCard(
    alert: Alert,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = if (alert.read) {
                MaterialTheme.colorScheme.surface
            } else {
                AppColors.SoftGreen
            },
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (!alert.read) {
                AccentDot(color = AppColors.Primary)
            } else {
                Spacer(modifier = Modifier.size(10.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = alert.message,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (alert.read) FontWeight.Normal else FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = formatDateTimeMillis(alert.createdAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}
