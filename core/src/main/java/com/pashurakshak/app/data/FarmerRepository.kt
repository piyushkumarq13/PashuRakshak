package com.pashurakshak.app.data

import android.util.Log
import com.pashurakshak.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class FarmerRepository {

    data class UserProfile(
        val id: String,
        val phone: String,
        val role: String,
        val name: String,
        val email: String,
        val preferredLanguage: String,
    )

    data class FarmerProfile(
        val animalCount: Int,
        val village: String,
        val pincode: String,
    )

    sealed class Result {
        data class Success(val profile: UserProfile?) : Result()
        data class Failure(val message: String) : Result()
    }

    sealed class ProfileResult {
        data class Success(val profile: FarmerProfile?) : ProfileResult()
        data class Failure(val message: String) : ProfileResult()
    }

    /** GET /core/users/me — 404 means "needs onboarding"; 401 = expired session. */
    suspend fun getProfile(): Result = withContext(Dispatchers.IO) {
        try {
            val sessionToken = SessionManager.sessionToken
                ?: return@withContext Result.Failure("Not signed in")
            val connection = URL("${BuildConfig.API_BASE_URL}/api/v1/core/users/me")
                .openConnection() as HttpURLConnection
            try {
                connection.requestMethod = "GET"
                connection.connectTimeout = 15_000
                connection.readTimeout = 30_000
                connection.setRequestProperty("Content-Type", "application/json")
                connection.setRequestProperty("X-App-Key", BuildConfig.APP_API_KEY)
                connection.setRequestProperty("Authorization", "Bearer $sessionToken")
                val code = connection.responseCode
                if (code == 404) {
                    return@withContext Result.Success(null)
                }
                if (code == 401) {
                    return@withContext Result.Failure("Session expired. Sign in again.")
                }
                if (code !in 200..299) {
                    val body = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                    return@withContext Result.Failure("GET /users/me failed (HTTP $code): $body")
                }
                val body = connection.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(body)
                // Backend wraps the row: { user: { ... } }; name/email may be null.
                val user = json.optJSONObject("user") ?: json
                val profile = UserProfile(
                    id = user.optString("id"),
                    phone = user.optString("phone"),
                    role = user.optString("role"),
                    name = user.optString("name"),
                    email = user.optString("email"),
                    preferredLanguage = user.optString("preferred_language", "hi"),
                )
                Result.Success(profile)
            } finally {
                connection.disconnect()
            }
        } catch (error: Exception) {
            Log.w(TAG, "getProfile failed: ${error.message}")
            Result.Failure(error.message ?: "Profile check failed")
        }
    }

    /** GET /pashu-health/farmer-profiles/me — 404 means "needs onboarding". */
    suspend fun getFarmerProfile(): ProfileResult = withContext(Dispatchers.IO) {
        try {
            val sessionToken = SessionManager.sessionToken
                ?: return@withContext ProfileResult.Failure("Not signed in")
            val connection = URL("${BuildConfig.API_BASE_URL}/api/v1/pashu-health/farmer-profiles/me")
                .openConnection() as HttpURLConnection
            try {
                connection.requestMethod = "GET"
                connection.connectTimeout = 15_000
                connection.readTimeout = 30_000
                connection.setRequestProperty("Content-Type", "application/json")
                connection.setRequestProperty("X-App-Key", BuildConfig.APP_API_KEY)
                connection.setRequestProperty("Authorization", "Bearer $sessionToken")
                val code = connection.responseCode
                if (code == 404) {
                    return@withContext ProfileResult.Success(null)
                }
                if (code == 401) {
                    return@withContext ProfileResult.Failure("Session expired. Sign in again.")
                }
                if (code !in 200..299) {
                    val body = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                    return@withContext ProfileResult.Failure("GET /farmer-profiles/me failed (HTTP $code): $body")
                }
                val body = connection.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(body)
                // Backend wraps the row: { profile: { animal_count, village, pincode } }.
                val row = json.optJSONObject("profile")
                    ?: return@withContext ProfileResult.Success(null)
                val profile = FarmerProfile(
                    animalCount = row.optInt("animal_count", 0),
                    village = row.optString("village"),
                    pincode = row.optString("pincode"),
                )
                ProfileResult.Success(profile)
            } finally {
                connection.disconnect()
            }
        } catch (error: Exception) {
            Log.w(TAG, "getFarmerProfile failed: ${error.message}")
            ProfileResult.Failure(error.message ?: "Failed to load farmer profile")
        }
    }

    suspend fun createUser(name: String, email: String, phone: String, preferredLanguage: String): Result {
        return withContext(Dispatchers.IO) {
            try {
                val sessionToken = SessionManager.sessionToken
                    ?: return@withContext Result.Failure("Not signed in")
                val payload = JSONObject().apply {
                    put("phone", phone)
                    put("role", "farmer")
                    put("name", name)
                    put("email", email)
                    put("preferredLanguage", preferredLanguage)
                }
                val connection = URL("${BuildConfig.API_BASE_URL}/api/v1/core/users")
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
                        return@withContext Result.Failure("POST /users failed (HTTP $code): $body")
                    }
                    Result.Success(null)
                } finally {
                    connection.disconnect()
                }
            } catch (error: Exception) {
                Log.w(TAG, "createUser failed: ${error.message}")
                Result.Failure(error.message ?: "Create user failed")
            }
        }
    }

    suspend fun createFarmerProfile(animalCount: Int, village: String, pincode: String): Result {
        return withContext(Dispatchers.IO) {
            try {
                val sessionToken = SessionManager.sessionToken
                    ?: return@withContext Result.Failure("Not signed in")
                val payload = JSONObject().apply {
                    put("animalCount", animalCount)
                    put("village", village)
                    put("pincode", pincode)
                }
                val connection = URL("${BuildConfig.API_BASE_URL}/api/v1/pashu-health/farmer-profiles")
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
                        return@withContext Result.Failure("POST /farmer-profiles failed (HTTP $code): $body")
                    }
                    Result.Success(null)
                } finally {
                    connection.disconnect()
                }
            } catch (error: Exception) {
                Log.w(TAG, "createFarmerProfile failed: ${error.message}")
                Result.Failure(error.message ?: "Create farmer profile failed")
            }
        }
    }

    companion object {
        private const val TAG = "FarmerRepository"
    }
}
