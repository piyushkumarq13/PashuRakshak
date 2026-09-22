package com.pashurakshak.app.data.sync

import android.util.Log
import com.pashurakshak.app.BuildConfig
import com.pashurakshak.app.data.AlertRepository
import com.pashurakshak.app.data.AnimalRepository
import com.pashurakshak.app.data.ReportRepository
import com.pashurakshak.app.data.VaccinationRepository
import com.pashurakshak.app.data.local.Alert
import com.pashurakshak.app.data.local.Animal
import com.pashurakshak.app.data.local.ReportStatus
import com.pashurakshak.app.data.local.SymptomReport
import com.pashurakshak.app.data.local.Vaccination
import com.pashurakshak.app.di.ServiceLocator
import kotlinx.coroutines.Dispatchers
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
    private val baseUrl: String get() = BuildConfig.API_BASE_URL

    suspend fun syncAll() {
        pushLocals()
        pullAll()
    }

    suspend fun pushAnimal(animal: Animal) = withContext(Dispatchers.IO) {
        postJson(
            "/api/v1/pashu-health/animals",
            JSONObject()
                .put("id", animal.id)
                .put("ownerFarmerId", animal.ownerFarmerId)
                .put("species", animal.species)
                .put("name", animal.name)
                .put("qrCodeId", animal.qrCodeId)
                .put("createdAt", animal.createdAt),
        )
    }

    suspend fun pushVaccination(vaccination: Vaccination) = withContext(Dispatchers.IO) {
        postJson(
            "/api/v1/pashu-health/vaccinations",
            JSONObject()
                .put("id", vaccination.id)
                .put("animalId", vaccination.animalId)
                .put("vaccineName", vaccination.vaccineName)
                .put("dateGiven", vaccination.dateGiven)
                .put("nextDue", vaccination.nextDue),
        )
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
        runCatching {
            ServiceLocator.animalRepository.getAll().forEach { animal ->
                runCatching { pushAnimal(animal) }
            }
        }
        runCatching {
            ServiceLocator.vaccinationRepository.getAll().forEach { v ->
                runCatching { pushVaccination(v) }
            }
        }
        runCatching {
            ServiceLocator.alertRepository.getAll().forEach { a ->
                runCatching { pushAlert(a) }
            }
        }
    }

    /** Pull remote → local. Order matters (FK): animals → vaccinations → reports → alerts. */
    suspend fun pullAll() = withContext(Dispatchers.IO) {
        pullAnimals()
        pullVaccinations()
        pullReports()
        pullAlerts()
    }

    private suspend fun pullAnimals() {
        val body = getJson("/api/v1/pashu-health/animals") ?: return
        val array = body.optJSONArray("animals") ?: JSONArray()
        val animals = ServiceLocator.animalRepository
        for (i in 0 until array.length()) {
            val row = array.getJSONObject(i)
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
        for (i in 0 until array.length()) {
            val row = array.getJSONObject(i)
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
        for (i in 0 until array.length()) {
            val row = array.getJSONObject(i)
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
                    ),
                )
            }.onFailure { Log.w(TAG, "report pull row failed: ${it.message}") }
        }
    }

    private suspend fun pullAlerts() {
        val body = getJson("/api/v1/pashu-health/alerts") ?: return
        val array = body.optJSONArray("alerts") ?: JSONArray()
        val repo = ServiceLocator.alertRepository
        for (i in 0 until array.length()) {
            val row = array.getJSONObject(i)
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
        if (baseUrl.isBlank()) return null
        return try {
            val connection = open("$baseUrl$path", "GET")
            connection.setRequestProperty("X-App-Key", BuildConfig.APP_API_KEY)
            readJson(connection)
        } catch (error: Exception) {
            Log.w(TAG, "GET $path failed: ${error.message}")
            null
        }
    }

    private fun postJson(path: String, payload: JSONObject) {
        if (baseUrl.isBlank()) return
        val connection = open("$baseUrl$path", "POST")
        try {
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("X-App-Key", BuildConfig.APP_API_KEY)
            connection.doOutput = true
            connection.outputStream.use {
                it.write(payload.toString().toByteArray(Charsets.UTF_8))
            }
            readJson(connection)
        } finally {
            connection.disconnect()
        }
    }

    private fun open(rawUrl: String, method: String): HttpURLConnection {
        val connection = URL(rawUrl).openConnection() as HttpURLConnection
        connection.requestMethod = method
        connection.connectTimeout = 15_000
        connection.readTimeout = 30_000
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
