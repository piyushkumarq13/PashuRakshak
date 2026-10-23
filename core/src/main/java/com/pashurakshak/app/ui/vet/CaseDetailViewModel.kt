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
    val isSubmittingResolution: Boolean = false,
    val notice: String? = null,
    val error: String? = null,
    val isQrScanned: Boolean = false,
    val scannedQrCodeId: String? = null,
    val qrVerified: Boolean = false,
    val assessment: String? = null,
    val assessmentText: String = "",
    val latitudeText: String = "",
    val longitudeText: String = "",
    val resolutionNote: String = "",
) {
    val isReadOnly: Boolean
        get() = report?.status == ReportStatus.RESOLVED
    val canMarkExamined: Boolean
        get() = report != null &&
            (report.status == ReportStatus.REPORTED || report.status == ReportStatus.VET_ASSIGNED) &&
            !isSubmittingFieldCheck &&
            !isReadOnly
    val canSubmitVisit: Boolean
        get() = !isReadOnly &&
            qrVerified &&
            assessment != null &&
            assessmentText.isNotBlank() &&
            latitudeText.toDoubleOrNull() != null &&
            longitudeText.toDoubleOrNull() != null &&
            !isSubmittingVisit
    val canResolve: Boolean
        get() = report != null &&
            (report.status == ReportStatus.EXAMINED ||
                report.status == ReportStatus.SAMPLE_SENT ||
                report.status == ReportStatus.CONFIRMED) &&
            !isSubmittingResolution &&
            !isSubmittingFieldCheck &&
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
            // Local-first: show the cached report immediately.
            runCatching { loadLocal() }.onSuccess { (report, animal) ->
                _uiState.update {
                    it.copy(isLoading = false, report = report, animal = animal, error = null)
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(isLoading = false, error = error.message ?: "Failed to load case")
                }
            }

            launch {
                runCatching { com.pashurakshak.app.data.sync.RemoteSync.pullAll(force = true) }
                runCatching { loadLocal() }.onSuccess { (report, animal) ->
                    _uiState.update {
                        it.copy(isLoading = false, report = report, animal = animal)
                    }
                }
            }
        }
    }

    private suspend fun loadLocal(): Pair<SymptomReport, Animal?> {
        val report = reportRepository.getById(reportId) ?: error("Report not found")
        val animal = animalRepository.getById(report.animalId)
        return report to animal
    }

    /** Called when the vet tries to mark examined without a verified QR scan. */
    fun blockUntilQrVerified() {
        _uiState.update {
            it.copy(error = "Scan the report QR first before submitting the details.")
        }
    }

    fun submitFieldCheck(sampleRequired: Boolean) {
        val report = _uiState.value.report ?: return
        if (report.status == ReportStatus.RESOLVED) return
        val assessmentText = _uiState.value.assessmentText
        val newStatus = if (sampleRequired) ReportStatus.SAMPLE_SENT else ReportStatus.EXAMINED
        // Marking a case examined without a successful QR scan is allowed for the
        // case to be tracked, but it is NOT QR-verified: the verification row is
        // written with matched = 0 so downstream screens can label it honestly.
        val verifiedThisCase = _uiState.value.qrVerified

        viewModelScope.launch {
            _uiState.update { it.copy(isSubmittingFieldCheck = true) }
            runCatching {
                reportRepository.update(report.copy(status = newStatus, vetAssessment = assessmentText, synced = false))
                // Persist the visit-verification row locally (matched = 1 only when
                // the QR actually matched this case; otherwise UNVERIFIED, matched = 0).
                runCatching {
                    val lat = _uiState.value.latitudeText.toDoubleOrNull() ?: report.latitude
                    val lon = _uiState.value.longitudeText.toDoubleOrNull() ?: report.longitude
                    reportRepository.insertVisitVerification(
                        reportId = report.id,
                        scannedQrCodeId = _uiState.value.scannedQrCodeId.orEmpty(),
                        latitude = lat,
                        longitude = lon,
                        assessment = assessmentText.ifBlank { newStatus.dbValue },
                        matched = if (verifiedThisCase) 1 else 0,
                    )
                }
                com.pashurakshak.app.data.sync.SyncScheduler.triggerNow(com.pashurakshak.app.di.ServiceLocator.context)
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

    fun onResolutionNoteChanged(text: String) {
        _uiState.update { it.copy(resolutionNote = text) }
    }

    fun resolveCase() {
        val state = _uiState.value
        val report = state.report ?: return
        if (!state.canResolve) return
        val vetId = SessionManager.uid ?: return
        val note = state.resolutionNote.ifBlank { state.assessmentText.trim() }.takeIf { it.isNotBlank() }
        val resolvedAt = System.currentTimeMillis()

        viewModelScope.launch {
            _uiState.update { it.copy(isSubmittingResolution = true, error = null) }
            runCatching {
                val resolved = report.copy(
                    status = ReportStatus.RESOLVED,
                    resolvedAt = resolvedAt,
                    resolvedBy = vetId,
                    resolutionNote = note,
                    synced = false,
                )
                reportRepository.update(resolved)
                SyncScheduler.triggerNow(ServiceLocator.context)
                val animalLabel = state.animal?.let { "${it.name} (${it.species})" }
                    ?: "Animal ${report.animalId.take(8)}"
                alertRepository.insert(
                    Alert(
                        recipientRole = "farmer",
                        recipientId = report.farmerId,
                        message = "Case resolved for $animalLabel." +
                            (note?.let { " Note: $it" } ?: ""),
                    ),
                )
                resolved
            }.onSuccess { resolved ->
                _uiState.update {
                    it.copy(
                        isSubmittingResolution = false,
                        report = resolved,
                        notice = "Case marked as resolved",
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isSubmittingResolution = false,
                        error = error.message ?: "Could not resolve the case",
                    )
                }
            }
        }
    }

    fun onQrScanned(qrCodeId: String) {
        val report = _uiState.value.report
        // First verify the QR payload's HMAC signature (anti guess/forgery), then
        // compare its embedded id against this report's QR (animal fallback for
        // legacy rows).
        val signedId = com.pashurakshak.app.data.remote.QrPayloads.verifyAndExtractId(qrCodeId)
        val expected = report?.qrCodeId?.takeIf { it.isNotBlank() }
            ?: _uiState.value.animal?.qrCodeId
        val matches = signedId != null && expected != null && signedId == expected
        _uiState.update {
            it.copy(
                isQrScanned = true,
                scannedQrCodeId = qrCodeId,
                qrVerified = matches,
                error = when {
                    signedId == null -> "QR code signature is invalid or forged."
                    !matches -> "This QR code does not match this case. Scan the QR shown for this report first."
                    else -> null
                },
                notice = if (matches) "QR verified for this case" else null,
            )
        }
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
        val resolvedAt = System.currentTimeMillis()
        val resolutionNote = assessmentText.trim().takeIf { it.isNotBlank() }

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
                    put("resolutionNote", resolutionNote ?: JSONObject.NULL)
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
                    connection.setRequestProperty("Idempotency-Key", visitId)
                    connection.setRequestProperty("Authorization", "Bearer $sessionToken")
                    connection.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
                    val code = connection.responseCode
                    if (code !in 200..299) {
                        val body = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                        // A 409 qr_mismatch means no QR verification happened, so the
                        // report status, local verification row and farmer alert are
                        // left untouched; show the friendly QR error instead.
                        val parsed = runCatching { JSONObject(body) }.getOrNull()
                        if (code == 409 && parsed?.optString("error") == "qr_mismatch") {
                            throw IOException("QR code did not match this animal")
                        }
                        throw IOException("POST /visits failed (HTTP $code): $body")
                    }
                    val body = connection.inputStream.bufferedReader().use { it.readText() }
                    val json = JSONObject(body)
                    val matched = json.optJSONObject("visit")?.optInt("matched", 0) == 1 ||
                        json.optBoolean("matched", false)
                    if (!matched) {
                        throw IOException("QR code did not match this report — verification failed")
                    }
                    // Update report status with vet_assessment and assigned_vet_id
                    reportRepository.update(
                        report.copy(
                            status = ReportStatus.RESOLVED,
                            vetAssessment = assessmentText,
                            assignedVetId = vetId,
                            synced = false,
                            qrVerified = true,
                            verifiedAt = resolvedAt,
                            verifiedBy = vetId,
                            resolvedAt = resolvedAt,
                            resolvedBy = vetId,
                            resolutionNote = resolutionNote,
                        )
                    )
                    SyncScheduler.triggerNow(ServiceLocator.context)
                    runCatching {
                        reportRepository.insertVisitVerification(
                            reportId = report.id,
                            scannedQrCodeId = qrCodeId,
                            latitude = latitude,
                            longitude = longitude,
                            assessment = assessmentText.ifBlank { assessment },
                            matched = 1,
                        )
                    }
                    val animalLabel = state.animal?.let { "${it.name} (${it.species})" }
                        ?: "Animal ${report.animalId.take(8)}"
                    alertRepository.insert(
                        Alert(
                            recipientRole = "farmer",
                            recipientId = report.farmerId,
                            message = "Visit verified and case resolved for $animalLabel. " +
                                "Assessment: $assessmentText.",
                        ),
                    )
                    _uiState.update {
                        it.copy(
                            isSubmittingVisit = false,
                            notice = "Visit verified — case resolved",
                            report = report.copy(
                                status = ReportStatus.RESOLVED,
                                vetAssessment = assessmentText,
                                assignedVetId = vetId,
                                qrVerified = true,
                                verifiedAt = resolvedAt,
                                verifiedBy = vetId,
                                resolvedAt = resolvedAt,
                                resolvedBy = vetId,
                                resolutionNote = resolutionNote,
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

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}
