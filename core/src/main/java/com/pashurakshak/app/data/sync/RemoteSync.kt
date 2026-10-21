package com.pashurakshak.app.data.sync

import android.util.Log
import com.pashurakshak.app.BuildConfig
import com.pashurakshak.app.data.AlertRepository
import com.pashurakshak.app.data.AnimalRepository
import com.pashurakshak.app.data.ReportRepository
import com.pashurakshak.app.data.SessionManager
import com.pashurakshak.app.data.VaccinationRepository
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
    private const val PULL_MIN_INTERVAL_MS = 4_000L

    suspend fun syncAll() {
        if (!SessionManager.isLoggedIn) return
        pushLocals()
        pullAll()
    }

    suspend fun pushAnimal(animal: Animal) = withContext(Dispatchers.IO) {
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

    suspend fun pushVaccination(vaccination: Vaccination) = withContext(Dispatchers.IO) {
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

    suspend fun pushVaccinationDelete(id: String) = withContext(Dispatchers.IO) {
        deleteJson("/api/v1/pashu-health/vaccinations/$id")
    }

    suspend fun pushAnimalDelete(id: String) = withContext(Dispatchers.IO) {
        deleteJson("/api/v1/pashu-health/animals/$id")
    }

    private fun deleteJson(path: String) {
        if (baseUrl.isBlank() || !SessionManager.isLoggedIn) return
        val connection = open("$baseUrl$path", "DELETE")
        try {
            connection.applyAuth()
            connection.doOutput = true
            connection.outputStream.use { it.write("{}".toByteArray(Charsets.UTF_8)) }
            readJson(connection)
        } finally {
            connection.disconnect()
        }
    }

    suspend fun pushAlert(alert: Alert) = withContext(Dispatchers.IO) {
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

    private suspend fun pushLocals() {
        val uid = SessionManager.uid ?: return
        val role = SessionManager.role ?: return
        val ownAnimals = runCatching {
            ServiceLocator.animalRepository.getAll().filter {
                role == SessionManager.Role.VET || it.ownerFarmerId == uid
            }
        }.getOrDefault(emptyList())
        ownAnimals.forEach { animal ->
            runCatching { pushAnimal(animal) }
        }

        val ownAnimalIds = ownAnimals.map { it.id }.toSet()
        runCatching {
            ServiceLocator.vaccinationRepository.getAll()
                .filter { role == SessionManager.Role.VET || it.animalId in ownAnimalIds }
                .forEach { v -> runCatching { pushVaccination(v) } }
        }
        runCatching {
            ServiceLocator.alertRepository.getAll().forEach { a ->
                runCatching { pushAlert(a) }
            }
        }
    }

    /**
     * Pull remote → local. Animals first (FK), then vaccinations/reports/alerts in parallel.
     * Concurrent callers within [PULL_MIN_INTERVAL_MS] share the in-flight pull.
     */
    suspend fun pullAll() = withContext(Dispatchers.IO) {
        pullMutex.withLock {
            val now = System.currentTimeMillis()
            if (now - lastPullAt < PULL_MIN_INTERVAL_MS) return@withLock
            pullAnimals()
            coroutineScope {
                val vaccinations = async { pullVaccinations() }
                val reports = async { pullReports() }
                val alerts = async { pullAlerts() }
                vaccinations.await()
                reports.await()
                alerts.await()
            }
            lastPullAt = System.currentTimeMillis()
        }
    }

    private suspend fun pullAnimals() {
        val body = getJson("/api/v1/pashu-health/animals") ?: return
        val array = body.optJSONArray("animals") ?: JSONArray()
        val animals = ServiceLocator.animalRepository
        val uid = SessionManager.uid ?: return
        val isVet = SessionManager.role == SessionManager.Role.VET
        for (i in 0 until array.length()) {
            val row = array.getJSONObject(i)
            if (!isVet && row.optString("owner_farmer_id") != uid) continue
            runCatching {
                animals.insertIfAbsent(
                    Animal(
                        id = row.optString("id"),
                        ownerFarmerId = row.optString("owner_farmer_id"),
                        species = row.optString("species"),
                        name = row.optString("name"),
                        qrCodeId = row.optString("qr_code_id"),
                        createdAt = row.optLong("created_at", System.currentTimeMillis()),
                    ),
                )
            }.onFailure { Log.w(TAG, "animal pull row failed: ${it.message}") }
        }
    }

    private suspend fun pullVaccinations() {
        val body = getJson("/api/v1/pashu-health/vaccinations") ?: return
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
            if (!isVet && row.optString("animal_id") !in ownedAnimalIds) continue
            runCatching {
                repo.insertIfAbsent(
                    Vaccination(
                        id = row.optString("id"),
                        animalId = row.optString("animal_id"),
                        vaccineName = row.optString("vaccine_name"),
                        dateGiven = row.optLong("date_given"),
                        nextDue = row.optLong("next_due"),
                    ),
                )
            }.onFailure { Log.w(TAG, "vaccination pull row failed: ${it.message}") }
        }
    }

    private suspend fun pullReports() {
        val body = getJson("/api/v1/pashu-health/reports") ?: return
        val array = body.optJSONArray("reports") ?: JSONArray()
        val repo = ServiceLocator.reportRepository
        val uid = SessionManager.uid ?: return
        val isVet = SessionManager.role == SessionManager.Role.VET
        for (i in 0 until array.length()) {
            val row = array.getJSONObject(i)
            if (!isVet && row.optString("farmer_id") != uid) continue
            runCatching {
                val symptomsJson = row.optString("symptoms", "[]")
                val symptomsList = JSONArray(symptomsJson).let { arr ->
                    (0 until arr.length()).map { arr.getString(it) }
                }
                val breakdownJson = row.optString("risk_breakdown", "{}")
                val breakdownObj = JSONObject(breakdownJson)
                val breakdown = mutableMapOf<String, Int>()
                val keys = breakdownObj.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    breakdown[key] = breakdownObj.getInt(key)
                }
                repo.upsertFromRemote(
                    SymptomReport(
                        id = row.optString("id"),
                        animalId = row.optString("animal_id"),
                        farmerId = row.optString("farmer_id"),
                        symptoms = symptomsList,
                        photoLocalPath = "",
                        photoRemoteUrl = row.optString("photo_remote_url").takeIf { it.isNotBlank() },
                        latitude = row.optDouble("latitude", 0.0),
                        longitude = row.optDouble("longitude", 0.0),
                        riskScore = row.optInt("risk_score", 0),
                        riskBreakdown = breakdown,
                        status = runCatching {
                            ReportStatus.fromDbValue(row.optString("status", "reported"))
                        }.getOrDefault(ReportStatus.REPORTED),
                        synced = true,
                        createdAt = row.optLong("created_at", System.currentTimeMillis()),
                        village = row.optString("village", ""),
                        aiAdvisory = row.optString("aiAdvisory").takeIf { it.isNotBlank() },
                        vetAssessment = row.optString("vet_assessment", "").takeIf { it.isNotBlank() },
                        assignedVetId = row.optString("assigned_vet_id", "").takeIf { it.isNotBlank() },
                        qrCodeId = row.optString("qr_code_id").takeIf { it.isNotBlank() }
                            ?: "QR-" + java.util.UUID.randomUUID().toString().uppercase(),
                        diseaseDescription = row.optString("disease_description").takeIf { it.isNotBlank() },
                        qrVerified = row.optBoolean("qrVerified", false) ||
                            row.optInt("qr_verified", 0) != 0,
                        verifiedAt = row.optLong("verifiedAt", 0L).takeIf { it > 0L }
                            ?: row.optLong("verified_at", 0L).takeIf { it > 0L },
                        verifiedBy = row.optString("verified_by").takeIf { it.isNotBlank() },
                    ),
                )
            }.onFailure { Log.w(TAG, "report pull row failed: ${it.message}") }
        }
    }

    private suspend fun pullAlerts() {
        val body = getJson("/api/v1/pashu-health/alerts") ?: return
        val array = body.optJSONArray("alerts") ?: JSONArray()
        val repo = ServiceLocator.alertRepository
        val uid = SessionManager.uid ?: return
        val role = SessionManager.role ?: return
        val roleValue = if (role == SessionManager.Role.VET) "vet" else "farmer"
        for (i in 0 until array.length()) {
            val row = array.getJSONObject(i)
            val recipientRole = row.optString("recipient_role")
            val recipientId = row.optString("recipient_id")
            val recipientMatches = recipientRole.equals(roleValue, ignoreCase = true) &&
                (recipientId == uid || (role == SessionManager.Role.VET && recipientId == ALL_VETS))
            if (!recipientMatches) continue
            runCatching {
                repo.insertIfAbsent(
                    Alert(
                        id = row.optString("id"),
                        recipientRole = row.optString("recipient_role"),
                        recipientId = row.optString("recipient_id"),
                        message = row.optString("message"),
                        read = row.optInt("read", 0) != 0,
                        createdAt = row.optLong("created_at", System.currentTimeMillis()),
                    ),
                )
            }.onFailure { Log.w(TAG, "alert pull row failed: ${it.message}") }
        }
    }

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

    private fun postJson(path: String, payload: JSONObject) {
        if (baseUrl.isBlank() || !SessionManager.isLoggedIn) return
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
