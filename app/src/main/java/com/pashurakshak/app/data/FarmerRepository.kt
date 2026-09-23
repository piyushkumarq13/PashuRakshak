package com.pashurakshak.app.data

import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.pashurakshak.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
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

    suspend fun getProfile(): Result = withContext(Dispatchers.IO) {
        try {
            val idToken = currentIdToken() ?: return@withContext Result.Failure("Not signed in")
            val connection = URL("${BuildConfig.API_BASE_URL}/api/v1/core/users/me")
                .openConnection() as HttpURLConnection
            try {
                connection.requestMethod = "GET"
                connection.connectTimeout = 15_000
                connection.readTimeout = 30_000
                connection.setRequestProperty("Content-Type", "application/json")
                connection.setRequestProperty("X-App-Key", BuildConfig.APP_API_KEY)
                connection.setRequestProperty("Authorization", "Bearer $idToken")
                val code = connection.responseCode
                if (code == 404) {
                    return@withContext Result.Success(null)
                }
                if (code !in 200..299) {
                    val body = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                    return@withContext Result.Failure("GET /users/me failed (HTTP $code): $body")
                }
                val body = connection.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(body)
                val profile = UserProfile(
                    id = json.getString("id"),
                    phone = json.getString("phone"),
                    role = json.getString("role"),
                    name = json.getString("name"),
                    email = json.getString("email"),
                    preferredLanguage = json.getString("preferredLanguage"),
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

    suspend fun getFarmerProfile(): ProfileResult = withContext(Dispatchers.IO) {
        try {
            val idToken = currentIdToken() ?: return@withContext ProfileResult.Failure("Not signed in")
            val connection = URL("${BuildConfig.API_BASE_URL}/api/v1/pashu-health/farmer-profiles/me")
                .openConnection() as HttpURLConnection
            try {
                connection.requestMethod = "GET"
                connection.connectTimeout = 15_000
                connection.readTimeout = 30_000
                connection.setRequestProperty("Content-Type", "application/json")
                connection.setRequestProperty("X-App-Key", BuildConfig.APP_API_KEY)
                connection.setRequestProperty("Authorization", "Bearer $idToken")
                val code = connection.responseCode
                if (code == 404) {
                    return@withContext ProfileResult.Success(null)
                }
                if (code !in 200..299) {
                    val body = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                    return@withContext ProfileResult.Failure("GET /farmer-profiles/me failed (HTTP $code): $body")
                }
                val body = connection.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(body)
                val profile = FarmerProfile(
                    animalCount = json.getInt("animalCount"),
                    village = json.getString("village"),
                    pincode = json.getString("pincode"),
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
                val idToken = currentIdToken() ?: return@withContext Result.Failure("Not signed in")
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
                    connection.setRequestProperty("Authorization", "Bearer $idToken")
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
                val idToken = currentIdToken() ?: return@withContext Result.Failure("Not signed in")
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
                    connection.setRequestProperty("Authorization", "Bearer $idToken")
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

    private fun currentIdToken(): String? {
        val user = FirebaseAuth.getInstance().currentUser ?: return null
        return try {
            Tasks.await(user.getIdToken(false)).token
        } catch (_: Exception) { null }
    }

    companion object {
        private const val TAG = "FarmerRepository"
    }
}
