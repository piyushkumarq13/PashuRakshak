package com.pashurakshak.app.ui.vet

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pashurakshak.app.data.AlertRepository
import com.pashurakshak.app.data.AnimalRepository
import com.pashurakshak.app.data.ReportRepository
import com.pashurakshak.app.data.local.Alert
import com.pashurakshak.app.data.local.Animal
import com.pashurakshak.app.data.local.ReportStatus
import com.pashurakshak.app.data.local.SymptomReport
import com.pashurakshak.app.ui.farmer.prettifyStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CaseDetailUiState(
    val isLoading: Boolean = true,
    val report: SymptomReport? = null,
    val animal: Animal? = null,
    val isSubmittingFieldCheck: Boolean = false,
    val notice: String? = null,
    val error: String? = null,
) {
    val canMarkExamined: Boolean
        get() = report != null &&
            (report.status == ReportStatus.REPORTED || report.status == ReportStatus.VET_ASSIGNED) &&
            !isSubmittingFieldCheck
}

class CaseDetailViewModel(
    private val reportId: String,
    private val reportRepository: ReportRepository,
    private val animalRepository: AnimalRepository,
    private val alertRepository: AlertRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CaseDetailUiState())
    val uiState: StateFlow<CaseDetailUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    private fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            runCatching {
                val report = reportRepository.getById(reportId) ?: error("Report not found")
                val animal = animalRepository.getById(report.animalId)
                report to animal
            }.onSuccess { (report, animal) ->
                _uiState.update {
                    it.copy(isLoading = false, report = report, animal = animal)
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(isLoading = false, error = error.message ?: "Failed to load case")
                }
            }
        }
    }

    fun submitFieldCheck(sampleRequired: Boolean) {
        val report = _uiState.value.report ?: return
        val newStatus = if (sampleRequired) ReportStatus.SAMPLE_SENT else ReportStatus.EXAMINED

        viewModelScope.launch {
            _uiState.update { it.copy(isSubmittingFieldCheck = true) }
            runCatching {
                reportRepository.update(report.copy(status = newStatus))
                val animalLabel = _uiState.value.animal?.let { "${it.name} (${it.species})" }
                    ?: "Animal ${report.animalId.take(8)}"
                val statusLabel = prettifyStatus(newStatus.dbValue)
                alertRepository.insert(
                    Alert(
                        recipientRole = "farmer",
                        recipientId = report.farmerId,
                        message = if (sampleRequired) {
                            "A vet examined $animalLabel — a sample was sent for lab testing."
                        } else {
                            "A vet examined $animalLabel — status: $statusLabel."
                        },
                    ),
                )
            }.onSuccess {
                _uiState.update {
                    it.copy(
                        isSubmittingFieldCheck = false,
                        report = report.copy(status = newStatus),
                        notice = if (sampleRequired) {
                            "Status updated to: sample sent"
                        } else {
                            "Status updated to: examined"
                        },
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isSubmittingFieldCheck = false,
                        notice = error.message ?: "Failed to update status",
                    )
                }
            }
        }
    }

    fun clearNotice() {
        _uiState.update { it.copy(notice = null) }
    }
}
