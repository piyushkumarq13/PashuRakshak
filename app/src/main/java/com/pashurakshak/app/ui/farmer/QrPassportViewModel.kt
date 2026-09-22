package com.pashurakshak.app.ui.farmer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pashurakshak.app.data.AnimalRepository
import com.pashurakshak.app.data.VaccinationRepository
import com.pashurakshak.app.data.local.Animal
import com.pashurakshak.app.data.local.Vaccination
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class QrPassportUiState(
    val isLoading: Boolean = true,
    val animal: Animal? = null,
    val vaccinations: List<Vaccination> = emptyList(),
    val error: String? = null,
) {
    val nextDue: Long? get() = vaccinations.minOfOrNull { it.nextDue }
    val isOverdue: Boolean get() = nextDue?.let { it < System.currentTimeMillis() } == true
}

class QrPassportViewModel(
    private val animalId: String,
    private val animalRepository: AnimalRepository,
    private val vaccinationRepository: VaccinationRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(QrPassportUiState())
    val uiState: StateFlow<QrPassportUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    private fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            runCatching {
                val animal = animalRepository.getById(animalId)
                    ?: error("Animal not found")
                val vaccinations = vaccinationRepository.getByAnimal(animalId)
                    .sortedByDescending { it.dateGiven }
                animal to vaccinations
            }.onSuccess { (animal, vaccinations) ->
                _uiState.update {
                    it.copy(isLoading = false, animal = animal, vaccinations = vaccinations)
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(isLoading = false, error = error.message ?: "Failed to load passport")
                }
            }
        }
    }
}
