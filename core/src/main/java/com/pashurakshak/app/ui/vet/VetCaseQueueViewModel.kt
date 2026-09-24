package com.pashurakshak.app.ui.vet

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pashurakshak.app.data.AnimalRepository
import com.pashurakshak.app.data.ReportRepository
import com.pashurakshak.app.data.local.Animal
import com.pashurakshak.app.data.local.ReportStatus
import com.pashurakshak.app.data.local.SymptomReport
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class QueueItem(
    val report: SymptomReport,
    val animal: Animal?,
    val riskLevel: RiskLevel,
)

data class VetCaseQueueUiState(
    val isLoading: Boolean = true,
    val items: List<QueueItem> = emptyList(),
    val error: String? = null,
    val scanQrReportId: String? = null,
)

class VetCaseQueueViewModel(
    private val reportRepository: ReportRepository,
    private val animalRepository: AnimalRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(VetCaseQueueUiState())
    val uiState: StateFlow<VetCaseQueueUiState> = _uiState.asStateFlow()

    fun requestQrScan(reportId: String) {
        _uiState.update { it.copy(scanQrReportId = reportId) }
    }

    fun clearQrScanRequest() {
        _uiState.update { it.copy(scanQrReportId = null) }
    }

    fun markAsExamined(reportId: String) {
        viewModelScope.launch {
            val item = _uiState.value.items.find { it.report.id == reportId }
            val report = item?.report ?: return@launch
            runCatching {
                reportRepository.update(report.copy(status = ReportStatus.EXAMINED, synced = false))
            }.onSuccess {
                refresh()
            }.onFailure { error ->
                _uiState.update {
                    it.copy(error = error.message ?: "Failed to mark as examined")
                }
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            // Local-first: show the cached queue immediately.
            runCatching { loadQueue() }.onSuccess { items ->
                _uiState.update { it.copy(isLoading = false, items = items, error = null) }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(isLoading = false, error = error.message ?: "Failed to load case queue")
                }
            }

            launch {
                runCatching { com.pashurakshak.app.data.sync.RemoteSync.pullAll() }
                runCatching { loadQueue() }.onSuccess { items ->
                    _uiState.update { it.copy(isLoading = false, items = items) }
                }
            }
        }
    }

    private suspend fun loadQueue(): List<QueueItem> {
        val animalsById = animalRepository.getAll().associateBy { it.id }
        return reportRepository.getAll()
            .filter {
                it.status == ReportStatus.REPORTED || it.status == ReportStatus.VET_ASSIGNED
            }
            .sortedWith(
                compareByDescending<SymptomReport> { it.riskScore }
                    .thenByDescending { it.createdAt },
            )
            .map { report ->
                QueueItem(
                    report = report,
                    animal = animalsById[report.animalId],
                    riskLevel = riskLevelFor(report.riskScore),
                )
            }
    }
}
