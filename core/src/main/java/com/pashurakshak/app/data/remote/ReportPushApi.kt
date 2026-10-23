package com.pashurakshak.app.data.remote

import android.util.Log
import com.pashurakshak.app.BuildConfig
import com.pashurakshak.app.data.SessionManager
import com.pashurakshak.app.data.cleanRemoteString
import com.pashurakshak.app.data.local.SymptomReport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Pushes symptom reports to the shared-backend (POST /api/v1/pashu-health/reports).
 *
 * Plain HttpURLConnection (same pattern as B2UploadService) — no Retrofit/OkHttp.
 * Requires X-App-Key (BuildConfig.APP_API_KEY) + a backend session token; on any
 * failure [pushReport] returns Failure so SyncWorker retries on the next cycle.
 */
class ReportPushApi(
    private val baseUrl: String = DEFAULT_BASE_URL,
) {

    sealed class PushResult {
        data class Success(val aiAdvisory: String?) : PushResult()
        data class Failure(val message: String) : PushResult()
    }

    suspend fun pushReport(report: SymptomReport, skipAi: Boolean = false): PushResult =
        withContext(Dispatchers.IO) {
            try {
                val payload = buildPayload(report)
                val response = executePush(payload, skipAi)
                val aiAdvisory = response.optString("aiAdvisory").takeIf { it.isNotBlank() }
                PushResult.Success(aiAdvisory)
            } catch (error: Exception) {
                Log.e(TAG, "Report push failed: ${error.message}", error)
                PushResult.Failure(error.message ?: "Push failed")
            }
        }

    /** Blocking — must only be called from Dispatchers.IO. Returns the response JSON. */
    private fun executePush(payload: JSONObject, skipAi: Boolean): JSONObject {
        val sessionToken = currentSessionToken()
        val query = if (skipAi) "?skipAi=1" else ""
        val connection = open("$baseUrl/api/v1/pashu-health/reports$query", "POST")
        connection.setRequestProperty("Content-Type", "application/json")
        connection.setRequestProperty("X-App-Key", BuildConfig.APP_API_KEY)
        connection.setRequestProperty("Idempotency-Key", idempotencyKey(payload, skipAi))
        connection.setRequestProperty("Authorization", "Bearer $sessionToken")
        connection.doOutput = true
        connection.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
        return readJson(connection)
    }

    private fun currentSessionToken(): String =
        SessionManager.sessionToken
            ?: throw IOException("Not signed in — no session token for push")

    /**
     * A report can be re-pushed later with a new photo URL or vet assessment.
     * Keying only by report id would replay the stale cached response, so bind
     * the key to the exact payload while keeping retries of the same payload stable.
     */
    private fun idempotencyKey(payload: JSONObject, skipAi: Boolean): String {
        val body = payload.toString() + if (skipAi) ":skipAi" else ""
        val hash = MessageDigest.getInstance("SHA-256")
            .digest(body.toByteArray(Charsets.UTF_8))
            .take(12)
            .joinToString("") { "%02x".format(it) }
        return "${payload.optString("id")}:$hash"
    }

    private fun buildPayload(report: SymptomReport): JSONObject =
        JSONObject().apply {
            put("id", report.id)
            put("animal_id", report.animalId)
            put("farmer_id", report.farmerId)
            put("symptoms", org.json.JSONArray(report.symptoms))
            put("photo_local_path", cleanRemoteString(report.photoLocalPath).orEmpty())
            putNullable("photo_remote_url", report.photoRemoteUrl)
            put("latitude", report.latitude)
            put("longitude", report.longitude)
            putNullable("village", report.village)
            putNullable("assigned_vet_id", report.assignedVetId)
            putNullable("vet_assessment", report.vetAssessment)
            put("risk_score", report.riskScore)
            put("risk_breakdown", JSONObject(report.riskBreakdown).toString())
            put("status", report.status.dbValue)
            put("created_at", report.createdAt)
            putNullable("qr_code_id", report.qrCodeId)
            putNullable("disease_description", report.diseaseDescription)
            put("resolved_at", report.resolvedAt ?: JSONObject.NULL)
            putNullable("resolved_by", report.resolvedBy)
            putNullable("resolution_note", report.resolutionNote)
        }

    private fun JSONObject.putNullable(key: String, value: String?) {
        put(key, cleanRemoteString(value) ?: JSONObject.NULL)
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
            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                throw PushHttpException(code, body)
            }
            return if (body.isBlank()) JSONObject() else JSONObject(body)
        } finally {
            connection.disconnect()
        }
    }

    private class PushHttpException(val code: Int, message: String) : IOException(
        "Backend HTTP $code: $message",
    )

    companion object {
        const val TAG = "ReportPushApi"
        val DEFAULT_BASE_URL: String = BuildConfig.API_BASE_URL
    }
}
