package com.pashurakshak.app.ui.farmer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pashurakshak.app.data.AlertRepository
import com.pashurakshak.app.data.local.Alert
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AlertsUiState(
    val isLoading: Boolean = true,
    val alerts: List<Alert> = emptyList(),
    val error: String? = null,
)

class AlertsViewModel(
    private val alertRepository: AlertRepository,
    private val recipientRole: String,
    /** Null → role-wide query (e.g. all vets see high-risk broadcasts). */
    private val recipientId: String?,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AlertsUiState())
    val uiState: StateFlow<AlertsUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            runCatching { com.pashurakshak.app.data.sync.RemoteSync.pullAll() }
            runCatching {
                if (recipientId == null) {
                    alertRepository.getForRole(recipientRole)
                } else {
                    alertRepository.getForRecipient(recipientRole, recipientId)
                }
            }.onSuccess { alerts ->
                _uiState.update { it.copy(isLoading = false, alerts = alerts) }
            }.onFailure { error ->
                _uiState.update { it.copy(isLoading = false, error = error.message ?: "Failed to load alerts") }
            }
        }
    }

    fun onAlertClicked(alert: Alert) {
        if (alert.read) return
        viewModelScope.launch {
            runCatching { alertRepository.markRead(alert.id) }
                .onSuccess { refresh() }
                .onFailure { error ->
                    _uiState.update { it.copy(error = error.message ?: "Failed to mark alert read") }
                }
        }
    }
}
