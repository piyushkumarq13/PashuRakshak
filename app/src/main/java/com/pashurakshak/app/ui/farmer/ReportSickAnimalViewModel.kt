package com.pashurakshak.app.ui.farmer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pashurakshak.app.data.AlertRepository
import com.pashurakshak.app.data.AnimalRepository
import com.pashurakshak.app.data.ReportRepository
import com.pashurakshak.app.data.SessionManager
import com.pashurakshak.app.data.local.Alert
import com.pashurakshak.app.data.local.Animal
import com.pashurakshak.app.data.local.ReportStatus
import com.pashurakshak.app.data.local.SymptomReport
import com.pashurakshak.app.data.sync.SyncScheduler
import com.pashurakshak.app.di.ServiceLocator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class Symptom(val label: String, val basePoints: Int) {
    FEVER("Fever", 20),
    COUGH("Cough", 0),
    NOT_EATING("Not Eating", 0),
    LESS_MILK("Less Milk", 15),
    SWELLING("Swelling", 0),
    MOUTH_PROBLEM("Mouth Problem", 0),
}

/**
 * Placeholder client-side weighting — refined server-side later.
 * Fever +20, Less Milk +15, +10 for each additional symptom beyond the first.
 */
internal fun calculateRiskScore(symptoms: Collection<Symptom>): Int {
    if (symptoms.isEmpty()) return 0
    val base = symptoms.sumOf { it.basePoints }
    val additional = 10 * (symptoms.size - 1)
    return base + additional
}

data class ReportSickAnimalUiState(
    val isLoadingAnimals: Boolean = true,
    val animals: List<Animal> = emptyList(),
    val selectedAnimalId: String? = null,
    val selectedSymptoms: Set<Symptom> = emptySet(),
    val photoPath: String? = null,
    val latitudeText: String = "",
    val longitudeText: String = "",
    val isSubmitting: Boolean = false,
    val submitted: Boolean = false,
    val error: String? = null,
) {
    val latitude: Double? get() = latitudeText.toDoubleOrNull()?.takeIf { it in -90.0..90.0 }
    val longitude: Double? get() = longitudeText.toDoubleOrNull()?.takeIf { it in -180.0..180.0 }
    val riskScore: Int get() = calculateRiskScore(selectedSymptoms)
    val canSubmit: Boolean
        get() = selectedAnimalId != null &&
            selectedSymptoms.isNotEmpty() &&
            latitude != null &&
            longitude != null &&
            !isSubmitting
}

class ReportSickAnimalViewModel(
    private val animalRepository: AnimalRepository,
    private val reportRepository: ReportRepository,
    private val alertRepository: AlertRepository,
    private val farmerId: String,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ReportSickAnimalUiState())
    val uiState: StateFlow<ReportSickAnimalUiState> = _uiState.asStateFlow()

    init {
        loadAnimals()
    }

    fun loadAnimals() {
        viewModelScope.launch {
            runCatching { com.pashurakshak.app.data.sync.RemoteSync.pullAll() }
            runCatching {
                animalRepository.getAll().filter { it.ownerFarmerId == farmerId }
            }.onSuccess { animals ->
                _uiState.update { it.copy(isLoadingAnimals = false, animals = animals) }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(isLoadingAnimals = false, error = error.message ?: "Failed to load animals")
                }
            }
        }
    }

    fun onAnimalSelected(animalId: String) {
        _uiState.update { it.copy(selectedAnimalId = animalId) }
    }

    fun onSymptomToggled(symptom: Symptom) {
        _uiState.update { state ->
            val selected = state.selectedSymptoms.toMutableSet()
            if (!selected.add(symptom)) selected.remove(symptom)
            state.copy(selectedSymptoms = selected)
        }
    }

    fun onPhotoSelected(path: String) {
        _uiState.update { it.copy(photoPath = path) }
    }

    fun onPhotoRemoved() {
        _uiState.update { it.copy(photoPath = null) }
    }

    fun onLocationFetched(latitude: Double, longitude: Double) {
        _uiState.update {
            it.copy(
                latitudeText = latitude.toString(),
                longitudeText = longitude.toString(),
                error = null,
            )
        }
    }

    fun onLatitudeChanged(text: String) {
        _uiState.update { it.copy(latitudeText = text) }
    }

    fun onLongitudeChanged(text: String) {
        _uiState.update { it.copy(longitudeText = text) }
    }

    fun onLocationError(message: String) {
        _uiState.update { it.copy(error = message) }
    }

    fun submit() {
        val state = _uiState.value
        if (!state.canSubmit) return
        val animalId = state.selectedAnimalId ?: return
        val latitude = state.latitude ?: return
        val longitude = state.longitude ?: return

        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true, error = null) }
            runCatching {
                val symptoms = Symptom.entries.filter { it in state.selectedSymptoms }
                val riskScore = calculateRiskScore(symptoms)
                val breakdown = buildMap {
                    symptoms.forEach { put(it.label, it.basePoints) }
                    val bonus = 10 * (symptoms.size - 1)
                    if (bonus > 0) put("multiple_symptoms", bonus)
                }
                val animal = state.animals.firstOrNull { it.id == animalId }
                val animalLabel = animal?.let { "${it.name} (${it.species})" } ?: "animal"
                reportRepository.insert(
                    SymptomReport(
                        animalId = animalId,
                        farmerId = farmerId,
                        symptoms = symptoms.map { it.label },
                        photoLocalPath = state.photoPath.orEmpty(),
                        photoRemoteUrl = null,
                        latitude = latitude,
                        longitude = longitude,
                        riskScore = riskScore,
                        riskBreakdown = breakdown,
                        status = ReportStatus.REPORTED,
                        synced = false,
                    )
                )
                // Alerts are best-effort — a failed alert must not fail the report itself.
                runCatching {
                    alertRepository.insert(
                        Alert(
                            recipientRole = "farmer",
                            recipientId = farmerId,
                            message = "Report submitted for $animalLabel (risk $riskScore). " +
                                "A vet will review it.",
                        ),
                    )
                    // Placeholder HIGH threshold — matches RiskLevel.kt (>= 60).
                    if (riskScore >= 60) {
                        alertRepository.insert(
                            Alert(
                                recipientRole = "vet",
                                recipientId = SessionManager.vetId,
                                message = "New high-risk case: $animalLabel — risk score $riskScore.",
                            ),
                        )
                    }
                }
            }.onSuccess {
                SyncScheduler.triggerNow(ServiceLocator.context)
                _uiState.update { it.copy(isSubmitting = false, submitted = true) }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(isSubmitting = false, error = error.message ?: "Failed to submit report")
                }
            }
        }
    }

    fun resetAfterSubmit() {
        _uiState.update {
            ReportSickAnimalUiState(
                isLoadingAnimals = false,
                animals = _uiState.value.animals,
            )
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}
