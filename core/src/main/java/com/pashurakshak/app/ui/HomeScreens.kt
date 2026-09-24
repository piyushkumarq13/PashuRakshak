package com.pashurakshak.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.Vaccines
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pashurakshak.app.BuildConfig
import com.pashurakshak.app.data.SessionManager
import com.pashurakshak.app.di.ServiceLocator
import com.pashurakshak.app.ui.components.AppSpacing
import com.pashurakshak.app.ui.components.FadeInContent
import com.pashurakshak.app.ui.components.InteractiveCard
import com.pashurakshak.app.ui.components.ScreenHeader
import com.pashurakshak.app.ui.components.SectionCard
import com.pashurakshak.app.ui.components.StatCard
import com.pashurakshak.app.ui.components.StatusPill
import com.pashurakshak.app.ui.theme.AppColors

data class HomeShortcut(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val tint: Color,
    val routeKey: String,
)

@Composable
fun FarmerHomeScreen(
    onOpenMyReports: () -> Unit,
    onOpenAnimals: () -> Unit,
    onOpenReport: () -> Unit,
    onOpenVaccination: () -> Unit,
    onOpenAlerts: () -> Unit,
    onOpenProfile: () -> Unit,
    onOpenB2Test: () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = viewModel {
        HomeViewModel(
            animalRepository = ServiceLocator.animalRepository,
            reportRepository = ServiceLocator.reportRepository,
            vaccinationRepository = ServiceLocator.vaccinationRepository,
            alertRepository = ServiceLocator.alertRepository,
            farmerId = SessionManager.farmerId,
        )
    },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val unreadAlerts = state.unreadAlertCount
    val displayName = SessionManager.name?.takeIf { it.isNotBlank() }
        ?: SessionManager.phone?.takeIf { it.isNotBlank() }
        ?: "Farmer"

    LaunchedEffect(Unit) {
        viewModel.refresh()
    }

    Scaffold(
        modifier = modifier,
        bottomBar = bottomBar,
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(bottom = AppSpacing.ListBottom),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                FadeInContent {
                    HeroHeader(
                        displayName = displayName,
                        village = SessionManager.village,
                        unreadAlerts = unreadAlerts,
                        onOpenProfile = onOpenProfile,
                        onOpenAlerts = onOpenAlerts,
                    )
                }
            }

            item {
                FadeInContent(delayMillis = 40) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = AppSpacing.Screen),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        StatCard(
                            label = "Animals",
                            value = state.animalCount.toString(),
                            icon = Icons.Default.Pets,
                            accent = AppColors.Primary,
                            modifier = Modifier.weight(1f),
                            onClick = onOpenAnimals,
                        )
                        StatCard(
                            label = "Reports",
                            value = state.reportCount.toString(),
                            icon = Icons.AutoMirrored.Filled.ReceiptLong,
                            accent = AppColors.Info,
                            modifier = Modifier.weight(1f),
                            onClick = onOpenMyReports,
                        )
                        StatCard(
                            label = "Active",
                            value = state.activeReportCount.toString(),
                            icon = Icons.Default.Warning,
                            accent = AppColors.Warning,
                            modifier = Modifier.weight(1f),
                            onClick = onOpenMyReports,
                        )
                    }
                }
            }

            item {
                FadeInContent(delayMillis = 80) {
                    Column(
                        modifier = Modifier.padding(horizontal = AppSpacing.Screen),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        SectionCard(title = "Quick actions") {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                QuickActionRow(
                                    icon = Icons.Default.Warning,
                                    title = "Report a sick animal",
                                    subtitle = "Share symptoms with a vet",
                                    tint = AppColors.Danger,
                                    onClick = onOpenReport,
                                )
                                QuickActionRow(
                                    icon = Icons.Default.Vaccines,
                                    title = "Vaccination status",
                                    subtitle = "History & upcoming doses",
                                    tint = AppColors.Secondary,
                                    onClick = onOpenVaccination,
                                )
                                QuickActionRow(
                                    icon = Icons.AutoMirrored.Filled.ReceiptLong,
                                    title = "My reports",
                                    subtitle = "Track AI risk & vet visits",
                                    tint = AppColors.Info,
                                    onClick = onOpenMyReports,
                                )
                                QuickActionRow(
                                    icon = Icons.Default.Pets,
                                    title = "My animals",
                                    subtitle = "QR passports & health",
                                    tint = AppColors.Primary,
                                    onClick = onOpenAnimals,
                                )
                            }
                        }
                    }
                }
            }

            if (BuildConfig.DEBUG) {
                item {
                    FadeInContent(delayMillis = 100) {
                        InteractiveCard(
                            onClick = onOpenB2Test,
                            modifier = Modifier.padding(horizontal = AppSpacing.Screen),
                        ) {
                            Row(
                                modifier = Modifier.padding(AppSpacing.Card),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = "B2 Upload Test",
                                    style = MaterialTheme.typography.titleMedium,
                                    modifier = Modifier.weight(1f),
                                )
                                Icon(
                                    imageVector = Icons.Default.ChevronRight,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }

            item {
                FadeInContent(delayMillis = 120) {
                    Column(
                        modifier = Modifier.padding(horizontal = AppSpacing.Screen),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            text = "Overview",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                        SectionCard {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                OverviewLine(
                                    label = "Village",
                                    value = SessionManager.village?.takeIf { it.isNotBlank() } ?: "Not set",
                                    icon = Icons.Default.LocationOn,
                                )
                                OverviewLine(
                                    label = "Phone",
                                    value = SessionManager.phone?.let { "+91 $it" } ?: "—",
                                    icon = Icons.Default.Person,
                                )
                                OverviewLine(
                                    label = "Vaccinations due soon",
                                    value = if (state.vaccinationsDue > 0) {
                                        state.vaccinationsDue.toString()
                                    } else {
                                        "All up to date"
                                    },
                                    icon = Icons.Default.Vaccines,
                                    highlight = state.vaccinationsDue > 0,
                                )
                                OverviewLine(
                                    label = "Unread alerts",
                                    value = unreadAlerts.toString(),
                                    icon = Icons.Default.Notifications,
                                    highlight = unreadAlerts > 0,
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
private fun HeroHeader(
    displayName: String,
    village: String?,
    unreadAlerts: Int,
    onOpenProfile: () -> Unit,
    onOpenAlerts: () -> Unit,
) {
    val greeting = when {
        java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY) < 12 -> "Good morning"
        java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY) < 17 -> "Good afternoon"
        else -> "Good evening"
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AppSpacing.Screen, vertical = 4.dp)
            .clip(MaterialTheme.shapes.large)
            .background(
                Brush.linearGradient(
                    colors = listOf(AppColors.Primary, AppColors.Secondary),
                ),
            ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(28.dp),
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = greeting,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.85f),
                )
                Text(
                    text = displayName,
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
                if (!village.isNullOrBlank()) {
                    Text(
                        text = village,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.85f),
                    )
                }
            }
            IconButton(
                onClick = onOpenAlerts,
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.18f)),
            ) {
                Icon(
                    imageVector = Icons.Default.Notifications,
                    contentDescription = "Alerts",
                    tint = Color.White,
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            IconButton(
                onClick = onOpenProfile,
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.18f)),
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = "Profile",
                    tint = Color.White,
                )
            }
        }
    }
}

