package com.pashurakshak.app.ui.farmer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pashurakshak.app.data.AnimalRepository
import com.pashurakshak.app.data.ReportRepository
import com.pashurakshak.app.data.local.Animal
import com.pashurakshak.app.data.local.ReportStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

enum class HealthStatus {
    HEALTHY,
    UNDER_OBSERVATION,
}

data class AnimalWithStatus(
    val animal: Animal,
    val healthStatus: HealthStatus,
)

data class MyAnimalsUiState(
    val isLoading: Boolean = true,
    val animals: List<AnimalWithStatus> = emptyList(),
    val error: String? = null,
)

class MyAnimalsViewModel(
    private val animalRepository: AnimalRepository,
    private val reportRepository: ReportRepository,
    private val farmerId: String,
) : ViewModel() {

    private val _uiState = MutableStateFlow(MyAnimalsUiState())
    val uiState: StateFlow<MyAnimalsUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            runCatching {
                val animals = animalRepository.getAll().filter { it.ownerFarmerId == farmerId }
                val reportsByAnimal = reportRepository.getAll().groupBy { it.animalId }
                animals.map { animal ->
                    val latest = reportsByAnimal[animal.id]?.maxByOrNull { it.createdAt }
                    val status = if (latest == null || latest.status == ReportStatus.RESOLVED) {
                        HealthStatus.HEALTHY
                    } else {
                        HealthStatus.UNDER_OBSERVATION
                    }
                    AnimalWithStatus(animal, status)
                }
            }.onSuccess { animals ->
                _uiState.update { it.copy(isLoading = false, animals = animals) }
            }.onFailure { error ->
                _uiState.update { it.copy(isLoading = false, error = error.message ?: "Failed to load animals") }
            }
        }
    }

    fun addAnimal(species: String, name: String) {
        viewModelScope.launch {
            runCatching {
                animalRepository.insert(
                    Animal(
                        ownerFarmerId = farmerId,
                        species = species,
                        name = name.trim(),
                        qrCodeId = "QR-" + UUID.randomUUID().toString().uppercase(),
                    )
                )
            }.onSuccess {
                refresh()
            }.onFailure { error ->
                _uiState.update { it.copy(error = error.message ?: "Failed to add animal") }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}
