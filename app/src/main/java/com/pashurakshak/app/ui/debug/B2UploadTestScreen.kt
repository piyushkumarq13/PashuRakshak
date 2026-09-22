package com.pashurakshak.app.ui.debug

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pashurakshak.app.BuildConfig
import com.pashurakshak.app.data.local.SymptomReport
import com.pashurakshak.app.data.remote.B2UploadService
import com.pashurakshak.app.di.ServiceLocator
import kotlinx.coroutines.launch

/**
 * Manual end-to-end test screen for B2UploadService.
 * Lists unsynced reports with a local photo and no remote URL; tap Upload on one
 * to confirm a single upload works before the Phase 7 sync queue takes over.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun B2UploadTestScreen(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val service = remember { ServiceLocator.b2UploadService }
    val reportRepository = remember { ServiceLocator.reportRepository }

    var pending by remember { mutableStateOf<List<SymptomReport>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var isUploading by remember { mutableStateOf(false) }
    var lastResult by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    suspend fun refresh() {
        isLoading = true
        error = null
        runCatching { reportRepository.getReportsPendingPhotoUpload() }
            .onSuccess { pending = it }
            .onFailure { error = it.message ?: "Failed to load pending photos" }
        isLoading = false
    }

    LaunchedEffect(Unit) { refresh() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("B2 Upload Test") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { scope.launch { refresh() } }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val credentialsConfigured = BuildConfig.B2_APPLICATION_KEY_ID.isNotBlank() &&
                BuildConfig.B2_APPLICATION_KEY.isNotBlank() &&
                BuildConfig.B2_BUCKET_NAME.isNotBlank()

            Text(
                text = if (credentialsConfigured) {
                    "B2 credentials: configured (bucket ${BuildConfig.B2_BUCKET_NAME})"
                } else {
                    "B2 credentials missing — fill B2_* fields in local.properties"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = if (credentialsConfigured) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.error
                },
            )

            Button(
                onClick = {
                    scope.launch {
                        isUploading = true
                        lastResult = null
                        val outcomes = service.uploadPendingReportPhotos()
                        lastResult = buildString {
                            append("${outcomes.size} photo(s) processed: ")
                            append(
                                outcomes.joinToString { (id, result) ->
                                    "${id.take(8)} → ${describe(result)}"
                                },
                            )
                        }
                        isUploading = false
                        refresh()
                    }
                },
                enabled = !isUploading && pending.isNotEmpty(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (isUploading) {
                    CircularProgressIndicator(
                        modifier = Modifier.padding(end = 8.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Icon(Icons.Default.Upload, contentDescription = null)
                    Text("  Upload all pending (${pending.size})")
                }
            }

            lastResult?.let { result ->
                Text(result, style = MaterialTheme.typography.bodySmall)
            }
            error?.let { message ->
                Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }

            if (isLoading) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator()
                }
            } else if (pending.isEmpty()) {
                Text(
                    text = "No pending photos (submit a Report with a photo first).",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(pending, key = { it.id }) { report ->
                        PendingPhotoCard(
                            report = report,
                            isUploading = isUploading,
                            onUpload = {
                                scope.launch {
                                    isUploading = true
                                    val result = service.uploadReportPhoto(report)
                                    lastResult = "Single upload: ${describe(result)}"
                                    isUploading = false
                                    refresh()
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PendingPhotoCard(
    report: SymptomReport,
    isUploading: Boolean,
    onUpload: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Report ${report.id.take(8)}",
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = report.photoLocalPath.substringAfterLast('/'),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "Risk ${report.riskScore} · ${report.status.dbValue}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            OutlinedButton(onClick = onUpload, enabled = !isUploading) {
                Text("Upload")
            }
        }
    }
}

private fun describe(result: B2UploadService.UploadResult): String = when (result) {
    is B2UploadService.UploadResult.Success -> "OK → ${result.fileUrl}"
    is B2UploadService.UploadResult.Failure -> "FAILED: ${result.message}"
    B2UploadService.UploadResult.Skipped -> "SKIPPED (offline/no file)"
}