@Composable
private fun QuickActionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    tint: Color,
    onClick: () -> Unit,
) {
    InteractiveCard(onClick = onClick) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(MaterialTheme.shapes.small)
                    .background(tint.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(22.dp),
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline,
            )
        }
    }
}

@Composable
private fun OverviewLine(
    label: String,
    value: String,
    icon: ImageVector,
    highlight: Boolean = false,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (highlight) AppColors.Warning else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        if (highlight) {
            StatusPill(
                text = value,
                container = AppColors.SoftAmber,
                content = AppColors.Warning,
            )
        } else {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
fun VetHomeScreen(
    onOpenCaseQueue: () -> Unit,
    onOpenAlerts: () -> Unit = {},
    onOpenProfile: () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: com.pashurakshak.app.ui.vet.VetHomeViewModel = viewModel {
        com.pashurakshak.app.ui.vet.VetHomeViewModel(
            reportRepository = ServiceLocator.reportRepository,
            alertRepository = ServiceLocator.alertRepository,
        )
    },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val displayName = SessionManager.name?.takeIf { it.isNotBlank() }
        ?: SessionManager.email?.takeIf { it.isNotBlank() }
        ?: "Vet"

    LaunchedEffect(Unit) {
        viewModel.refresh()
    }

    Scaffold(
        modifier = modifier,
        bottomBar = bottomBar,
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(bottom = AppSpacing.ListBottom),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                FadeInContent {
                    VetHeroHeader(
                        displayName = displayName,
                        email = SessionManager.email,
                        unreadAlerts = state.unreadAlertCount,
                        onOpenProfile = onOpenProfile,
                        onOpenAlerts = onOpenAlerts,
                    )
                }
            }

            item {
                FadeInContent(delayMillis = 40) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = AppSpacing.Screen),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        StatCard(
                            label = "In queue",
                            value = state.pendingCount.toString(),
                            icon = Icons.Default.ContentPaste,
                            accent = AppColors.Primary,
                            modifier = Modifier.weight(1f),
                            onClick = onOpenCaseQueue,
                        )
                        StatCard(
                            label = "High risk",
                            value = state.highRiskCount.toString(),
                            icon = Icons.Default.Warning,
                            accent = AppColors.Danger,
                            modifier = Modifier.weight(1f),
                            onClick = onOpenCaseQueue,
                        )
                        StatCard(
                            label = "Examined",
                            value = state.examinedCount.toString(),
                            icon = Icons.Default.CheckCircle,
                            accent = AppColors.Info,
                            modifier = Modifier.weight(1f),
                            onClick = onOpenCaseQueue,
                        )
                    }
                }
            }

            item {
                FadeInContent(delayMillis = 80) {
                    Column(
                        modifier = Modifier.padding(horizontal = AppSpacing.Screen),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        SectionCard(title = "Quick actions") {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                QuickActionRow(
                                    icon = Icons.Default.ContentPaste,
                                    title = "Case queue",
                                    subtitle = "Triage incoming farmer reports",
                                    tint = AppColors.Primary,
                                    onClick = onOpenCaseQueue,
                                )
                                QuickActionRow(
                                    icon = Icons.Default.Notifications,
                                    title = "Outbreak alerts",
                                    subtitle = "High-risk broadcast notifications",
                                    tint = AppColors.Danger,
                                    onClick = onOpenAlerts,
                                )
                                QuickActionRow(
                                    icon = Icons.Default.Person,
                                    title = "Profile",
                                    subtitle = "Account & sign out",
                                    tint = AppColors.Info,
                                    onClick = onOpenProfile,
                                )
                            }
                        }
                    }
                }
            }

            item {
                FadeInContent(delayMillis = 120) {
                    Column(
                        modifier = Modifier.padding(horizontal = AppSpacing.Screen),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            text = "Overview",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                        SectionCard {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                OverviewLine(
                                    label = "Pending cases",
                                    value = state.pendingCount.toString(),
                                    icon = Icons.Default.ContentPaste,
                                    highlight = state.pendingCount > 0,
                                )
                                OverviewLine(
                                    label = "High-risk cases",
                                    value = state.highRiskCount.toString(),
                                    icon = Icons.Default.Warning,
                                    highlight = state.highRiskCount > 0,
                                )
                                OverviewLine(
                                    label = "Unread alerts",
                                    value = state.unreadAlertCount.toString(),
                                    icon = Icons.Default.Notifications,
                                    highlight = state.unreadAlertCount > 0,
                                )
                                OverviewLine(
                                    label = "Email",
                                    value = SessionManager.email?.takeIf { it.isNotBlank() } ?: "Not set",
                                    icon = Icons.Default.Person,
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
private fun VetHeroHeader(
    displayName: String,
    email: String?,
    unreadAlerts: Int,
    onOpenProfile: () -> Unit,
    onOpenAlerts: () -> Unit,
) {
    val greeting = when {
        java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY) < 12 -> "Good morning"
        java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY) < 17 -> "Good afternoon"
        else -> "Good evening"
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AppSpacing.Screen, vertical = 4.dp)
            .clip(MaterialTheme.shapes.large)
            .background(
                Brush.linearGradient(
                    colors = listOf(AppColors.Secondary, AppColors.Accent),
                ),
            ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.MedicalServices,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(28.dp),
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "$greeting · Veterinarian",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.85f),
                )
                Text(
                    text = displayName,
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
                if (!email.isNullOrBlank()) {
                    Text(
                        text = email,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.85f),
                        maxLines = 1,
                    )
                }
            }
            IconButton(
                onClick = onOpenAlerts,
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.18f)),
            ) {
                Icon(
                    imageVector = Icons.Default.Notifications,
                    contentDescription = "Alerts",
                    tint = Color.White,
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            IconButton(
                onClick = onOpenProfile,
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.18f)),
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = "Profile",
                    tint = Color.White,
                )
            }
        }
    }
}
