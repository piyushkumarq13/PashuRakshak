package com.pashurakshak.app.ui.aiInsight

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pashurakshak.app.data.SessionManager

@Composable
fun AiInsightScreen(
    reportId: String,
    aiAdvisory: String?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AiInsightViewModel = viewModel { AiInsightViewModel(reportId, aiAdvisory) },
) {
    val state by viewModel.uiState.collectAsState()
    val advisory = state.aiAdvisory

    Scaffold(
        modifier = modifier,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "AI Insight",
                style = MaterialTheme.typography.headlineSmall,
            )

            if (state.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.padding(top = 8.dp),
                )
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (advisory != null) {
                        item {
                            MessageBubble(
                                role = "assistant",
                                text = advisory,
                            )
                        }
                    }
                    state.messages.forEach { message ->
                        item {
                            MessageBubble(
                                role = message.role,
                                text = message.text,
                            )
                        }
                    }
                    if (advisory == null && state.messages.isEmpty()) {
                        item {
                            Text(
                                text = "AI insight will appear once you're back online.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = state.inputText,
                    onValueChange = viewModel::onInputChanged,
                    label = { Text("Message") },
                    modifier = Modifier.weight(1f),
                )
                Button(
                    onClick = { viewModel.sendMessage() },
                    enabled = state.inputText.isNotBlank() && !state.isSending,
                    modifier = Modifier.weight(1f),
                ) {
                    if (state.isSending) {
                        CircularProgressIndicator(
                            modifier = Modifier.padding(end = 4.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    } else {
                        Text("Send")
                    }
                }
            }
        }
    }
}

@Composable
private fun MessageBubble(role: String, text: String) {
    Surface(
        color = if (role == "user") MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surface,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(12.dp),
        )
    }
}
