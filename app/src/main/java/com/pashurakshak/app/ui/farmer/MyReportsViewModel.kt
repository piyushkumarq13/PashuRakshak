package com.pashurakshak.app.ui.farmer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
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

data class MyReportItem(
    val report: SymptomReport,
    val animal: Animal?,
    val vetVisited: Boolean,
    val isResolved: Boolean,
)

data class MyReportsUiState(
    val isLoading: Boolean = true,
    val items: List<MyReportItem> = emptyList(),
    val error: String? = null,
)

class MyReportsViewModel(
    private val reportRepository: ReportRepository,
    private val animalRepository: AnimalRepository,
    private val farmerId: String,
) : ViewModel() {

    private val _uiState = MutableStateFlow(MyReportsUiState())
    val uiState: StateFlow<MyReportsUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            runCatching { com.pashurakshak.app.data.sync.RemoteSync.pullAll() }
            runCatching {
                val animalsById = animalRepository.getAll().associateBy { it.id }
                reportRepository.getByFarmer(farmerId)
                    .sortedByDescending { it.createdAt }
                    .map { report ->
                        val animal = animalsById[report.animalId]
                        val status = report.status
                        val vetVisited = status == ReportStatus.EXAMINED ||
                            status == ReportStatus.SAMPLE_SENT ||
                            status == ReportStatus.CONFIRMED ||
                            status == ReportStatus.RESOLVED
                        val isResolved = status == ReportStatus.RESOLVED
                        MyReportItem(report = report, animal = animal, vetVisited = vetVisited, isResolved = isResolved)
                    }
            }.onSuccess { items ->
                _uiState.update { it.copy(isLoading = false, items = items) }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(isLoading = false, error = error.message ?: "Failed to load reports")
                }
            }
        }
    }
}
