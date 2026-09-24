package com.pashurakshak.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pashurakshak.app.data.AlertRepository
import com.pashurakshak.app.data.AnimalRepository
import com.pashurakshak.app.data.ReportRepository
import com.pashurakshak.app.data.VaccinationRepository
import com.pashurakshak.app.data.local.ReportStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HomeUiState(
    val isLoading: Boolean = true,
    val animalCount: Int = 0,
    val reportCount: Int = 0,
    val activeReportCount: Int = 0,
    val unreadAlertCount: Int = 0,
    val vaccinationsDue: Int = 0,
    val error: String? = null,
)

class HomeViewModel(
    private val animalRepository: AnimalRepository,
    private val reportRepository: ReportRepository,
    private val vaccinationRepository: VaccinationRepository,
    private val alertRepository: AlertRepository,
    private val farmerId: String,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            // Paint local data immediately so the UI never waits on the network.
            runCatching { buildState() }.onSuccess { state ->
                _uiState.update { state }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = error.message ?: "Failed to load dashboard",
                    )
                }
            }

            // Refresh from the backend in the background, then repaint.
            launch {
                runCatching { com.pashurakshak.app.data.sync.RemoteSync.pullAll() }
                runCatching { buildState() }.onSuccess { state ->
                    _uiState.update { state }
                }
            }
        }
    }

    private suspend fun buildState(): HomeUiState {
        val animals = animalRepository.getAll().filter { it.ownerFarmerId == farmerId }
        val reports = reportRepository.getByFarmer(farmerId)
        val vaccinations = animals.flatMap { animal ->
            vaccinationRepository.getByAnimal(animal.id)
        }
        val now = System.currentTimeMillis()
        val soon = now + 30L * 24 * 60 * 60 * 1000
        val dueSoon = vaccinations.count { it.nextDue in now..soon || it.nextDue < now }
        val alerts = alertRepository.getAll()
            .filter { it.recipientRole == "farmer" && it.recipientId == farmerId }
        return HomeUiState(
            isLoading = false,
            animalCount = animals.size,
            reportCount = reports.size,
            activeReportCount = reports.count { it.status != ReportStatus.RESOLVED },
            unreadAlertCount = alerts.count { !it.read },
            vaccinationsDue = dueSoon,
        )
    }
}
