package com.pashurakshak.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pashurakshak.app.data.SessionManager

@Composable
fun RoleSelectScreen(
    onRoleSelected: (SessionManager.Role) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "PashuRakshak",
            style = MaterialTheme.typography.headlineLarge,
        )
        Text(
            text = "Choose how you want to continue",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(
            onClick = { onRoleSelected(SessionManager.Role.FARMER) },
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp),
        ) {
            Text(
                text = "Continue as Farmer",
                style = MaterialTheme.typography.titleLarge,
            )
        }
        OutlinedButton(
            onClick = { onRoleSelected(SessionManager.Role.VET) },
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp),
        ) {
            Text(
                text = "Continue as Vet",
                style = MaterialTheme.typography.titleLarge,
            )
        }
    }
}
