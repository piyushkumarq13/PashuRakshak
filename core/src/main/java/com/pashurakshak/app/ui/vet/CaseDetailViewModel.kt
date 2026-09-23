package com.pashurakshak.app.ui.vet

import android.Manifest
import android.content.pm.PackageManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.pashurakshak.app.BuildConfig
import com.pashurakshak.app.data.AlertRepository
import com.pashurakshak.app.data.AnimalRepository
import com.pashurakshak.app.data.ReportRepository
import com.pashurakshak.app.data.local.Alert
import com.pashurakshak.app.data.local.Animal
import com.pashurakshak.app.data.local.ReportStatus
import com.pashurakshak.app.data.local.SymptomReport
import com.pashurakshak.app.data.SessionManager
import com.pashurakshak.app.data.sync.SyncScheduler
import com.pashurakshak.app.di.ServiceLocator
import com.pashurakshak.app.ui.farmer.prettifyStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

data class CaseDetailUiState(
    val isLoading: Boolean = true,
    val report: SymptomReport? = null,
    val animal: Animal? = null,
    val isSubmittingFieldCheck: Boolean = false,
    val isSubmittingVisit: Boolean = false,
    val notice: String? = null,
    val error: String? = null,
    val isQrScanned: Boolean = false,
    val scannedQrCodeId: String? = null,
    val assessment: String? = null,
    val assessmentText: String = "",
    val latitudeText: String = "",
    val longitudeText: String = "",
) {
    val canMarkExamined: Boolean
        get() = report != null &&
            (report.status == ReportStatus.REPORTED || report.status == ReportStatus.VET_ASSIGNED) &&
            !isSubmittingFieldCheck
    val canSubmitVisit: Boolean
        get() = isQrScanned &&
            assessment != null &&
            assessmentText.isNotBlank() &&
            latitudeText.toDoubleOrNull() != null &&
            longitudeText.toDoubleOrNull() != null &&
            !isSubmittingVisit
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
            runCatching { com.pashurakshak.app.data.sync.RemoteSync.pullAll() }
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
        val assessmentText = _uiState.value.assessmentText
        val newStatus = if (sampleRequired) ReportStatus.SAMPLE_SENT else ReportStatus.EXAMINED

        viewModelScope.launch {
            _uiState.update { it.copy(isSubmittingFieldCheck = true) }
            runCatching {
                reportRepository.update(report.copy(status = newStatus, vetAssessment = assessmentText, synced = false))
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
                        report = report.copy(status = newStatus, vetAssessment = assessmentText),
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
                        error = error.message ?: "Failed to update status",
                    )
                }
            }
        }
    }

    fun onQrScanned(qrCodeId: String) {
        _uiState.update { it.copy(isQrScanned = true, scannedQrCodeId = qrCodeId) }
    }

    fun onAssessmentSelected(assessment: String) {
        _uiState.update { it.copy(assessment = assessment) }
    }

    fun onAssessmentTextChanged(text: String) {
        _uiState.update { it.copy(assessmentText = text) }
    }

    fun onLatitudeChanged(text: String) {
        _uiState.update { it.copy(latitudeText = text) }
    }

    fun onLongitudeChanged(text: String) {
        _uiState.update { it.copy(longitudeText = text) }
    }

    fun onLocationFetched(latitude: Double, longitude: Double) {
        _uiState.update {
            it.copy(
                latitudeText = latitude.toString(),
                longitudeText = longitude.toString(),
            )
        }
    }

    fun submitVisit() {
        val state = _uiState.value
        if (!state.canSubmitVisit) return
        val report = state.report ?: return
        val visitId = UUID.randomUUID().toString()
        val vetId = SessionManager.uid ?: return
        val assessmentText = state.assessmentText
        val qrCodeId = state.scannedQrCodeId ?: return
        val assessment = state.assessment ?: return
        val latitude = state.latitudeText.toDouble()
        val longitude = state.longitudeText.toDouble()

        viewModelScope.launch {
            _uiState.update { it.copy(isSubmittingVisit = true, error = null) }
            runCatching {
                val payload = JSONObject().apply {
                    put("visitId", visitId)
                    put("reportId", report.id)
                    put("scannedQrCodeId", qrCodeId)
                    put("latitude", latitude)
                    put("longitude", longitude)
                    put("assessment", assessment)
                }
                val sessionToken = SessionManager.sessionToken ?: throw IOException("Not signed in")
                val connection = URL("${BuildConfig.API_BASE_URL}/api/v1/pashu-health/visits")
                    .openConnection() as HttpURLConnection
                try {
                    connection.requestMethod = "POST"
                    connection.connectTimeout = 15_000
                    connection.readTimeout = 30_000
                    connection.doOutput = true
                    connection.setRequestProperty("Content-Type", "application/json")
                    connection.setRequestProperty("X-App-Key", BuildConfig.APP_API_KEY)
                    connection.setRequestProperty("Authorization", "Bearer $sessionToken")
                    connection.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
                    val code = connection.responseCode
                    if (code !in 200..299) {
                        val body = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                        throw IOException("POST /visits failed (HTTP $code): $body")
                    }
                    val body = connection.inputStream.bufferedReader().use { it.readText() }
                    val json = JSONObject(body)
                    val matched = json.optBoolean("matched", false)
                    if (!matched) {
                        throw IOException("QR code did not match this animal — verification failed")
                    }
                    // Update report status with vet_assessment and assigned_vet_id
                    reportRepository.update(
                        report.copy(
                            status = ReportStatus.EXAMINED,
                            vetAssessment = assessmentText,
                            assignedVetId = vetId,
                            synced = false,
                        )
                    )
                    SyncScheduler.triggerNow(ServiceLocator.context)
                    val animalLabel = state.animal?.let { "${it.name} (${it.species})" }
                        ?: "Animal ${report.animalId.take(8)}"
                    alertRepository.insert(
                        Alert(
                            recipientRole = "farmer",
                            recipientId = report.farmerId,
                            message = "Visit verified for $animalLabel. Assessment: $assessmentText.",
                        ),
                    )
                    _uiState.update {
                        it.copy(
                            isSubmittingVisit = false,
                            notice = "Visit verified successfully",
                            report = report.copy(
                                status = ReportStatus.EXAMINED,
                                vetAssessment = assessmentText,
                                assignedVetId = vetId,
                            ),
                        )
                    }
                } finally {
                    connection.disconnect()
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isSubmittingVisit = false,
                        error = error.message ?: "Visit verification failed",
                    )
                }
            }
        }
    }

    fun clearNotice() {
        _uiState.update { it.copy(notice = null) }
    }
}
