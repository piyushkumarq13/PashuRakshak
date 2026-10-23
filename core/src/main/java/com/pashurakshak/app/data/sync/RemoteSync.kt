package com.pashurakshak.app.data.sync

import android.util.Log
import com.pashurakshak.app.BuildConfig
import com.pashurakshak.app.data.PendingSyncRepository
import com.pashurakshak.app.data.SessionManager
import com.pashurakshak.app.data.cleanRemoteString
import com.pashurakshak.app.data.local.Alert
import com.pashurakshak.app.data.local.Animal
import com.pashurakshak.app.data.local.ReportStatus
import com.pashurakshak.app.data.local.SymptomReport
import com.pashurakshak.app.data.local.Vaccination
import com.pashurakshak.app.di.ServiceLocator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Bidirectional sync with shared-backend (Render):
 *  push — local animals / vaccinations / alerts (idempotent upsert by id)
 *  pull — remote reports / animals / vaccinations / alerts → local SQLite
 *
 * Report POSTs stay in SyncWorker (X-App-Key + Firebase token). GETs only need X-App-Key.
 */
object RemoteSync {

    private const val TAG = "RemoteSync"
    private const val ALL_VETS = "all-vets"
    private val baseUrl: String get() = BuildConfig.API_BASE_URL

    /** Coalesce concurrent pullAll() calls so screens don't stack network storms. */
    private val pullMutex = Mutex()
    private var lastPullAt = 0L
    private const val PULL_MIN_INTERVAL_MS = 15_000L

    suspend fun syncAll() {
        if (!SessionManager.isLoggedIn) return
        pushLocals()
        // A push may make the backend assign a vet or change a status, so pull
        // immediately instead of letting the screen-level throttle skip it.
        pullAll(force = true)
    }

    suspend fun pushAnimal(animal: Animal) {
        val pushed = withContext(Dispatchers.IO) {
            postJson(
                "/api/v1/pashu-health/animals",
                JSONObject()
                    .put("id", animal.id)
                    .put("owner_farmer_id", animal.ownerFarmerId)
                    .put("species", animal.species)
                    .put("name", animal.name)
                    .put("qr_code_id", animal.qrCodeId)
                    .put("created_at", animal.createdAt),
            )
        }
        if (pushed && ServiceLocator.animalRepository.getById(animal.id) == animal) {
            ServiceLocator.pendingSyncRepository.remove(
                PendingSyncRepository.EntityType.ANIMAL,
                animal.id,
            )
        }
    }

    suspend fun pushVaccination(vaccination: Vaccination) {
        val pushed = withContext(Dispatchers.IO) {
            postJson(
                "/api/v1/pashu-health/vaccinations",
                JSONObject()
                    .put("id", vaccination.id)
                    .put("animal_id", vaccination.animalId)
                    .put("vaccine_name", vaccination.vaccineName)
                    .put("date_given", vaccination.dateGiven)
                    .put("next_due", vaccination.nextDue),
            )
        }
        if (pushed &&
            ServiceLocator.vaccinationRepository.getById(vaccination.id) == vaccination
        ) {
            ServiceLocator.pendingSyncRepository.remove(
                PendingSyncRepository.EntityType.VACCINATION,
                vaccination.id,
            )
        }
    }

    suspend fun pushVaccinationDelete(id: String) {
        val deleted = withContext(Dispatchers.IO) {
            deleteJson("/api/v1/pashu-health/vaccinations/$id")
        }
        if (deleted) {
            ServiceLocator.pendingSyncRepository.remove(
                PendingSyncRepository.EntityType.VACCINATION,
                id,
            )
        }
    }

    suspend fun pushAnimalDelete(id: String) {
        val deleted = withContext(Dispatchers.IO) {
            deleteJson("/api/v1/pashu-health/animals/$id")
        }
        if (deleted) {
            ServiceLocator.pendingSyncRepository.remove(
                PendingSyncRepository.EntityType.ANIMAL,
                id,
            )
        }
    }

