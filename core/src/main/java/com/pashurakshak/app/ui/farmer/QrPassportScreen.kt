package com.pashurakshak.app.ui.farmer

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.Vaccines
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.zxing.BarcodeFormat
import com.journeyapps.barcodescanner.BarcodeEncoder
import com.pashurakshak.app.di.ServiceLocator
import com.pashurakshak.app.ui.components.AppSpacing
import com.pashurakshak.app.ui.components.SectionCard
import com.pashurakshak.app.ui.components.StatusPill
import com.pashurakshak.app.ui.theme.AppColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private fun passportSpeciesEmoji(species: String): String = when (species.lowercase()) {
    "cow" -> "🐄"
    "buffalo" -> "🐃"
    "goat" -> "🐐"
    "sheep" -> "🐑"
    "pig" -> "🐖"
    "poultry" -> "🐔"
    "horse" -> "🐴"
    "camel" -> "🐫"
    else -> "🐾"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QrPassportScreen(
    animalId: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: QrPassportViewModel = viewModel {
        QrPassportViewModel(
            animalId = animalId,
            animalRepository = ServiceLocator.animalRepository,
            vaccinationRepository = ServiceLocator.vaccinationRepository,
        )
    },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Animal Passport") },
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
    ) { innerPadding ->
        val animal = state.animal
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

            animal == null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = state.error ?: "Animal not found",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }

            else -> {
                val qrResult by produceState<Result<android.graphics.Bitmap>?>(initialValue = null, animal.qrCodeId) {
                    value = withContext(Dispatchers.Default) {
                        runCatching {
                            BarcodeEncoder().encodeBitmap(
                                animal.qrCodeId,
                                BarcodeFormat.QR_CODE,
                                640,
                                640,
                            )
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .verticalScroll(rememberScrollState())
                        .padding(AppSpacing.Screen),
                    verticalArrangement = Arrangement.spacedBy(AppSpacing.MD),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
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
                                .padding(AppSpacing.LG),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = passportSpeciesEmoji(animal.species),
                                    style = MaterialTheme.typography.headlineMedium,
                                )
                            }
                            Spacer(modifier = Modifier.width(AppSpacing.MD))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = animal.name,
                                    style = MaterialTheme.typography.titleLarge,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                )
                                Text(
                                    text = animal.species,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.White.copy(alpha = 0.85f),
                                )
                                Surface(
                                    color = Color.White.copy(alpha = 0.18f),
                                    contentColor = Color.White,
                                    shape = RoundedCornerShape(50),
                                    modifier = Modifier.padding(top = AppSpacing.SM),
                                ) {
                                    Text(
                                        text = "ID · ${animal.qrCodeId.takeLast(8)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                                    )
                                }
                            }
                        }
                    }

                    Surface(
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 1.dp,
                        shadowElevation = 2.dp,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(AppSpacing.LG),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(AppSpacing.MD),
                        ) {
                            Text(
                                text = "Scan for full health record",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            val qrBitmap = qrResult?.getOrNull()
                            when {
                                qrResult == null -> Box(
                                    modifier = Modifier.size(220.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    CircularProgressIndicator()
                                }
                                qrBitmap != null -> Image(
                                    bitmap = qrBitmap.asImageBitmap(),
                                    contentDescription = "QR code for ${animal.name}",
                                    modifier = Modifier.size(220.dp),
                                    contentScale = ContentScale.Fit,
                                )
                                else -> Text(
                                    text = "Could not generate QR code",
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                    }

                    SectionCard(title = "Vaccination summary") {
                        Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.MD)) {
                            PassportRow(
                                icon = Icons.Default.Vaccines,
                                label = "Doses recorded",
                                value = "${state.vaccinations.size}",
                            )
                            if (state.vaccinations.isNotEmpty()) {
                                val last = state.vaccinations.first()
                                PassportRow(
                                    icon = Icons.Default.MedicalServices,
                                    label = "Last dose",
                                    value = "${last.vaccineName} · ${formatDateMillis(last.dateGiven)}",
                                )
                            }
                            val nextDue = state.nextDue
                            if (nextDue != null) {
                                val overdue = state.isOverdue
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Event,
                                        contentDescription = null,
                                        tint = if (overdue) {
                                            AppColors.Danger
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                        modifier = Modifier.size(20.dp),
                                    )
                                    Spacer(modifier = Modifier.width(AppSpacing.MD))
                                    Text(
                                        text = "Next due",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.weight(1f),
                                    )
                                    StatusPill(
                                        text = buildString {
                                            append(formatDateMillis(nextDue))
                                            if (overdue) append(" · overdue")
                                        },
                                        container = if (overdue) {
                                            AppColors.SoftRed
                                        } else {
                                            AppColors.SoftBlue
                                        },
                                        content = if (overdue) {
                                            AppColors.Danger
                                        } else {
                                            AppColors.Info
                                        },
                                    )
                                }
                            } else {
                                PassportRow(
                                    icon = Icons.Default.Event,
                                    label = "Next due",
                                    value = "None scheduled",
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
private fun PassportRow(
    icon: ImageVector,
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
        Spacer(modifier = Modifier.width(AppSpacing.MD))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
