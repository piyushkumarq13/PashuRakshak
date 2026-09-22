package com.pashurakshak.app.ui.components

import android.content.Context
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.pashurakshak.app.data.local.SymptomReport
import com.pashurakshak.app.data.remote.B2UploadService
import com.pashurakshak.app.di.ServiceLocator
import java.io.File

@Composable
fun ReportPhoto(
    report: SymptomReport? = null,
    remoteUrl: String? = null,
    localPath: String = "",
    modifier: Modifier = Modifier,
    contentDescription: String = "Report photo",
) {
    val context = LocalContext.current
    val b2 = remember { ServiceLocator.b2UploadService }
    var displayUrl by remember { mutableStateOf<String?>(null) }

    val effectiveRemote = remoteUrl ?: report?.photoRemoteUrl

    LaunchedEffect(effectiveRemote) {
        displayUrl = if (!effectiveRemote.isNullOrBlank()) {
            b2.withAuth(effectiveRemote)
        } else {
            null
        }
    }

    val model = remember(displayUrl, localPath) {
        when {
            displayUrl != null -> ImageRequest.Builder(context).data(displayUrl).build()
            localPath.isNotBlank() -> File(localPath)
            else -> null
        }
    }

    if (model == null) {
        androidx.compose.foundation.layout.Box(
            modifier = modifier
                .fillMaxWidth()
                .height(180.dp),
            contentAlignment = androidx.compose.ui.Alignment.Center,
        ) {
            Text(
                text = "No photo",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    } else {
        AsyncImage(
            model = model,
            contentDescription = contentDescription,
            modifier = modifier
                .fillMaxWidth()
                .height(180.dp),
        )
    }
}