    private fun deleteJson(path: String): Boolean {
        if (baseUrl.isBlank() || !SessionManager.isLoggedIn) return false
        return try {
            val connection = open("$baseUrl$path", "DELETE")
            try {
                connection.applyAuth()
                connection.doOutput = true
                connection.outputStream.use { it.write("{}".toByteArray(Charsets.UTF_8)) }
                readJson(connection)
            } finally {
                connection.disconnect()
            }
            true
        } catch (error: Exception) {
            // A 404 means the row is already gone on the server; treat it as done
            // so a delete cannot stay stuck in the pending queue forever.
            if (error.message?.contains("HTTP 404") == true) return true
            Log.w(TAG, "DELETE $path failed: ${error.message}")
            false
        }
    }

    suspend fun pushAlert(alert: Alert) {
        val pushed = withContext(Dispatchers.IO) {
            postJson(
                "/api/v1/pashu-health/alerts",
                JSONObject()
                    .put("id", alert.id)
                    .put("recipientRole", alert.recipientRole)
                    .put("recipientId", alert.recipientId)
                    .put("message", alert.message)
                    .put("read", alert.read)
                    .put("createdAt", alert.createdAt),
            )
        }
        if (pushed && ServiceLocator.alertRepository.getById(alert.id) == alert) {
            ServiceLocator.pendingSyncRepository.remove(
                PendingSyncRepository.EntityType.ALERT,
                alert.id,
            )
        }
    }

    suspend fun pushAlertDelete(id: String) {
        val deleted = withContext(Dispatchers.IO) {
            deleteJson("/api/v1/pashu-health/alerts/$id")
        }
        if (deleted) {
            ServiceLocator.pendingSyncRepository.remove(
                PendingSyncRepository.EntityType.ALERT,
                id,
            )
        }
    }

    private suspend fun pushLocals() {
        val uid = SessionManager.uid ?: return
        val role = SessionManager.role ?: return
        val pending = ServiceLocator.pendingSyncRepository
        val animals = ServiceLocator.animalRepository
        val vaccinations = ServiceLocator.vaccinationRepository
        val alerts = ServiceLocator.alertRepository

        for (item in pending.get(PendingSyncRepository.EntityType.ANIMAL)) {
            when (item.operation) {
                PendingSyncRepository.Operation.DELETE -> {
                    pushAnimalDelete(item.entityId)
                }
                PendingSyncRepository.Operation.UPSERT -> {
                    val animal = animals.getById(item.entityId)
                    if (animal == null ||
                        (role != SessionManager.Role.VET && animal.ownerFarmerId != uid)
                    ) {
                        pending.remove(PendingSyncRepository.EntityType.ANIMAL, item.entityId)
                    } else {
                        pushAnimal(animal)
                    }
                }
            }
        }

        val ownAnimalIds = runCatching {
            animals.getAll()
                .filter { role == SessionManager.Role.VET || it.ownerFarmerId == uid }
                .map { it.id }
                .toSet()
        }.getOrDefault(emptySet())

        for (item in pending.get(PendingSyncRepository.EntityType.VACCINATION)) {
            when (item.operation) {
                PendingSyncRepository.Operation.DELETE -> {
                    pushVaccinationDelete(item.entityId)
                }
                PendingSyncRepository.Operation.UPSERT -> {
                    val vaccination = vaccinations.getById(item.entityId)
                    if (vaccination == null ||
                        (role != SessionManager.Role.VET && vaccination.animalId !in ownAnimalIds)
                    ) {
                        pending.remove(
                            PendingSyncRepository.EntityType.VACCINATION,
                            item.entityId,
                        )
                    } else {
                        pushVaccination(vaccination)
                    }
                }
            }
        }

        for (item in pending.get(PendingSyncRepository.EntityType.ALERT)) {
            when (item.operation) {
                PendingSyncRepository.Operation.DELETE -> {
                    pushAlertDelete(item.entityId)
                }
                PendingSyncRepository.Operation.UPSERT -> {
                    val alert = alerts.getById(item.entityId)
                    if (alert == null) {
                        pending.remove(PendingSyncRepository.EntityType.ALERT, item.entityId)
                    } else {
                        pushAlert(alert)
                    }
                }
            }
        }
    }

