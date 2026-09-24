package com.pashurakshak.app.ui.vet

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pashurakshak.app.data.AlertRepository
import com.pashurakshak.app.data.ReportRepository
import com.pashurakshak.app.data.local.ReportStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class VetHomeUiState(
    val isLoading: Boolean = true,
    val pendingCount: Int = 0,
    val highRiskCount: Int = 0,
    val examinedCount: Int = 0,
    val unreadAlertCount: Int = 0,
    val error: String? = null,
)

class VetHomeViewModel(
    private val reportRepository: ReportRepository,
    private val alertRepository: AlertRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(VetHomeUiState())
    val uiState: StateFlow<VetHomeUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            // Local-first: show cached stats immediately.
            runCatching {
                buildState()
            }.onSuccess { state ->
                _uiState.update { state }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(isLoading = false, error = error.message ?: "Failed to load dashboard")
                }
            }

            launch {
                runCatching { com.pashurakshak.app.data.sync.RemoteSync.pullAll() }
                runCatching { buildState() }.onSuccess { state ->
                    _uiState.update { state }
                }
            }
        }
    }

    private suspend fun buildState(): VetHomeUiState {
        val reports = reportRepository.getAll()
        val pending = reports.filter {
            it.status == ReportStatus.REPORTED || it.status == ReportStatus.VET_ASSIGNED
        }
        val examined = reports.count {
            it.status != ReportStatus.REPORTED && it.status != ReportStatus.VET_ASSIGNED
        }
        val alerts = alertRepository.getAll()
            .filter { it.recipientRole == "vet" && !it.read }
        return VetHomeUiState(
            isLoading = false,
            pendingCount = pending.size,
            highRiskCount = pending.count { riskLevelFor(it.riskScore) == RiskLevel.HIGH },
            examinedCount = examined,
            unreadAlertCount = alerts.size,
        )
    }
}
