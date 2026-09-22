package com.pashurakshak.app.data.remote

import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.pashurakshak.app.BuildConfig
import com.pashurakshak.app.data.local.SymptomReport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Pushes symptom reports to the shared-backend (POST /api/v1/pashu-health/reports).
 *
 * Plain HttpURLConnection (same pattern as B2UploadService) — no Retrofit/OkHttp.
 * Requires X-App-Key (BuildConfig.APP_API_KEY) + a Firebase ID token; on any
 * failure [pushReport] returns Failure so SyncWorker retries on the next cycle.
 */
class ReportPushApi(
    private val baseUrl: String = DEFAULT_BASE_URL,
) {

    sealed class PushResult {
        data object Success : PushResult()
        data class Failure(val message: String) : PushResult()
    }

    suspend fun pushReport(report: SymptomReport): PushResult =
        withContext(Dispatchers.IO) {
            try {
                val payload = buildPayload(report)
                // Simulated network latency so the UI can show the syncing state.
                delay(50)
                executePush(payload)
                PushResult.Success
            } catch (error: Exception) {
                Log.e(TAG, "Report push failed: ${error.message}", error)
                PushResult.Failure(error.message ?: "Push failed")
            }
        }

    private fun executePush(payload: JSONObject) {
        val idToken = currentIdToken()
        val connection = open("$baseUrl/api/v1/pashu-health/reports", "POST")
        connection.setRequestProperty("Content-Type", "application/json")
        connection.setRequestProperty("X-App-Key", BuildConfig.APP_API_KEY)
        connection.setRequestProperty("Authorization", "Bearer $idToken")
        connection.doOutput = true
        connection.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
        readJson(connection)
    }

    /** Blocking wait — must only be called from Dispatchers.IO. */
    private fun currentIdToken(): String {
        val user = FirebaseAuth.getInstance().currentUser
            ?: throw IOException("Not signed in — no Firebase user for ID token")
        return Tasks.await(user.getIdToken(false)).token
            ?: throw IOException("Firebase returned an empty ID token")
    }

    private fun buildPayload(report: SymptomReport): JSONObject =
        JSONObject().apply {
            put("id", report.id)
            put("animal_id", report.animalId)
            put("farmer_id", report.farmerId)
            put("symptoms", org.json.JSONArray(report.symptoms))
            put("photo_local_path", report.photoLocalPath)
            put("photo_remote_url", report.photoRemoteUrl ?: JSONObject.NULL)
            put("latitude", report.latitude)
            put("longitude", report.longitude)
            put("risk_score", report.riskScore)
            put("risk_breakdown", JSONObject(report.riskBreakdown).toString())
            put("status", report.status.dbValue)
            put("created_at", report.createdAt)
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