    /**
     * Pull remote → local. Animals first (FK), then vaccinations/reports/alerts in parallel.
     * Concurrent callers within [PULL_MIN_INTERVAL_MS] share the in-flight pull.
     * Pass [force] for user-initiated refreshes and post-push reconciliation.
     */
    suspend fun pullAll(force: Boolean = false) = withContext(Dispatchers.IO) {
        pullMutex.withLock {
            val now = System.currentTimeMillis()
            if (!force && now - lastPullAt < PULL_MIN_INTERVAL_MS) return@withLock
            val animalsSucceeded = pullAnimals()
            val othersSucceeded = coroutineScope {
                val vaccinations = async { pullVaccinations() }
                val reports = async { pullReports() }
                val alerts = async { pullAlerts() }
                listOf(vaccinations.await(), reports.await(), alerts.await())
            }
            // A failed pull must not suppress the next attempt for 15 seconds.
            if (animalsSucceeded || othersSucceeded.any { it }) {
                lastPullAt = System.currentTimeMillis()
            }
        }
    }

    private suspend fun pullAnimals(): Boolean {
        val body = getJson("/api/v1/pashu-health/animals") ?: return false
        val array = body.optJSONArray("animals") ?: JSONArray()
        val animals = ServiceLocator.animalRepository
        val uid = SessionManager.uid ?: return false
        val isVet = SessionManager.role == SessionManager.Role.VET
        for (i in 0 until array.length()) {
            val row = array.getJSONObject(i)
            val id = row.optString("id")
            if (id.isBlank() || (!isVet && row.optString("owner_farmer_id") != uid)) continue
            if (ServiceLocator.pendingSyncRepository.isDeletePending(
                    PendingSyncRepository.EntityType.ANIMAL,
                    id,
                )
            ) {
                continue
            }
            runCatching {
                animals.insertIfAbsent(
                    Animal(
                        id = id,
                        ownerFarmerId = row.optCleanString("owner_farmer_id").orEmpty(),
                        species = row.optCleanString("species").orEmpty(),
                        name = row.optCleanString("name").orEmpty(),
                        qrCodeId = row.optCleanString("qr_code_id").orEmpty(),
                        createdAt = row.optLong("created_at", System.currentTimeMillis()),
                    ),
                )
            }.onFailure { Log.w(TAG, "animal pull row failed: ${it.message}") }
        }
        return true
    }

    private suspend fun pullVaccinations(): Boolean {
        val body = getJson("/api/v1/pashu-health/vaccinations") ?: return false
        val array = body.optJSONArray("vaccinations") ?: JSONArray()
        val repo = ServiceLocator.vaccinationRepository
        val isVet = SessionManager.role == SessionManager.Role.VET
        val ownedAnimalIds = if (isVet) {
            emptySet()
        } else {
            runCatching { ServiceLocator.animalRepository.getAll().map { it.id }.toSet() }
                .getOrDefault(emptySet())
        }
        for (i in 0 until array.length()) {
            val row = array.getJSONObject(i)
            val id = row.optString("id")
            if (id.isBlank() || (!isVet && row.optString("animal_id") !in ownedAnimalIds)) continue
            if (ServiceLocator.pendingSyncRepository.isDeletePending(
                    PendingSyncRepository.EntityType.VACCINATION,
                    id,
                )
            ) {
                continue
            }
            runCatching {
                repo.insertIfAbsent(
                    Vaccination(
                        id = id,
                        animalId = row.optCleanString("animal_id").orEmpty(),
                        vaccineName = row.optCleanString("vaccine_name").orEmpty(),
                        dateGiven = row.optLong("date_given"),
                        nextDue = row.optLong("next_due"),
                    ),
                )
            }.onFailure { Log.w(TAG, "vaccination pull row failed: ${it.message}") }
        }
        return true
    }

