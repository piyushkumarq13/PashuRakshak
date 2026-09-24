package com.pashurakshak.app.ui.farmer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pashurakshak.app.data.AlertRepository
import com.pashurakshak.app.data.AnimalRepository
import com.pashurakshak.app.data.FarmerRepository
import com.pashurakshak.app.data.ReportRepository
import com.pashurakshak.app.data.SessionManager
import com.pashurakshak.app.data.local.Alert
import com.pashurakshak.app.data.local.Animal
import com.pashurakshak.app.data.local.ReportStatus
import com.pashurakshak.app.data.local.SymptomReport
import com.pashurakshak.app.data.remote.ReportPushApi
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

internal fun calculateRiskScore(symptoms: Collection<Symptom>): Int {
    if (symptoms.isEmpty()) return 0
    val base = symptoms.sumOf { it.basePoints }
    val additional = 10 * (symptoms.size - 1)
    return base + additional
}

data class ReportSickAnimalUiState(
    val isLoadingAnimals: Boolean = true,
    val isLoadingProfile: Boolean = true,
    val animals: List<Animal> = emptyList(),
    val selectedAnimalId: String? = null,
    val selectedSymptoms: Set<Symptom> = emptySet(),
    val photoPath: String? = null,
    val latitudeText: String = "",
    val longitudeText: String = "",
    val villageText: String = "",
    val isSubmitting: Boolean = false,
    val submitted: Boolean = false,
    val error: String? = null,
    val aiAdvisory: String? = null,
    val lastReportId: String? = null,
) {
    val latitude: Double? get() = latitudeText.toDoubleOrNull()?.takeIf { it in -90.0..90.0 }
    val longitude: Double? get() = longitudeText.toDoubleOrNull()?.takeIf { it in -180.0..180.0 }
    val riskScore: Int get() = calculateRiskScore(selectedSymptoms)
    val canSubmit: Boolean
        get() = selectedAnimalId != null &&
            selectedSymptoms.isNotEmpty() &&
            latitude != null &&
            longitude != null &&
            villageText.isNotBlank() &&
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
        loadFarmerProfile()
    }

    private fun loadAnimals() {
        viewModelScope.launch {
            // Local-first, then refresh from the backend in the background.
            runCatching {
                animalRepository.getAll().filter { it.ownerFarmerId == farmerId }
            }.onSuccess { animals ->
                _uiState.update { it.copy(isLoadingAnimals = false, animals = animals) }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(isLoadingAnimals = false, error = error.message ?: "Failed to load animals")
                }
            }

            launch {
                runCatching { com.pashurakshak.app.data.sync.RemoteSync.pullAll() }
                runCatching {
                    animalRepository.getAll().filter { it.ownerFarmerId == farmerId }
                }.onSuccess { animals ->
                    _uiState.update { it.copy(isLoadingAnimals = false, animals = animals) }
                }
            }
        }
    }

    private fun loadFarmerProfile() {
        viewModelScope.launch {
            runCatching {
                ServiceLocator.farmerRepository.getFarmerProfile()
            }.onSuccess { result ->
                when (result) {
                    is FarmerRepository.ProfileResult.Success -> {
                        val profile = result.profile
                        if (profile != null) {
                            _uiState.update { it.copy(villageText = profile.village, isLoadingProfile = false) }
                        } else {
                            _uiState.update { it.copy(isLoadingProfile = false) }
                        }
                    }
                    is FarmerRepository.ProfileResult.Failure -> {
                        _uiState.update { it.copy(isLoadingProfile = false, error = result.message) }
                    }
                }
            }.onFailure {
                _uiState.update { it.copy(isLoadingProfile = false) }
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

    fun onVillageChanged(text: String) {
        _uiState.update { it.copy(villageText = text) }
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
        val village = state.villageText

        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true, error = null, aiAdvisory = null) }
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
                val report = SymptomReport(
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
                    village = village,
                )
                val reportId = report.id
                reportRepository.insert(report)
                runCatching {
                    alertRepository.insert(
                        Alert(
                            recipientRole = "farmer",
                            recipientId = farmerId,
                            message = "Report submitted for $animalLabel (risk $riskScore). " +
                                "A vet will review it.",
                        ),
                    )
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
                // Direct push to get aiAdvisory back immediately.
                val pushResult = ServiceLocator.reportPushApi.pushReport(report)
                when (pushResult) {
                    is ReportPushApi.PushResult.Success -> {
                        val aiAdvisory = pushResult.aiAdvisory
                        _uiState.update { it.copy(isSubmitting = false, submitted = true, aiAdvisory = aiAdvisory, lastReportId = reportId) }
                    }
                    is ReportPushApi.PushResult.Failure -> {
                        runCatching {
                            com.pashurakshak.app.data.sync.SyncScheduler.triggerNow(
                                com.pashurakshak.app.di.ServiceLocator.context,
                            )
                        }
                        _uiState.update { it.copy(isSubmitting = false, submitted = true, aiAdvisory = null, lastReportId = reportId) }
                    }
                }
            }.onFailure { error ->
                runCatching {
                    com.pashurakshak.app.data.sync.SyncScheduler.triggerNow(
                        com.pashurakshak.app.di.ServiceLocator.context,
                    )
                }
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
                villageText = _uiState.value.villageText,
            )
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    fun clearAiAdvisory() {
        _uiState.update { it.copy(aiAdvisory = null) }
    }
}
