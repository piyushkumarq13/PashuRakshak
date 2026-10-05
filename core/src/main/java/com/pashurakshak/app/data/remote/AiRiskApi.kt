package com.pashurakshak.app.data.remote

import android.util.Log
import com.pashurakshak.app.BuildConfig
import com.pashurakshak.app.data.SessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Calls POST /api/v1/pashu-health/reports/analyze-risk so the server-side AI
 * analyzes the reported disease FIRST and returns a score + analysis, which the
 * app combines with its rule-based score. On failure the caller falls back to
 * the rule-based score only.
 */
class AiRiskApi(
    private val baseUrl: String = DEFAULT_BASE_URL,
) {

    sealed class Result {
        data class Success(
            val score: Int,
            val analysis: String,
            val likelyCondition: String?,
            val severity: String?,
        ) : Result()

        data class Failure(val message: String) : Result()
    }

    suspend fun analyze(
        symptoms: List<String>,
        diseaseDescription: String?,
        species: String?,
    ): Result = withContext(Dispatchers.IO) {
        try {
            val sessionToken = SessionManager.sessionToken
                ?: throw IOException("Not signed in")
            val payload = JSONObject().apply {
                put("symptoms", JSONArray(symptoms))
                put("diseaseDescription", diseaseDescription ?: JSONObject.NULL)
                put("species", species ?: JSONObject.NULL)
            }
            val connection =
                (URL("$baseUrl/api/v1/pashu-health/reports/analyze-risk")
                    .openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 15_000
                    readTimeout = 30_000
                    doOutput = true
                    instanceFollowRedirects = false
                    setRequestProperty("Content-Type", "application/json")
                    setRequestProperty("X-App-Key", BuildConfig.APP_API_KEY)
                    setRequestProperty("Authorization", "Bearer $sessionToken")
                }
            try {
                connection.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
                val code = connection.responseCode
                val body = (if (code in 200..299) connection.inputStream else connection.errorStream)
                    ?.bufferedReader()?.use { it.readText() }.orEmpty()
                if (code !in 200..299) {
                    return@withContext Result.Failure("HTTP $code: $body")
                }
                val json = JSONObject(body)
                Result.Success(
                    score = json.optInt("score", 0).coerceIn(0, 100),
                    analysis = json.optString("analysis").orEmpty(),
                    likelyCondition = json.optString("likelyCondition").takeIf { it.isNotBlank() },
                    severity = json.optString("severity").takeIf { it.isNotBlank() },
                )
            } finally {
                connection.disconnect()
            }
        } catch (error: Exception) {
            Log.w(TAG, "AI risk analysis failed: ${error.message}")
            Result.Failure(error.message ?: "Analysis failed")
        }
    }

    companion object {
        const val TAG = "AiRiskApi"
        val DEFAULT_BASE_URL: String = BuildConfig.API_BASE_URL
    }
}