    private suspend fun pullReports(): Boolean {
        val body = getJson("/api/v1/pashu-health/reports") ?: return false
        val array = body.optJSONArray("reports") ?: JSONArray()
        val repo = ServiceLocator.reportRepository
        val uid = SessionManager.uid ?: return false
        val isVet = SessionManager.role == SessionManager.Role.VET
        for (i in 0 until array.length()) {
            val row = array.getJSONObject(i)
            val id = row.optCleanString("id").orEmpty()
            val animalId = row.optCleanString("animal_id").orEmpty()
            if (id.isBlank() || animalId.isBlank()) continue
            if (!isVet && row.optString("farmer_id") != uid) continue
            runCatching {
                val symptomsJson = row.optCleanString("symptoms") ?: "[]"
                val symptomsList = runCatching {
                    JSONArray(symptomsJson).let { arr ->
                        (0 until arr.length()).map { arr.getString(it) }
                    }
                }.getOrDefault(emptyList())
                val breakdownJson = row.optCleanString("risk_breakdown") ?: "{}"
                val breakdown = runCatching {
                    val breakdownObj = JSONObject(breakdownJson)
                    val keys = breakdownObj.keys()
                    mutableMapOf<String, Int>().apply {
                        while (keys.hasNext()) {
                            val key = keys.next()
                            put(key, breakdownObj.getInt(key))
                        }
                    }
                }.getOrDefault(mutableMapOf())
                // Legacy reports have no report QR. Falling back to the animal QR
                // keeps vet scan verification working; inventing a random QR here
                // makes every scan mismatch.
                val animalQr = ServiceLocator.animalRepository.getById(animalId)?.qrCodeId
                repo.upsertFromRemote(
                    SymptomReport(
                        id = id,
                        animalId = animalId,
                        farmerId = row.optCleanString("farmer_id").orEmpty(),
                        symptoms = symptomsList,
                        photoLocalPath = "",
                        photoRemoteUrl = row.optCleanString("photo_remote_url"),
                        latitude = row.optDouble("latitude", 0.0),
                        longitude = row.optDouble("longitude", 0.0),
                        riskScore = row.optInt("risk_score", 0),
                        riskBreakdown = breakdown,
                        status = runCatching {
                            ReportStatus.fromDbValue(row.optString("status", "reported"))
                        }.getOrDefault(ReportStatus.REPORTED),
                        synced = true,
                        createdAt = row.optLong("created_at", System.currentTimeMillis()),
                        village = row.optCleanString("village"),
                        aiAdvisory = row.optCleanString("ai_advisory")
                            ?: row.optCleanString("aiAdvisory"),
                        vetAssessment = row.optCleanString("vet_assessment"),
                        assignedVetId = row.optCleanString("assigned_vet_id"),
                        qrCodeId = row.optCleanString("qr_code_id")
                            ?: cleanRemoteString(animalQr).orEmpty(),
                        diseaseDescription = row.optCleanString("disease_description"),
                        qrVerified = row.optBoolean("qrVerified", false) ||
                            row.optInt("qr_verified", 0) != 0,
                        verifiedAt = row.optLong("verifiedAt", 0L).takeIf { it > 0L }
                            ?: row.optLong("verified_at", 0L).takeIf { it > 0L },
                        verifiedBy = row.optCleanString("verified_by"),
                        resolvedAt = row.optLong("resolvedAt", 0L).takeIf { it > 0L }
                            ?: row.optLong("resolved_at", 0L).takeIf { it > 0L },
                        resolvedBy = row.optCleanString("resolved_by"),
                        resolutionNote = row.optCleanString("resolution_note"),
                    ),
                )
            }.onFailure { Log.w(TAG, "report pull row failed: ${it.message}") }
        }
        return true
    }

