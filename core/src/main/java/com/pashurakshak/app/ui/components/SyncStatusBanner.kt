package com.pashurakshak.app.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pashurakshak.app.data.sync.SyncStatusHolder
import kotlinx.coroutines.delay

/**
 * Thin status banner: "X reports pending sync" / "Syncing…".
 * Hidden when fully synced so it never wastes vertical space.
 * Sits above the NavHost; re-checks the local queue every 30s.
 */
@Composable
fun SyncStatusBanner(
    modifier: Modifier = Modifier,
) {
    val status by SyncStatusHolder.status.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        SyncStatusHolder.refreshPendingCount()
        while (true) {
            delay(30_000)
            SyncStatusHolder.refreshPendingCount()
        }
    }

    val label = when {
        status.isSyncing -> "Syncing…"
        status.pendingCount > 0 ->
            "${status.pendingCount} report${if (status.pendingCount == 1) "" else "s"} pending sync"
        else -> return
    }
    val containerColor = if (status.isSyncing) {
        MaterialTheme.colorScheme.secondaryContainer
    } else {
        MaterialTheme.colorScheme.tertiaryContainer
    }
    val contentColor = if (status.isSyncing) {
        MaterialTheme.colorScheme.onSecondaryContainer
    } else {
        MaterialTheme.colorScheme.onTertiaryContainer
    }

    Surface(
        color = containerColor,
        contentColor = contentColor,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}
