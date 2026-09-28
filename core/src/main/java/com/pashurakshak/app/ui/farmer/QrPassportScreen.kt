package com.pashurakshak.app.ui.farmer

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.zxing.BarcodeFormat
import com.journeyapps.barcodescanner.BarcodeEncoder
import com.pashurakshak.app.di.ServiceLocator
import com.pashurakshak.app.ui.components.AppSpacing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
                title = { Text("QR Passport") },
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
                        .padding(AppSpacing.LG),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(AppSpacing.LG),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(AppSpacing.LG),
                        ) {
                            Text(
                                text = "Animal Passport",
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
                            Text(
                                text = animal.name,
                                style = MaterialTheme.typography.headlineSmall,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Text(
                                text = animal.species,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.fillMaxWidth(),
                            )

                            HorizontalDivider()

                            Text(
                                text = "Vaccination summary",
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Text(
                                text = "${state.vaccinations.size} dose(s) recorded",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            if (state.vaccinations.isNotEmpty()) {
                                val last = state.vaccinations.first()
                                Text(
                                    text = "Last: ${last.vaccineName} — ${formatDateMillis(last.dateGiven)}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                            state.nextDue?.let { nextDue ->
                                val overdue = state.isOverdue
                                Text(
                                    text = buildString {
                                        append("Next due: ")
                                        append(formatDateMillis(nextDue))
                                        if (overdue) append(" (overdue)")
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (overdue) {
                                        MaterialTheme.colorScheme.error
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    },
                                    fontWeight = if (overdue) FontWeight.Bold else FontWeight.Normal,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            } ?: Text(
                                text = "No vaccinations scheduled",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.fillMaxWidth(),
                            )

                            HorizontalDivider()

                            Text(
                                text = "QR ID: ${animal.qrCodeId}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }
        }
    }
}