    private suspend fun pullAlerts(): Boolean {
        val body = getJson("/api/v1/pashu-health/alerts") ?: return false
        val array = body.optJSONArray("alerts") ?: JSONArray()
        val repo = ServiceLocator.alertRepository
        val uid = SessionManager.uid ?: return false
        val role = SessionManager.role ?: return false
        val roleValue = if (role == SessionManager.Role.VET) "vet" else "farmer"
        for (i in 0 until array.length()) {
            val row = array.getJSONObject(i)
            val id = row.optString("id")
            val recipientRole = row.optCleanString("recipient_role").orEmpty()
            val recipientId = row.optCleanString("recipient_id").orEmpty()
            val recipientMatches = recipientRole.equals(roleValue, ignoreCase = true) &&
                (recipientId == uid || (role == SessionManager.Role.VET && recipientId == ALL_VETS))
            if (id.isBlank() || !recipientMatches) continue
            if (ServiceLocator.pendingSyncRepository.isDeletePending(
                    PendingSyncRepository.EntityType.ALERT,
                    id,
                )
            ) {
                continue
            }
            runCatching {
                repo.insertIfAbsent(
                    Alert(
                        id = id,
                        recipientRole = recipientRole,
                        recipientId = recipientId,
                        message = row.optCleanString("message").orEmpty(),
                        read = row.optInt("read", 0) != 0,
                        createdAt = row.optLong("created_at", System.currentTimeMillis()),
                    ),
                )
            }.onFailure { Log.w(TAG, "alert pull row failed: ${it.message}") }
        }
        return true
    }

    private fun JSONObject.optCleanString(key: String): String? =
        cleanRemoteString(optString(key))

    private fun getJson(path: String): JSONObject? {
        if (baseUrl.isBlank() || !SessionManager.isLoggedIn) return null
        return try {
            val connection = open("$baseUrl$path", "GET")
            connection.applyAuth()
            readJson(connection)
        } catch (error: Exception) {
            Log.w(TAG, "GET $path failed: ${error.message}")
            null
        }
    }

    private fun postJson(path: String, payload: JSONObject): Boolean {
        if (baseUrl.isBlank() || !SessionManager.isLoggedIn) return false
        return try {
            val connection = open("$baseUrl$path", "POST")
            try {
                connection.setRequestProperty("Content-Type", "application/json")
                connection.applyAuth()
                connection.setRequestProperty(
                    "Idempotency-Key",
                    payload.optString("id") + ":" + payload.toString().hashCode().toString(16),
                )
                connection.doOutput = true
                connection.outputStream.use {
                    it.write(payload.toString().toByteArray(Charsets.UTF_8))
                }
                readJson(connection)
            } finally {
                connection.disconnect()
            }
            true
        } catch (error: Exception) {
            Log.w(TAG, "POST $path failed: ${error.message}")
            false
        }
    }

    private fun HttpURLConnection.applyAuth() {
        setRequestProperty("X-App-Key", BuildConfig.APP_API_KEY)
        SessionManager.sessionToken?.let { setRequestProperty("Authorization", "Bearer $it") }
    }

    private fun open(rawUrl: String, method: String): HttpURLConnection {
        val connection = URL(rawUrl).openConnection() as HttpURLConnection
        connection.requestMethod = method
        connection.connectTimeout = 8_000
        connection.readTimeout = 15_000
        connection.instanceFollowRedirects = false
        return connection
    }

    private fun readJson(connection: HttpURLConnection): JSONObject {
        try {
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                throw IOException("HTTP $code: $text")
            }
            return if (text.isBlank()) JSONObject() else JSONObject(text)
        } finally {
            connection.disconnect()
        }
    }
}
