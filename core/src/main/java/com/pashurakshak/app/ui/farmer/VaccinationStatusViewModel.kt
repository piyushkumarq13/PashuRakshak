package com.pashurakshak.app.ui.farmer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pashurakshak.app.data.AlertRepository
import com.pashurakshak.app.data.AnimalRepository
import com.pashurakshak.app.data.VaccinationRepository
import com.pashurakshak.app.data.local.Alert
import com.pashurakshak.app.data.local.Animal
import com.pashurakshak.app.data.local.Vaccination
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class VaccinationListItem(
    val animal: Animal,
    val vaccination: Vaccination,
)

data class VaccinationStatusUiState(
    val isLoading: Boolean = true,
    val items: List<VaccinationListItem> = emptyList(),
    val isSaving: Boolean = false,
    val notice: String? = null,
    val error: String? = null,
)

class VaccinationStatusViewModel(
    private val animalRepository: AnimalRepository,
    private val vaccinationRepository: VaccinationRepository,
    private val alertRepository: AlertRepository,
    private val farmerId: String,
) : ViewModel() {

    private val _uiState = MutableStateFlow(VaccinationStatusUiState())
    val uiState: StateFlow<VaccinationStatusUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            runCatching { loadItems() }.onSuccess { items ->
                _uiState.update { it.copy(isLoading = false, items = items, error = null) }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(isLoading = false, error = error.message ?: "Failed to load vaccinations")
                }
            }

            launch {
                runCatching { com.pashurakshak.app.data.sync.RemoteSync.pullAll() }
                runCatching { loadItems() }.onSuccess { items ->
                    _uiState.update { it.copy(isLoading = false, items = items) }
                }
            }
        }
    }

    private suspend fun loadItems(): List<VaccinationListItem> {
        val animals = animalRepository.getAll().filter { it.ownerFarmerId == farmerId }
        return animals.flatMap { animal ->
            val vaccinations = vaccinationRepository.getByAnimal(animal.id)
            vaccinations.map { vaccination ->
                VaccinationListItem(
                    animal = animal,
                    vaccination = vaccination,
                )
            }
        }
    }

    fun addVaccination(animalId: String, vaccineName: String, dateGiven: Long, nextDue: Long) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, error = null) }
            runCatching {
                val vaccination = Vaccination(
                    animalId = animalId,
                    vaccineName = vaccineName,
                    dateGiven = dateGiven,
                    nextDue = nextDue,
                )
                vaccinationRepository.insert(vaccination)
                runCatching { com.pashurakshak.app.data.sync.RemoteSync.pushVaccination(vaccination) }
                val animalLabel = animalRepository.getById(animalId)?.let { "${it.name} (${it.species})" }
                    ?: "animal"
                alertRepository.insert(
                    Alert(
                        recipientRole = "farmer",
                        recipientId = farmerId,
                        message = "Vaccination recorded for $animalLabel: $vaccineName.",
                    ),
                )
            }.onSuccess {
                _uiState.update { it.copy(isSaving = false, notice = "Vaccination recorded") }
                refresh()
            }.onFailure { error ->
                _uiState.update {
                    it.copy(isSaving = false, error = error.message ?: "Failed to save vaccination")
                }
            }
        }
    }

    fun clearNotice() {
        _uiState.update { it.copy(notice = null) }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    fun deleteVaccination(id: String) {
        viewModelScope.launch {
            runCatching {
                vaccinationRepository.delete(id)
                com.pashurakshak.app.data.sync.RemoteSync.pushVaccinationDelete(id)
            }.onSuccess { refresh() }
                .onFailure { error ->
                    _uiState.update { it.copy(error = error.message ?: "Failed to delete vaccination") }
                }
        }
    }

    fun editVaccination(vaccination: Vaccination) {
        viewModelScope.launch {
            runCatching {
                vaccinationRepository.update(vaccination)
                com.pashurakshak.app.data.sync.RemoteSync.pushVaccination(vaccination)
            }.onSuccess { refresh() }
                .onFailure { error ->
                    _uiState.update { it.copy(error = error.message ?: "Failed to update vaccination") }
                }
        }
    }
}
