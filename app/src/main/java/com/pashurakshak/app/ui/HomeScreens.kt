package com.pashurakshak.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.Button
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pashurakshak.app.BuildConfig
import com.pashurakshak.app.navigation.Screen

@Composable
fun FarmerHomeScreen(
    onOpenMyReports: () -> Unit,
    onOpenB2Test: () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    HomePlaceholder(
        text = "Logged in as: Farmer",
        bottomBar = bottomBar,
        modifier = modifier,
        onOpenMyReports = onOpenMyReports,
        onOpenB2Test = onOpenB2Test,
    )
}

@Composable
fun VetHomeScreen(
    bottomBar: @Composable () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    HomePlaceholder(text = "Logged in as: Vet", bottomBar = bottomBar, modifier = modifier)
}

@Composable
private fun HomePlaceholder(
    text: String,
    bottomBar: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    onOpenMyReports: (() -> Unit)? = null,
    onOpenB2Test: (() -> Unit)? = null,
) {
    Scaffold(
        modifier = modifier,
        bottomBar = bottomBar,
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = text,
                    style = MaterialTheme.typography.headlineSmall,
                )
                if (onOpenMyReports != null) {
                    Button(
                        onClick = onOpenMyReports,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("My Reports")
                    }
                }
                // Debug-only entry to manually verify a single B2 photo upload end-to-end.
                if (BuildConfig.DEBUG && onOpenB2Test != null) {
                    OutlinedButton(
                        onClick = onOpenB2Test,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("B2 Upload Test")
                    }
                }
            }
        }
    }
}
